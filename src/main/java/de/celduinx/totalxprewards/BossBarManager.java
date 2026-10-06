package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Manages the BossBar showing progress within the current rank. */
public class BossBarManager {

    private final TotalXPRewardsPlugin plugin;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final java.util.Set<UUID> hiddenPlayers = new java.util.HashSet<>();
    private final Map<UUID, Integer> hideTasks = new HashMap<>(); // Store task IDs

    private boolean enabled;
    private boolean dynamicMode;
    private int timeout;
    private String titleTemplate;
    private String maxTitleTemplate;
    private String noRanksTitleTemplate;
    private BarColor barColor;
    private BarStyle barStyle;

    public BossBarManager(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    static void validateConfig(FileConfiguration config) {
        try {
            BarColor.valueOf(config.getString("bossbar.color", "BLUE").toUpperCase(java.util.Locale.ROOT));
            BarStyle.valueOf(config.getString("bossbar.style", "SOLID").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid BossBar color or style", e);
        }
        if (config.getInt("bossbar.timeout", 10) < 0)
            throw new IllegalArgumentException("bossbar.timeout must be nonnegative");
    }

    /**
     * Reloads BossBar settings from config.
     */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("bossbar.enabled", false);
        this.dynamicMode = plugin.getConfig().getBoolean("bossbar.dynamic-mode", false);
        this.timeout = plugin.getConfig().getInt("bossbar.timeout", 5);
        this.titleTemplate = plugin.getConfig().getString("bossbar.title", "Next Rank: %next_rank%");
        this.maxTitleTemplate = plugin.getConfig().getString("bossbar.max-rank-title",
                "%current_rank%&r &7· Rang %rank_number%/%rank_count% &7· Höchster Rang erreicht");
        this.noRanksTitleTemplate = plugin.getConfig().getString("bossbar.no-ranks-title", "&7Keine Ränge konfiguriert");

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

        RankProgress rank = RankProgress.calculate(plugin.getRewards(), currentXp);

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar b = Bukkit.createBossBar("", barColor, barStyle);
            b.addPlayer(player);
            return b;
        });

        // Ensure settings are up to date
        bar.setColor(barColor);
        bar.setStyle(barStyle);
        bar.setVisible(true);

        bar.setProgress(rank.fill());
        String template = rank.count() == 0 ? noRanksTitleTemplate
                : rank.maximum() ? maxTitleTemplate : titleTemplate;
        String title = plugin.format(player, template, currentXp, rank.nextThreshold(), true);

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
