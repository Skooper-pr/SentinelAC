package com.skooper.sentinelac.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite persistence manager logging flags and labeled verdicts for audit and ML training data.
 */
public final class StorageManager implements AutoCloseable {

    private final String dbUrl;
    private final Logger logger;
    private Connection connection;

    public StorageManager(File dbFile, Logger logger) {
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.logger = logger != null ? logger : Logger.getLogger(StorageManager.class.getName());
        initDatabase();
    }

    public StorageManager(String customJdbcUrl, Logger logger) {
        this.dbUrl = customJdbcUrl;
        this.logger = logger != null ? logger : Logger.getLogger(StorageManager.class.getName());
        initDatabase();
    }

    private synchronized void initDatabase() {
        try {
            this.connection = createNewConnection();

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS flags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        lambda_value REAL NOT NULL,
                        evidence_summary TEXT NOT NULL
                    );
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS verdicts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        flag_id INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        staff_member TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (flag_id) REFERENCES flags(id)
                    );
                """);
            }
        } catch (Exception e) {
            this.logger.log(Level.SEVERE, "Failed to initialize SentinelAC SQLite database at " + dbUrl, e);
        }
    }

    private Connection createNewConnection() throws SQLException {
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl(dbUrl);
        return ds.getConnection();
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = createNewConnection();
        }
        return connection;
    }

    public synchronized int saveFlag(UUID playerUuid, String playerName, String category, double lambda, String summary) {
        String sql = "INSERT INTO flags (player_uuid, player_name, category, timestamp, lambda_value, evidence_summary) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, playerName);
            pstmt.setString(3, category);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.setDouble(5, lambda);
            pstmt.setString(6, summary);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error inserting flag into SQLite", e);
        }
        return -1;
    }

    public synchronized boolean saveVerdict(int flagId, String outcome, String staffMember) {
        String sql = "INSERT INTO verdicts (flag_id, outcome, staff_member, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, flagId);
            pstmt.setString(2, outcome.toLowerCase());
            pstmt.setString(3, staffMember);
            pstmt.setLong(4, System.currentTimeMillis());
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error inserting verdict into SQLite", e);
            return false;
        }
    }

    public synchronized Optional<FlagRecord> getFlag(int flagId) {
        String sql = "SELECT id, player_uuid, player_name, category, timestamp, lambda_value, evidence_summary FROM flags WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, flagId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFlag(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error querying flag by id", e);
        }
        return Optional.empty();
    }

    public synchronized List<FlagRecord> getRecentFlags(int limit) {
        List<FlagRecord> list = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, category, timestamp, lambda_value, evidence_summary FROM flags ORDER BY id DESC LIMIT ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFlag(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error querying recent flags", e);
        }
        return list;
    }

    public synchronized List<FlagRecord> getFlagsForPlayer(UUID playerUuid) {
        List<FlagRecord> list = new ArrayList<>();
        String sql = "SELECT id, player_uuid, player_name, category, timestamp, lambda_value, evidence_summary FROM flags WHERE player_uuid = ? ORDER BY id DESC";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFlag(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error querying player flags", e);
        }
        return list;
    }

    public synchronized List<VerdictRecord> getVerdictsForFlag(int flagId) {
        List<VerdictRecord> list = new ArrayList<>();
        String sql = "SELECT id, flag_id, outcome, staff_member, timestamp FROM verdicts WHERE flag_id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, flagId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new VerdictRecord(
                            rs.getInt("id"),
                            rs.getInt("flag_id"),
                            rs.getString("outcome"),
                            rs.getString("staff_member"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error querying verdicts for flag", e);
        }
        return list;
    }

    private FlagRecord mapFlag(ResultSet rs) throws SQLException {
        return new FlagRecord(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("category"),
                rs.getLong("timestamp"),
                rs.getDouble("lambda_value"),
                rs.getString("evidence_summary")
        );
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {
            }
        }
    }
}
