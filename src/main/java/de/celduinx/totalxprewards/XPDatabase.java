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
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_xp (" +
                    "uuid TEXT PRIMARY KEY," +
                    "xp INTEGER NOT NULL" +
                    ")"
                );
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_rewards (" +
                    "uuid TEXT NOT NULL," +
                    "threshold INTEGER NOT NULL," +
                    "PRIMARY KEY (uuid, threshold)" +
                    ")"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not initialise SQLite database: " + e.getMessage());
        }
    }

    /**
     * Retrieves the stored total XP for a player.
     *
     * @param uuid the player's UUID
     * @return the total XP, or 0 if absent or on error
     */
    public long getXp(UUID uuid) {
        if (connection == null) return 0L;
        String sql = "SELECT xp FROM player_xp WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("xp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reading XP from database: " + e.getMessage());
        }
        return 0L;
    }

    /**
     * Saves the total XP for a player. If the record exists, it is updated.
     *
     * @param uuid the player's UUID
     * @param xp   the total XP to store
     */
    public void setXp(UUID uuid, long xp) {
        if (connection == null) return;
        String sql = "INSERT INTO player_xp (uuid, xp) VALUES (?, ?) " +
                     "ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, xp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving XP to database: " + e.getMessage());
        }
    }

    /**
     * Checks whether a reward at a given threshold has already been issued to a player.
     *
     * @param uuid      the player's UUID
     * @param threshold the reward threshold
     * @return {@code true} if already issued
     */
    public boolean hasReward(UUID uuid, long threshold) {
        if (connection == null) return false;
        String sql = "SELECT 1 FROM player_rewards WHERE uuid = ? AND threshold = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking reward in database: " + e.getMessage());
        }
        return false;
    }

    /**
     * Records that a reward has been given to a player at a particular threshold.
     *
     * @param uuid      the player's UUID
     * @param threshold the reward threshold
     */
    public void setRewardGiven(UUID uuid, long threshold) {
        if (connection == null) return;
        String sql = "INSERT OR IGNORE INTO player_rewards (uuid, threshold) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, threshold);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving reward to database: " + e.getMessage());
        }
    }

    /**
     * Deletes all XP and reward records for a player.
     *
     * @param uuid the player's UUID
     */
    public void resetPlayer(UUID uuid) {
        if (connection == null) return;
        try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM player_xp WHERE uuid = ?");
             PreparedStatement ps2 = connection.prepareStatement("DELETE FROM player_rewards WHERE uuid = ?")) {
            ps1.setString(1, uuid.toString());
            ps1.executeUpdate();

            ps2.setString(1, uuid.toString());
            ps2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error resetting player in database: " + e.getMessage());
        }
    }

    /**
     * Closes the SQLite connection when the plugin is disabled.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
    }
}