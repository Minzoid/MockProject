package com.minzoid.mockproject;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MockProject extends JavaPlugin implements Listener {

    private FoliaLib foliaLib;
    private PlayerDataService playerDataService;
    private final Set<UUID> processedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        this.foliaLib = new FoliaLib(this);
        // Initialize real SQLite database wrapped as our async Mock database
        this.playerDataService = new PlayerDataService(this, foliaLib);

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("checkdata").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (sender instanceof Player player) {
                    playerDataService.loadPlayerData(player.getUniqueId()).thenAccept(data -> {
                        foliaLib.getScheduler().runAtEntity(player, task -> {
                            player.sendMessage("§a[MockProject] Your current database data:");
                            player.sendMessage("§eHealth: §f" + data.getHealth());
                            player.sendMessage("§eLevel: §f" + data.getLevel());
                        });
                    });
                } else {
                    sender.sendMessage("This command is for players only.");
                }
                return true;
            }
        });

        getLogger().info("MockProject enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MockProject disabled!");
    }

    public PlayerDataService getPlayerDataService() {
        return this.playerDataService;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Only trigger if they actually changed blocks to avoid too many calls
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        // Ensure data is loaded and applied once they move
        if (!processedPlayers.contains(player.getUniqueId())) {
            processedPlayers.add(player.getUniqueId());
            playerDataService.loadAndApply(player);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        processedPlayers.remove(event.getPlayer().getUniqueId());
    }
}