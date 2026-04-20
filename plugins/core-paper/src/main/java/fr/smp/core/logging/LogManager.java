package fr.smp.core.logging;

import fr.smp.core.SMPCore;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Async, category-based rotating logger.
 * Each category writes to logs/{category}/{yyyy-MM-dd}.log.
 * Also mirrors to a combined logs/all/{date}.log.
 */
public class LogManager {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SMPCore plugin;
    private final File root;
    private final Map<LogCategory, BufferedWriter> writers = new EnumMap<>(LogCategory.class);
    private final Map<LogCategory, LocalDate> writerDay = new EnumMap<>(LogCategory.class);
    private BufferedWriter combined;
    private LocalDate combinedDay;

    private final BlockingQueue<Entry> queue = new ArrayBlockingQueue<>(50_000);
    private volatile boolean running = true;
    private Thread worker;

    public LogManager(SMPCore plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "logs");
        if (!root.exists()) root.mkdirs();
    }

    public void start() {
        worker = new Thread(this::loop, "SMPCore-Log");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
        synchronized (writers) {
            writers.values().forEach(this::closeQuiet);
            writers.clear();
            writerDay.clear();
            closeQuiet(combined);
            combined = null;
        }
    }

    public void log(LogCategory cat, String msg) {
        queue.offer(new Entry(LocalDateTime.now(), cat, msg));
    }

    public void log(LogCategory cat, Player p, String msg) {
        log(cat, "[" + p.getName() + "/" + p.getUniqueId() + "] " + msg);
    }

    private void loop() {
        while (running) {
            try {
                Entry e = queue.take();
                write(e);
                // drain a batch while available to reduce syscalls
                Entry next;
                while ((next = queue.poll()) != null) write(next);
                flush();
            } catch (InterruptedException ignored) {
                return;
            } catch (Exception ex) {
                plugin.getLogger().warning("Log writer error: " + ex.getMessage());
            }
        }
    }

    private void write(Entry e) throws IOException {
        LocalDate day = e.time.toLocalDate();
        BufferedWriter w = writerFor(e.cat, day);
        String line = "[" + e.time.format(TS) + "] [" + e.cat.name() + "] " + e.msg + "\n";
        w.write(line);

        BufferedWriter c = combinedFor(day);
        c.write(line);
    }

    private void flush() {
        synchronized (writers) {
            writers.values().forEach(w -> { try { w.flush(); } catch (IOException ignored) {} });
            if (combined != null) { try { combined.flush(); } catch (IOException ignored) {} }
        }
    }

    private BufferedWriter writerFor(LogCategory cat, LocalDate day) throws IOException {
        synchronized (writers) {
            BufferedWriter w = writers.get(cat);
            LocalDate current = writerDay.get(cat);
            if (w == null || !day.equals(current)) {
                if (w != null) closeQuiet(w);
                File dir = new File(root, cat.name().toLowerCase());
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, day.format(DAY) + ".log");
                w = new BufferedWriter(new FileWriter(f, true));
                writers.put(cat, w);
                writerDay.put(cat, day);
            }
            return w;
        }
    }

    private BufferedWriter combinedFor(LocalDate day) throws IOException {
        synchronized (writers) {
            if (combined == null || !day.equals(combinedDay)) {
                if (combined != null) closeQuiet(combined);
                File dir = new File(root, "all");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, day.format(DAY) + ".log");
                combined = new BufferedWriter(new FileWriter(f, true));
                combinedDay = day;
            }
            return combined;
        }
    }

    private void closeQuiet(BufferedWriter w) {
        try { if (w != null) w.close(); } catch (IOException ignored) {}
    }

    private record Entry(LocalDateTime time, LogCategory cat, String msg) {}
}
