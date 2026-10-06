package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Delivers configured vanilla give rewards and drops inventory overflow. */
final class ItemRewardDelivery {
    private static final Pattern GIVE = Pattern.compile(
            "^/?(?:minecraft:)?give\\s+%player%\\s+(.+?)(?:\\s+(\\d+))?$", Pattern.CASE_INSENSITIVE);

    private ItemRewardDelivery() {}

    static boolean deliver(Player player, String command) {
        Matcher match = GIVE.matcher(command.strip());
        if (!match.matches()) return false;
        String itemArgument = match.group(1);
        int amount = match.group(2) == null ? 1 : Integer.parseInt(match.group(2));
        if (amount < 1 || amount > 2304)
            throw new IllegalArgumentException("Give reward amount must be between 1 and 2304: " + command);
        ItemStack template = Bukkit.getItemFactory().createItemStack(itemArgument);
        int stackSize = Math.max(1, template.getMaxStackSize());
        while (amount > 0) {
            int count = Math.min(amount, stackSize);
            ItemStack stack = template.clone();
            stack.setAmount(count);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
            amount -= count;
        }
        return true;
    }
}
