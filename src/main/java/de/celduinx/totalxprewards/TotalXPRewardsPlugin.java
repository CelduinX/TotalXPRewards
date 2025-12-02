package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static TotalXPRewardsPlugin instance;

    private XPDatabase database;
    private final Map<Long, Reward> rewards = new TreeMap<>();

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
        saveResource("lang.yml", false);

        // Initialise language manager
        Lang.init(this);

        // Load config + language
        reloadSettings();

        // Init SQLite
        this.database = new XPDatabase(this);

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

        getLogger().info("TotalXPRewards enabled.");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("TotalXPRewards disabled.");
    }

    /**
     * Reloads plugin settings, including config and language files.
     */
    public void reloadSettings() {
        reloadConfig();
        Lang.reload(this);
        loadRewards();
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

                Reward reward = new Reward(threshold, commands, broadcast);
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

    /**
     * Handles an XP gain event.
     */
    public void handleXpGain(Player player, int amount) {
        if (amount <= 0) {
            return; // ignore zero/negative XP
        }

        UUID uuid = player.getUniqueId();
        long current = database.getXp(uuid);
        long newTotal = current + amount;
        database.setXp(uuid, newTotal);

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
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = text.replace("%player%", player.getName())
                .replace("%xp%", String.valueOf(xp))
                .replace("%threshold%", String.valueOf(threshold));

        if (isPlaceholderAPIEnabled()) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable t) {
                getLogger().warning("Error applying PlaceholderAPI: " + t.getMessage());
            }
        }

        if (colour) {
            text = ChatColor.translateAlternateColorCodes('&', text);
        }

        return text;
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
