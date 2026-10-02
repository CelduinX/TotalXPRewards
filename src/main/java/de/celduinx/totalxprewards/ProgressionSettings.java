package de.celduinx.totalxprewards;

import org.bukkit.configuration.ConfigurationSection;

/** Validated, immutable settings; an invalid reload never replaces the active settings. */
public record ProgressionSettings(boolean enabled, double capacity, double refillPerHour,
        int activitySeconds, boolean farmEnabled, int windowSeconds, double radius,
        int killThreshold, double farmFactor) {
    public static final ProgressionSettings DEFAULT = new ProgressionSettings(true, 1000, 500, 120,
            true, 300, 24, 30, 0.10);

    public ProgressionSettings {
        if (!Double.isFinite(capacity) || capacity < 1 || capacity > 1_000_000_000
                || !Double.isFinite(refillPerHour) || refillPerHour <= 0 || refillPerHour > 1_000_000_000
                || activitySeconds < 1 || activitySeconds > 3600
                || windowSeconds < 1 || windowSeconds > 3600
                || !Double.isFinite(radius) || radius <= 0 || radius > 256
                || killThreshold < 2 || killThreshold > 10000
                || !Double.isFinite(farmFactor) || farmFactor < 0 || farmFactor > 1) {
            throw new IllegalArgumentException("Invalid progression settings: positive finite budget/rate, "
                    + "activity/window 1..3600, radius (0,256], threshold 2..10000, factor 0..1 required");
        }
    }

    public static ProgressionSettings read(ConfigurationSection c) {
        return new ProgressionSettings(bool(c, "enabled", true), number(c, "budget-capacity", 1000),
                number(c, "refill-per-active-hour", 500), integer(c, "activity-timeout-seconds", 120),
                bool(c, "farm.enabled", true), integer(c, "farm.window-seconds", 300),
                number(c, "farm.radius", 24), integer(c, "farm.kill-threshold", 30),
                number(c, "farm.factor", 0.10));
    }

    private static double number(ConfigurationSection c, String key, double fallback) {
        Object value = c == null ? null : c.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n)) throw new IllegalArgumentException("progression." + key + " must be numeric");
        return n.doubleValue();
    }

    private static int integer(ConfigurationSection c, String key, int fallback) {
        double value = number(c, key, fallback);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)
            throw new IllegalArgumentException("progression." + key + " must be an integer");
        return (int) value;
    }

    private static boolean bool(ConfigurationSection c, String key, boolean fallback) {
        Object value = c == null ? null : c.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException("progression." + key + " must be boolean");
        return b;
    }
}
