package macronis.xdisablephantom;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private final JavaPlugin plugin;

    private Connection connection;
    private PreparedStatement selectStmt;
    private PreparedStatement insertStmt;
    private PreparedStatement updateStmt;

    private final ExecutorService writer;
    private final ConcurrentHashMap<UUID, Boolean> cache = new ConcurrentHashMap<>();

    private enum DbType { SQLITE, MYSQL }
    private DbType dbType = DbType.SQLITE;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.writer = new ThreadPoolExecutor(
                1, 1,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "DonutDisablePhantom-DB");
                    t.setDaemon(true);
                    return t;
                }
        );
        connect();
        createTable();
        prepareStatements();
    }

    private void connect() {
        try {
            ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
            if (dbConfig == null) {
                dbConfig = plugin.getConfig().createSection("database");
            }
            String type = dbConfig.getString("type", "sqlite");

            if ("mysql".equalsIgnoreCase(type)) {
                this.dbType = DbType.MYSQL;
                ConfigurationSection mysql = dbConfig.getConfigurationSection("mysql");
                if (mysql == null) {
                    throw new IllegalStateException("database.mysql section missing in config");
                }
                String host = mysql.getString("host", "localhost");
                int port = mysql.getInt("port", 3306);
                String database = mysql.getString("database", "minecraft");
                String username = mysql.getString("username", "root");
                String password = mysql.getString("password", "");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
                this.connection = DriverManager.getConnection(url, username, password);
            } else {
                this.dbType = DbType.SQLITE;

                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {

                    dataFolder.mkdirs();
                }
                ConfigurationSection sqlite = dbConfig.getConfigurationSection("sqlite");
                String fileName = sqlite != null ? sqlite.getString("file", "disable-phantom.db") : "disable-phantom.db";
                Path dbPath = dataFolder.toPath().resolve(fileName);
                Files.createDirectories(dbPath.getParent());

                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException ignored) {}
                String url = "jdbc:sqlite:" + dbPath.toString();
                this.connection = DriverManager.getConnection(url);
                
                try (Statement st = this.connection.createStatement()) {
                    st.execute("PRAGMA journal_mode = WAL;");
                    st.execute("PRAGMA synchronous = NORMAL;");
                } catch (SQLException ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS phantom_settings (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "disabled BOOLEAN NOT NULL" +
                ");";
        try (PreparedStatement ps = connection.prepareStatement(ddl)) {
            ps.execute();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed creating table phantom_settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void prepareStatements() {
        try {
            this.selectStmt = connection.prepareStatement(
                    "SELECT disabled FROM phantom_settings WHERE uuid = ?;"
            );
            this.insertStmt = connection.prepareStatement(
                    "INSERT INTO phantom_settings (uuid, disabled) VALUES (?, ?);"
            );
            this.updateStmt = connection.prepareStatement(
                    "UPDATE phantom_settings SET disabled = ? WHERE uuid = ?;"
            );
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed preparing statements: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public boolean togglePhantom(UUID uuid) {
        boolean current = isPhantomDisabled(uuid);
        boolean next = !current;
        cache.put(uuid, next);


        writer.submit(() -> {
            try {
                if (rowExists(uuid)) {
                    updateStmt.setBoolean(1, next);
                    updateStmt.setString(2, uuid.toString());
                    updateStmt.executeUpdate();
                } else {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setBoolean(2, next);
                    insertStmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to persist togglePhantom(" + uuid + "): " + e.getMessage());
            }
        });

        return next;
    }


    public boolean isPhantomDisabled(UUID uuid) {
        Boolean cached = cache.get(uuid);
        if (cached != null) return cached;

        AtomicBoolean result = new AtomicBoolean(false);
        try {
            selectStmt.setString(1, uuid.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    result.set(rs.getBoolean("disabled"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query phantom state for " + uuid + ": " + e.getMessage());
        }
        cache.put(uuid, result.get());
        return result.get();
    }

    private boolean rowExists(UUID uuid) throws SQLException {
        selectStmt.setString(1, uuid.toString());
        try (ResultSet rs = selectStmt.executeQuery()) {
            return rs.next();
        }
    }

    public void closeConnection() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        tryClose(selectStmt);
        tryClose(insertStmt);
        tryClose(updateStmt);
        if (this.connection != null) {
            try { this.connection.close(); } catch (Exception ignored) {}
        }
    }

    private static void tryClose(Statement st) {
        if (st != null) {
            try { st.close(); } catch (Exception ignored) {}
        }
    }

}
