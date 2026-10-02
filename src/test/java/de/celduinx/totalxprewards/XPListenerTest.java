package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XPListenerTest {
    @Test
    void tracksLevelChangesUsingCalculatedPointsAndNamespacedRconCommands() {
        runCommandCheck("minecraft:experience add @a 1 levels", 7, false, true);
    }

    @Test
    void countsRepeatedCommandsInOneTickOnlyOnce() {
        runCommandCheck("xp add @a 10 points", 20, true, false);
    }

    @Test
    void excludesNaturalXpAlreadyCountedInTheSameTick() {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AtomicInteger xp = new AtomicInteger(0);
        when(player.calculateTotalExperiencePoints()).thenAnswer(i -> xp.get());
        AtomicReference<Runnable> task = new AtomicReference<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> {
            task.set(i.getArgument(1));
            return null;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            XPListener listener = new XPListener(plugin);
            listener.onServerCommand(new ServerCommandEvent(mock(CommandSender.class), "minecraft:xp add @a 10"));
            listener.onPlayerExpChange(new PlayerExpChangeEvent(player, null, 5));
            xp.set(15);
            task.get().run();
            verify(plugin).handleXpGain(player, 5);
            verify(plugin).handleXpGain(player, 10);
            verifyNoMoreInteractions(plugin);
        }
    }

    @Test
    void ignoresNegativeAndZeroNaturalXp() {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        XPListener listener = new XPListener(plugin);
        Player player = mock(Player.class);
        listener.onPlayerExpChange(new PlayerExpChangeEvent(player, null, -5));
        listener.onPlayerExpChange(new PlayerExpChangeEvent(player, null, 0));
        verifyNoInteractions(plugin);
    }

    @Test
    void ignoresXpLossFromCommands() {
        runCommandCheck("experience set @a 0 points", -20, false, false);
    }

    private void runCommandCheck(String command, int gain, boolean repeat, boolean remote) {
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AtomicInteger xp = new AtomicInteger(20);
        when(player.calculateTotalExperiencePoints()).thenAnswer(i -> xp.get());
        // This counter deliberately stays unchanged, as with vanilla level commands.
        when(player.getTotalExperience()).thenReturn(20);
        AtomicReference<Runnable> task = new AtomicReference<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> {
            assertNull(task.get(), "Only one check should be queued per tick");
            task.set(i.getArgument(1));
            return null;
        });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            XPListener listener = new XPListener(plugin);
            if (remote) {
                listener.onRemoteServerCommand(new RemoteServerCommandEvent(mock(CommandSender.class), command));
            } else {
                listener.onServerCommand(new ServerCommandEvent(mock(CommandSender.class), command));
            }
            xp.set(20 + gain);
            if (repeat) {
                listener.onServerCommand(new ServerCommandEvent(mock(CommandSender.class), command));
            }
            assertNotNull(task.get());
            task.get().run();
            if (gain > 0) {
                verify(plugin, times(1)).handleXpGain(player, gain);
            } else {
                verifyNoInteractions(plugin);
            }
        }
    }
}
