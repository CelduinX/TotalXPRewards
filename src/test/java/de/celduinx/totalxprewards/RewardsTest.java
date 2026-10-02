package de.celduinx.totalxprewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardsTest {
    @Test
    void parsesLegacyAndRgbTextWithPaperAdventureFive() {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        doCallRealMethod().when(plugin).formatToComponent(any(), anyString(), anyLong(), anyLong());
        Component text = plugin.formatToComponent(player, "<#ff0000>%player%</#ff0000> &a%xp%", 1234, 1000);
        assertEquals("TestPlayer 1234", PlainTextComponentSerializer.plainText().serialize(text));
        String json = GsonComponentSerializer.gson().serialize(text).toLowerCase(java.util.Locale.ROOT);
        assertTrue(json.contains("#ff0000"), json);
        assertTrue(json.contains("green") || json.contains("#55ff55"), json);
    }

    @Test
    void executesThresholdRewardOnlyOnceEvenAfterXpIsSetBack() throws ReflectiveOperationException {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        PlayerData data = new PlayerData(uuid, "TestPlayer", 990);
        PlayerDataManager manager = mock(PlayerDataManager.class);
        when(manager.getData(uuid)).thenReturn(data);
        XPDatabase database = mock(XPDatabase.class);
        AtomicBoolean given = new AtomicBoolean();
        when(database.hasReward(uuid, 1000)).thenAnswer(i -> given.get());
        doAnswer(i -> { given.set(true); return null; }).when(database).setRewardGiven(uuid, 1000);
        TreeMap<Long, Reward> rewards = new TreeMap<>();
        rewards.put(1000L, new Reward(1000, List.of("give TestPlayer diamond 1"), "", "First Rank"));
        setField(plugin, "playerDataManager", manager);
        setField(plugin, "database", database);
        setField(plugin, "rewards", rewards);
        when(plugin.getRankName(anyLong())).thenReturn("First Rank");
        when(plugin.format(eq(player), anyString(), anyLong(), anyLong(), eq(false)))
                .thenAnswer(i -> i.getArgument(1));
        doCallRealMethod().when(plugin).handleXpGain(any(), anyInt());
        doCallRealMethod().when(plugin).handleXpGain(any(), anyInt(), any());
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            plugin.handleXpGain(player, 20);
            assertEquals(1010, data.getTotalXp());
            assertEquals("First Rank", data.getCurrentRankName());
            data.setTotalXp(990);
            plugin.handleXpGain(player, 20);
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "give TestPlayer diamond 1"), times(1));
            verify(database, times(1)).setRewardGiven(uuid, 1000);
        }
    }

    private static void setField(Object object, String name, Object value) throws ReflectiveOperationException {
        var field = TotalXPRewardsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
