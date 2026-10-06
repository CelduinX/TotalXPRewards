package de.celduinx.totalxprewards;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Player XP and rank values for PlaceholderAPI consumers such as TAB. */
public final class TotalXPPlaceholderExpansion extends PlaceholderExpansion {
    private final TotalXPRewardsPlugin plugin;

    public TotalXPPlaceholderExpansion(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "totalxprewards"; }
    @Override public @NotNull String getAuthor() { return "CelduinX"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        // Online players use the live cache; offline queries use the saved XP.
        long xp = data != null ? data.getTotalXp() : plugin.getDatabase().getXp(player.getUniqueId());
        return resolve(plugin.getRewards(), xp, params);
    }

    static String resolve(Map<Long, Reward> rewards, long xp, String params) {
        RankProgress progress = RankProgress.calculate(rewards, xp);
        Reward current = null;
        Reward next = null;
        long currentThreshold = 0;
        for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
            if (entry.getKey() <= xp) {
                current = entry.getValue();
                currentThreshold = entry.getKey();
            } else {
                next = entry.getValue();
                break;
            }
        }
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "xp" -> String.valueOf(xp);
            case "rank_number" -> String.valueOf(progress.number());
            case "rank_count" -> String.valueOf(progress.count());
            case "rank_xp" -> String.valueOf(progress.earned());
            case "rank_required_xp" -> String.valueOf(progress.required());
            case "rank_remaining_xp" -> String.valueOf(progress.remaining());
            case "rank_start_xp" -> String.valueOf(currentThreshold);
            case "next_rank_xp", "required_xp" -> String.valueOf(progress.nextThreshold());
            case "current_rank" -> current == null ? "None" : RankNameFormatting.formattedName(current.getName());
            case "current_rank_plain" -> current == null ? "None" : RankNameFormatting.plainName(current.getName());
            case "current_rank_group" -> current == null ? "" : current.getGroup();
            case "next_rank" -> next == null ? Lang.get("max-rank") : RankNameFormatting.formattedName(next.getName());
            case "next_rank_plain" -> next == null ? RankNameFormatting.plainName(Lang.get("max-rank"))
                    : RankNameFormatting.plainName(next.getName());
            case "next_rank_group" -> next == null ? "" : next.getGroup();
            default -> null;
        };
    }
}
