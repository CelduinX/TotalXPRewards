package de.celduinx.totalxprewards;

import java.util.Map;
import java.util.TreeMap;

/** One shared calculation for rank-local placeholders and the BossBar fill. */
public record RankProgress(int number, int count, long earned, long required, long remaining,
        long nextThreshold, boolean maximum) {
    public static RankProgress calculate(Map<Long, Reward> rewards, long xp) {
        int number = 0;
        long previous = 0;
        for (long threshold : new TreeMap<>(rewards).keySet()) {
            if (threshold > xp) {
                long required = threshold - previous;
                long earned = Math.max(0, xp - previous);
                return new RankProgress(number, rewards.size(), earned, required,
                        Math.max(0, required - earned), threshold, false);
            }
            previous = threshold;
            number++;
        }
        return new RankProgress(number, rewards.size(), 0, 0, 0, 0, !rewards.isEmpty());
    }

    public double fill() {
        if (maximum) return 1;
        return required > 0 ? Math.clamp((double) earned / required, 0, 1) : 0;
    }
}
