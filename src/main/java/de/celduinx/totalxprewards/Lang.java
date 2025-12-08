package de.celduinx.totalxprewards;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for loading and retrieving translatable messages from a YAML
 * file.
 *
 * <p>
 * This class loads {@code lang.yml} from the plugin's data folder, copying the
 * default version from the JAR on first run. It also maintains a set of
 * built‑in English defaults so that missing keys in the external file do not
 * produce null messages. Colour codes prefixed with {@code &} are
 * automatically translated to Minecraft's {@code §} character when retrieving
 * string messages or lists.
 * </p>
 */
public final class Lang {

    private static FileConfiguration config;
    private static final Map<String, Object> defaults = new HashMap<>();

    private Lang() {
        // utility class
    }

    /**
     * Initialises the language system and loads messages. This should be called
     * once from the plugin's {@code onEnable()} method.
     *
     * @param plugin the plugin instance
     */
    public static void init(JavaPlugin plugin) {
        // Define built‑in defaults. These act as fallbacks when keys are missing.
        defaults.put("prefix", "&7[&aTotalXP&7] ");
        defaults.put("no-permission", "&cYou do not have permission.");
        defaults.put("player-not-found", "&cPlayer not found.");
        defaults.put("invalid-number", "&cPlease enter a valid number.");
        defaults.put("negative-amount", "&cAmount must not be negative.");
        defaults.put("xp-view", "&a%player% has &e%xp% &atotal XP.");
        defaults.put("xp-set", "&aSet &e%player%&a's XP to &e%amount%.");
        defaults.put("xp-reset", "&aReset XP of &e%player%&a.");
        defaults.put("max-rank", "Max Rank");
        defaults.put("reload-done", "&aTotal XP Rewards config and language reloaded.");
        defaults.put("help", java.util.Arrays.asList(
                "&7---- &aTotal XP Rewards Help &7----",
                "&a/%label% get <player> &7- Show player's total XP",
                "&a/%label% set <player> <amount> &7- Set player's total XP",
                "&a/%label% reset <player> &7- Reset player's total XP",
                "&a/%label% reload &7- Reload config and language"));

        reload(plugin);
    }

    /**
     * Reloads the language file. This reads {@code lang.yml} from the data
     * folder, copying the default version from the JAR if necessary, and sets
     * up defaults from any bundled resource. Should be called when reloading
     * plugin settings.
     *
     * @param plugin the plugin instance
     */
    public static void reload(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            // Copy the default language file from the JAR on first run
            plugin.saveResource("lang.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Load defaults from a resource inside the JAR, if present. This ensures
        // that any keys defined in the default file act as fallbacks in the
        // external file.
        InputStream defConfigStream = plugin.getResource("lang.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            config.setDefaults(defConfig);
        }
    }

    /**
     * Retrieves a string message from the language file. If the key is missing
     * entirely, the built‑in default is returned instead. Colour codes are
     * translated.
     *
     * @param key the configuration key
     * @return the translated message, or an empty string if undefined
     */
    public static String get(String key) {
        Object val = config.get(key);
        if (val == null) {
            val = defaults.get(key);
        }
        if (val == null) {
            return "";
        }
        if (val instanceof String) {
            return ChatColor.translateAlternateColorCodes('&', (String) val);
        }
        return val.toString();
    }

    /**
     * Retrieves a list of string messages from the language file. If the key is
     * missing or empty, the built‑in default list is returned instead.
     * Colour codes are translated on each line.
     *
     * @param key the configuration key
     * @return the list of translated messages, or {@code null} if undefined
     */
    @SuppressWarnings("unchecked")
    public static List<String> getList(String key) {
        List<String> list = config.getStringList(key);
        if (list == null || list.isEmpty()) {
            Object def = defaults.get(key);
            if (def instanceof List) {
                list = (List<String>) def;
            }
        }
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                list.set(i, ChatColor.translateAlternateColorCodes('&', list.get(i)));
            }
        }
        return list;
    }
}