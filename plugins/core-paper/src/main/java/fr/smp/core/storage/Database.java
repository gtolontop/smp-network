package fr.smp.core.storage;

import fr.smp.core.SMPCore;

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

public class Database {

    /**
     * SQLite in WAL mode supports unlimited concurrent readers + one writer, so a small
     * pool of 8 real connections is plenty. The previous implementation opened a fresh
     * connection per query (DriverManager.getConnection + PRAGMA) which cost ~1-5ms each
     * and dominated command latency under load.
     */
    private static final int POOL_SIZE = 8;
    private static final long BORROW_TIMEOUT_SEC = 10;

    private final SMPCore plugin;
    private String url;
    private final BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(POOL_SIZE);
    private final List<Connection> allConnections = new ArrayList<>();
    private volatile boolean closed = false;

    public Database(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        File dir = new File(plugin.getConfig().getString(
                "storage.directory", "../shared-data"));
        if (!dir.exists()) dir.mkdirs();

        File dbFile = new File(dir, "smp.db");
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }

        // Initialize WAL + schema on a bootstrap connection, then build the pool.
        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException(e);
        }

        createTables();

        // Populate pool with long-lived connections. Each connection gets its PRAGMAs
        // set once — the old code ran PRAGMA foreign_keys=ON on every acquire.
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                Connection c = DriverManager.getConnection(url);
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA foreign_keys=ON");
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("PRAGMA synchronous=NORMAL");
                    s.execute("PRAGMA busy_timeout=5000");
                }
                pool.add(c);
                allConnections.add(c);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to build connection pool: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        plugin.getLogger().info("SQLite connected: " + dbFile.getAbsolutePath() + " (pool=" + POOL_SIZE + ")");
    }

    public void close() {
        closed = true;
        for (Connection c : allConnections) {
            try { c.close(); } catch (SQLException ignored) {}
        }
        pool.clear();
        allConnections.clear();
    }

    /**
     * Borrow a pooled connection. Returned object is a Proxy whose close() returns the
     * real connection to the pool instead of physically closing it. Safe with the
     * existing try-with-resources call sites.
     */
    public Connection get() throws SQLException {
        if (closed) throw new SQLException("Database pool is closed");
        Connection real;
        try {
            real = pool.poll(BORROW_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while acquiring connection", e);
        }
        if (real == null) {
            throw new SQLException("Connection pool exhausted after " + BORROW_TIMEOUT_SEC + "s");
        }
        // If the real connection silently died (driver-side close, e.g. from a fatal IO),
        // replace it transparently so the pool self-heals instead of returning a dead conn.
        if (real.isClosed()) {
            Connection fresh = DriverManager.getConnection(url);
            try (Statement s = fresh.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA busy_timeout=5000");
            }
            synchronized (allConnections) {
                allConnections.remove(real);
                allConnections.add(fresh);
            }
            real = fresh;
        }
        return wrap(real);
    }

    private Connection wrap(Connection real) {
        InvocationHandler h = (proxy, method, args) -> {
            if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                if (!closed && !real.isClosed()) {
                    pool.offer(real);
                } else {
                    try { real.close(); } catch (SQLException ignored) {}
                }
                return null;
            }
            if ("isWrapperFor".equals(method.getName())) {
                Class<?> target = (Class<?>) args[0];
                return target.isAssignableFrom(real.getClass()) || real.isWrapperFor(target);
            }
            if ("unwrap".equals(method.getName())) {
                Class<?> target = (Class<?>) args[0];
                if (target.isAssignableFrom(real.getClass())) return real;
                return real.unwrap(target);
            }
            try {
                return method.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw ite.getTargetException();
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                h);
    }

    private void createTables() {
        String[] stmts = {
            """
            CREATE TABLE IF NOT EXISTS players (
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              money REAL NOT NULL DEFAULT 0,
              shards INTEGER NOT NULL DEFAULT 0,
              kills INTEGER NOT NULL DEFAULT 0,
              deaths INTEGER NOT NULL DEFAULT 0,
              playtime_sec INTEGER NOT NULL DEFAULT 0,
              first_join INTEGER NOT NULL,
              last_seen INTEGER NOT NULL,
              team_id TEXT,
              scoreboard INTEGER NOT NULL DEFAULT 1,
              fullbright INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS homes (
              uuid TEXT NOT NULL,
              slot INTEGER NOT NULL,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY(uuid, slot)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS warps (
              name TEXT PRIMARY KEY,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              material TEXT NOT NULL DEFAULT 'COMPASS',
              description TEXT,
              created_by TEXT,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS teams (
              id TEXT PRIMARY KEY,
              tag TEXT NOT NULL UNIQUE,
              name TEXT NOT NULL,
              color TEXT NOT NULL DEFAULT '<white>',
              owner TEXT NOT NULL,
              balance REAL NOT NULL DEFAULT 0,
              home_world TEXT, home_x REAL, home_y REAL, home_z REAL,
              home_yaw REAL, home_pitch REAL,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS team_members (
              team_id TEXT NOT NULL,
              uuid TEXT NOT NULL UNIQUE,
              role TEXT NOT NULL DEFAULT 'MEMBER',
              joined_at INTEGER NOT NULL,
              PRIMARY KEY(team_id, uuid),
              FOREIGN KEY(team_id) REFERENCES teams(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS auctions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              seller TEXT NOT NULL,
              seller_name TEXT NOT NULL,
              item_data BLOB NOT NULL,
              price REAL NOT NULL,
              listed_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL,
              sold INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mailbox (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid TEXT NOT NULL,
              kind TEXT NOT NULL,
              payload BLOB,
              amount REAL NOT NULL DEFAULT 0,
              message TEXT,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS waypoints (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_type TEXT NOT NULL,     -- 'solo' | 'team'
              owner_id TEXT NOT NULL,       -- uuid for solo, team_id for team
              name TEXT NOT NULL,
              server TEXT NOT NULL,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              color TEXT,
              created_at INTEGER NOT NULL,
              UNIQUE(owner_type, owner_id, name)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mod_bans (
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              issuer TEXT NOT NULL,
              reason TEXT,
              issued_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL  -- 0 for permanent
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mod_mutes (
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              issuer TEXT NOT NULL,
              reason TEXT,
              issued_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mod_ip_bans (
              ip TEXT PRIMARY KEY,
              uuid TEXT NOT NULL,
              name TEXT NOT NULL,
              issuer TEXT NOT NULL,
              reason TEXT,
              issued_at INTEGER NOT NULL,
              expires_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS mod_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid TEXT NOT NULL,
              name TEXT NOT NULL,
              action TEXT NOT NULL,        -- kick | ban | unban | mute | unmute
              issuer TEXT NOT NULL,
              reason TEXT,
              duration TEXT,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bounties (
              target_uuid TEXT PRIMARY KEY,
              target_name TEXT NOT NULL,
              amount REAL NOT NULL,
              last_issuer TEXT,
              last_issuer_name TEXT,
              contributors INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bounty_contributions (
              target_uuid TEXT NOT NULL,
              issuer_uuid TEXT NOT NULL,
              issuer_name TEXT NOT NULL,
              amount REAL NOT NULL DEFAULT 0,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY (target_uuid, issuer_uuid)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_bounty_contrib_target ON bounty_contributions(target_uuid, amount DESC)",
            """
            CREATE TABLE IF NOT EXISTS hunted_state (
              id INTEGER PRIMARY KEY CHECK (id = 1),
              date TEXT NOT NULL,
              bounty_used INTEGER NOT NULL DEFAULT 0,
              target_uuid TEXT,
              target_name TEXT,
              target_kills INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS spawners (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              world TEXT NOT NULL,
              x INTEGER NOT NULL,
              y INTEGER NOT NULL,
              z INTEGER NOT NULL,
              type TEXT NOT NULL,
              stack INTEGER NOT NULL DEFAULT 1,
              UNIQUE(world, x, y, z)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS spawner_loot (
              spawner_id INTEGER NOT NULL,
              material TEXT NOT NULL,
              amount INTEGER NOT NULL,
              PRIMARY KEY (spawner_id, material),
              FOREIGN KEY (spawner_id) REFERENCES spawners(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS auth_accounts (
              name_lower TEXT PRIMARY KEY,
              password_hash TEXT,                -- pbkdf2$<iter>$<saltB64>$<hashB64>; null = premium-only
              premium_uuid TEXT,                 -- last known premium UUID, null if never premium
              cracked_uuid TEXT,                 -- last known offline UUID, null if never cracked
              registered_at INTEGER NOT NULL,
              last_login INTEGER NOT NULL DEFAULT 0,
              last_ip TEXT,
              failed_attempts INTEGER NOT NULL DEFAULT 0,
              locked_until INTEGER NOT NULL DEFAULT 0,
              must_rechange INTEGER NOT NULL DEFAULT 0   -- admin-forced re-register on next join
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS player_skins (
              name_lower TEXT PRIMARY KEY,
              mode TEXT NOT NULL,                -- default | random | taken
              skin_owner TEXT NOT NULL,
              skin_value TEXT NOT NULL,
              skin_signature TEXT,
              updated_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS gates (
              name TEXT PRIMARY KEY,
              server TEXT NOT NULL,
              world TEXT NOT NULL,
              x1 INTEGER NOT NULL, y1 INTEGER NOT NULL, z1 INTEGER NOT NULL,
              x2 INTEGER NOT NULL, y2 INTEGER NOT NULL, z2 INTEGER NOT NULL,
              radius REAL NOT NULL DEFAULT 5.0,
              blocks BLOB NOT NULL,
              created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS npcs (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              server TEXT NOT NULL,
              display_name TEXT NOT NULL,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              skin_owner TEXT,
              skin_value TEXT,
              skin_signature TEXT,
              wander INTEGER NOT NULL DEFAULT 0,
              wander_radius REAL NOT NULL DEFAULT 5.0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS holograms (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              server TEXT NOT NULL,
              name TEXT NOT NULL UNIQUE,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              lines TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS inv_snapshots (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid TEXT NOT NULL,
              name TEXT NOT NULL,
              source TEXT NOT NULL,           -- periodic | quit | manual | preapply | shutdown
              server TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              yaml TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS leaderboard_stats (
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              distance_cm INTEGER NOT NULL DEFAULT 0,
              updated_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller, sold)",
            "CREATE INDEX IF NOT EXISTS idx_auctions_active ON auctions(sold, expires_at)",
            "CREATE INDEX IF NOT EXISTS idx_mailbox_uuid ON mailbox(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_homes_uuid ON homes(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_team_members_uuid ON team_members(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_waypoints_owner ON waypoints(owner_type, owner_id)",
            "CREATE INDEX IF NOT EXISTS idx_mod_history_uuid ON mod_history(uuid, created_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_mod_ip_bans_uuid ON mod_ip_bans(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_spawner_loot_id ON spawner_loot(spawner_id)",
            "CREATE INDEX IF NOT EXISTS idx_inv_snapshots_uuid ON inv_snapshots(uuid, created_at DESC)"
        };

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            for (String sql : stmts) s.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
            throw new RuntimeException(e);
        }

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            try { s.execute("ALTER TABLE players ADD COLUMN shards_last_mc_min INTEGER NOT NULL DEFAULT -1"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN fullbright INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE homes ADD COLUMN server TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE warps ADD COLUMN server TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE teams ADD COLUMN home_server TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN daily_kills INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN daily_kills_date TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN survival_joined INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_world TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_x REAL"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_y REAL"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_z REAL"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_yaw REAL"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN last_pitch REAL"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE hunted_state ADD COLUMN target_uuid TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE hunted_state ADD COLUMN target_name TEXT"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE hunted_state ADD COLUMN target_kills INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE players ADD COLUMN nickname TEXT"); }
            catch (SQLException ignored) {}
        } catch (SQLException ignored) {}
    }
}
