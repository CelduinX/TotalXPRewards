package de.celduinx.totalxprewards;

import org.bukkit.boss.BossBar;
import java.util.UUID;

/**
 * Holds runtime data for a player to reduce database calls.
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;
    private long totalXp;
    private String currentRankName;
    private BossBar bossBar; // Assigned by BossBarManager

    public PlayerData(UUID uuid, String name, long totalXp) {
        this.uuid = uuid;
        this.name = name;
        this.totalXp = totalXp;
        this.currentRankName = "None"; // Default
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(long xp) {
        this.totalXp = xp;
    }

    public void addXp(long amount) {
        this.totalXp += amount;
    }

    public String getCurrentRankName() {
        return currentRankName;
    }

    public void setCurrentRankName(String rankName) {
        this.currentRankName = rankName;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }
}
