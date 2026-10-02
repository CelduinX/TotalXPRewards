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
            case "status":
                handleStatus(sender, args);
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
        sender.sendMessage(Lang.get("progression-help").replace("%label%", label));
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

        if (targets.isEmpty() && !arg.startsWith("@")) {
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
            sender.sendMessage(msg + " (Rang-XP)");
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

                plugin.getDatabase().setPlayerData(uuid, amount, name, data.getCurrentRankName());
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
                plugin.getRankGroups().sync((Player) target);
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
                data.setCurrentRankName(plugin.getRankName(0));
                data.setProgression(new ProgressionState(plugin.getProgressionSettings().capacity()));
                plugin.getDatabase().saveData(data);
            }

            String msg = Lang.get("xp-reset").replace("%player%", name);
            sender.sendMessage(msg);
            if (target.isOnline()) {
                plugin.getBossBarManager().update((Player) target, 0);
                plugin.getRankGroups().sync((Player) target);
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("totalxp.admin")) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }
        try {
            plugin.reloadSettings();
            sender.sendMessage(Lang.get("prefix") + "Configuration reloaded.");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Configuration rejected; previous settings remain active: " + e.getMessage());
            sender.sendMessage(Lang.get("progression-config-error") + " " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        List<OfflinePlayer> targets;
        if (args.length == 1 && sender instanceof Player p) {
            targets = List.of(p);
        } else if (args.length >= 2) {
            if (!sender.hasPermission("totalxp.admin")) {
                // Explicitly naming oneself is allowed, selectors require admin permission.
                if (sender instanceof Player p && args[1].equalsIgnoreCase(p.getName())) targets = List.of(p);
                else { sender.sendMessage(Lang.get("no-permission")); return; }
            } else targets = resolveTargets(sender, args[1]);
        } else {
            sender.sendMessage(Lang.get("progression-help").replace("%label%", "totalxp"));
            return;
        }
        if (targets.isEmpty()) { sender.sendMessage(Lang.get("player-not-found")); return; }
        ProgressionSettings settings = plugin.getProgressionSettings();
        for (OfflinePlayer target : targets) {
            PlayerData data = plugin.getPlayerDataManager().getData(target.getUniqueId());
            if (target.getPlayer() != null) plugin.getProgressionService().settlePlayer(target.getPlayer());
            ProgressionState state = data == null ? plugin.getDatabase().getProgression(target.getUniqueId(), settings.capacity())
                    : data.getProgression();
            long xp = data == null ? plugin.getDatabase().getXp(target.getUniqueId()) : data.getTotalXp();
            sender.sendMessage(Lang.get("progression-status")
                    .replace("%player%", target.getName() == null ? args[1] : target.getName())
                    .replace("%xp%", Long.toString(xp))
                    .replace("%budget%", String.format(java.util.Locale.ROOT, "%.1f", state.budget()))
                    .replace("%capacity%", String.format(java.util.Locale.ROOT, "%.0f", settings.capacity()))
                    .replace("%active%", Lang.get(state.active(System.nanoTime()) && target.isOnline()
                            ? "progression-active" : "progression-inactive"))
                    .replace("%hours%", String.format(java.util.Locale.ROOT, "%.2f", state.activeSeconds() / 3600))
                    .replace("%enabled%", Lang.get(settings.enabled() ? "progression-enabled" : "progression-disabled"))
                    .replace("%reason%", ProgressionService.reasonText(state.reason())));
        }
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
            if ("status".startsWith(prefix)) result.add("status");
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
                || args[0].equalsIgnoreCase("reset")
                || (args[0].equalsIgnoreCase("status") && sender.hasPermission("totalxp.admin")))) {

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
