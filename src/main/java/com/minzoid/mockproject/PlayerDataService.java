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

public class PlayerDataService {

    private final JavaPlugin plugin;
    private Connection connection;

    public PlayerDataService(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "mock_database.db");
            // Connect to SQLite database using Bukkit's bundled SQLite driver
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                                  "uuid TEXT PRIMARY KEY," +
                                  "health REAL," +
                                  "level INTEGER)");
            }
            plugin.getLogger().info("SQLite Mock Database initialized successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Mock async database load
    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // simulate DB delay
                Thread.sleep(100); 
                
                try (PreparedStatement select = connection.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                    select.setString(1, uuid.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            return new PlayerData(uuid, rs.getDouble("health"), rs.getInt("level"));
                        } else {
                            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO player_data (uuid, health, level) VALUES (?, ?, ?)")) {
                                insert.setString(1, uuid.toString());
                                insert.setDouble(2, 20.0);
                                insert.setInt(3, 10);
                                insert.executeUpdate();
                                return new PlayerData(uuid, 20.0, 10);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return new PlayerData(uuid, 20.0, 10); // fallback
            }
        });
    }

    public void loadAndApply(Player player) {
        UUID uuid = player.getUniqueId();

        loadPlayerData(uuid).thenAccept(data -> {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || player.isDead()) return;

                player.setHealth(data.getHealth());
                player.setLevel(data.getLevel());
                player.sendMessage("§a[MockProject] Your data was safely applied while moving!");
            }, null);
        });
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