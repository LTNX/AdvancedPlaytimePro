package com.zib.playtime.database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zib.playtime.Playtime;
import com.zib.playtime.config.PlaytimeConfig;
import com.zib.playtime.config.Reward;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private final File dataFolder;
    private boolean isMySQL;

    private final Logger logger = LoggerFactory.getLogger("Playtime-DB");

    public boolean isMySQL() { return isMySQL; }

    public DatabaseManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void init() {
        PlaytimeConfig.DatabaseSettings settings = Playtime.get().getConfigManager().getConfig().database;
        HikariConfig config = new HikariConfig();

        if (settings.type.equalsIgnoreCase("mysql")) {
            config.setJdbcUrl("jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.databaseName + "?useSSL=" + settings.useSSL);
            config.setUsername(settings.username);
            config.setPassword(settings.password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            isMySQL = true;
            logger.info("Connecting to MySQL Database...");
        } else {
            if (!dataFolder.exists()) dataFolder.mkdirs();
            config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "playtime.db").getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            isMySQL = false;
            logger.info("Using local SQLite Database.");
        }

        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);
        createTable();
    }

    private void createTable() {
        String sessionsSql;
        String rewardsSql;
        String hiddenSql;

        if (isMySQL) {
            sessionsSql = "CREATE TABLE IF NOT EXISTS playtime_sessions (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "uuid VARCHAR(36)," +
                    "username VARCHAR(16)," +
                    "start_time BIGINT," +
                    "duration BIGINT," +
                    "session_date DATE)";

            rewardsSql = "CREATE TABLE IF NOT EXISTS playtime_rewards_log (" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "uuid VARCHAR(36)," +
                    "reward_id VARCHAR(64)," +
                    "claim_date DATETIME DEFAULT CURRENT_TIMESTAMP)";

            hiddenSql = "CREATE TABLE IF NOT EXISTS playtime_hidden (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(16))";
        } else {
            sessionsSql = "CREATE TABLE IF NOT EXISTS playtime_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36)," +
                    "username VARCHAR(16)," +
                    "start_time BIGINT," +
                    "duration BIGINT," +
                    "session_date DATE DEFAULT CURRENT_DATE)";

            rewardsSql = "CREATE TABLE IF NOT EXISTS playtime_rewards_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "uuid VARCHAR(36)," +
                    "reward_id VARCHAR(64)," +
                    "claim_date DATETIME DEFAULT CURRENT_TIMESTAMP)";

            hiddenSql = "CREATE TABLE IF NOT EXISTS playtime_hidden (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(16))";
        }

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sessionsSql);
            stmt.execute(rewardsSql);
            stmt.execute(hiddenSql);
            logger.info("Successfully created/verified database tables.");
        } catch (SQLException e) {
            logger.error("Failed to create table: " + e.getMessage(), e);
            throw new RuntimeException("Failed to create database table", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) dataSource.close();
    }

    public boolean hasClaimedReward(String uuid, Reward reward) {
        String timeClause = "";
        if (isMySQL) {
            if (reward.period.equalsIgnoreCase("daily")) timeClause = " AND DATE(claim_date) = CURDATE()";
            else if (reward.period.equalsIgnoreCase("weekly")) timeClause = " AND claim_date >= DATE_SUB(NOW(), INTERVAL 7 DAY)";
            else if (reward.period.equalsIgnoreCase("monthly")) timeClause = " AND claim_date >= DATE_SUB(NOW(), INTERVAL 1 MONTH)";
        } else {
            if (reward.period.equalsIgnoreCase("daily")) timeClause = " AND date(claim_date) = date('now')";
            else if (reward.period.equalsIgnoreCase("weekly")) timeClause = " AND date(claim_date) >= date('now', '-7 days')";
            else if (reward.period.equalsIgnoreCase("monthly")) timeClause = " AND date(claim_date) >= date('now', '-1 month')";
        }

        String query = "SELECT id FROM playtime_rewards_log WHERE uuid = ? AND reward_id = ?" + timeClause;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid);
            ps.setString(2, reward.id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking reward claim", e);
            return true;
        }
    }

    public void logRewardClaim(String uuid, String rewardId) {
        String sql = "INSERT INTO playtime_rewards_log (uuid, reward_id) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, rewardId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error logging reward claim", e);
        }
    }

    // Ocultar jugador del leaderboard por nombre de usuario
    public boolean hidePlayer(String username) {
        String findUuid = "SELECT uuid FROM playtime_sessions WHERE username = ? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(findUuid)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false; // jugador no encontrado
                String uuid = rs.getString("uuid");
                String insertSql = "INSERT OR REPLACE INTO playtime_hidden (uuid, username) VALUES (?, ?)";
                if (isMySQL) insertSql = "INSERT IGNORE INTO playtime_hidden (uuid, username) VALUES (?, ?)";
                try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
                    ps2.setString(1, uuid);
                    ps2.setString(2, username);
                    ps2.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error hiding player", e);
            return false;
        }
    }

    // Mostrar jugador de nuevo en el leaderboard
    public boolean showPlayer(String username) {
        String sql = "DELETE FROM playtime_hidden WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error showing player", e);
            return false;
        }
    }

    // Verificar si un UUID está oculto
    public boolean isHidden(String uuid) {
        String sql = "SELECT uuid FROM playtime_hidden WHERE uuid = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking hidden status", e);
            return false;
        }
    }
}