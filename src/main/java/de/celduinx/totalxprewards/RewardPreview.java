package de.celduinx.totalxprewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the next rank with visible rewards and describes its give commands. */
final class RewardPreview {
    private static final Pattern GIVE = Pattern.compile(
            "^/?(?:minecraft:)?give\\s+%player%\\s+(.+?)(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);

    record Next(int number, Reward reward) {}

    private RewardPreview() {}

    static Next next(Map<Long, Reward> ranks, long xp) {
        int number = 0;
        for (Map.Entry<Long, Reward> entry : ranks.entrySet()) {
            number++;
            if (entry.getKey() <= xp) continue;
            Reward reward = entry.getValue();
            if (!reward.getScoreboardRewards().isEmpty() || reward.getCommands().stream()
                    .anyMatch(command -> command != null && GIVE.matcher(command.strip()).matches())) {
                return new Next(number, reward);
            }
        }
        return null;
    }

    static List<Component> lines(Reward reward, TotalXPRewardsPlugin plugin,
            org.bukkit.entity.Player player, long xp) {
        List<Component> lines = new ArrayList<>();
        if (!reward.getScoreboardRewards().isEmpty()) {
            for (String label : reward.getScoreboardRewards()) {
                lines.add(plugin.formatToComponent(player, label, xp, reward.getThreshold()));
            }
            return lines;
        }
        for (String command : reward.getCommands()) {
            if (command == null) continue;
            Matcher match = GIVE.matcher(command.strip());
            if (!match.matches()) continue;
            try {
                int amount = match.group(2) == null ? 1 : Integer.parseInt(match.group(2));
                ItemStack item = Bukkit.getItemFactory().createItemStack(match.group(1));
                lines.add(Component.text("- ", NamedTextColor.GRAY)
                        .append(Component.text(amount + " ", NamedTextColor.WHITE))
                        .append(item.effectiveName()));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Cannot preview give reward at XP " + reward.getThreshold()
                        + ": " + error.getMessage());
            }
        }
        return lines;
    }
}
