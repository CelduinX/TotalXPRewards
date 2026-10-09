package de.celduinx.totalxprewards;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** ActionBar presentation settings and active-playtime estimates. */
public record NoticeSettings(int durationSeconds, String message) {
    public static final String DEFAULT_MESSAGE = "&eRang-XP: %reason% &7| %budget%/%capacity% "
            + "&7| +1 in ~%time_to_next_xp% &7| voll in ~%time_to_full% &7(aktiv)";
    public static final NoticeSettings DEFAULT = new NoticeSettings(10, DEFAULT_MESSAGE);

    public NoticeSettings {
        if (durationSeconds < 1 || durationSeconds > 60)
            throw new IllegalArgumentException("progression.notice.duration-seconds must be 1..60");
        if (message == null || message.isBlank())
            throw new IllegalArgumentException("progression.notice.message must not be empty");
    }

    public static NoticeSettings read(ConfigurationSection section) {
        if (section == null) return DEFAULT;
        Object duration = section.get("duration-seconds");
        if (duration != null && (!(duration instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                || n.intValue() < 1 || n.intValue() > 60))
            throw new IllegalArgumentException("progression.notice.duration-seconds must be an integer from 1..60");
        Object message = section.get("message");
        if (message != null && !(message instanceof String))
            throw new IllegalArgumentException("progression.notice.message must be text");
        return new NoticeSettings(duration == null ? 10 : ((Number) duration).intValue(),
                message == null ? DEFAULT_MESSAGE : (String) message);
    }

    public String render(ProgressionState state, ProgressionSettings progression, String reason) {
        return message.replace("%reason%", reason)
                .replace("%budget%", String.format(Locale.ROOT, "%.1f", state.budget()))
                .replace("%capacity%", String.format(Locale.ROOT, "%.0f", progression.capacity()))
                .replace("%time_to_next_xp%", timeUntil(Math.max(0, 1 - state.budget()), progression.refillPerHour()))
                .replace("%time_to_full%", timeUntil(Math.max(0, progression.capacity() - state.budget()),
                        progression.refillPerHour()));
    }

    static String timeUntil(double remaining, double refillPerHour) {
        if (remaining <= 0) return "jetzt";
        long seconds = (long) Math.ceil(remaining * 3600 / refillPerHour);
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return (seconds + 59) / 60 + " min";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600 + 59) / 60;
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }
}
