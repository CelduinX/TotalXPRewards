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
 * Handles the /totalxp command, providing subcommands to view, set, reset and
 * reload XP statistics. Permissions are checked based on totalxp.view and
 * totalxp.admin.
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
            case "show":
                handleShow(sender);
                break;
            case "hide":
                handleHide(sender);
                break;
            default:
                sendHelp(sender, label);
        }

        return true;
    }

    /**
     * Sends the help message lines to a command sender.
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

    private List<OfflinePlayer> resolveTargets(CommandSender sender, String arg) {
        List<OfflinePlayer> targets = new ArrayList<>();
        try {
            List<org.bukkit.entity.Entity> entities = Bukkit.selectEntities(sender, arg);
            for (org.bukkit.entity.Entity entity : entities) {
                if (entity instanceof Player) {
                    targets.add((Player) entity);
                }
            }
        } catch (IllegalArgumentException | NoSuchMethodError ignored) {
        }

        if (targets.isEmpty()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(arg);
            if (target.hasPlayedBefore() || (target.getName() != null) || target.isOnline()) {
                targets.add(target);
            }
        }
        return targets;
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.view")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }
        if (args.length < 2) {
            sendHelp(sender, "totalxp");
            return;
        }
        List<OfflinePlayer> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        for (OfflinePlayer target : targets) {
            UUID uuid = target.getUniqueId();
            String name = target.getName() != null ? target.getName() : args[1];

            long xp;
            PlayerData data = plugin.getPlayerDataManager().getData(uuid);
            if (data != null) {
                // Online/Cached
                xp = data.getTotalXp();
            } else {
                // Offline fallback
                xp = plugin.getDatabase().getXp(uuid);
            }

            String msg = Lang.get("xp-view")
                    .replace("%player%", name)
                    .replace("%xp%", String.valueOf(xp));
            sender.sendMessage(msg);
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }
        if (args.length < 3) {
            sendHelp(sender, "totalxp");
            return;
        }
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
        List<OfflinePlayer> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        for (OfflinePlayer target : targets) {
            UUID uuid = target.getUniqueId();
            String name = target.getName() != null ? target.getName() : "?";

            PlayerData data = plugin.getPlayerDataManager().getData(uuid);
            if (data != null) {
                // Online/Cached
                data.setTotalXp(amount);
                data.setCurrentRankName(plugin.getRankName(amount));

                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getDatabase().setPlayerData(uuid, amount, name, data.getCurrentRankName()));
            } else {
                // Offline
                String rankName = plugin.getRankName(amount);
                plugin.getDatabase().setPlayerData(uuid, amount, name, rankName);
            }

            String msg = Lang.get("xp-set")
                    .replace("%player%", name)
                    .replace("%amount%", String.valueOf(amount));
            sender.sendMessage(msg);

            // If online, update bossbar
            if (target.isOnline()) {
                plugin.getBossBarManager().update((Player) target, amount);
            }
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }
        if (args.length < 2) {
            sendHelp(sender, "totalxp");
            return;
        }
        List<OfflinePlayer> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage(Lang.get("player-not-found"));
            return;
        }
        for (OfflinePlayer target : targets) {
            UUID uuid = target.getUniqueId();
            String name = target.getName() != null ? target.getName() : "?";

            plugin.getDatabase().resetPlayer(uuid);

            PlayerData data = plugin.getPlayerDataManager().getData(uuid);
            if (data != null) {
                data.setTotalXp(0);
            }

            String msg = Lang.get("xp-reset").replace("%player%", name);
            sender.sendMessage(msg);
            if (target.isOnline()) {
                plugin.getBossBarManager().update((Player) target, 0);
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }
        plugin.reloadSettings();
        sender.sendMessage(Lang.get("prefix") + "Configuration reloaded.");
    }

    private void handleShow(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.get("prefix") + "Only players can use this command.");
            return;
        }
        Player player = (Player) sender;
        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().showBar(player);
            player.sendMessage(Lang.get("prefix") + "BossBar shown.");
        }
    }

    private void handleHide(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.get("prefix") + "Only players can use this command.");
            return;
        }
        Player player = (Player) sender;
        if (plugin.getBossBarManager() != null) {
            plugin.getBossBarManager().hideBar(player);
            player.sendMessage(Lang.get("prefix") + "BossBar hidden.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("get".startsWith(prefix))
                result.add("get");
            if ("set".startsWith(prefix) && sender.hasPermission("totalxp.admin"))
                result.add("set");
            if ("reset".startsWith(prefix) && sender.hasPermission("totalxp.admin"))
                result.add("reset");
            if ("reload".startsWith(prefix) && sender.hasPermission("totalxp.admin"))
                result.add("reload");
            if ("show".startsWith(prefix))
                result.add("show");
            if ("hide".startsWith(prefix))
                result.add("hide");
            return result;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("get")
                || args[0].equalsIgnoreCase("set")
                || args[0].equalsIgnoreCase("reset"))) {

            String namePrefix = args[1].toLowerCase();
            if ("@a".startsWith(namePrefix))
                result.add("@a");
            if ("@p".startsWith(namePrefix))
                result.add("@p");
            if ("@r".startsWith(namePrefix))
                result.add("@r");
            if ("@s".startsWith(namePrefix))
                result.add("@s");
            if ("@e".startsWith(namePrefix))
                result.add("@e");

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(namePrefix)) {
                    result.add(p.getName());
                }
            }
        }

        return result;
    }
}