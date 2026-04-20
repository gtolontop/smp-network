package fr.smp.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String PREFIX = "<gradient:#a8edea:#fed6e3>SMP</gradient> <dark_gray>»</dark_gray> ";
    public static final String ERR = "<red>✖</red> ";
    public static final String OK = "<green>✔</green> ";

    private Msg() {}

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    public static Component ok(String s) {
        return MM.deserialize(PREFIX + OK + s);
    }

    public static Component err(String s) {
        return MM.deserialize(PREFIX + ERR + s);
    }

    public static Component info(String s) {
        return MM.deserialize(PREFIX + s);
    }

    public static String money(double amount) {
        if (amount >= 1_000_000) return String.format("%.2fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.2fK", amount / 1_000.0);
        return String.format("%.2f", amount);
    }

    /**
     * Parse a human-readable amount: "67", "67.5", "67k", "67K", "67m", "67M", "67b", "67B".
     * Returns -1 if invalid. Non-negative amounts only.
     */
    public static double parseAmount(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase().replace(",", ".").replace("_", "").replace(" ", "");
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') { mult = 1_000d; s = s.substring(0, s.length() - 1); }
        else if (last == 'm') { mult = 1_000_000d; s = s.substring(0, s.length() - 1); }
        else if (last == 'b') { mult = 1_000_000_000d; s = s.substring(0, s.length() - 1); }
        if (s.isEmpty()) return -1;
        try {
            double v = Double.parseDouble(s) * mult;
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) return -1;
            return v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60, s = seconds % 60;
        if (m < 60) return m + "m " + s + "s";
        long h = m / 60; m = m % 60;
        if (h < 24) return h + "h " + m + "m";
        long d = h / 24; h = h % 24;
        return d + "d " + h + "h";
    }
}
