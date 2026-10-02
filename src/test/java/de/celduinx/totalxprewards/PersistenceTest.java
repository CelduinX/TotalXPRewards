package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistenceTest {
    @TempDir Path directory;

    @Test
    void cachedXpAndRewardHistorySurviveClosingAndReopeningDatabase() throws ReflectiveOperationException {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TotalXPRewardsTest"));
        when(plugin.getRankName(anyLong())).thenReturn("Test Rank");
        XPDatabase database = new XPDatabase(plugin);
        when(plugin.getDatabase()).thenReturn(database);
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            PlayerDataManager manager = new PlayerDataManager(plugin);
            manager.onJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            assertNotNull(manager.getData(uuid), "Join fallback must load synchronously");
            manager.getData(uuid).addXp(1234);
            database.setRewardGiven(uuid, 1000);
            var managerField = TotalXPRewardsPlugin.class.getDeclaredField("playerDataManager");
            managerField.setAccessible(true);
            managerField.set(plugin, manager);
            var databaseField = TotalXPRewardsPlugin.class.getDeclaredField("database");
            databaseField.setAccessible(true);
            databaseField.set(plugin, database);
            doCallRealMethod().when(plugin).onDisable();
            plugin.onDisable();
        }
        XPDatabase reopened = new XPDatabase(plugin);
        try {
            assertEquals(1234, reopened.getXp(uuid));
            assertTrue(reopened.hasReward(uuid, 1000));
            reopened.resetPlayer(uuid);
            assertEquals(0, reopened.getXp(uuid));
            assertFalse(reopened.hasReward(uuid, 1000));
        } finally {
            reopened.close();
        }
    }
}
