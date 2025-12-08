package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * package de.celduinx.totalxprewards;
 * 
 * import org.bukkit.Bukkit;
 * import org.bukkit.ChatColor;
 * import org.bukkit.boss.BarColor;
 * import org.bukkit.boss.BarStyle;
 * import org.bukkit.boss.BossBar;
 * import org.bukkit.entity.Player;
 * 
 * import java.util.HashMap;
 * import java.util.Map;
 * import java.util.UUID;
 * 
 * /**
 * Manages the BossBar for each player to display XP progress.
 */
public class BossBarManager {

    private final TotalXPRewardsPlugin plugin;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final java.util.Set<UUID> hiddenPlayers = new java.util.HashSet<>();
    private final Map<UUID, Integer> hideTasks = new HashMap<>(); // Store task IDs

    private boolean enabled;
    private boolean dynamicMode;
    private int timeout;
    private String titleTemplate;
    private BarColor barColor;
    private BarStyle barStyle;

    public BossBarManager(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads BossBar settings from config.
     */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("bossbar.enabled", false);
        this.dynamicMode = plugin.getConfig().getBoolean("bossbar.dynamic-mode", false);
        this.timeout = plugin.getConfig().getInt("bossbar.timeout", 5);
        this.titleTemplate = plugin.getConfig().getString("bossbar.title", "Next Rank: %next_rank%");

        String colorStr = plugin.getConfig().getString("bossbar.color", "BLUE");
        try {
            this.barColor = BarColor.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bossbar color: " + colorStr + ". Defaulting to BLUE.");
            this.barColor = BarColor.BLUE;
        }

        String styleStr = plugin.getConfig().getString("bossbar.style", "SOLID");
        try {
            this.barStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid bossbar style: " + styleStr + ". Defaulting to SOLID.");
            this.barStyle = BarStyle.SOLID;
        }

        // Update all online players to match new settings
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (enabled && !hiddenPlayers.contains(player.getUniqueId())) {
                // Determine their XP and update/create bar
                PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
                long xp = (data != null) ? data.getTotalXp() : 0;
                update(player, xp);
            } else {
                // If disabled or user hid it, remove bar
                remove(player);
            }
        }
    }

    public void showBar(Player player) {
        hiddenPlayers.remove(player.getUniqueId());
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        long xp = (data != null) ? data.getTotalXp() : 0;
        update(player, xp);
    }

    public void hideBar(Player player) {
        hiddenPlayers.add(player.getUniqueId());
        remove(player);
    }

    /**
     * Removes the BossBar for a player (e.g. on quit).
     */
    public void remove(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll(); // Removes from player
        }

        // Cancel any pending hide task
        if (hideTasks.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().cancelTask(hideTasks.get(player.getUniqueId()));
            hideTasks.remove(player.getUniqueId());
        }
    }

    /**
     * Updates the BossBar for a player based on their current XP.
     */
    public void update(Player player, long currentXp) {
        // If globally disabled or locally hidden, do nothing (or remove)
        if (!enabled || hiddenPlayers.contains(player.getUniqueId())) {
            if (bossBars.containsKey(player.getUniqueId())) {
                remove(player);
            }
            return;
        }

        // Find next threshold using the actual Reward object to get the Name
        long nextThreshold = -1;
        long prevThreshold = 0; // The threshold of the current rank (start of progress bar)

        // Maps are sorted in plugin.getRewards() (TreeMap)
        for (java.util.Map.Entry<Long, Reward> entry : plugin.getRewards().entrySet()) {
            long threshold = entry.getKey();
            if (threshold > currentXp) {
                nextThreshold = threshold;
                break;
            }
            prevThreshold = threshold;
        }

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar b = Bukkit.createBossBar("", barColor, barStyle);
            b.addPlayer(player);
            return b;
        });

        // Ensure settings are up to date
        bar.setColor(barColor);
        bar.setStyle(barStyle);
        bar.setVisible(true);

        if (nextThreshold == -1) {
            // Max level reached
            String maxTitle = ChatColor.translateAlternateColorCodes('&', "&aMax Rank Reached");
            // If the user wants to keep a specific title for max rank, we could add that to
            // config
            // For now, hardcoded or maybe use the last rank name?
            // Let's stick to "Max Rank Reached" or similar
            bar.setTitle(maxTitle);
            bar.setProgress(1.0);
            return;
        }

        // Calculate progress
        double range = nextThreshold - prevThreshold;
        double currentInRange = currentXp - prevThreshold;
        double progress = 0.0;

        if (range > 0) {
            progress = currentInRange / range;
        }

        // Clamp progress
        progress = Math.max(0.0, Math.min(1.0, progress));
        bar.setProgress(progress);

        // Format Title
        // Placeholder replacement for %next_rank% and %required_xp% is now handled
        // in plugin.format(), so we can just pass the raw template string.
        // We still pass nextThreshold as the 'threshold' legacy argument just in case.
        String title = plugin.format(player, titleTemplate, currentXp, nextThreshold, true);

        bar.setTitle(title);

        // Dynamic Mode Logic
        if (dynamicMode) {
            // Cancel existing hide task if any
            if (hideTasks.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().cancelTask(hideTasks.get(player.getUniqueId()));
                hideTasks.remove(player.getUniqueId());
            }

            // Schedule new hide task
            int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (bossBars.containsKey(player.getUniqueId())) {
                    // Only hide if allowed (not forced shown by command?)
                    // If we want /txp show to override dynamic mode, we need to check hiddenPlayers
                    // logic?
                    // But strictly speaking, dynamic mode usually means "auto hide".
                    // If user typed /txp show, they removed themselves from hiddenPlayers.
                    // But dynamic mode is top-level.
                    // Let's assume dynamic mode just hides the bar structure from the player,
                    // OR we just setVisible(false).
                    // Ideally we remove the bar to save resources.
                    remove(player);
                }
                hideTasks.remove(player.getUniqueId());
            }, timeout * 20L);

            hideTasks.put(player.getUniqueId(), taskId);
        }
    }
}
