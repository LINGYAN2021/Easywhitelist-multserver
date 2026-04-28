package xyz.nikitacartes.easywhitelist.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nikitacartes.easywhitelist.config.DatabaseConfig;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {
    private static final Logger LOGGER = LogManager.getLogger("EasyWhitelist-DB");
    private HikariDataSource dataSource;
    private final DatabaseConfig config;
    private final String whitelistTable;
    private final Set<String> whitelistCache = ConcurrentHashMap.newKeySet();

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
        this.whitelistTable = config.tablePrefix + "whitelist";
    }

    public boolean connect() {
        try {
            // Ensure JDBC driver is loaded with the correct classloader (Fabric Knot)
            ClassLoader modClassLoader = DatabaseManager.class.getClassLoader();
            Class.forName("com.mysql.cj.jdbc.Driver", true, modClassLoader);
            LOGGER.info("[EasyWhitelist] MySQL JDBC driver loaded successfully.");

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&defaultAuthenticationPlugin=caching_sha2_password&disabledAuthenticationPlugins=mysql_native_password",
                    config.host, config.port, config.database));
            hikariConfig.setUsername(config.username);
            hikariConfig.setPassword(config.password);
            hikariConfig.setMaximumPoolSize(config.connectionPoolSize);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setIdleTimeout(60000);
            hikariConfig.setMaxLifetime(300000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setPoolName("EasyWhitelist-Pool");

            dataSource = new HikariDataSource(hikariConfig);

            // Test connection
            try (var conn = dataSource.getConnection()) {
                LOGGER.info("[EasyWhitelist] Database connection test OK. URL: {}:{}/{}", config.host, config.port, config.database);
            }

            createTables();
            refreshCache();
            LOGGER.info("[EasyWhitelist] Database connected successfully. Cache size: {}", whitelistCache.size());
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("[EasyWhitelist] MySQL JDBC driver not found! Make sure the mod JAR includes mysql-connector-j.", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("[EasyWhitelist] Failed to connect to database at {}:{}/{}", config.host, config.port, config.database);
            LOGGER.error("[EasyWhitelist] Error: ", e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(String.format(
                    "CREATE TABLE IF NOT EXISTS `%s` (" +
                            "`id` INT AUTO_INCREMENT PRIMARY KEY," +
                            "`name` VARCHAR(16) NOT NULL," +
                            "`uuid` VARCHAR(36) NOT NULL," +
                            "`server_id` VARCHAR(64) DEFAULT 'global'," +
                            "`added_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY `unique_name_server` (`name`, `server_id`)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;", whitelistTable));
            LOGGER.info("[EasyWhitelist] Database table '{}' ready.", whitelistTable);
        }
    }

    public void refreshCache() {
        Set<String> newCache = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     String.format("SELECT `name` FROM `%s` WHERE `server_id` = ? OR `server_id` = 'global'", whitelistTable))) {
            stmt.setString(1, config.serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    newCache.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[EasyWhitelist] Failed to refresh whitelist cache: {}", e.getMessage());
            return;
        }
        whitelistCache.clear();
        whitelistCache.addAll(newCache);
    }

    public boolean isWhitelisted(String name) {
        if (whitelistCache.contains(name)) {
            return true;
        }
        // Cache miss — query database directly for real-time accuracy
        return queryFromDatabase(name);
    }

    private boolean queryFromDatabase(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     String.format("SELECT 1 FROM `%s` WHERE `name` = ? AND (`server_id` = ? OR `server_id` = 'global') LIMIT 1", whitelistTable))) {
            stmt.setString(1, name);
            stmt.setString(2, config.serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    whitelistCache.add(name);
                    LOGGER.debug("[EasyWhitelist] Cache miss for '{}', found in database.", name);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[EasyWhitelist] Failed to query whitelist for '{}': {}", name, e.getMessage());
        }
        return false;
    }

    public boolean addToWhitelist(String name, String uuid) {
        String serverId = config.shareAcrossServers ? "global" : config.serverId;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     String.format("INSERT INTO `%s` (`name`, `uuid`, `server_id`) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE `uuid` = VALUES(`uuid`)", whitelistTable))) {
            stmt.setString(1, name);
            stmt.setString(2, uuid);
            stmt.setString(3, serverId);
            stmt.executeUpdate();
            whitelistCache.add(name);
            LOGGER.info("[EasyWhitelist] Added '{}' to database whitelist.", name);
            return true;
        } catch (SQLException e) {
            LOGGER.error("[EasyWhitelist] Failed to add '{}' to whitelist: {}", name, e.getMessage());
            return false;
        }
    }

    public boolean removeFromWhitelist(String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     String.format("DELETE FROM `%s` WHERE `name` = ? AND (`server_id` = ? OR `server_id` = 'global')", whitelistTable))) {
            stmt.setString(1, name);
            stmt.setString(2, config.serverId);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                whitelistCache.remove(name);
                LOGGER.info("[EasyWhitelist] Removed '{}' from database whitelist.", name);
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOGGER.error("[EasyWhitelist] Failed to remove '{}' from whitelist: {}", name, e.getMessage());
            return false;
        }
    }

    public int bulkAddToWhitelist(java.util.Map<String, String> nameUuidMap) {
        String serverId = config.shareAcrossServers ? "global" : config.serverId;
        int count = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     String.format("INSERT INTO `%s` (`name`, `uuid`, `server_id`) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE `uuid` = VALUES(`uuid`)", whitelistTable))) {
            for (var entry : nameUuidMap.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue());
                stmt.setString(3, serverId);
                stmt.addBatch();
                count++;
            }
            stmt.executeBatch();
            whitelistCache.addAll(nameUuidMap.keySet());
            LOGGER.info("[EasyWhitelist] Bulk imported {} players into database whitelist.", count);
        } catch (SQLException e) {
            LOGGER.error("[EasyWhitelist] Failed to bulk import whitelist: {}", e.getMessage());
            return -1;
        }
        return count;
    }

    public Set<String> getAllWhitelisted() {
        return Set.copyOf(whitelistCache);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("[EasyWhitelist] Database connection closed.");
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
