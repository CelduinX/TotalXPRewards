package de.celduinx.totalxprewards;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;

/**
 * Simple SQLite wrapper for storing player XP totals and issued rewards.
 */
public class XPDatabase {
    private final Plugin plugin;
    private final Object lock = new Object();
    private Connection connection;

    /**
     * Creates a new database instance and initialises tables.
     *
     * @param plugin the owning plugin
     */
    public XPDatabase(Plugin plugin) {
        this.plugin = plugin;
        init();
    }

    /**
     * Establishes the SQLite connection and creates tables if they do not already
     * exist. The database file is stored in the plugin's data folder with the
     * name {@code totalxp.db}.
     */
    private void init() {
        try {
            File dbFolder = plugin.getDataFolder();
            if (!dbFolder.exists() && !dbFolder.mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder");
            }

            File dbFile = new File(dbFolder, "totalxp.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to SQLite database.");

            try (Statement st = connection.createStatement()) {
                // Main XP table
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_xp (" +
                                "uuid TEXT PRIMARY KEY," +
                                "xp INTEGER NOT NULL" +
                                ")");

                // Add new columns if they don't exist (SQLite doesn't support IF NOT EXISTS for
                // ADD COLUMN in older versions easily,
                // but checking schema is safer or just catching ignore)
                // SQLite 3.35+ supports ALTER TABLE ADD COLUMN IF NOT EXISTS?
                // Let's rely on standard try-catch or explicit check.

                migrateTable(st);
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_progression ("
                        + "uuid TEXT PRIMARY KEY, budget REAL NOT NULL, fraction REAL NOT NULL, "
                        + "active_seconds REAL NOT NULL, reason TEXT NOT NULL, kills TEXT NOT NULL)");

                // Rewards table
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_rewards (" +
                                "uuid TEXT NOT NULL," +
                                "threshold INTEGER NOT NULL," +
                                "PRIMARY KEY (uuid, threshold)" +
                                ")");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialise SQLite database", e);
        }
    }

    private void migrateTable(Statement st) {
        // Migration: Add username and current_rank
        // We use a safe approach by try-catching each alter
        try {
            st.executeUpdate("ALTER TABLE player_xp ADD COLUMN username TEXT");
            plugin.getLogger().info("Database: Added 'username' column.");
        } catch (SQLException ignored) {
            // Likely already exists
        }

        try {
            st.executeUpdate("ALTER TABLE player_xp ADD COLUMN current_rank TEXT");
            plugin.getLogger().info("Database: Added 'current_rank' column.");
        } catch (SQLException ignored) {
            // Likely already exists
        }
    }

    /**
     * Retrieves the stored total XP for a player.
     *
     * @param uuid the player's UUID
     * @return the total XP, or 0 if absent or on error
     */
    public long getXp(UUID uuid) {
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            String sql = "SELECT xp FROM player_xp WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("xp");
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot read XP for " + uuid, e);
            }
            return 0L;
        }
    }

    /**
     * Saves the total XP for a player. If the record exists, it is updated.
     *
     * @param uuid the player's UUID
     * @param xp   the total XP to store
     */
    /**
     * Saves the player data including XP, username, and rank.
     */
    public void setPlayerData(UUID uuid, long xp, String username, String rank) {
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            // Upsert with new fields
            String sql = "INSERT INTO player_xp (uuid, xp, username, current_rank) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "xp = excluded.xp, " +
                    "username = excluded.username, " +
                    "current_rank = excluded.current_rank";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, xp);
                ps.setString(3, username);
                ps.setString(4, rank);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Error saving player data to database", e);
            }
        }
    }

    // Deprecated or simplified setter used by legacy calls?
    // We should redirect setXp to setPlayerData but we need name/rank.
    // Ideally we update all callers. For now, let's keep setXp as a partial update?
    // No, we want to enforce new data.
    // BUT legacy setXp(uuid, xp) doesn't have name/rank.
    // We can just update XP if name/rank are not provided?
    public void setXp(UUID uuid, long xp) {
        // Fallback: Just update XP, leave others as is.
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            String sql = "INSERT INTO player_xp (uuid, xp) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, xp);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot save XP for " + uuid, e);
            }
        }
    }

    /**
     * Checks whether a reward at a given threshold has already been issued to a
     * player.
     *
     * @param uuid      the player's UUID
     * @param threshold the reward threshold
     * @return {@code true} if already issued
     */
    public boolean hasReward(UUID uuid, long threshold) {
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            String sql = "SELECT 1 FROM player_rewards WHERE uuid = ? AND threshold = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, threshold);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot check reward history for " + uuid + " at " + threshold, e);
            }
        }
    }

    /**
     * Records that a reward has been given to a player at a particular threshold.
     *
     * @param uuid      the player's UUID
     * @param threshold the reward threshold
     */
    public void setRewardGiven(UUID uuid, long threshold) {
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            String sql = "INSERT OR IGNORE INTO player_rewards (uuid, threshold) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, threshold);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot save reward history for " + uuid + " at " + threshold, e);
            }
        }
    }

    public java.util.Set<Long> getRewardHistory(UUID uuid) {
        synchronized (lock) {
            if (connection == null) throw new IllegalStateException("XP database is unavailable");
            java.util.Set<Long> history = new java.util.HashSet<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT threshold FROM player_rewards WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) history.add(rs.getLong(1));
                }
                return history;
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot load reward history for " + uuid, e);
            }
        }
    }

    public boolean isHealthy() {
        synchronized (lock) {
            if (connection == null) return false;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                return result.next();
            } catch (SQLException e) {
                return false;
            }
        }
    }

    /**
     * Deletes all XP and reward records for a player.
     *
     * @param uuid the player's UUID
     */
    public void resetPlayer(UUID uuid) {
        synchronized (lock) {
            if (connection == null)
                throw new IllegalStateException("XP database is unavailable");
            try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM player_xp WHERE uuid = ?");
                    PreparedStatement ps2 = connection.prepareStatement("DELETE FROM player_rewards WHERE uuid = ?");
                    PreparedStatement ps3 = connection.prepareStatement("DELETE FROM player_progression WHERE uuid = ?")) {
                ps1.setString(1, uuid.toString());
                ps1.executeUpdate();

                ps2.setString(1, uuid.toString());
                ps2.executeUpdate();
                ps3.setString(1, uuid.toString());
                ps3.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot reset XP for " + uuid, e);
            }
        }
    }

    public ProgressionState getProgression(UUID uuid, double capacity) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_progression WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new ProgressionState(capacity);
                    double budget = rs.getDouble("budget"), fraction = rs.getDouble("fraction");
                    double seconds = rs.getDouble("active_seconds");
                    if (!Double.isFinite(budget) || budget < 0 || !Double.isFinite(fraction)
                            || fraction < 0 || fraction >= 1 || !Double.isFinite(seconds) || seconds < 0)
                        throw new IllegalStateException("Invalid progression data for " + uuid);
                    ProgressionState state = new ProgressionState(Math.min(capacity, budget));
                    state.restore(fraction, seconds, rs.getString("reason"),
                            ProgressionState.decodeKills(rs.getString("kills")));
                    return state;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot load progression for " + uuid, e);
            }
        }
    }

    public void saveProgression(UUID uuid, ProgressionState state) {
        if (state == null) return;
        synchronized (lock) {
            String sql = "INSERT INTO player_progression VALUES (?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET "
                    + "budget=excluded.budget, fraction=excluded.fraction, active_seconds=excluded.active_seconds, "
                    + "reason=excluded.reason, kills=excluded.kills";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, state.budget());
                ps.setDouble(3, state.fraction());
                ps.setDouble(4, state.activeSeconds());
                ps.setString(5, state.reason());
                ps.setString(6, state.encodeKills());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot save progression for " + uuid, e);
            }
        }
    }

    public void saveData(PlayerData data) {
        synchronized (lock) {
            try {
                connection.setAutoCommit(false);
                setPlayerData(data.getUuid(), data.getTotalXp(), data.getName(), data.getCurrentRankName());
                saveProgression(data.getUuid(), data.getProgression());
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw new IllegalStateException("Cannot save player data", e);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException e) {
                    throw new IllegalStateException("Cannot restore database transaction mode", e);
                }
            }
        }
    }

    /**
     * Closes the SQLite connection when the plugin is disabled.
     */
    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                    connection = null;
                    plugin.getLogger().info("SQLite database connection closed.");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
                }
            }
        }
    }
}
