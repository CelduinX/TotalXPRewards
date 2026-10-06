package de.celduinx.totalxprewards;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class RewardPreviewTest {
    @Test
    void skipsRanksWithoutDisplayableRewards() {
        Map<Long, Reward> ranks = new TreeMap<>();
        ranks.put(3760L, rank(3760, "Abenteurer III", List.of("title %player% title hallo"), List.of()));
        ranks.put(4020L, rank(4020, "Abenteurer IV", List.of("title %player% title hallo"), List.of()));
        ranks.put(4280L, rank(4280, "Abenteurer V", List.of(), List.of()));
        ranks.put(4540L, rank(4540, "Entdecker I", List.of(
                "give %player% diamond 4", "give %player% golden_carrot 16"), List.of()));

        RewardPreview.Next next = RewardPreview.next(ranks, 3952);
        assertNotNull(next);
        assertEquals(4, next.number());
        assertEquals("Entdecker I", next.reward().getName());
        assertNull(RewardPreview.next(ranks, 4540));
    }

    @Test
    void customDescriptionsMakeOtherRewardCommandsVisible() {
        Map<Long, Reward> ranks = new TreeMap<>();
        ranks.put(100L, rank(100, "Erster", List.of("eco give %player% 100"), List.of("- 100 Münzen")));
        RewardPreview.Next next = RewardPreview.next(ranks, 0);
        assertNotNull(next);
        assertEquals(1, next.number());
    }

    private Reward rank(long xp, String name, List<String> commands, List<String> descriptions) {
        return new Reward(xp, commands, "", name, "rank_" + xp, descriptions);
    }
}
