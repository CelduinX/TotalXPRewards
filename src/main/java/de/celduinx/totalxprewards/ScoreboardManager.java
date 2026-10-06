package de.celduinx.totalxprewards;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Personal right-side scoreboard, added to the player's existing scoreboard. */
final class ScoreboardManager {
    private static final String OBJECTIVE = "totalxp_sidebar";
    private static final String TEAM_PREFIX = "txp_line_";
    private static final int MAX_LINES = 15;
    private final TotalXPRewardsPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> warnedSharedBoards = new java.util.HashSet<>();
    private boolean enabled;
    private boolean dynamic;
    private int timeout;
    private String title;
    private List<String> lines;
    private List<String> maxLines;

    private record Session(Scoreboard board, boolean owned, BukkitTask hideTask) {}

    ScoreboardManager(TotalXPRewardsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    static void validateConfig(FileConfiguration config) {
        if (config.getInt("scoreboard.timeout", 10) < 0)
            throw new IllegalArgumentException("scoreboard.timeout must be nonnegative");
        for (String key : List.of("scoreboard.lines", "scoreboard.max-lines")) {
            if (config.contains(key) && !config.isList(key))
                throw new IllegalArgumentException(key + " must be a list");
            if (config.getStringList(key).size() > MAX_LINES)
                throw new IllegalArgumentException(key + " must have at most 15 lines");
        }
    }

    void reload() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("scoreboard.enabled", false);
        dynamic = config.getBoolean("scoreboard.dynamic-mode", false);
        timeout = config.getInt("scoreboard.timeout", 10);
        title = config.getString("scoreboard.title", "&aTotal XP");
        lines = config.getStringList("scoreboard.lines");
        maxLines = config.getStringList("scoreboard.max-lines");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (enabled) {
                PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
                if (data != null) update(player, data.getTotalXp());
            } else {
                remove(player);
            }
        }
    }

    void update(Player player, long xp) {
        if (!enabled || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        Session old = sessions.get(id);
        Scoreboard board = player.getScoreboard();
        if (old != null && old.board() != board) {
            clear(old);
            sessions.remove(id);
            old = null;
        }
        Scoreboard viewedBoard = board;
        if (board != Bukkit.getScoreboardManager().getMainScoreboard()
                && Bukkit.getOnlinePlayers().stream().anyMatch(other -> other != player && other.getScoreboard() == viewedBoard)) {
            if (old != null) remove(player);
            if (warnedSharedBoards.add(id)) plugin.getLogger().warning(
                    "Cannot show a private sidebar on a scoreboard shared by multiple players: " + player.getName());
            return;
        }
        boolean owned = false;
        if (old == null && board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
            owned = true;
        } else if (old != null) {
            owned = old.owned();
        }

        Objective other = board.getObjective(DisplaySlot.SIDEBAR);
        if (other != null && !OBJECTIVE.equals(other.getName())) return;
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
            objective.numberFormat(NumberFormat.blank());
        }
        if (old != null && old.hideTask() != null) old.hideTask().cancel();

        RewardPreview.Next next = RewardPreview.next(plugin.getRewards(), xp);
        List<Component> rendered = render(player, xp, next);
        objective.displayName(plugin.formatToComponent(player, title, xp, 0));
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = ChatColor.BLACK.toString() + ChatColor.values()[i];
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (i < rendered.size()) {
                if (team == null) {
                    team = board.registerNewTeam(TEAM_PREFIX + i);
                    team.addEntry(entry);
                }
                team.prefix(rendered.get(i));
                objective.getScore(entry).setScore(MAX_LINES - i);
            } else {
                board.resetScores(entry);
                if (team != null) team.unregister();
            }
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        BukkitTask hideTask = dynamic ? Bukkit.getScheduler().runTaskLater(plugin, () -> hide(player), timeout * 20L) : null;
        sessions.put(id, new Session(board, owned, hideTask));
    }

    private List<Component> render(Player player, long xp, RewardPreview.Next next) {
        List<Component> output = new ArrayList<>();
        List<String> template = next == null ? maxLines : lines;
        for (String line : template) {
            if ("%reward_items%".equals(line)) {
                if (next != null) output.addAll(RewardPreview.lines(next.reward(), plugin, player, xp));
            } else {
                String text = line;
                if (next != null) {
                    text = text.replace("%next_reward_rank%", next.reward().getName())
                            .replace("%next_reward_number%", String.valueOf(next.number()))
                            .replace("%next_reward_xp%", String.valueOf(next.reward().getThreshold()))
                            .replace("%next_reward_remaining_xp%", String.valueOf(next.reward().getThreshold() - xp));
                }
                output.add(plugin.formatToComponent(player, text, xp, 0));
            }
            if (output.size() >= MAX_LINES) break;
        }
        if (output.size() > MAX_LINES) return output.subList(0, MAX_LINES);
        return output;
    }

    private void hide(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Objective objective = session.board().getObjective(OBJECTIVE);
        if (objective != null && objective.getDisplaySlot() == DisplaySlot.SIDEBAR)
            objective.setDisplaySlot(null);
        sessions.put(player.getUniqueId(), new Session(session.board(), session.owned(), null));
    }

    void remove(Player player) {
        warnedSharedBoards.remove(player.getUniqueId());
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        clear(session);
        if (session.owned() && player.isOnline() && player.getScoreboard() == session.board())
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void clear(Session session) {
        if (session.hideTask() != null) session.hideTask().cancel();
        Scoreboard board = session.board();
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) objective.unregister();
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = board.getTeam(TEAM_PREFIX + i);
            if (team != null) team.unregister();
        }
    }
}
