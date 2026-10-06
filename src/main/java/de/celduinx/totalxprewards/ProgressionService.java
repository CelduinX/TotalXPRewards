package de.celduinx.totalxprewards;

import com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread adapter: does not change XP amounts, drops, spawns or event cancellation. */
public final class ProgressionService implements Listener {
    private final TotalXPRewardsPlugin plugin;
    private final NamespacedKey factorKey;
    private final Map<UUID, Location> movementPoints = new HashMap<>();
    private final Map<UUID, Death> deaths = new HashMap<>();
    private final BukkitTask timer;
    private long lastTick = System.nanoTime();
    private long lastSave = lastTick;
    private record Death(double factor, long expires) {}

    public ProgressionService(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
        factorKey = new NamespacedKey(plugin, "rank_xp_factor");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) movementPoints.put(p.getUniqueId(), p.getLocation());
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    static boolean eligible(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private ProgressionState state(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        return data == null ? null : data.getProgression();
    }

    private void tick() {
        long now = System.nanoTime();
        // A long main-thread stall must not turn server downtime into active playtime.
        boolean running = now - lastTick <= 5_000_000_000L;
        lastTick = now;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ProgressionState state = state(player);
            if (state != null) {
                state.settle(now, running && eligible(player), plugin.getProgressionSettings());
                state.prune(System.currentTimeMillis(), plugin.getProgressionSettings().windowSeconds());
            }
        }
        deaths.values().removeIf(d -> d.expires() < now);
        if (now - lastSave >= 60_000_000_000L) {
            plugin.getPlayerDataManager().saveAllAsync();
            lastSave = now;
        }
    }

    public int filter(Player player, int raw, Entity source) {
        ProgressionState state = state(player);
        if (state == null) return 0;
        ProgressionSettings settings = plugin.getProgressionSettings();
        long now = System.nanoTime();
        state.settle(now, now - lastTick <= 5_000_000_000L && eligible(player), settings);
        double factor = settings.farmEnabled() && source instanceof ExperienceOrb orb ? factor(orb) : 1;
        int result = state.accept(raw, factor, settings);
        if (settings.enabled() && state.noticeDue(now)) {
            String message = Lang.get("progression-limited").replace("%reason%", reasonText(state.reason()))
                    .replace("%budget%", String.format(java.util.Locale.ROOT, "%.1f", state.budget()));
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
        }
        return result;
    }

    public void settlePlayer(Player player) {
        ProgressionState state = state(player);
        long now = System.nanoTime();
        if (state != null) state.settle(now, now - lastTick <= 5_000_000_000L && eligible(player), plugin.getProgressionSettings());
    }

    public static String reasonText(String reason) {
        return Lang.get("progression-reason-" + reason);
    }

    private void activate(Player player) {
        ProgressionState state = state(player);
        long now = System.nanoTime();
        if (state != null) {
            if (now - lastTick > 5_000_000_000L) state.settle(now, false, plugin.getProgressionSettings());
            state.activate(now, eligible(player), plugin.getProgressionSettings());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        movementPoints.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { movementPoints.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        movementPoints.put(event.getPlayer().getUniqueId(), event.getTo().clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        Player p = event.getPlayer();
        Location to = event.getTo();
        Location previous = movementPoints.computeIfAbsent(p.getUniqueId(), key -> event.getFrom().clone());
        org.bukkit.Input input = p.getCurrentInput();
        boolean selfMovement = input != null && (input.isForward() || input.isBackward()
                || input.isLeft() || input.isRight() || input.isJump());
        if (p.isInsideVehicle() || p.isFlying() || p.isGliding() || !eligible(p)
                || !selfMovement || previous.getWorld() != to.getWorld()) {
            movementPoints.put(p.getUniqueId(), to.clone());
            return;
        }
        if (previous.distanceSquared(to) >= 4) {
            movementPoints.put(p.getUniqueId(), to.clone());
            activate(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { activate(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) { if (event.canBuild()) activate(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void attack(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (event.getDamager() instanceof Player player) activate(player);
        // Projectile impacts may happen long after the player went AFK; shooting is the activity.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player p) activate(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player p) activate(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventory(InventoryClickEvent event) {
        if (event.getAction() != org.bukkit.event.inventory.InventoryAction.NOTHING
                && event.getWhoClicked() instanceof Player p) activate(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryDrag(InventoryDragEvent event) {
        if (!event.getNewItems().isEmpty() && event.getWhoClicked() instanceof Player p) activate(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void changeMode(PlayerGameModeChangeEvent event) {
        settlePlayer(event.getPlayer());
        ProgressionState state = state(event.getPlayer());
        if (state != null) state.settle(System.nanoTime(), false, plugin.getProgressionSettings());
    }

    static boolean excluded(EntityType type) {
        return type == EntityType.PLAYER || type == EntityType.ENDER_DRAGON
                || type == EntityType.WITHER || type == EntityType.WARDEN;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (excluded(entity.getType()) || !(entity instanceof Mob)) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        ProgressionState state = state(killer);
        if (state == null) return;
        Location at = entity.getLocation();
        double factor = state.recordKill(new ProgressionState.Kill(System.currentTimeMillis(),
                at.getWorld().getUID(), at.getX(), at.getY(), at.getZ()), plugin.getProgressionSettings());
        deaths.put(entity.getUniqueId(), new Death(factor, System.nanoTime() + 600_000_000_000L));
        if (factor < 1 && event.getDroppedExp() > 0) {
            // Paper's award-time stacking can bypass both spawn and merge events.
            // Mark existing candidates before the death XP is awarded as well.
            for (Entity nearby : at.getWorld().getNearbyEntities(at, 1.5, 1.5, 1.5)) {
                if (nearby instanceof ExperienceOrb orb) stamp(orb, Math.min(factor, factor(orb)));
            }
        }
    }

    private double factor(ExperienceOrb orb) {
        Double stored = orb.getPersistentDataContainer().get(factorKey, PersistentDataType.DOUBLE);
        if (stored != null && Double.isFinite(stored)) return Math.clamp(stored, 0, 1);
        Death death = deaths.get(orb.getSourceEntityId());
        return death != null && death.expires() >= System.nanoTime() ? death.factor() : 1;
    }

    private void stamp(ExperienceOrb orb, double factor) {
        orb.getPersistentDataContainer().set(factorKey, PersistentDataType.DOUBLE, factor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof ExperienceOrb orb) stamp(orb, factor(orb));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void merge(ExperienceOrbMergeEvent event) {
        stamp(event.getMergeTarget(), Math.min(factor(event.getMergeTarget()), factor(event.getMergeSource())));
    }

    public void close() {
        timer.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) settlePlayer(p);
    }
}
