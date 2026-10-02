package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossBarProgressTest {
    private static TreeMap<Long, Reward> ranks() {
        TreeMap<Long, Reward> ranks = new TreeMap<>();
        long[] first = {160, 280, 440, 610, 790};
        String[] names = {"Initiat", "Neuling", "Novize", "Wanderer", "Sammler"};
        for (int i = 0; i < first.length; i++)
            ranks.put(first[i], new Reward(first[i], List.of(), "", "&7" + names[i]));
        for (int i = 6; i <= 100; i++) {
            long threshold = 790 + (i - 5) * 400;
            ranks.put(threshold, new Reward(threshold, List.of(), "", i == 100 ? "&6&lLegende" : "Rang " + i));
        }
        return ranks;
    }

    @Test void screenshotExampleUsesLocalXpAndActualConfiguredRankCount() {
        RankProgress p = RankProgress.calculate(ranks(), 626);
        assertEquals(4, p.number());
        assertEquals(100, p.count());
        assertEquals(16, p.earned());
        assertEquals(180, p.required());
        assertEquals(164, p.remaining());
        assertEquals(16.0 / 180, p.fill());
    }

    @Test void rankBoundaryResetsDisplayWithoutChangingCumulativeXp() {
        assertEquals(179, RankProgress.calculate(ranks(), 789).earned());
        RankProgress p = RankProgress.calculate(ranks(), 790);
        assertEquals(5, p.number());
        assertEquals(0, p.earned());
        assertEquals(400, p.required());
        assertEquals(0, p.fill());
        assertEquals(8, RankProgress.calculate(ranks(), 2000).number()); // multiple thresholds skipped
    }

    @Test void beforeFirstRankAndEmptyListHaveUnambiguousZeroRank() {
        RankProgress p = RankProgress.calculate(ranks(), 0);
        assertEquals(0, p.number());
        assertEquals(160, p.required());
        assertEquals(0, p.earned());
        RankProgress empty = RankProgress.calculate(new TreeMap<>(), 1234);
        assertEquals(0, empty.count());
        assertFalse(empty.maximum());
        assertEquals(0, empty.fill());
    }

    @Test void highestRankKeepsFullBarWithNoNextRankXp() {
        RankProgress p = RankProgress.calculate(ranks(), 100000);
        assertEquals(100, p.number());
        assertTrue(p.maximum());
        assertEquals(1, p.fill());
        assertEquals(0, p.earned());
        assertEquals(0, p.required());
    }

    @Test void defaultAndCustomTitlesRenderAndReloadForExistingBar() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class); var lang = mockStatic(Lang.class)) {
            TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
            TreeMap<Long, Reward> ranks = ranks();
            var field = TotalXPRewardsPlugin.class.getDeclaredField("rewards");
            field.setAccessible(true);
            field.set(plugin, ranks);
            when(plugin.getRewards()).thenReturn(ranks);
            doCallRealMethod().when(plugin).getRankName(anyLong());
            doCallRealMethod().when(plugin).formatToComponent(any(), anyString(), anyLong(), anyLong());
            doCallRealMethod().when(plugin).format(any(), anyString(), anyLong(), anyLong(), anyBoolean());
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
            config.set("bossbar.dynamic-mode", false);
            when(plugin.getConfig()).thenReturn(config);
            lang.when(() -> Lang.get("max-rank")).thenReturn("Max Rank");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            BossBar bar = mock(BossBar.class);
            bukkit.when(() -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_20)).thenReturn(bar);
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn("Tester");
            BossBarManager manager = new BossBarManager(plugin);
            manager.update(player, 626);
            verify(bar).setTitle(argThat(t -> ChatColor.stripColor(t).equals("Wanderer · Rang 4/100 → Sammler · 16/180 XP")));
            verify(bar).setProgress(16.0 / 180);
            manager.update(player, 790);
            verify(bar).setTitle(argThat(t -> ChatColor.stripColor(t).contains("Rang 5/100")
                    && ChatColor.stripColor(t).endsWith("0/400 XP")));
            verify(bar).setProgress(0);
            manager.update(player, 100000);
            verify(bar).setTitle(argThat(t -> ChatColor.stripColor(t).equals("Legende · Rang 100/100 · Höchster Rang erreicht")));
            verify(bar).setProgress(1);
            config.set("bossbar.title", "%rank_remaining_xp% offen | %xp% gesamt | %required_xp% Ziel");
            config.set("bossbar.max-rank-title", "Fertig %rank_number%/%rank_count%");
            manager.reload();
            manager.update(player, 626);
            verify(bar).setTitle("164 offen | 626 gesamt | 790 Ziel");
            manager.update(player, 100000);
            verify(bar).setTitle("Fertig 100/100");
            bukkit.verify(() -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_20), times(1));
        }
    }
}
