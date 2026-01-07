package com.buyerplugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MultiplierManager {
    
    private Main plugin;
    private Map<UUID, Integer> playerLevels;
    private Map<UUID, Integer> playerSoldItems;
    private Map<UUID, Double> activeBoosterMultipliers = new HashMap<>();
    private Map<UUID, Long> activeBoosterExpirations = new HashMap<>();
    
    public MultiplierManager(Main plugin) {
        this.plugin = plugin;
        this.playerLevels = new HashMap<>();
        this.playerSoldItems = new HashMap<>();
        loadData();
        startBoosterTimer();
    }
    
    private void loadData() {
        plugin.getShopManager().getDatabaseManager().loadMultipliers(playerLevels, playerSoldItems);
        plugin.getShopManager().getDatabaseManager().loadActiveBoosters(activeBoosterMultipliers, activeBoosterExpirations);
    }
    
    private void startBoosterTimer() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            FileConfiguration bConfig = plugin.getConfigManager().getBoostersConfig();
            String format = bConfig.getString("timer.format", "&eАктивный бустер: &fx%multiplier% &7(%time%)");
            int interval = bConfig.getInt("timer.update_interval", 10);
            String finishMsg = bConfig.getString("timer.finish_message", "&cВаш временный бустер закончился!");

            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (activeBoosterExpirations.containsKey(uuid)) {
                    long expire = activeBoosterExpirations.get(uuid);
                    if (expire != -1 && now >= expire) {
                        activeBoosterExpirations.remove(uuid);
                        activeBoosterMultipliers.remove(uuid);
                        plugin.getShopManager().getDatabaseManager().removeActiveBooster(uuid);
                        player.sendMessage(plugin.getConfigManager().colorize(finishMsg));
                    } else {
                        String timeStr;
                        if (expire == -1) {
                            timeStr = "∞";
                        } else {
                            long timeLeft = (expire - now) / 1000;
                            timeStr = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
                        }
                        
                        if (expire == -1 || ((expire - now) / 1000) % interval == 0) {
                            String actionBarMessage = plugin.getConfigManager().colorize(format
                                .replace("%multiplier%", String.format("%.1f", activeBoosterMultipliers.get(uuid)))
                                .replace("%time%", timeStr));
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage));
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    public void addBooster(UUID uuid, double multiplier, int minutes) {
        long expireTime = (minutes == -1) ? -1 : System.currentTimeMillis() + (minutes * 60 * 1000L);
        activeBoosterMultipliers.put(uuid, multiplier);
        activeBoosterExpirations.put(uuid, expireTime);
        plugin.getShopManager().getDatabaseManager().saveActiveBooster(uuid, multiplier, expireTime);
    }
    
    public void saveData() {
        DatabaseManager db = plugin.getShopManager().getDatabaseManager();
        for (Map.Entry<UUID, Integer> entry : playerLevels.entrySet()) {
            db.saveMultiplier(entry.getKey(), entry.getValue(), playerSoldItems.getOrDefault(entry.getKey(), 0));
        }
    }
    
    public int getLevel(UUID playerUUID) {
        return playerLevels.getOrDefault(playerUUID, 1);
    }
    
    public int getSoldItems(UUID playerUUID) {
        return playerSoldItems.getOrDefault(playerUUID, 0);
    }
    
    public double getMultiplier(UUID playerUUID) {
        double base = 1.0;
        if (plugin.getConfig().getBoolean("multiplier.enabled", true)) {
            int level = getLevel(playerUUID);
            double percentPerLevel = plugin.getConfig().getDouble("multiplier.percent-per-level", 10.0);
            base = 1.0 + ((level - 1) * percentPerLevel / 100.0);
        }
        
        if (activeBoosterExpirations.containsKey(playerUUID)) {
            long expire = activeBoosterExpirations.get(playerUUID);
            if (expire == -1 || System.currentTimeMillis() < expire) {
                base *= activeBoosterMultipliers.get(playerUUID);
            }
        }
        
        return base;
    }
    
    public void addSoldItems(UUID playerUUID, int amount) {
        int current = getSoldItems(playerUUID);
        int newAmount = current + amount;
        playerSoldItems.put(playerUUID, newAmount);
        
        checkLevelUp(playerUUID, newAmount);
        
        plugin.getShopManager().getDatabaseManager().saveMultiplier(playerUUID, getLevel(playerUUID), newAmount);
    }
    
    private void checkLevelUp(UUID playerUUID, int soldItems) {
        int currentLevel = getLevel(playerUUID);
        int maxLevel = plugin.getConfig().getInt("multiplier.max-level", 10);
        
        if (currentLevel >= maxLevel) {
            return;
        }
        
        for (int level = currentLevel + 1; level <= maxLevel; level++) {
            int required = plugin.getConfig().getInt("multiplier.level-requirements." + level, Integer.MAX_VALUE);
            
            if (soldItems >= required) {
                playerLevels.put(playerUUID, level);
                
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                    String message = plugin.getConfigManager().colorize(
                        "#00FF00✦ Поздравляем! Ваш уровень множителя повышен до " + level + "!"
                    );
                    player.sendMessage(message);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else {
                break;
            }
        }
    }
    
    public int getNextLevelRequirement(UUID playerUUID) {
        int currentLevel = getLevel(playerUUID);
        int maxLevel = plugin.getConfig().getInt("multiplier.max-level", 10);
        
        if (currentLevel >= maxLevel) {
            return -1;
        }
        
        return plugin.getConfig().getInt("multiplier.level-requirements." + (currentLevel + 1), -1);
    }
    
    public int getItemsToNextLevel(UUID playerUUID) {
        int nextRequired = getNextLevelRequirement(playerUUID);
        
        if (nextRequired == -1) {
            return 0;
        }
        
        int sold = getSoldItems(playerUUID);
        return Math.max(0, nextRequired - sold);
    }
    
    public void clearAllData() {
        playerLevels.clear();
        playerSoldItems.clear();
        plugin.getShopManager().getDatabaseManager().clearMultipliers();
    }

    public void clearAllBoosters() {
        activeBoosterMultipliers.clear();
        activeBoosterExpirations.clear();
        plugin.getShopManager().getDatabaseManager().clearAllBoosters();
    }
}
