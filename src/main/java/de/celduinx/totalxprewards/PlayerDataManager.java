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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlayerDataManager implements Listener {

    private final TotalXPRewardsPlugin plugin;
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();
    private final ExecutorService saveWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TotalXPRewards-save");
        thread.setDaemon(true);
        return thread;
    });

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
            data.setRewardHistory(plugin.getDatabase().getRewardHistory(uuid));
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
            try {
                // Run after queued periodic snapshots so an older snapshot cannot
                // overwrite the final quit state.
                PlayerData snapshot = data.snapshot();
                saveWorker.submit(() -> plugin.getDatabase().saveData(snapshot)).get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Could not save XP on quit", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while saving XP on quit", e);
            }
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

    /** Snapshot on the server thread, write on one worker to avoid tick stalls. */
    public void saveAllAsync() {
        for (PlayerData data : dataMap.values()) {
            PlayerData snapshot = data.snapshot();
            saveWorker.execute(() -> {
                try {
                    plugin.getDatabase().saveData(snapshot);
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Could not save XP for " + snapshot.getUuid() + ": " + e);
                }
            });
        }
    }

    public void close() {
        saveWorker.shutdown();
        boolean interrupted = false;
        while (true) {
            try {
                if (saveWorker.awaitTermination(30, TimeUnit.SECONDS)) break;
                plugin.getLogger().warning("Still waiting for queued XP saves.");
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        // Final current state is written after queued snapshots, before the DB closes.
        saveAll();
    }

    public PlayerData getData(UUID uuid) {
        return dataMap.get(uuid);
    }

    public PlayerData getData(Player player) {
        return getData(player.getUniqueId());
    }
}
