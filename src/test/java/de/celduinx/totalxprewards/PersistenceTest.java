package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistenceTest {
    @TempDir Path directory;

    @Test void migratesExistingRankDefinitionsWithoutReplacingServerSettings() throws Exception {
        Path config = directory.resolve("config.yml");
        String original = "config-version: 2\nsettings:\n  use-placeholderapi: false\n"
                + "bossbar:\n  color: PURPLE\nrewards:\n  '160':\n    group: initiat\n"
                + "    name: '&7Initiat'\n    commands:\n      - 'give %player% diamond 1'\n"
                + "    broadcast: '&aRank reached'\nprogression:\n  enabled: true\n";
        Files.writeString(config, original);
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RankMigrationTest"));
        var method = TotalXPRewardsPlugin.class.getDeclaredMethod("migrateConfig");
        method.setAccessible(true);
        method.invoke(plugin);
        var settings = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        var ranks = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(directory.resolve("ranks.yml").toFile());
        assertEquals(4, settings.getInt("config-version"));
        assertEquals(10, settings.getInt("progression.notice.duration-seconds"));
        assertEquals(NoticeSettings.DEFAULT_MESSAGE, settings.getString("progression.notice.message"));
        assertFalse(settings.getBoolean("settings.use-placeholderapi"));
        assertEquals("PURPLE", settings.getString("bossbar.color"));
        assertFalse(settings.contains("rewards"));
        assertEquals("initiat", ranks.getString("rewards.160.group"));
        assertEquals("&7Initiat", ranks.getString("rewards.160.name"));
        assertEquals(List.of("give %player% diamond 1"), ranks.getStringList("rewards.160.commands"));
        assertEquals(original, Files.readString(directory.resolve("config.before-ranks-v3.yml")));
    }

    @Test void conflictingRankFilesLeaveLegacyConfigurationUntouched() throws Exception {
        Path config = directory.resolve("config.yml");
        String original = "config-version: 2\nrewards:\n  '160':\n    group: initiat\n";
        Files.writeString(config, original);
        Files.writeString(directory.resolve("ranks.yml"), "rewards:\n  '280':\n    group: neuling\n");
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RankConflictTest"));
        var method = TotalXPRewardsPlugin.class.getDeclaredMethod("migrateConfig");
        method.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(plugin));
        assertEquals(original, Files.readString(config));
    }

    @Test void migratesCustomLimitedMessageWithoutReplacingExistingNoticeDuration() throws Exception {
        Path config = directory.resolve("config.yml");
        Files.writeString(config, "config-version: 3\nprogression:\n  notice:\n    duration-seconds: 18\n");
        Files.writeString(directory.resolve("ranks.yml"), "rewards: {}\n");
        Files.writeString(directory.resolve("lang.yml"),
                "progression-limited: '&cEigener Text: %reason% | %budget%'\n");
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("NoticeMigrationTest"));
        var method = TotalXPRewardsPlugin.class.getDeclaredMethod("migrateConfig");
        method.setAccessible(true);
        method.invoke(plugin);
        var migrated = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        assertEquals(4, migrated.getInt("config-version"));
        assertEquals(18, migrated.getInt("progression.notice.duration-seconds"));
        assertEquals("&cEigener Text: %reason% | %budget%", migrated.getString("progression.notice.message"));
    }

    @Test void replacesOldDefaultLimitedMessageWithInformativeTemplate() throws Exception {
        Path config = directory.resolve("config.yml");
        Files.writeString(config, "config-version: 3\n");
        Files.writeString(directory.resolve("ranks.yml"), "rewards: {}\n");
        Files.writeString(directory.resolve("lang.yml"),
                "progression-limited: '&eRang-XP begrenzt: %reason% &7| Budget: %budget% &7| Minecraft-XP bleiben erhalten.'\n");
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DefaultNoticeMigrationTest"));
        var method = TotalXPRewardsPlugin.class.getDeclaredMethod("migrateConfig");
        method.setAccessible(true);
        method.invoke(plugin);
        var migrated = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile());
        assertEquals(NoticeSettings.DEFAULT_MESSAGE, migrated.getString("progression.notice.message"));
    }

    @Test void oldDatabaseMigratesWithoutChangingXpOrRewards() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("totalxp.db"));
             var st = c.createStatement()) {
            st.execute("CREATE TABLE player_xp (uuid TEXT PRIMARY KEY, xp INTEGER NOT NULL)");
            st.execute("CREATE TABLE player_rewards (uuid TEXT, threshold INTEGER, PRIMARY KEY(uuid,threshold))");
            st.execute("INSERT INTO player_xp VALUES ('" + uuid + "',12345)");
            st.execute("INSERT INTO player_rewards VALUES ('" + uuid + "',1000)");
        }
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MigrationTest"));
        XPDatabase db = new XPDatabase(plugin);
        try {
            assertEquals(12345, db.getXp(uuid));
            assertTrue(db.hasReward(uuid, 1000));
            assertEquals(java.util.Set.of(1000L), db.getRewardHistory(uuid));
            assertEquals(1000, db.getProgression(uuid, 1000).budget());
            db.saveProgression(uuid, new ProgressionState(123));
            assertEquals(123, db.getProgression(uuid, 1000).budget());
            assertEquals(12345, db.getXp(uuid));
        } finally { db.close(); }
    }

    @Test void rewardLookupFailsClosedWhenDatabaseIsUnavailable() {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RewardFailureTest"));
        XPDatabase db = new XPDatabase(plugin);
        db.close();
        assertThrows(IllegalStateException.class, () -> db.getXp(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> db.hasReward(UUID.randomUUID(), 100));
        assertThrows(IllegalStateException.class, () -> db.setRewardGiven(UUID.randomUUID(), 100));
        assertFalse(db.isHealthy());
    }

    @Test void invalidReloadDoesNotReplaceSettingsOrReadRewards() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("config.yml"), "progression:\n  budget-capacity: -1\n");
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var field = TotalXPRewardsPlugin.class.getDeclaredField("progressionSettings");
        field.setAccessible(true);
        field.set(plugin, ProgressionSettings.DEFAULT);
        doCallRealMethod().when(plugin).reloadSettings();
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertEquals(ProgressionSettings.DEFAULT, field.get(plugin));
        verify(plugin, never()).reloadConfig();
        java.nio.file.Files.writeString(directory.resolve("config.yml"), "progression: [invalid: yaml\n");
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertEquals(ProgressionSettings.DEFAULT, field.get(plugin));
    }

    @Test void missingLuckPermsGroupRejectsReloadBeforeChangingActiveRanks() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "config-version: 3\n");
        Files.writeString(directory.resolve("ranks.yml"), "rewards:\n  '100':\n    group: missing\n"
                + "    name: 'Test'\n    broadcast: 'Reached'\n");
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RejectedReloadTest"));
        RankGroups rankGroups = mock(RankGroups.class);
        when(rankGroups.configurationProblems(anyMap())).thenReturn(List.of("Missing LuckPerms group: missing"));
        var groupsField = TotalXPRewardsPlugin.class.getDeclaredField("rankGroups");
        groupsField.setAccessible(true);
        groupsField.set(plugin, rankGroups);
        var rewardsField = TotalXPRewardsPlugin.class.getDeclaredField("rewards");
        rewardsField.setAccessible(true);
        var original = java.util.Map.of(50L, new Reward(50, List.of(), "Old", "Old", "old"));
        rewardsField.set(plugin, original);
        doCallRealMethod().when(plugin).reloadSettings();
        assertThrows(IllegalArgumentException.class, plugin::reloadSettings);
        assertSame(original, rewardsField.get(plugin));
        verify(plugin, never()).reloadConfig();
        verify(rankGroups, never()).validate(anyMap());
    }

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
            ProgressionState state = manager.getData(uuid).getProgression();
            state.accept(999, 1, ProgressionSettings.DEFAULT);
            state.accept(1, .1, ProgressionSettings.DEFAULT);
            state.recordKill(new ProgressionState.Kill(System.currentTimeMillis(), UUID.randomUUID(), 0, 64, 0),
                    ProgressionSettings.DEFAULT);
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
            assertEquals(java.util.Set.of(1000L), reopened.getRewardHistory(uuid));
            ProgressionState restored = reopened.getProgression(uuid, 1000);
            assertEquals(.9, restored.budget(), 1e-9);
            assertEquals(.1, restored.fraction(), 1e-9);
            assertEquals(1, restored.kills().size());
            assertFalse(restored.active(System.nanoTime()));
            reopened.resetPlayer(uuid);
            assertEquals(0, reopened.getXp(uuid));
            assertFalse(reopened.hasReward(uuid, 1000));
            assertEquals(java.util.Set.of(), reopened.getRewardHistory(uuid));
            assertEquals(1000, reopened.getProgression(uuid, 1000).budget());
        } finally {
            reopened.close();
        }
    }

    @Test void periodicSaveUsesSnapshotAndFinalSaveKeepsLatestXp() {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        XPDatabase database = mock(XPDatabase.class);
        when(plugin.getDatabase()).thenReturn(database);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AsyncSaveTest"));
        when(plugin.getRankName(anyLong())).thenReturn("Test");
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Tester");
        java.util.List<Long> saved = new java.util.concurrent.CopyOnWriteArrayList<>();
        doAnswer(i -> { saved.add(((PlayerData) i.getArgument(0)).getTotalXp()); return null; })
                .when(database).saveData(any(PlayerData.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            PlayerDataManager manager = new PlayerDataManager(plugin);
            manager.onJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
            PlayerData data = manager.getData(uuid);
            data.setTotalXp(10);
            manager.saveAllAsync();
            data.setTotalXp(20);
            manager.close();
        }
        assertEquals(List.of(10L, 20L), saved);
    }
}
