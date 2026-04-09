package com.minzoid.mockproject;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PlayerDataService {

    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private final ExecutorService dbExecutor;

    public PlayerDataService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.jdbcUrl = buildJdbcUrl();
        int dbThreads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        this.dbExecutor = Executors.newFixedThreadPool(dbThreads, new ThreadFactory() {
            private int index = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mockproject-db-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });
        setupDatabase();
    }

    private String buildJdbcUrl() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File dbFile = new File(plugin.getDataFolder(), "mock_database.db");
        return "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    private void setupDatabase() {
        // Runs during plugin enable (server thread). Uses a short-lived connection.
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                              "uuid TEXT PRIMARY KEY," +
                              "health REAL," +
                              "level INTEGER)");
            plugin.getLogger().info("SQLite Mock Database initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
        }
    }

    // Runs on dedicated DB executor only. Never touch Bukkit/Player API here.
    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement select = connection.prepareStatement("SELECT health, level FROM player_data WHERE uuid = ?")) {
                // Isolated SQL work on this thread-local connection.
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerData(uuid, rs.getDouble("health"), rs.getInt("level"));
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_data (uuid, health, level) VALUES (?, ?, ?)")) {
                    insert.setString(1, uuid.toString());
                    insert.setDouble(2, 20.0);
                    insert.setInt(3, 10);
                    insert.executeUpdate();
                }

                return new PlayerData(uuid, 20.0, 10);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player data for " + uuid + ": " + e.getMessage());
                return new PlayerData(uuid, 20.0, 10); // fallback
            }
        }, dbExecutor);
    }

    public void loadAndApply(Player player) {
        // Capture only immutable/simple data before async boundary.
        UUID uuid = player.getUniqueId();

        loadPlayerData(uuid).thenAccept(data -> {
            // Back on the player's Folia thread before touching Player API.
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || player.isDead()) return;

                player.setHealth(data.getHealth());
                player.setLevel(data.getLevel());
                player.sendMessage("§a[MockProject] Your data was safely applied while moving!");
            }, null);
        });
    }

    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dbExecutor.shutdownNow();
        }
    }

    public static class PlayerData {
        private final UUID uuid;
        private final double health;
        private final int level;

        public PlayerData(UUID uuid, double health, int level) {
            this.uuid = uuid;
            this.health = health;
            this.level = level;
        }

        public double getHealth() {
            return health;
        }

        public int getLevel() {
            return level;
        }
    }
}