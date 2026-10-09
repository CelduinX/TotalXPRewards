package de.celduinx.totalxprewards;

import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgressionServiceTest {
    static class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<Lang> lang = mockStatic(Lang.class);
        final TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final UUID uuid = UUID.randomUUID();
        final PlayerData data = new PlayerData(uuid, "Tester", 0);
        final PlayerDataManager manager = mock(PlayerDataManager.class);
        final ProgressionService service;
        Runnable tick;
        Fixture() {
            when(plugin.getName()).thenReturn("TotalXPRewards");
            when(plugin.namespace()).thenReturn("totalxprewards");
            when(plugin.getProgressionSettings()).thenReturn(ProgressionSettings.DEFAULT);
            when(plugin.getNoticeSettings()).thenReturn(NoticeSettings.DEFAULT);
            when(plugin.getPlayerDataManager()).thenReturn(manager);
            when(manager.getData(uuid)).thenReturn(data);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            Input input = mock(Input.class);
            when(input.isForward()).thenReturn(true);
            when(player.getCurrentInput()).thenReturn(input);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(world.getUID()).thenReturn(UUID.randomUUID());
            data.setProgression(new ProgressionState(1000));
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L)))
                    .thenAnswer(i -> { tick = i.getArgument(1); return mock(BukkitTask.class); });
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            lang.when(() -> Lang.get(anyString())).thenReturn("Reason %reason%, budget %budget%");
            try { service = new ProgressionService(plugin); }
            catch (RuntimeException | Error e) { lang.close(); bukkit.close(); throw e; }
        }
        public void close() { service.close(); lang.close(); bukkit.close(); }
        ExperienceOrb orb(UUID source) {
            ExperienceOrb orb = mock(ExperienceOrb.class);
            when(orb.getSourceEntityId()).thenReturn(source);
            PersistentDataContainer pdc = mock(PersistentDataContainer.class);
            AtomicReference<Double> value = new AtomicReference<>();
            when(orb.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.DOUBLE))).thenAnswer(i -> value.get());
            doAnswer(i -> { value.set(i.getArgument(2)); return null; }).when(pdc)
                    .set(any(NamespacedKey.class), eq(PersistentDataType.DOUBLE), anyDouble());
            return orb;
        }
        UUID kills(int count, EntityType type) {
            UUID last = null;
            for (int i = 0; i < count; i++) {
                Mob mob = mock(Mob.class);
                last = UUID.randomUUID();
                when(mob.getUniqueId()).thenReturn(last);
                when(mob.getType()).thenReturn(type);
                when(mob.getKiller()).thenReturn(player);
                when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));
                EntityDeathEvent death = mock(EntityDeathEvent.class);
                when(death.getEntity()).thenReturn(mob);
                when(death.getDroppedExp()).thenReturn(5);
                service.death(death);
            }
            return last;
        }
    }

    @Test void normalXpAndCommandXpUseSameBudgetWithoutChangingMinecraftXp() {
        try (Fixture f = new Fixture()) {
            assertEquals(7, f.service.filter(f.player, 7, null));
            assertEquals(993, f.service.filter(f.player, 30000, null));
            assertEquals(0, f.service.filter(f.player, 5, f.orb(UUID.randomUUID())));
            verify(f.player, never()).giveExp(anyInt());
            verify(f.player, never()).setTotalExperience(anyInt());
        }
    }

    @Test void limitedNoticeRefreshesAndStopsWhenPlayerQuits() {
        try (Fixture f = new Fixture()) {
            assertEquals(1000, f.service.filter(f.player, 2000, null));
            verify(f.player).sendActionBar(any(Component.class));
            f.tick.run();
            verify(f.player, times(2)).sendActionBar(any(Component.class));
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(f.player);
            f.service.quit(quit);
            f.tick.run();
            verify(f.player, times(2)).sendActionBar(any(Component.class));
        }
    }

    @Test void configuredDurationEndsNotice() throws InterruptedException {
        try (Fixture f = new Fixture()) {
            when(f.plugin.getNoticeSettings()).thenReturn(new NoticeSettings(1, "Budget: %budget%"));
            f.service.filter(f.player, 2000, null);
            Thread.sleep(1100);
            f.tick.run();
            verify(f.player).sendActionBar(any(Component.class));
        }
    }

    @Test void farmTagSurvivesTransferAndOrbMergingWithUnknownSources() {
        try (Fixture f = new Fixture()) {
            UUID last = f.kills(30, EntityType.ZOMBIE);
            ExperienceOrb reduced = f.orb(last), normal = f.orb(null);
            f.service.spawn(new EntitySpawnEvent(reduced));
            f.service.merge(new ExperienceOrbMergeEvent(normal, reduced));
            assertEquals(1, f.service.filter(f.player, 10, normal));
            Player receiver = mock(Player.class);
            UUID receiverId = UUID.randomUUID();
            when(receiver.getUniqueId()).thenReturn(receiverId);
            when(receiver.getGameMode()).thenReturn(GameMode.SURVIVAL);
            PlayerData other = new PlayerData(receiverId, "Receiver", 0);
            other.setProgression(new ProgressionState(1000));
            when(f.manager.getData(receiverId)).thenReturn(other);
            assertEquals(1, f.service.filter(receiver, 10, normal));
            assertEquals(10, f.service.filter(receiver, 10, f.orb(UUID.randomUUID())));
            verify(normal, never()).setExperience(anyInt());
            verify(normal, never()).setCount(anyInt());
        }
    }

    @Test void awardTimeStackingCandidatesAreTaggedBeforeXpDrops() {
        try (Fixture f = new Fixture()) {
            ExperienceOrb existing = f.orb(null);
            when(f.world.getNearbyEntities(any(Location.class), eq(1.5), eq(1.5), eq(1.5)))
                    .thenReturn(List.of(existing));
            f.kills(29, EntityType.ZOMBIE);
            assertEquals(10, f.service.filter(f.player, 10, existing));
            f.kills(1, EntityType.SKELETON);
            assertEquals(1, f.service.filter(f.player, 10, existing));
        }
    }

    @Test void bossesAndPlayerDeathsDoNotContributeToFarmDetection() {
        try (Fixture f = new Fixture()) {
            for (EntityType type : new EntityType[] {EntityType.ENDER_DRAGON, EntityType.WITHER,
                    EntityType.WARDEN, EntityType.PLAYER}) {
                f.kills(30, type);
            }
            assertTrue(f.data.getProgression().kills().isEmpty());
            UUID last = f.kills(29, EntityType.ZOMBIE);
            assertEquals(10, f.service.filter(f.player, 10, f.orb(last)));
        }
    }

    @Test void movementRequiresTwoBlocksAndTeleportsAndVehiclesAreNotActivity() {
        try (Fixture f = new Fixture()) {
            Location start = new Location(f.world, 0, 64, 0);
            f.service.move(new PlayerMoveEvent(f.player, start, new Location(f.world, 1, 64, 0)));
            assertFalse(f.data.getProgression().active(System.nanoTime()));
            Location destination = new Location(f.world, 100, 64, 0);
            PlayerTeleportEvent teleport = new PlayerTeleportEvent(f.player, start, destination);
            f.service.teleport(teleport);
            f.service.move(teleport);
            assertFalse(f.data.getProgression().active(System.nanoTime()));
            when(f.player.isInsideVehicle()).thenReturn(true);
            f.service.move(new PlayerMoveEvent(f.player, destination, new Location(f.world, 102, 64, 0)));
            assertFalse(f.data.getProgression().active(System.nanoTime()));
            when(f.player.isInsideVehicle()).thenReturn(false);
            f.service.move(new PlayerMoveEvent(f.player, new Location(f.world, 102, 64, 0), new Location(f.world, 104, 64, 0)));
            assertTrue(f.data.getProgression().active(System.nanoTime()));
        }
    }

    @Test void cancelledEventsAreIgnoredByAllMutationObservers() throws Exception {
        for (var method : ProgressionService.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(org.bukkit.event.EventHandler.class);
            if (annotation != null && org.bukkit.event.Cancellable.class.isAssignableFrom(method.getParameterTypes()[0])) {
                assertTrue(annotation.ignoreCancelled(), method.getName());
                assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority());
            }
        }
    }

    @Test void flowingWaterOrOtherPassiveMovementDoesNotRefillBudget() {
        try (Fixture f = new Fixture()) {
            when(f.player.getCurrentInput()).thenReturn(mock(Input.class));
            f.service.move(new PlayerMoveEvent(f.player, new Location(f.world, 0, 64, 0),
                    new Location(f.world, 10, 64, 0)));
            assertFalse(f.data.getProgression().active(System.nanoTime()));
        }
    }
}
