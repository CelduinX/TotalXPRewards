package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the /totalxp command, providing subcommands to view, set, reset and reload
 * XP statistics. Permissions are checked based on totalxp.view and totalxp.admin.
 */
public class CommandTotalXP implements CommandExecutor, TabCompleter {

    private final TotalXPRewardsPlugin plugin;

    public CommandTotalXP(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "get":
                handleGet(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender, label);
        }

        return true;
    }

    /**
     * Sends the help message lines to a command sender. The %label% placeholder
     * is replaced with the actual command label used.
     *
     * @param sender the command sender
     * @param label  the command label
     */
    private void sendHelp(CommandSender sender, String label) {
        List<String> lines = Lang.getList("help");
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            sender.sendMessage(line.replace("%label%", label));
        }
    }

    /**
     * Handles the /totalxp get subcommand to display a player's total XP.
     */
    private void handleGet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.view")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sendHelp(sender, "totalxp");
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        UUID uuid = target.getUniqueId();

        long xp = plugin.getDatabase().getXp(uuid);
        String msg = Lang.get("xp-view")
                .replace("%player%", targetName)
                .replace("%xp%", String.valueOf(xp));
        sender.sendMessage(msg);
    }

    /**
     * Handles the /totalxp set subcommand to assign a player's total XP.
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        if (args.length < 3) {
            sendHelp(sender, "totalxp");
            return;
        }

        String targetName = args[1];
        String amountStr = args[2];

        long amount;
        try {
            amount = Long.parseLong(amountStr);
            if (amount < 0) {
                sender.sendMessage(Lang.get("negative-amount"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Lang.get("invalid-number"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        UUID uuid = target.getUniqueId();

        plugin.getDatabase().setXp(uuid, amount);
        String msg = Lang.get("xp-set")
                .replace("%player%", targetName)
                .replace("%amount%", String.valueOf(amount));
        sender.sendMessage(msg);
    }

    /**
     * Handles the /totalxp reset subcommand to clear a player's total XP.
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        if (args.length < 2) {
            sendHelp(sender, "totalxp");
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        UUID uuid = target.getUniqueId();

        plugin.getDatabase().resetPlayer(uuid);
        String msg = Lang.get("xp-reset")
                .replace("%player%", targetName);
        sender.sendMessage(msg);
    }

    /**
     * Handles the /totalxp reload subcommand to reload config and language files.
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        plugin.reloadSettings();
        sender.sendMessage(Lang.get("reload-done"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("get".startsWith(prefix)) result.add("get");
            if ("set".startsWith(prefix) && sender.hasPermission("totalxp.admin")) result.add("set");
            if ("reset".startsWith(prefix) && sender.hasPermission("totalxp.admin")) result.add("reset");
            if ("reload".startsWith(prefix) && sender.hasPermission("totalxp.admin")) result.add("reload");
            return result;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("get")
                || args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("reset"))) {

            String namePrefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(namePrefix)) {
                    result.add(p.getName());
                }
            }
        }

        return result;
    }
}