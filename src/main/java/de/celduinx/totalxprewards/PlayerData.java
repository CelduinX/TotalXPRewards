package de.celduinx.totalxprewards;

import org.bukkit.boss.BossBar;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

/**
 * Holds runtime data for a player to reduce database calls.
 */
public class PlayerData {

    private final UUID uuid;
    private final String name;
    private long totalXp;
    private String currentRankName;
    private BossBar bossBar; // Assigned by BossBarManager
    private ProgressionState progression;
    private Set<Long> rewardHistory;

    public PlayerData(UUID uuid, String name, long totalXp) {
        this.uuid = uuid;
        this.name = name;
        this.totalXp = totalXp;
        this.currentRankName = "None"; // Default
    }

    public UUID getUuid() {
        return uuid;
    }

    public ProgressionState getProgression() { return progression; }
    public void setProgression(ProgressionState progression) { this.progression = progression; }

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

    public void setRewardHistory(Set<Long> thresholds) {
        rewardHistory = new HashSet<>(thresholds == null ? Set.of() : thresholds);
    }

    public boolean hasReward(long threshold) {
        return rewardHistory != null && rewardHistory.contains(threshold);
    }

    public boolean hasLoadedRewardHistory() { return rewardHistory != null; }

    public void markReward(long threshold) {
        if (rewardHistory != null) rewardHistory.add(threshold);
    }

    public void clearRewardHistory() {
        if (rewardHistory != null) rewardHistory.clear();
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public PlayerData snapshot() {
        PlayerData copy = new PlayerData(uuid, name, totalXp);
        copy.setCurrentRankName(currentRankName);
        if (progression != null) copy.setProgression(progression.snapshot());
        return copy;
    }
}
