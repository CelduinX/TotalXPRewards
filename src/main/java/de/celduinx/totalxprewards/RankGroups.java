package de.celduinx.totalxprewards;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Direct, global LuckPerms parents are the sole unlock authority. */
public final class RankGroups {
    private final TotalXPRewardsPlugin plugin;
    private final LuckPerms luckPerms;
    private final Set<String> rankNames = new HashSet<>();
    private final Set<UUID> reportedLegacy = new HashSet<>();
    private EventSubscription<NodeAddEvent> unlockSubscription;
    private EventSubscription<UserDataRecalculateEvent> recalculationSubscription;
    private boolean valid;

    RankGroups(TotalXPRewardsPlugin plugin) {
        this(plugin, LuckPermsProvider.get());
    }

    RankGroups(TotalXPRewardsPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    static boolean hasDirectGlobalParent(User user, String group) {
        return user != null && user.data().toCollection().stream().anyMatch(node ->
                node instanceof InheritanceNode inheritance && node.getValue()
                        && node.getContexts().isEmpty() && inheritance.getGroupName().equalsIgnoreCase(group));
    }

    boolean isUnlocked(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null || !hasDirectGlobalParent(user, "spieler")) {
            if (user != null && reportedLegacy.add(uuid)
                    && user.data().toCollection().stream().anyMatch(n -> n instanceof InheritanceNode inheritance
                            && rankNames.contains(inheritance.getGroupName().toLowerCase(java.util.Locale.ROOT)))) {
                plugin.getLogger().warning("Legacy XP group without direct spieler: " + uuid
                        + " (manual migration required; no unlock inferred)");
            }
            return false;
        }
        return valid;
    }

    void validate(Map<Long, Reward> rewards) {
        rankNames.clear();
        valid = !rewards.isEmpty();
        for (Map.Entry<Long, Reward> entry : rewards.entrySet()) {
            Reward reward = entry.getValue();
            String group = reward.getGroup();
            if (group == null || !group.matches("[a-z0-9_]+") || group.equals("spieler")
                    || group.equals("admin") || group.equals("moderator")
                    || !rankNames.add(group) || luckPerms.getGroupManager().getGroup(group) == null) {
                plugin.getLogger().severe("Invalid or missing LuckPerms rank group at XP " + entry.getKey() + ": " + group);
                valid = false;
            }
        }
        if (!valid) {
            plugin.getLogger().severe("Rank progression disabled until every configured group exists and is mapped once.");
            return;
        }
        // These groups already exist. Never create groups from display titles.
        for (Reward reward : rewards.values()) {
            Group group = luckPerms.getGroupManager().getGroup(reward.getGroup());
            String display = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', reward.getName()));
            group.data().clear(node -> node instanceof PrefixNode || node instanceof DisplayNameNode
                    || node instanceof WeightNode);
            group.data().add(DisplayNameNode.builder(display).build());
            group.data().add(PrefixNode.builder("&e[" + display + "] ", 40).build());
            group.data().add(WeightNode.builder(40).build());
            luckPerms.getGroupManager().saveGroup(group).exceptionally(error -> {
                plugin.getLogger().severe("Could not save rank group " + group.getName() + ": " + error);
                return null;
            });
        }
    }

    void listen() {
        unlockSubscription = luckPerms.getEventBus().subscribe(plugin, NodeAddEvent.class, event -> {
            if (event.getDataType() != DataType.NORMAL || !(event.getTarget() instanceof User user)
                    || !(event.getNode() instanceof InheritanceNode node)
                    || !node.getValue() || !node.getContexts().isEmpty()
                    || !node.getGroupName().equalsIgnoreCase("spieler")) return;
            // LP's command mutates the User before its asynchronous save completes.
            // Reconcile after the mutation and again after the save has had time to finish.
            Runnable reconcile = () -> {
                Player player = Bukkit.getPlayer(user.getUniqueId());
                if (player != null) sync(player);
            };
            Bukkit.getScheduler().runTask(plugin, reconcile);
            Bukkit.getScheduler().runTaskLater(plugin, reconcile, 40L);
        });
        recalculationSubscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            // Covers a late LP load or external user update after join. sync is idempotent.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(event.getUser().getUniqueId());
                if (player != null) sync(player);
            });
        });
    }

    void sync(Player player) {
        if (!valid || !isUnlocked(player.getUniqueId())) return;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        PlayerData data = plugin.getPlayerDataManager().getData(player);
        if (user == null || data == null) return;
        String desired = null;
        for (Map.Entry<Long, Reward> entry : plugin.getRewards().entrySet()) {
            if (entry.getKey() > data.getTotalXp()) break;
            desired = entry.getValue().getGroup();
        }
        boolean changed = false;
        for (Node node : new ArrayList<>(user.data().toCollection())) {
            if (node instanceof InheritanceNode inheritance
                    && rankNames.contains(inheritance.getGroupName().toLowerCase(java.util.Locale.ROOT))
                    && (desired == null || !node.getContexts().isEmpty()
                    || !inheritance.getGroupName().equalsIgnoreCase(desired))) {
                user.data().remove(node);
                changed = true;
            }
        }
        if (desired != null && !hasDirectGlobalParent(user, desired)) {
            user.data().add(InheritanceNode.builder(desired).build());
            changed = true;
        }
        if (changed) luckPerms.getUserManager().saveUser(user).exceptionally(error -> {
            plugin.getLogger().severe("Could not save XP rank for " + player.getUniqueId() + ": " + error);
            return null;
        });
    }

    void close() {
        if (unlockSubscription != null) unlockSubscription.close();
        if (recalculationSubscription != null) recalculationSubscription.close();
    }
}
