package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgressionCommandsTest {
    @Test void adminSetPreservesBudgetAndResetClearsAllProtectionState() {
        try (var f = new ProgressionServiceTest.Fixture()) {
            when(f.player.getName()).thenReturn("Tester");
            when(f.player.hasPermission("totalxp.admin")).thenReturn(true);
            when(f.player.isOnline()).thenReturn(true);
            XPDatabase db = mock(XPDatabase.class);
            when(f.plugin.getDatabase()).thenReturn(db);
            when(f.plugin.getBossBarManager()).thenReturn(mock(BossBarManager.class));
            when(f.plugin.getRankGroups()).thenReturn(mock(RankGroups.class));
            when(f.plugin.getRankName(anyLong())).thenReturn("Rank");
            f.bukkit.when(() -> Bukkit.selectEntities(f.player, "@a")).thenReturn(List.of(f.player));
            f.data.getProgression().accept(750, 1, ProgressionSettings.DEFAULT);
            f.kills(30, org.bukkit.entity.EntityType.ZOMBIE);
            CommandTotalXP command = new CommandTotalXP(f.plugin);
            command.onCommand(f.player, mock(Command.class), "totalxp", new String[] {"set", "@a", "30000"});
            assertEquals(30000, f.data.getTotalXp());
            assertEquals(250, f.data.getProgression().budget());
            command.onCommand(f.player, mock(Command.class), "totalxp", new String[] {"reset", "@a"});
            assertEquals(0, f.data.getTotalXp());
            assertEquals(1000, f.data.getProgression().budget());
            assertTrue(f.data.getProgression().kills().isEmpty());
            assertFalse(f.data.getProgression().active(System.nanoTime()));
            verify(db).resetPlayer(f.uuid);
            verify(db).saveData(f.data);
        }
    }

    @Test void statusIsAvailableForSelfButOtherPlayersAndSelectorsRequireAdmin() {
        try (var f = new ProgressionServiceTest.Fixture()) {
            when(f.player.getName()).thenReturn("Tester");
            when(f.plugin.getProgressionService()).thenReturn(f.service);
            when(f.manager.getData(f.uuid)).thenReturn(f.data);
            CommandTotalXP command = new CommandTotalXP(f.plugin);
            f.lang.when(() -> Lang.get("no-permission")).thenReturn("denied");
            f.lang.when(() -> Lang.get("progression-status")).thenReturn("%player% %xp% %budget% %active% %reason%");
            command.onCommand(f.player, mock(Command.class), "totalxp", new String[] {"status"});
            verify(f.player).sendMessage(startsWith("Tester 0 1000.0"));
            command.onCommand(f.player, mock(Command.class), "totalxp", new String[] {"status", "SomeoneElse"});
            command.onCommand(f.player, mock(Command.class), "totalxp", new String[] {"status", "@a"});
            verify(f.player, times(2)).sendMessage("denied");
        }
    }

    @Test void naturalEventKeepsRawXpWhenRankBudgetIsExhausted() throws Exception {
        try (var f = new ProgressionServiceTest.Fixture()) {
            f.data.getProgression().accept(1000, 1, ProgressionSettings.DEFAULT);
            set(f.plugin, "progressionService", f.service);
            set(f.plugin, "playerDataManager", f.manager);
            set(f.plugin, "rewards", new java.util.TreeMap<Long, Reward>());
            doCallRealMethod().when(f.plugin).handleXpGain(any(), anyInt(), any());
            var event = new org.bukkit.event.player.PlayerExpChangeEvent(f.player, null, 30000);
            new XPListener(f.plugin).onPlayerExpChange(event);
            assertEquals(30000, event.getAmount());
            assertEquals(0, f.data.getTotalXp());
            assertEquals(0, f.data.getProgression().budget());
        }
    }

    private static void set(Object target, String key, Object value) throws Exception {
        var field = TotalXPRewardsPlugin.class.getDeclaredField(key);
        field.setAccessible(true);
        field.set(target, value);
    }
}
