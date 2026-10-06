package de.celduinx.totalxprewards;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankNameFormattingTest {
    @Test void rendersMiniMessageAndLegacyNamesForLuckPerms() {
        String gradient = "<gradient:#ffcc00:#ff6600>Legende</gradient>";
        assertEquals("Legende", RankNameFormatting.plainName(gradient));
        assertTrue(RankNameFormatting.formattedName(gradient).startsWith("&#"));
        assertEquals("Anwärter I", RankNameFormatting.plainName("&7Anwärter I"));
        assertTrue(RankNameFormatting.formattedName("&7Anwärter I").startsWith("&7"));
        assertTrue(RankNameFormatting.formattedName("&6&lLegende").contains("&l"));
    }
}
