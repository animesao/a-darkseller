package com.buyerplugin;

import org.bukkit.Material;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.logging.Level;

public class DatabaseManager {
    private final Main plugin;
    private Connection connection;
    private final String type;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        setupDatabase();
    }

    private void setupDatabase() {
        try {
            if (type.equals("mysql")) {
                String host = plugin.getConfig().getString("database.mysql.host");
                int port = plugin.getConfig().getInt("database.mysql.port");
                String database = plugin.getConfig().getString("database.mysql.database");
                String username = plugin.getConfig().getString("database.mysql.username");
                String password = plugin.getConfig().getString("database.mysql.password");
                boolean useSSL = plugin.getConfig().getBoolean("database.mysql.use-ssl");

                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL;
                connection = DriverManager.getConnection(url, username, password);
            } else {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/database.db");
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS player_points (uuid VARCHAR(36) PRIMARY KEY, points DOUBLE DEFAULT 0)");
                statement.execute("CREATE TABLE IF NOT EXISTS player_multipliers (uuid VARCHAR(36) PRIMARY KEY, level INT DEFAULT 1, sold INT DEFAULT 0)");
                statement.execute("CREATE TABLE IF NOT EXISTS player_active_boosters (uuid VARCHAR(36) PRIMARY KEY, multiplier DOUBLE, expire_time LONG)");
                statement.execute("CREATE TABLE IF NOT EXISTS global_item_stats (material VARCHAR(64) PRIMARY KEY, amount INT DEFAULT 0)");
                statement.execute("CREATE TABLE IF NOT EXISTS current_rotation (material VARCHAR(64) PRIMARY KEY, price DOUBLE)");
                statement.execute("CREATE TABLE IF NOT EXISTS wipe_stats (id INT PRIMARY KEY, wipe_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                statement.execute("CREATE TABLE IF NOT EXISTS player_autosell_whitelist (uuid VARCHAR(36), material VARCHAR(64), PRIMARY KEY (uuid, material))");
                statement.execute("CREATE TABLE IF NOT EXISTS player_autosell_status (uuid VARCHAR(36) PRIMARY KEY, enabled BOOLEAN DEFAULT FALSE)");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to database!", e);
        }
    }

    public void saveAutoSellWhitelist(UUID uuid, Set<Material> whitelist) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_autosell_whitelist WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (whitelist.isEmpty()) return;

        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO player_autosell_whitelist (uuid, material) VALUES (?, ?)")) {
            for (Material mat : whitelist) {
                ps.setString(1, uuid.toString());
                ps.setString(2, mat.name());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadAutoSellWhitelists(Map<UUID, Set<Material>> whitelists) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM player_autosell_whitelist")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Material mat = Material.valueOf(rs.getString("material"));
                whitelists.computeIfAbsent(uuid, k -> new java.util.HashSet<>()).add(mat);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveAutoSellStatus(UUID uuid, boolean enabled) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO player_autosell_status (uuid, enabled) VALUES (?, ?) ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        } else {
            query = "REPLACE INTO player_autosell_status (uuid, enabled) VALUES (?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadAutoSellStatuses(Map<UUID, Boolean> statuses) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM player_autosell_status")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                statuses.put(uuid, rs.getBoolean("enabled"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getWipeDate() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT wipe_date FROM wipe_stats WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("wipe_date").getTime();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return System.currentTimeMillis();
    }

    public void setWipeDate(long timestamp) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO wipe_stats (id, wipe_date) VALUES (1, ?) ON DUPLICATE KEY UPDATE wipe_date = VALUES(wipe_date)";
        } else {
            query = "REPLACE INTO wipe_stats (id, wipe_date) VALUES (1, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setTimestamp(1, new Timestamp(timestamp));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveMultiplier(UUID uuid, int level, int sold) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO player_multipliers (uuid, level, sold) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE level = VALUES(level), sold = VALUES(sold)";
        } else {
            query = "REPLACE INTO player_multipliers (uuid, level, sold) VALUES (?, ?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, level);
            ps.setInt(3, sold);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadMultipliers(Map<UUID, Integer> levels, Map<UUID, Integer> sold) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM player_multipliers")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                levels.put(uuid, rs.getInt("level"));
                sold.put(uuid, rs.getInt("sold"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearMultipliers() {
        try (Statement s = connection.createStatement()) {
            s.execute("DELETE FROM player_multipliers");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getPoints(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT points FROM player_points WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("points");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public void setPoints(UUID uuid, double points) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO player_points (uuid, points) VALUES (?, ?) ON DUPLICATE KEY UPDATE points = VALUES(points)";
        } else {
            query = "REPLACE INTO player_points (uuid, points) VALUES (?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, points);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveActiveBooster(UUID uuid, double multiplier, long expireTime) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO player_active_boosters (uuid, multiplier, expire_time) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE multiplier = VALUES(multiplier), expire_time = VALUES(expire_time)";
        } else {
            query = "REPLACE INTO player_active_boosters (uuid, multiplier, expire_time) VALUES (?, ?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, multiplier);
            ps.setLong(3, expireTime);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadActiveBoosters(Map<UUID, Double> multipliers, Map<UUID, Long> expireTimes) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM player_active_boosters")) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                multipliers.put(uuid, rs.getDouble("multiplier"));
                expireTimes.put(uuid, rs.getLong("expire_time"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeActiveBooster(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_active_boosters WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearAllBoosters() {
        try (Statement s = connection.createStatement()) {
            s.execute("DELETE FROM player_active_boosters");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveItemStats(Map<Material, Integer> stats) {
        String query;
        if (type.equals("mysql")) {
            query = "INSERT INTO global_item_stats (material, amount) VALUES (?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        } else {
            query = "REPLACE INTO global_item_stats (material, amount) VALUES (?, ?)";
        }
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            for (Map.Entry<Material, Integer> entry : stats.entrySet()) {
                ps.setString(1, entry.getKey().name());
                ps.setInt(2, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadItemStats(Map<Material, Integer> stats) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM global_item_stats")) {
            while (rs.next()) {
                try {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat != null) {
                        stats.put(mat, rs.getInt("amount"));
                    }
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveRotation(Map<Material, Double> prices) {
        try (Statement s = connection.createStatement()) {
            s.execute("DELETE FROM current_rotation");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO current_rotation (material, price) VALUES (?, ?)")) {
            for (Map.Entry<Material, Double> entry : prices.entrySet()) {
                ps.setString(1, entry.getKey().name());
                ps.setDouble(2, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadRotation(Map<Material, Double> prices) {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM current_rotation")) {
            while (rs.next()) {
                try {
                    Material mat = Material.matchMaterial(rs.getString("material"));
                    if (mat != null) {
                        prices.put(mat, rs.getDouble("price"));
                    }
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}