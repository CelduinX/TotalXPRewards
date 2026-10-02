package de.celduinx.totalxprewards;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionStateTest {
    private static final ProgressionSettings S = ProgressionSettings.DEFAULT;
    private static final long SECOND = 1_000_000_000L;
    private static final UUID WORLD = UUID.randomUUID();

    @Test void capsBurstAndDiscardsSurplus() {
        ProgressionState s = new ProgressionState(1000);
        assertEquals(1000, s.accept(30000, 1, S));
        assertEquals(0, s.accept(30000, 1, S));
        assertEquals("budget", s.reason());
        assertEquals(0, s.budget());
    }

    @Test void shortNormalGainsCountFullyAndOnlyActualCreditCostsBudget() {
        ProgressionState s = new ProgressionState(1000);
        assertEquals(12, s.accept(12, 1, S));
        assertEquals(988, s.budget());
        assertEquals(10, s.accept(100, .1, S));
        assertEquals(978, s.budget());
        assertEquals("farm", s.reason());
    }

    @Test void fractionsCountSmallOrbsWithoutRoundingExploits() {
        ProgressionState s = new ProgressionState(1000);
        int credited = 0;
        for (int i = 0; i < 1000; i++) credited += s.accept(1, .1, S);
        assertEquals(100, credited);
        assertEquals(900, s.budget(), 1e-7);
        assertEquals(0, s.fraction(), 1e-7);
    }

    @Test void inactivityAndOfflineTimeDoNotRefill() {
        ProgressionState s = new ProgressionState(0);
        s.settle(SECOND, true, S);
        s.settle(3601 * SECOND, true, S);
        assertEquals(0, s.budget());
        s.activate(3601 * SECOND, true, S);
        s.settle(7201 * SECOND, true, S);
        assertEquals(120, s.activeSeconds());
        assertEquals(500.0 / 30, s.budget(), 1e-9);
        ProgressionState reconnected = new ProgressionState(s.budget());
        reconnected.restore(s.fraction(), s.activeSeconds(), s.reason(), s.kills());
        reconnected.settle(9000 * SECOND, true, S);
        reconnected.settle(18000 * SECOND, true, S);
        assertEquals(s.budget(), reconnected.budget());
    }

    @Test void firstActionDoesNotBackfillIdleTimeAndCreativeClearsActivity() {
        ProgressionState s = new ProgressionState(0);
        s.activate(3600 * SECOND, true, S);
        assertEquals(0, s.budget());
        s.settle(3660 * SECOND, true, S);
        assertEquals(60, s.activeSeconds());
        s.settle(3660 * SECOND, false, S);
        s.activate(3700 * SECOND, false, S);
        s.settle(3800 * SECOND, true, S);
        assertEquals(60, s.activeSeconds());
        assertFalse(s.active(3800 * SECOND));
    }

    @Test void refillCapsAtCapacityAndChangingCapacityDoesNotRefill() {
        ProgressionState s = new ProgressionState(999);
        s.activate(SECOND, true, S);
        s.settle(61 * SECOND, true, S);
        assertEquals(1000, s.budget());
        ProgressionSettings small = new ProgressionSettings(true, 250, 500, 120, true, 300, 24, 30, .1);
        s.settle(61 * SECOND, true, small);
        assertEquals(250, s.budget());
        s.settle(61 * SECOND, true, S);
        assertEquals(250, s.budget());
    }

    @Test void sustainedFarmCannotExceedOneThousandPlusFiveHundredPerActiveHour() {
        ProgressionState s = new ProgressionState(1000);
        long now = SECOND;
        s.activate(now, true, S);
        long credits = s.accept(30000, 1, S);
        for (int i = 0; i < 58 * 60; i++) {
            now += 60 * SECOND;
            s.activate(now, true, S);
            credits += s.accept(30000, 1, S);
            assertTrue(credits <= 1000 + 500 * s.activeSeconds() / 3600 + 1e-6);
        }
        assertEquals(30000, credits);
        assertEquals(58 * 3600, s.activeSeconds());
    }

    @Test void thirtiethLocalKillIsReducedAcrossMobTypesAndHistoryRoundTrips() {
        ProgressionState s = new ProgressionState(1000);
        for (int i = 0; i < 29; i++) assertEquals(1, s.recordKill(kill(100000 + i, WORLD, 0), S));
        assertEquals(.1, s.recordKill(kill(100030, WORLD, 0), S));
        assertEquals(s.kills(), ProgressionState.decodeKills(s.encodeKills()));
        ProgressionState restored = new ProgressionState(1000);
        restored.restore(0, 0, "none", ProgressionState.decodeKills(s.encodeKills()));
        assertEquals(.1, restored.recordKill(kill(100040, WORLD, 0), S));
    }

    @Test void farmChecksWorldRadiusAndExpiryIncludingBoundaries() {
        ProgressionState s = new ProgressionState(1000);
        for (int i = 0; i < 29; i++) s.recordKill(kill(100000, WORLD, 0), S);
        assertEquals(1, s.recordKill(kill(100000, UUID.randomUUID(), 0), S));
        assertEquals(1, s.recordKill(kill(100000, WORLD, 24.01), S));
        assertEquals(.1, s.recordKill(kill(400000, WORLD, 24), S));
        assertEquals(1, s.recordKill(kill(400001, WORLD, 0), S));
    }

    @Test void farmAndBudgetReasonsAndNoticeCooldownAreIndependentOfWholePoints() {
        ProgressionState s = new ProgressionState(.05);
        assertEquals(0, s.accept(1, .1, S));
        assertEquals("farm+budget", s.reason());
        assertTrue(s.noticeDue(SECOND));
        assertFalse(s.noticeDue(60 * SECOND));
        assertTrue(s.noticeDue(61 * SECOND));
    }

    @Test void disabledProtectionPreservesFullXpAndDoesNotRefillOrConsumeBudget() {
        ProgressionSettings disabled = new ProgressionSettings(false, 1000, 500, 120, true, 300, 24, 30, .1);
        ProgressionState s = new ProgressionState(42);
        s.activate(SECOND, true, disabled);
        s.settle(61 * SECOND, true, disabled);
        assertEquals(30000, s.accept(30000, .1, disabled));
        assertEquals(42, s.budget());
    }

    @Test void configRejectsNegativeNanFractionsAndWrongTypes() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(S, ProgressionSettings.read(config));
        for (Object value : new Object[] {-1, Double.NaN, Double.POSITIVE_INFINITY, "500"}) {
            config.set("budget-capacity", value);
            assertThrows(IllegalArgumentException.class, () -> ProgressionSettings.read(config));
        }
        config.set("budget-capacity", 1000);
        config.set("farm.kill-threshold", 30.5);
        assertThrows(IllegalArgumentException.class, () -> ProgressionSettings.read(config));
        config.set("farm.kill-threshold", 30);
        config.set("farm.enabled", "true");
        assertThrows(IllegalArgumentException.class, () -> ProgressionSettings.read(config));
    }

    private static ProgressionState.Kill kill(long time, UUID world, double x) {
        return new ProgressionState.Kill(time, world, x, 64, 0);
    }
}
