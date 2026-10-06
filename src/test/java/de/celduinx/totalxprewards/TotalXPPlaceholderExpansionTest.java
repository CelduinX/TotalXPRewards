package de.celduinx.totalxprewards;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TotalXPPlaceholderExpansionTest {
    private final Map<Long, Reward> ranks = new TreeMap<>(Map.of(
            100L, new Reward(100, List.of("say first"), "", "&7Anwärter", "anwaerter"),
            250L, new Reward(250, List.of("say second"), "", "<gradient:#ff0000:#0000ff>Meister</gradient>", "meister")));

    @Test void beforeFirstRank() {
        assertEquals("0", value(0, "rank_number"));
        assertEquals("100", value(0, "rank_required_xp"));
        assertEquals("100", value(0, "rank_remaining_xp"));
        assertEquals("0", value(0, "rank_start_xp"));
        assertEquals("100", value(0, "next_rank_xp"));
        assertEquals("None", value(0, "current_rank"));
        assertEquals("", value(0, "current_rank_group"));
        assertEquals("anwaerter", value(0, "next_rank_group"));
    }

    @Test void liveRankProgressAndNames() {
        assertEquals("175", value(175, "xp"));
        assertEquals("1", value(175, "rank_number"));
        assertEquals("2", value(175, "rank_count"));
        assertEquals("75", value(175, "rank_xp"));
        assertEquals("150", value(175, "rank_required_xp"));
        assertEquals("75", value(175, "rank_remaining_xp"));
        assertEquals("100", value(175, "rank_start_xp"));
        assertEquals("250", value(175, "required_xp"));
        assertEquals("&7Anwärter", value(175, "current_rank"));
        assertEquals("Anwärter", value(175, "current_rank_plain"));
        assertEquals("anwaerter", value(175, "current_rank_group"));
        assertEquals("Meister", value(175, "next_rank_plain"));
        assertEquals("meister", value(175, "next_rank_group"));
    }

    @Test void maximumRankAndUnknownPlaceholder() {
        assertEquals("2", value(250, "rank_number"));
        assertEquals("0", value(250, "rank_required_xp"));
        assertEquals("0", value(250, "rank_remaining_xp"));
        assertEquals("0", value(250, "next_rank_xp"));
        assertEquals("", value(250, "next_rank_group"));
        assertNull(value(250, "unknown"));
    }

    private String value(long xp, String name) {
        return TotalXPPlaceholderExpansion.resolve(ranks, xp, name);
    }
}
