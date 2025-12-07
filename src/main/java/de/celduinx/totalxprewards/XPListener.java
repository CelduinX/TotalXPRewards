package de.celduinx.totalxprewards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

/**
 * Listens for player XP changes and forwards positive gains to the plugin for
 * processing. Negative or zero XP changes are ignored, as configured.
 */
public class XPListener implements Listener {

    private final TotalXPRewardsPlugin plugin;

    public XPListener(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        if (amount <= 0) {
            return;
        }
        plugin.handleXpGain(event.getPlayer(), amount);
    }

    /**
     * Intercepts player commands to check for /xp or /experience usage.
     * Calculates XP difference before and after command execution to track gains.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith("/xp ") || msg.startsWith("/experience ") || msg.equals("/xp")
                || msg.equals("/experience")) {
            handleXpCommand();
        }
    }

    /**
     * Intercepts console commands to check for xp or experience usage.
     */
    @EventHandler(ignoreCancelled = true)
    public void onServerCommand(org.bukkit.event.server.ServerCommandEvent event) {
        String cmd = event.getCommand().toLowerCase();
        if (cmd.startsWith("xp ") || cmd.startsWith("experience ") || cmd.equals("xp") || cmd.equals("experience")) {
            handleXpCommand();
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (plugin.getBossBarManager() != null) {
            java.util.UUID uuid = event.getPlayer().getUniqueId();
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                long xp = plugin.getDatabase().getXp(uuid);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (event.getPlayer().isOnline()) {
                        plugin.getBossBarManager().update(event.getPlayer(), xp);
                    }
                });
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().remove(event.getPlayer());
        }
    }

    private void handleXpCommand() {
        // Snapshot current total XP for all online players
        java.util.Map<java.util.UUID, Integer> beforeXp = new java.util.HashMap<>();
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            beforeXp.put(p.getUniqueId(), p.getTotalExperience());
        }

        // Check 1 tick later
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!beforeXp.containsKey(p.getUniqueId()))
                    continue;

                int oldTotal = beforeXp.get(p.getUniqueId());
                int newTotal = p.getTotalExperience();
                int diff = newTotal - oldTotal;

                if (diff > 0) {
                    plugin.handleXpGain(p, diff);
                }
            }
        });
    }
}