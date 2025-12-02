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
}