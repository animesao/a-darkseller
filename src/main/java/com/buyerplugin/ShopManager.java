package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopManager {
    private final Main plugin;
    private final File shopFile;
    private FileConfiguration shopConfig;
    private final Map<UUID, Double> playerPoints = new HashMap<>();

    public ShopManager(Main plugin) {
        this.plugin = plugin;
        this.shopFile = new File(plugin.getDataFolder(), "shop.yml");
        loadConfigs();
    }

    public void loadConfigs() {
        if (!shopFile.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
    }

    public double getPoints(UUID uuid) {
        if (!playerPoints.containsKey(uuid)) {
            playerPoints.put(uuid, plugin.getDatabaseManager().getPoints(uuid));
        }
        return playerPoints.get(uuid);
    }

    public void addPoints(UUID uuid, double amount) {
        double newPoints = getPoints(uuid) + amount;
        playerPoints.put(uuid, newPoints);
        plugin.getDatabaseManager().setPoints(uuid, newPoints);
    }
    
    public void setPoints(UUID uuid, double amount) {
        playerPoints.put(uuid, amount);
        plugin.getDatabaseManager().setPoints(uuid, amount);
    }

    public DatabaseManager getDatabaseManager() {
        return plugin.getDatabaseManager();
    }

    public boolean removePoints(UUID uuid, double amount) {
        double current = getPoints(uuid);
        if (current < amount) return false;
        double newPoints = current - amount;
        playerPoints.put(uuid, newPoints);
        plugin.getDatabaseManager().setPoints(uuid, newPoints);
        return true;
    }

    public void openShop(Player player) {
        FileConfiguration config = getShopConfig();
        String title = plugin.getConfigManager().colorize(config.getString("title", "#FFD700Магазин Скупщика"));
        int size = config.getInt("size", 54);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, size, title);

        if (config.contains("items")) {
            for (String key : config.getConfigurationSection("items").getKeys(false)) {
                try {
                    int slot = config.getInt("items." + key + ".slot");
                    Material material = Material.valueOf(config.getString("items." + key + ".material"));
                    String name = config.getString("items." + key + ".name");
                    String texture = config.getString("items." + key + ".texture");
                    List<String> lore = new ArrayList<>();
                    for (String line : config.getStringList("items." + key + ".lore")) {
                        lore.add(line.replace("%cost%", 
                                String.valueOf(config.getDouble("items." + key + ".cost"))));
                    }

                    ItemStack item = plugin.getConfigManager().createGuiItem(material, name, lore, texture);
                    inv.setItem(slot, item);
                } catch (Exception ignored) {}
            }
        }
        player.openInventory(inv);
    }

    public void close() {
        // Database connection is managed by Main now
    }

    public FileConfiguration getShopConfig() {
        return shopConfig;
    }
}