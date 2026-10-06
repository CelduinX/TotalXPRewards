package de.celduinx.totalxprewards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

/**
 * Listens for player XP changes and forwards positive gains to the plugin for
 * processing. Negative or zero XP changes are ignored, as configured.
 */
public class XPListener implements Listener {

    private final TotalXPRewardsPlugin plugin;
    private final java.util.Map<java.util.UUID, Integer> beforeXp = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> naturalGains = new java.util.HashMap<>();
    private boolean commandCheckPending;

    public XPListener(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        if (amount <= 0) {
            return;
        }
        plugin.handleXpGain(event.getPlayer(), amount, event.getSource());
        if (beforeXp.containsKey(event.getPlayer().getUniqueId())) {
            naturalGains.merge(event.getPlayer().getUniqueId(), amount, Integer::sum);
        }
    }

    /**
     * Intercepts player commands to check for /xp or /experience usage.
     * Calculates XP difference before and after command execution to track gains.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (isXpCommand(event.getMessage())) {
            handleXpCommand();
        }
    }

    /**
     * Intercepts console commands to check for xp or experience usage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(org.bukkit.event.server.ServerCommandEvent event) {
        if (isXpCommand(event.getCommand())) {
            handleXpCommand();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoteServerCommand(org.bukkit.event.server.RemoteServerCommandEvent event) {
        if (isXpCommand(event.getCommand())) {
            handleXpCommand();
        }
    }

    private boolean isXpCommand(String command) {
        String name = command.strip().split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name.equals("xp") || name.equals("experience")
                || name.equals("minecraft:xp") || name.equals("minecraft:experience");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (plugin.getRankGroups() != null) plugin.getRankGroups().sync(event.getPlayer());
        if (plugin.getBossBarManager() != null) {
            PlayerData data = plugin.getPlayerDataManager().getData(event.getPlayer());
            if (data != null) {
                plugin.getBossBarManager().update(event.getPlayer(), data.getTotalXp());
            }
        }
        // TAB may install its per-player scoreboard during join; attach afterwards.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline() || plugin.getScoreboardManager() == null) return;
            PlayerData data = plugin.getPlayerDataManager().getData(event.getPlayer());
            if (data != null) plugin.getScoreboardManager().update(event.getPlayer(), data.getTotalXp());
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        beforeXp.remove(event.getPlayer().getUniqueId());
        naturalGains.remove(event.getPlayer().getUniqueId());
        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().remove(event.getPlayer());
        }
        if (plugin.getScoreboardManager() != null) plugin.getScoreboardManager().remove(event.getPlayer());
    }

    private void handleXpCommand() {
        // Share one snapshot for commands in the same tick to avoid counting twice.
        if (commandCheckPending) {
            return;
        }
        commandCheckPending = true;
        // Snapshot current total XP for all online players
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            beforeXp.put(p.getUniqueId(), p.calculateTotalExperiencePoints());
        }

        // Check 1 tick later
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            java.util.Map<java.util.UUID, Integer> snapshot = new java.util.HashMap<>(beforeXp);
            java.util.Map<java.util.UUID, Integer> gainedNaturally = new java.util.HashMap<>(naturalGains);
            beforeXp.clear();
            naturalGains.clear();
            commandCheckPending = false;
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                Integer oldTotal = snapshot.get(p.getUniqueId());
                if (oldTotal != null) {
                    int diff = p.calculateTotalExperiencePoints() - oldTotal
                            - gainedNaturally.getOrDefault(p.getUniqueId(), 0);
                    if (diff > 0) {
                        plugin.handleXpGain(p, diff);
                    }
                }
            }
        });
    }
}
