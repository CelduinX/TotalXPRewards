package de.celduinx.totalxprewards;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure progression accounting. Wall time is used for kill expiry, monotonic time for activity. */
public final class ProgressionState {
    public record Kill(long time, UUID world, double x, double y, double z) {}
    private double budget;
    private double fraction;
    private double activeSeconds;
    private String reason = "none";
    private final List<Kill> kills = new ArrayList<>();
    private transient long activeUntilNanos;
    private transient long accountedNanos;
    private transient boolean clockStarted;
    private transient long lastNoticeNanos;

    public ProgressionState(double budget) { this.budget = budget; }
    public double budget() { return budget; }
    public double fraction() { return fraction; }
    public double activeSeconds() { return activeSeconds; }
    public String reason() { return reason; }
    public List<Kill> kills() { return List.copyOf(kills); }
    public ProgressionState snapshot() {
        ProgressionState copy = new ProgressionState(budget);
        copy.restore(fraction, activeSeconds, reason, kills());
        return copy;
    }
    public boolean active(long now) { return now < activeUntilNanos; }

    public void restore(double fraction, double seconds, String reason, List<Kill> history) {
        this.fraction = fraction;
        this.activeSeconds = seconds;
        this.reason = reason;
        kills.addAll(history);
    }

    public void settle(long now, boolean eligible, ProgressionSettings settings) {
        budget = Math.min(budget, settings.capacity());
        if (clockStarted && eligible && settings.enabled()) {
            long end = Math.min(now, activeUntilNanos);
            double seconds = Math.max(0, end - accountedNanos) / 1_000_000_000.0;
            activeSeconds += seconds;
            budget = Math.min(settings.capacity(), budget + seconds * settings.refillPerHour() / 3600);
        }
        accountedNanos = now;
        clockStarted = true;
        if (!eligible) activeUntilNanos = 0;
    }

    public void activate(long now, boolean eligible, ProgressionSettings settings) {
        settle(now, eligible, settings); // never credit time before the first real action
        if (eligible) activeUntilNanos = now + settings.activitySeconds() * 1_000_000_000L;
    }

    public int accept(int raw, double factor, ProgressionSettings settings) {
        if (raw <= 0) return 0;
        if (!settings.enabled()) return raw;
        double weighted = raw * factor;
        double accepted = Math.min(weighted, budget);
        budget = Math.max(0, budget - accepted);
        reason = accepted + 1e-9 < weighted ? (factor < 1 ? "farm+budget" : "budget")
                : factor < 1 ? "farm" : "none";
        double wholeAndFraction = fraction + accepted;
        int whole = (int) Math.floor(wholeAndFraction + 1e-9);
        fraction = Math.max(0, wholeAndFraction - whole);
        return whole;
    }

    public boolean noticeDue(long now) {
        if (reason.equals("none")) return false;
        if (lastNoticeNanos != 0 && now - lastNoticeNanos < 60_000_000_000L) return false;
        lastNoticeNanos = now;
        return true;
    }

    public void prune(long nowMillis, int seconds) {
        kills.removeIf(k -> nowMillis - k.time() > seconds * 1000L || k.time() > nowMillis);
    }

    public double recordKill(Kill kill, ProgressionSettings settings) {
        prune(kill.time(), settings.windowSeconds());
        kills.add(kill);
        if (!settings.enabled() || !settings.farmEnabled()) return 1;
        double r2 = settings.radius() * settings.radius();
        int count = 0;
        for (Kill other : kills) {
            if (!other.world().equals(kill.world())) continue;
            double dx = other.x() - kill.x(), dy = other.y() - kill.y(), dz = other.z() - kill.z();
            if (dx * dx + dy * dy + dz * dz <= r2 && ++count >= settings.killThreshold())
                return settings.farmFactor();
        }
        return 1;
    }

    public String encodeKills() {
        StringBuilder result = new StringBuilder();
        for (Kill k : kills) result.append(k.time()).append(',').append(k.world()).append(',')
                .append(k.x()).append(',').append(k.y()).append(',').append(k.z()).append(';');
        return result.toString();
    }

    public static List<Kill> decodeKills(String text) {
        List<Kill> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        for (String line : text.split(";")) {
            String[] v = line.split(",");
            if (v.length != 5) throw new IllegalArgumentException("Invalid stored kill history");
            result.add(new Kill(Long.parseLong(v[0]), UUID.fromString(v[1]), Double.parseDouble(v[2]),
                    Double.parseDouble(v[3]), Double.parseDouble(v[4])));
        }
        return result;
    }
}
