package de.celduinx.totalxprewards;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoticeSettingsTest {
    @Test void estimatesActiveTimeAndRendersAllPlaceholders() {
        ProgressionState state = new ProgressionState(0);
        NoticeSettings notice = new NoticeSettings(10,
                "%reason% | %budget%/%capacity% | %time_to_next_xp% | %time_to_full%");
        assertEquals("Budget | 0.0/1000 | 8 s | 2 h",
                notice.render(state, ProgressionSettings.DEFAULT, "Budget"));
        assertEquals("jetzt", NoticeSettings.timeUntil(0, 500));
        assertEquals("2 min", NoticeSettings.timeUntil(10, 500));
        assertEquals("1 h 1 min", NoticeSettings.timeUntil(501, 500));
    }

    @Test void validatesNoticeConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(NoticeSettings.DEFAULT, NoticeSettings.read(config));
        config.set("duration-seconds", 18);
        config.set("message", "Eigene Meldung");
        assertEquals(new NoticeSettings(18, "Eigene Meldung"), NoticeSettings.read(config));
        config.set("duration-seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> NoticeSettings.read(config));
        config.set("duration-seconds", 18.5);
        assertThrows(IllegalArgumentException.class, () -> NoticeSettings.read(config));
        config.set("duration-seconds", 18);
        config.set("message", " ");
        assertThrows(IllegalArgumentException.class, () -> NoticeSettings.read(config));
    }
}
