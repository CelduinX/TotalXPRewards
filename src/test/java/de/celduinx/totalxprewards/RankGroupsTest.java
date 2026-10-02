package de.celduinx.totalxprewards;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.context.ImmutableContextSet;
import org.mockito.MockedStatic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankGroupsTest {
    @Test void requiresDirectGlobalSpielerAndPreservesIndependentParentsAcrossRankChanges() throws Exception {
        UUID id = UUID.randomUUID();
        TotalXPRewardsPlugin plugin = mock(TotalXPRewardsPlugin.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        LuckPerms lp = mock(LuckPerms.class);
        UserManager users = mock(UserManager.class);
        User user = mock(User.class);
        NodeMap nodes = mock(NodeMap.class);
        Player player = mock(Player.class);
        PlayerDataManager manager = mock(PlayerDataManager.class);
        PlayerData data = new PlayerData(id, "Tester", 500);
        Set<Node> parents = new HashSet<>();
        parents.add(parent("team_rot"));
        parents.add(parent("xp_eins"));
        when(player.getUniqueId()).thenReturn(id);
        when(lp.getUserManager()).thenReturn(users);
        when(users.getUser(id)).thenReturn(user);
        when(users.saveUser(user)).thenReturn(CompletableFuture.completedFuture(null));
        when(user.data()).thenReturn(nodes);
        when(nodes.toCollection()).thenAnswer(i -> Set.copyOf(parents));
        when(nodes.add(any(Node.class))).thenAnswer(i -> { parents.add(i.getArgument(0)); return null; });
        when(nodes.remove(any(Node.class))).thenAnswer(i -> { parents.remove(i.getArgument(0)); return null; });
        when(plugin.getPlayerDataManager()).thenReturn(manager);
        when(manager.getData(player)).thenReturn(data);
        TreeMap<Long, Reward> rewards = new TreeMap<>();
        rewards.put(100L, new Reward(100, java.util.List.of(), "", "Eins", "xp_eins"));
        rewards.put(300L, new Reward(300, java.util.List.of(), "", "Zwei", "xp_zwei"));
        rewards.put(600L, new Reward(600, java.util.List.of(), "", "Drei", "xp_drei"));
        when(plugin.getRewards()).thenReturn(rewards);
        RankGroups ranks = new RankGroups(plugin, lp);
        Field valid = RankGroups.class.getDeclaredField("valid"); valid.setAccessible(true); valid.set(ranks, true);
        Field names = RankGroups.class.getDeclaredField("rankNames"); names.setAccessible(true);
        ((Set<String>) names.get(ranks)).addAll(Set.of("xp_eins", "xp_zwei", "xp_drei"));

        assertFalse(ranks.isUnlocked(id));
        ranks.sync(player); // An old rank must not unlock a guest.
        assertTrue(has(parents, "xp_eins"));
        verify(users, never()).saveUser(user);

        InheritanceNode contextual = parent("spieler");
        when(contextual.getContexts().isEmpty()).thenReturn(false);
        parents.add(contextual);
        assertFalse(ranks.isUnlocked(id));
        parents.remove(contextual);

        parents.add(parent("spieler")); // SelfUnlock's LP mutation
        assertTrue(ranks.isUnlocked(id));
        try (MockedStatic<InheritanceNode> builders = mockStatic(InheritanceNode.class)) {
            for (String name : Set.of("xp_zwei", "xp_drei")) {
                InheritanceNode.Builder builder = mock(InheritanceNode.Builder.class);
                when(builder.build()).thenAnswer(i -> parent(name));
                builders.when(() -> InheritanceNode.builder(name)).thenReturn(builder);
            }
        ranks.sync(player);
        assertFalse(has(parents, "xp_eins"));
        assertTrue(has(parents, "xp_zwei"));
        assertTrue(has(parents, "spieler"));
        assertTrue(has(parents, "team_rot"));
        ranks.sync(player); // login or repeated XP event
        verify(users, times(1)).saveUser(user);

        data.setTotalXp(700); // crossed another threshold
        ranks.sync(player);
        assertFalse(has(parents, "xp_zwei"));
        assertTrue(has(parents, "xp_drei"));
        assertTrue(has(parents, "spieler"));
        assertTrue(has(parents, "team_rot"));
        verify(users, times(2)).saveUser(user);
        }
    }

    private static InheritanceNode parent(String name) {
        InheritanceNode node = mock(InheritanceNode.class);
        ImmutableContextSet contexts = mock(ImmutableContextSet.class);
        when(contexts.isEmpty()).thenReturn(true);
        when(node.getContexts()).thenReturn(contexts);
        when(node.getValue()).thenReturn(true);
        when(node.getGroupName()).thenReturn(name);
        return node;
    }

    private static boolean has(Set<Node> nodes, String name) {
        return nodes.stream().anyMatch(n -> n instanceof InheritanceNode p && p.getGroupName().equals(name));
    }
}
