package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Main class for the Total XP Rewards plugin.
 *
 * <p>
 * This plugin tracks the total amount of XP a player ever gains (across deaths
 * and resets) and issues rewards when configured thresholds are reached.
 * XP is stored in a SQLite database for persistency. Administrators can
 * configure reward thresholds and associated console commands and broadcast
 * messages in {@code config.yml}, and can localise static messages via
 * {@code lang.yml}.
 * </p>
 */
public final class TotalXPRewardsPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 1;
    private static TotalXPRewardsPlugin instance;

    private XPDatabase database;
    private final Map<Long, Reward> rewards = new TreeMap<>();
    private BossBarManager bossBarManager;
    private PlayerDataManager playerDataManager;

    /**
     * Gets the singleton instance of this plugin.
     *
     * @return the plugin instance
     */
    public static TotalXPRewardsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Copy default config and language file from JAR
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "lang.yml").exists()) {
            saveResource("lang.yml", false);
        }

        // Migrate config (e.g. v1.0.0 -> v1.0.1 -> v1.0.2)
        migrateConfig();

        // Initialise language manager
        Lang.init(this);

        // Init SQLite
        this.database = new XPDatabase(this);

        // Init Cache Manager
        this.playerDataManager = new PlayerDataManager(this);

        // Load config + language + rewards
        reloadSettings();

        // Initialise BossBar manager
        this.bossBarManager = new BossBarManager(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(new XPListener(this), this);

        // Register commands
        CommandTotalXP cmd = new CommandTotalXP(this);
        PluginCommand command = getCommand("totalxp");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        } else {
            getLogger().severe("Command 'totalxp' not found in plugin.yml!");
        }

        // Initialise bStats Metrics
        int pluginId = 28208;
        new Metrics(this, pluginId);

        getLogger().info("TotalXPRewards enabled.");
    }

    private void migrateConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return;
        }

        // Load directly from disk to ensure we have the latest
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configFile);

        int currentVersion = config.getInt("config-version", 0);
        boolean changed = false;

        if (currentVersion < CONFIG_VERSION) {
            getLogger().info("Migrating configuration from v" + currentVersion + " to v" + CONFIG_VERSION + "...");

            // Migration Steps
            if (currentVersion < 1) {
                // Pre-versioning migration (add bossbar, update rewards)

                // BossBar Section check
                if (!config.isConfigurationSection("bossbar")) {
                    config.set("bossbar.enabled", true);
                    config.set("bossbar.dynamic-mode", false);
                    config.set("bossbar.timeout", 10);
                    config.set("bossbar.title", "&bNext Rank: &e%next_rank% &7(&a%xp%&7/&c%required_xp%&7)");
                    config.set("bossbar.color", "BLUE");
                    config.set("bossbar.style", "SOLID");
                    changed = true;
                } else {
                    if (!config.contains("bossbar.dynamic-mode")) {
                        config.set("bossbar.dynamic-mode", false);
                        changed = true;
                    }
                    if (!config.contains("bossbar.timeout")) {
                        config.set("bossbar.timeout", 10);
                        changed = true;
                    }
                }

                // Reward Names check
                ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
                if (rewardsSection != null) {
                    for (String key : rewardsSection.getKeys(false)) {
                        if (!rewardsSection.contains(key + ".name")) {
                            config.set("rewards." + key + ".name", "Rank " + key);
                            changed = true;
                        }
                    }
                }
            }

            // Mark validation as done by updating version
            config.set("config-version", CONFIG_VERSION);
            changed = true;
        }

        if (changed) {
            try {
                config.save(configFile);
                getLogger().info("Config migration complete. Saved to disk.");
                // Reload the plugin config instance to pick up changes
                reloadConfig();
            } catch (java.io.IOException e) {
                getLogger().severe("Could not save migrated config: " + e.getMessage());
            }
        }
    }

    /**
     * Reloads plugin settings, including config and language files.
     */
    public void reloadSettings() {
        reloadConfig();
        Lang.reload(this);
        loadRewards();
        if (bossBarManager != null) {
            bossBarManager.reload();
        }
    }

    /**
     * Parses reward thresholds and commands from config.yml.
     */
    private void loadRewards() {
        rewards.clear();

        ConfigurationSection section = getConfig().getConfigurationSection("rewards");
        if (section == null) {
            getLogger().warning("No rewards section found in config.yml");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                long threshold = Long.parseLong(key);

                // Load commands list
                List<String> commands = section.getStringList(key + ".commands");
                String broadcast = section.getString(key + ".broadcast", "");
                String name = section.getString(key + ".name", "Rank " + threshold);

                // Backwards compatibility: "command: <string>"
                if (commands.isEmpty()) {
                    String single = section.getString(key + ".command");
                    if (single != null && !single.isEmpty()) {
                        commands = java.util.Collections.singletonList(single);
                    }
                }

                // Skip invalid entries
                if ((commands == null || commands.isEmpty()) &&
                        (broadcast == null || broadcast.isEmpty())) {
                    getLogger().warning("Reward " + key + " has no commands and no broadcast, skipping.");
                    continue;
                }

                Reward reward = new Reward(threshold, commands, broadcast, name);
                rewards.put(threshold, reward);

            } catch (NumberFormatException e) {
                getLogger().warning("Invalid reward key (not numeric): " + key);
            }
        }

        getLogger().info("Loaded " + rewards.size() + " rewards from config.");
    }

    public XPDatabase getDatabase() {
        return database;
    }

    public Map<Long, Reward> getRewards() {
        return rewards;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    /**
     * Handles an XP gain event.
     */
    public void handleXpGain(Player player, int amount) {
        if (amount <= 0) {
            return; // ignore zero/negative XP
        }

        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);
        if (data == null)
            return; // Should not happen if online

        data.addXp(amount);
        long newTotal = data.getTotalXp();

        // Update cached rank name for DB consistency
        data.setCurrentRankName(getRankName(newTotal));

        long current = newTotal - amount;

        // We do NOT save to DB here instantly anymore. Caching handles it.

        // Update BossBar
        if (bossBarManager != null) {
            bossBarManager.update(player, newTotal);
        }

        // Check reward thresholds
        for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
            long threshold = entry.getKey();

            if (threshold > newTotal) {
                break;
            }
            if (threshold <= current) {
                continue;
            }
            if (database.hasReward(uuid, threshold)) {
                continue;
            }

            Reward reward = entry.getValue();
            executeReward(player, reward, newTotal, threshold);
            database.setRewardGiven(uuid, threshold);
        }
    }

    /**
     * Executes all commands and broadcast for a reward.
     */
    private void executeReward(Player player, Reward reward, long xp, long threshold) {

        // Run commands
        for (String command : reward.getCommands()) {
            if (command == null || command.isEmpty()) {
                continue;
            }

            String cmd = format(player, command, xp, threshold, false);

            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // Run broadcast
        String broadcast = reward.getBroadcast();
        if (broadcast != null && !broadcast.isEmpty()) {
            String msg = format(player, broadcast, xp, threshold, true);
            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(Lang.get("prefix") + msg);
            }
        }
    }

    /**
     * Applies placeholders + color codes.
     */
    public String format(Player player, String text, long xp, long threshold, boolean colour) {
        Component comp = formatToComponent(player, text, xp, threshold);
        // Serialize to Legacy String with Hex support
        return LegacyComponentSerializer.legacySection().serialize(comp);
    }

    public String getRankName(long xp) {
        String currentRankName = "None"; // Default if no rank
        // Iterate rewards to find the highest threshold <= xp
        for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
            if (entry.getKey() <= xp) {
                currentRankName = entry.getValue().getName();
            } else {
                break;
            }
        }
        return currentRankName;
    }

    public Component formatToComponent(Player player, String text, long xp, long threshold) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // 1. Calculate %current_rank% if needed
        if (text.contains("%current_rank%")) {
            text = text.replace("%current_rank%", getRankName(xp));
        }

        // 2. Calculate %next_rank% and %required_xp% if needed
        if (text.contains("%next_rank%") || text.contains("%required_xp%")) {
            String nextRankName = Lang.get("max-rank");
            long nextThresholdVal = -1;

            for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
                if (entry.getKey() > xp) {
                    nextRankName = entry.getValue().getName();
                    nextThresholdVal = entry.getKey();
                    break;
                }
            }

            text = text.replace("%next_rank%", nextRankName);

            if (nextThresholdVal != -1) {
                text = text.replace("%required_xp%", String.valueOf(nextThresholdVal));
            } else {
                text = text.replace("%required_xp%", "0");
            }
        }

        // 3. Standard replacements
        text = text.replace("%player%", player.getName())
                .replace("%xp%", String.valueOf(xp))
                .replace("%threshold%", String.valueOf(threshold));

        // 4. PlaceholderAPI
        if (isPlaceholderAPIEnabled()) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable t) {
                getLogger().warning("Error applying PlaceholderAPI: " + t.getMessage());
            }
        }

        // 5. Parse
        // Hybrid Support:
        // If the text contains legacy '&' codes, we convert them to MiniMessage tags
        // FIRST.
        // This allows mixed usage (e.g. "&bTitle: <gradient>...") to work correctly.
        if (text.contains("&")) {
            text = convertLegacyToMiniMessage(text);
        }

        return MiniMessage.miniMessage().deserialize(text);
    }

    private String convertLegacyToMiniMessage(String text) {
        return text.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>")
                // Handle uppercase variants
                .replace("&A", "<green>")
                .replace("&B", "<aqua>")
                .replace("&C", "<red>")
                .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")
                .replace("&F", "<white>")
                .replace("&K", "<obfuscated>")
                .replace("&L", "<bold>")
                .replace("&M", "<strikethrough>")
                .replace("&N", "<underlined>")
                .replace("&O", "<italic>")
                .replace("&R", "<reset>");
    }

    /**
     * Checks PlaceholderAPI availability.
     */
    public boolean isPlaceholderAPIEnabled() {
        boolean configUsePapi = getConfig().getBoolean("settings.use-placeholderapi", true);
        boolean hasPapi = (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);
        return configUsePapi && hasPapi;
    }
}
