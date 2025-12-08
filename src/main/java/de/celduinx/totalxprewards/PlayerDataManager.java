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
            load(p.getUniqueId(), p.getName(), true);
        }
    }

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        // Pre-load data async if possible
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            load(event.getUniqueId(), event.getName(), false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Ensure data is loaded (if async login failed or wasn't used)
        if (!dataMap.containsKey(event.getPlayer().getUniqueId())) {
            // Fallback sync load if needed, but ideally we did it async
            load(event.getPlayer().getUniqueId(), event.getPlayer().getName(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveAndRemove(event.getPlayer().getUniqueId());
    }

    private void load(UUID uuid, String name, boolean async) {
        if (dataMap.containsKey(uuid))
            return;

        Runnable loadTask = () -> {
            long xp = plugin.getDatabase().getXp(uuid);
            PlayerData data = new PlayerData(uuid, name, xp);
            // Calculate Rank
            String rank = plugin.getRankName(xp);
            data.setCurrentRankName(rank);

            dataMap.put(uuid, data);
        };

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, loadTask);
        } else {
            loadTask.run();
        }
    }

    private void saveAndRemove(UUID uuid) {
        PlayerData data = dataMap.remove(uuid);
        if (data != null) {
            // Save Async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabase().setPlayerData(uuid, data.getTotalXp(), data.getName(), data.getCurrentRankName());
            });

            // Cleanup BossBar
            if (data.getBossBar() != null) {
                data.getBossBar().removeAll();
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : dataMap.values()) {
            plugin.getDatabase().setPlayerData(data.getUuid(), data.getTotalXp(), data.getName(),
                    data.getCurrentRankName());
        }
    }

    public PlayerData getData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public PlayerData getData(Player player) {
        return getData(player.getUniqueId());
    }
}
