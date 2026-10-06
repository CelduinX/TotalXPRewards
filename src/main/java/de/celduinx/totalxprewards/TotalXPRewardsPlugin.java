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
import java.io.IOException;
import java.nio.file.Files;
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
 * messages in {@code ranks.yml}, and can localise static messages via
 * {@code lang.yml}.
 * </p>
 */
public final class TotalXPRewardsPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 3;
    private static TotalXPRewardsPlugin instance;

    private XPDatabase database;
    private volatile Map<Long, Reward> rewards = Map.of();
    private BossBarManager bossBarManager;
    private ScoreboardManager scoreboardManager;
    private PlayerDataManager playerDataManager;
    private volatile ProgressionSettings progressionSettings = ProgressionSettings.DEFAULT;
    private ProgressionService progressionService;
    private RankGroups rankGroups;
    private TotalXPPlaceholderExpansion placeholderExpansion;

    public ProgressionSettings getProgressionSettings() { return progressionSettings; }
    public ProgressionService getProgressionService() { return progressionService; }

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
        boolean freshInstall = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        if (freshInstall && !new File(getDataFolder(), "ranks.yml").exists()) {
            saveResource("ranks.yml", false);
        }
        if (!new java.io.File(getDataFolder(), "lang.yml").exists()) {
            saveResource("lang.yml", false);
        }

        // Migrate settings and extract legacy rewards into ranks.yml.
        migrateConfig();

        // Initialise language manager
        Lang.init(this);

        // Init SQLite
        this.database = new XPDatabase(this);

        // Load config + language + rewards
        reloadSettings();
        this.rankGroups = new RankGroups(this);
        rankGroups.validate(rewards);

        // Load rewards before calculating ranks for cached players.
        this.playerDataManager = new PlayerDataManager(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = new TotalXPPlaceholderExpansion(this);
            if (!placeholderExpansion.register()) {
                getLogger().warning("Could not register TotalXPRewards PlaceholderAPI expansion.");
                placeholderExpansion = null;
            }
        }

        // Initialise BossBar manager
        this.bossBarManager = new BossBarManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.progressionService = new ProgressionService(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(new XPListener(this), this);
        rankGroups.listen();

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

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) placeholderExpansion.unregister();
        if (progressionService != null) progressionService.close();
        if (rankGroups != null) rankGroups.close();
        if (bossBarManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBarManager.remove(player);
            }
        }
        if (scoreboardManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) scoreboardManager.remove(player);
        }
        if (playerDataManager != null) {
            playerDataManager.close();
        }
        if (database != null) {
            database.close();
        }
        instance = null;
    }

    private void migrateConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return;
        }

        // Load directly from disk to ensure we have the latest
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            config.load(configFile);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalArgumentException("Cannot migrate invalid config.yml", e);
        }

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

            if (currentVersion < 2) {
                org.bukkit.configuration.file.YamlConfiguration defaults =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                new java.io.InputStreamReader(java.util.Objects.requireNonNull(getResource("config.yml")),
                                        java.nio.charset.StandardCharsets.UTF_8));
                for (String key : defaults.getConfigurationSection("progression").getKeys(true)) {
                    String path = "progression." + key;
                    if (!defaults.isConfigurationSection(path) && !config.contains(path))
                        config.set(path, defaults.get(path));
                }
            }

            if (currentVersion < 3) {
                ConfigurationSection oldRewards = config.getConfigurationSection("rewards");
                File ranksFile = new File(getDataFolder(), "ranks.yml");
                if (oldRewards != null) {
                    if (ranksFile.exists()) {
                        throw new IllegalArgumentException("Both legacy config.yml rewards and ranks.yml exist; resolve before migration");
                    }
                    File backup = new File(getDataFolder(), "config.before-ranks-v3.yml");
                    try {
                        Files.copy(configFile.toPath(), backup.toPath());
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // Keep the original migration backup on repeated attempts.
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not back up config.yml before rank migration", e);
                    }
                    org.bukkit.configuration.file.YamlConfiguration ranks = new org.bukkit.configuration.file.YamlConfiguration();
                    for (String path : oldRewards.getKeys(true)) {
                        if (!oldRewards.isConfigurationSection(path)) {
                            ranks.set("rewards." + path, oldRewards.get(path));
                        }
                    }
                    try {
                        ranks.save(ranksFile);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not write ranks.yml; config.yml was not changed", e);
                    }
                    config.set("rewards", null);
                    changed = true;
                } else if (!ranksFile.exists()) {
                    throw new IllegalArgumentException("Missing both legacy rewards and ranks.yml");
                }
            }

            // Mark validation as done by updating version
            config.set("config-version", CONFIG_VERSION);
            changed = true;
        }

        if (config.isConfigurationSection("rewards")) {
            throw new IllegalArgumentException("Rewards belong in ranks.yml, not config.yml");
        }
        if (!new File(getDataFolder(), "ranks.yml").isFile()) {
            throw new IllegalArgumentException("Missing ranks.yml");
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
        org.bukkit.configuration.file.YamlConfiguration disk = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            disk.load(new File(getDataFolder(), "config.yml"));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalArgumentException("Cannot read config.yml: " + e.getMessage(), e);
        }
        ProgressionSettings candidate = ProgressionSettings.read(disk.getConfigurationSection("progression"));
        if (disk.isConfigurationSection("rewards")) {
            throw new IllegalArgumentException("Rewards belong in ranks.yml, not config.yml");
        }
        org.bukkit.configuration.file.YamlConfiguration rankDisk = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            rankDisk.load(new File(getDataFolder(), "ranks.yml"));
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalArgumentException("Cannot read ranks.yml: " + e.getMessage(), e);
        }
        Map<Long, Reward> candidateRewards = loadRewards(rankDisk.getConfigurationSection("rewards"));
        if (rankGroups != null) {
            List<String> problems = rankGroups.configurationProblems(candidateRewards);
            if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        }
        BossBarManager.validateConfig(disk);
        ScoreboardManager.validateConfig(disk);
        if (progressionService != null) {
            for (Player p : Bukkit.getOnlinePlayers()) progressionService.settlePlayer(p);
        }
        reloadConfig();
        Lang.reload(this);
        progressionSettings = candidate;
        rewards = java.util.Collections.unmodifiableMap(new TreeMap<>(candidateRewards));
        if (rankGroups != null) rankGroups.validate(rewards);
        if (bossBarManager != null) {
            bossBarManager.reload();
        }
        if (scoreboardManager != null) scoreboardManager.reload();
    }

    /**
     * Parses reward thresholds and commands from ranks.yml.
     */
    private Map<Long, Reward> loadRewards(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("No rewards section found in ranks.yml");
        }

        Map<Long, Reward> candidate = new TreeMap<>();

        for (String key : section.getKeys(false)) {
            long threshold;
            try {
                threshold = Long.parseLong(key);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid rank XP threshold: " + key, e);
            }
            if (threshold <= 0) throw new IllegalArgumentException("Rank XP threshold must be positive: " + key);

            // Backwards compatibility: a single "command" is still accepted.
            List<String> commands = section.getStringList(key + ".commands");
            String broadcast = section.getString(key + ".broadcast", "");
            String name = section.getString(key + ".name", "Rank " + threshold);
            String group = section.getString(key + ".group");
            List<String> scoreboardRewards = section.getStringList(key + ".scoreboard-rewards");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Missing rank name at XP " + key);
            if (commands.isEmpty()) {
                String single = section.getString(key + ".command");
                if (single != null && !single.isEmpty()) {
                    commands = java.util.Collections.singletonList(single);
                }
            }
            if (commands.stream().filter(java.util.Objects::nonNull)
                    .anyMatch(command -> command.strip().matches("(?i)^/?(lp|luckperms)(:luckperms)?\\s+.*"))) {
                throw new IllegalArgumentException("LuckPerms commands are not allowed in XP rewards at " + key);
            }

            Reward reward = new Reward(threshold, commands, broadcast, name, group, scoreboardRewards);
            if (candidate.putIfAbsent(threshold, reward) != null)
                throw new IllegalArgumentException("Duplicate rank XP threshold: " + key);
        }

        getLogger().info("Loaded " + candidate.size() + " rewards from ranks.yml.");
        return candidate;
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

    ScoreboardManager getScoreboardManager() { return scoreboardManager; }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RankGroups getRankGroups() { return rankGroups; }
    public boolean hasPlaceholderExpansion() { return placeholderExpansion != null; }

    /**
     * Handles an XP gain event.
     */
    public void handleXpGain(Player player, int amount) {
        handleXpGain(player, amount, null);
    }

    public void handleXpGain(Player player, int amount, org.bukkit.entity.Entity source) {
        if (amount <= 0) {
            return; // ignore zero/negative XP
        }
        if (rankGroups == null || !rankGroups.isUnlocked(player.getUniqueId())) return;

        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);
        if (data == null)
            return; // Should not happen if online

        if (progressionService != null) amount = progressionService.filter(player, amount, source);
        if (amount <= 0) return;

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
        if (scoreboardManager != null) scoreboardManager.update(player, newTotal);

        // Check reward thresholds
        try {
        for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
            long threshold = entry.getKey();

            if (threshold > newTotal) {
                break;
            }
            if (threshold <= current) {
                continue;
            }
            if (data.hasLoadedRewardHistory() ? data.hasReward(threshold) : database.hasReward(uuid, threshold)) {
                continue;
            }
            if (!rankGroups.isUnlocked(uuid)) return;

            Reward reward = entry.getValue();
            if (!executeReward(player, reward, newTotal, threshold)) return;
            database.setRewardGiven(uuid, threshold);
            data.markReward(threshold);
        }
        } catch (RuntimeException error) {
            getLogger().severe("Could not safely award rank XP reward for " + uuid + ": " + error);
            return;
        }
        rankGroups.sync(player);
    }

    /**
     * Executes all commands and broadcast for a reward.
     */
    private boolean executeReward(Player player, Reward reward, long xp, long threshold) {

        // Run commands
        for (String command : reward.getCommands()) {
            if (!rankGroups.isUnlocked(player.getUniqueId())) return false;
            if (command == null || command.isEmpty()) {
                continue;
            }

            if (ItemRewardDelivery.deliver(player, command)) continue;

            String cmd = format(player, command, xp, threshold, false);

            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }

            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                getLogger().severe("Reward command failed for " + player.getUniqueId() + ": " + cmd);
                return false;
            }
        }

        // Run broadcast
        String broadcast = reward.getBroadcast();
        if (broadcast != null && !broadcast.isEmpty()) {
            String msg = format(player, broadcast, xp, threshold, true);
            if (msg != null && !msg.isEmpty()) {
                Bukkit.broadcastMessage(Lang.get("prefix") + msg);
            }
        }
        return true;
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
        if (text.contains("%rank_")) {
            RankProgress progress = RankProgress.calculate(rewards, xp);
            text = text.replace("%rank_number%", String.valueOf(progress.number()))
                    .replace("%rank_count%", String.valueOf(progress.count()))
                    .replace("%rank_xp%", String.valueOf(progress.earned()))
                    .replace("%rank_required_xp%", String.valueOf(progress.required()))
                    .replace("%rank_remaining_xp%", String.valueOf(progress.remaining()));
        }
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
