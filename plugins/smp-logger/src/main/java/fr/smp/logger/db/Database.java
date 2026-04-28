package fr.smp.logger.db;

import fr.smp.logger.SMPLogger;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dedicated SQLite handle for the logger. Kept independent of core-paper's DB so:
 *  - schema churn / partition DDL never touches gameplay data
 *  - the logger can VACUUM / DROP TABLE freely without locking core queries
 *  - the file can be backed up / rotated separately
 *
 * One writer connection (used by {@link fr.smp.logger.queue.EventQueue}) plus a
 * small reader pool for /lookup commands. WAL gives us unlimited concurrent reads.
 */
public class Database {

    private static final int READER_POOL_SIZE = 4;
    private static final long BORROW_TIMEOUT_SEC = 10;

    private final SMPLogger plugin;
    private String url;
    private File dbFile;

    private Connection writer;                       // single dedicated writer
    private final BlockingQueue<Connection> readers = new ArrayBlockingQueue<>(READER_POOL_SIZE);
    private final List<Connection> all = new ArrayList<>();
    private final ReentrantLock writerLock = new ReentrantLock(true);
    private volatile boolean closed = false;

    public Database(SMPLogger plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String dirPath = plugin.getConfig().getString("storage.directory", "../shared-data/smplogger");
        String fileName = plugin.getConfig().getString("storage.filename", "smplogger.db");
        File dir = new File(plugin.getServer().getWorldContainer(), dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Could not create logger storage dir: " + dir.getAbsolutePath());
        }
        this.dbFile = new File(dir, fileName);
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        // Bootstrap: set page_size BEFORE any table ever exists, since it cannot
        // be changed later without VACUUM.
        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.execute("PRAGMA page_size=4096");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA temp_store=MEMORY");
            s.execute("PRAGMA mmap_size=268435456");          // 256 MB
            s.execute("PRAGMA auto_vacuum=INCREMENTAL");
            s.execute("PRAGMA wal_autocheckpoint=2000");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init logger SQLite", e);
        }

        // Create the cross-day dictionary + cross-day tables. Daily partitions are
        // created on demand by PartitionManager.
        new SchemaInit(plugin, url).run();

        // Single writer connection — owned by EventQueue's writer thread.
        this.writer = openConnection();

        // Reader pool — used by /lookup, /seen, /trades, etc.
        for (int i = 0; i < READER_POOL_SIZE; i++) {
            Connection c = openConnection();
            readers.add(c);
            all.add(c);
        }
        all.add(writer);

        plugin.getLogger().info("SMPLogger SQLite ready: " + dbFile.getAbsolutePath()
                + " (writer=1, readers=" + READER_POOL_SIZE + ")");
    }

    private Connection openConnection() {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA temp_store=MEMORY");
                s.execute("PRAGMA busy_timeout=10000");
                s.execute("PRAGMA cache_size=-32768");        // 32 MB cache per conn
            }
            return c;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open logger connection", e);
        }
    }

    /**
     * Borrow the single writer connection.
     *
     * The returned proxy must be closed by callers, but close() only releases the
     * writer lock. It never closes the underlying SQLite connection.
     */
    public Connection writer() throws SQLException {
        writerLock.lock();
        try {
            if (closed || writer == null || writer.isClosed()) {
                throw new SQLException("Logger DB is closed");
            }
            Connection target = writer;
            InvocationHandler h = new InvocationHandler() {
                private boolean released = false;

                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                        release();
                        return null;
                    }
                    if ("isClosed".equals(method.getName()) && (args == null || args.length == 0)) {
                        return released || closed || target.isClosed();
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (java.lang.reflect.InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }

                private void release() {
                    if (!released) {
                        released = true;
                        writerLock.unlock();
                    }
                }
            };
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, h);
        } catch (SQLException | RuntimeException e) {
            writerLock.unlock();
            throw e;
        }
    }

    /** Borrow a reader from the pool. Returns a Proxy whose close() recycles. */
    public Connection reader() throws SQLException {
        if (closed) throw new SQLException("Logger DB is closed");
        Connection real;
        try {
            real = readers.poll(BORROW_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted acquiring reader", e);
        }
        if (real == null) throw new SQLException("Reader pool exhausted");
        if (real.isClosed()) {
            Connection fresh = openConnection();
            synchronized (all) { all.remove(real); all.add(fresh); }
            real = fresh;
        }
        final Connection target = real;
        InvocationHandler h = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                if (!closed && !target.isClosed()) readers.offer(target);
                else try { target.close(); } catch (SQLException ignored) {}
                return null;
            }
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, h);
    }

    public File file() { return dbFile; }

    public void close() {
        closed = true;
        writerLock.lock();
        try {
            synchronized (all) {
                for (Connection c : all) {
                    try { c.close(); } catch (SQLException ignored) {}
                }
                all.clear();
                readers.clear();
                writer = null;
            }
        } finally {
            writerLock.unlock();
        }
    }
}
