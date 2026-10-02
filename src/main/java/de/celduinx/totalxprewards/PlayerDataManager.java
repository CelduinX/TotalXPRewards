package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager implements Listener {

    private final TotalXPRewardsPlugin plugin;
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public PlayerDataManager(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Load data for any players already online (reloads)
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p.getUniqueId(), p.getName());
        }
    }

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        // Pre-load data async if possible
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            load(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Ensure data is loaded (if async login failed or wasn't used)
        if (!dataMap.containsKey(event.getPlayer().getUniqueId())) {
            // Fallback sync load if needed, but ideally we did it async
            load(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveAndRemove(event.getPlayer().getUniqueId());
    }

    private void load(UUID uuid, String name) {
        // Usually runs during async pre-login; the join fallback must finish
        // before the player can gain XP. Never publish a stale async load later.
        dataMap.computeIfAbsent(uuid, key -> {
            long xp = plugin.getDatabase().getXp(uuid);
            PlayerData data = new PlayerData(uuid, name, xp);
            ProgressionSettings settings = plugin.getProgressionSettings();
            data.setProgression(plugin.getDatabase().getProgression(uuid,
                    settings == null ? 1000 : settings.capacity()));
            // Calculate Rank
            String rank = plugin.getRankName(xp);
            data.setCurrentRankName(rank);

            return data;
        });
    }

    private void saveAndRemove(UUID uuid) {
        PlayerData data = dataMap.get(uuid);
        if (data != null) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
            if (player != null && plugin.getProgressionService() != null)
                plugin.getProgressionService().settlePlayer(player);
            // Finish saving before a reconnect or server shutdown can reload/close it.
            plugin.getDatabase().saveData(data);
            dataMap.remove(uuid, data);

            // Cleanup BossBar
            if (data.getBossBar() != null) {
                data.getBossBar().removeAll();
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : dataMap.values()) {
            plugin.getDatabase().saveData(data);
        }
    }

    public PlayerData getData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public PlayerData getData(Player player) {
        return getData(player.getUniqueId());
    }
}
