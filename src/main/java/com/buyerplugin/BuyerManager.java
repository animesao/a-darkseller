package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BuyerManager {

    private Main plugin;
    private MultiplierManager multiplierManager;
    private Map<Material, Double> buyPrices;
    private Map<Material, Integer> buyLimits;
    private Map<Material, Integer> currentStock;
    private BukkitTask rotationTask;
    private BukkitTask autoSellTask;
    private Map<UUID, Boolean> autoSellPlayers;
    private Map<UUID, Set<Material>> playerAutoSellWhitelist;
    private Map<Material, Integer> itemStats;
    private Map<String, CustomItemData> customItems;
    private Map<String, Double> customItemPrices;

    private static class CustomItemData {
        double price;
        String displayName;
        List<String> lore;

        CustomItemData(double price, String displayName, List<String> lore) {
            this.price = price;
            this.displayName = displayName;
            this.lore = lore;
        }
    }

    public BuyerManager(Main plugin, MultiplierManager multiplierManager) {
        this.plugin = plugin;
        this.multiplierManager = multiplierManager;
        this.buyPrices = new HashMap<>();
        this.buyLimits = new HashMap<>();
        this.currentStock = new HashMap<>();
        this.autoSellPlayers = new HashMap<>();
        this.playerAutoSellWhitelist = new HashMap<>();
        this.itemStats = new HashMap<>();
        this.customItems = new HashMap<>();
        this.customItemPrices = new HashMap<>(); // Deprecated but kept for compatibility
        
        loadStats();
        
        // Randomize assortment on startup if enabled
        if (plugin.getConfig().getBoolean("rotation.randomize-on-start", true)) {
            rotateItems();
        } else {
            loadRotation();
            if (buyPrices.isEmpty() || !plugin.getConfig().getBoolean("rotation.enabled", true)) {
                rotateItems(); // Принудительно вызываем ротацию, если она выключена, чтобы загрузить все предметы
            }
            loadLimits();
        }
        
        startRotationTask();
        startAutoSellTask();
    }

    public void saveRotation() {
        plugin.getDatabaseManager().saveRotation(buyPrices);
    }

    public void loadRotation() {
        buyPrices.clear();
        plugin.getDatabaseManager().loadRotation(buyPrices);
    }

    public void loadBuyPrices() {
        buyPrices.clear();
        FileConfiguration config = plugin.getConfig();

        if (config.contains("items")) {
            for (String key : config.getConfigurationSection("items").getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    double price = config.getDouble("items." + key);
                    buyPrices.put(material, price);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Неизвестный материал: " + key);
                }
            }
        }
    }

    public void loadLimits() {
        buyLimits.clear();
        currentStock.clear();
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("limits.enabled", false)) {
            return;
        }

        int defaultLimit = config.getInt("limits.per-item.default", 256);

        for (Material material : buyPrices.keySet()) {
            String key = material.name();
            int limit = config.getInt("limits.per-item." + key, defaultLimit);
            buyLimits.put(material, limit);
            currentStock.put(material, 0);
        }
    }

    public void startRotationTask() {
        if (rotationTask != null) {
            rotationTask.cancel();
        }

        if (!plugin.getConfig().getBoolean("rotation.enabled", false)) {
            return;
        }

        int intervalMinutes = plugin.getConfig().getInt("rotation.update-interval", 60);
        long intervalTicks = intervalMinutes * 60 * 20L;

        rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            rotateItems();
        }, intervalTicks, intervalTicks);
    }

    public void stopRotationTask() {
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
    }

    public void startAutoSellTask() {
        if (autoSellTask != null) {
            autoSellTask.cancel();
        }

        if (!plugin.getConfig().getBoolean("auto-sell.enabled", false)) {
            return;
        }

        int intervalSeconds = plugin.getConfig().getInt("auto-sell.interval", 60);
        long intervalTicks = intervalSeconds * 20L;

        autoSellTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            processAutoSell();
        }, intervalTicks, intervalTicks);
    }

    public void stopAutoSellTask() {
        if (autoSellTask != null) {
            autoSellTask.cancel();
            autoSellTask = null;
        }
    }

    private void processAutoSell() {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            
            if (!isAutoSellEnabled(playerUUID)) {
                continue;
            }

            double totalMoney = 0.0;
            double totalPointsEarned = 0.0;
            int totalItems = 0;
            Set<Material> playerWhitelist = playerAutoSellWhitelist.get(playerUUID);

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);

                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                if (!isBuyable(item)) {
                    continue;
                }

                // Auto-sell usually only for materials if not specified
                if (getCustomItemId(item) == null && playerWhitelist != null) {
                    if (!playerWhitelist.contains(item.getType())) {
                        continue;
                    }
                }

                int amount = item.getAmount();
                int remaining = Integer.MAX_VALUE;
                if (getCustomItemId(item) == null) {
                    remaining = getRemainingStock(item.getType());
                }

                if (remaining != Integer.MAX_VALUE && amount > remaining) {
                    amount = remaining;
                }

                if (amount <= 0) {
                    continue;
                }

                double price = getPriceWithMultiplier(item, playerUUID);
                double itemTotal = price * amount;
                
                // Расчет очков за предмет из points.yml
                FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
                String matName = item.getType().name();
                double itemPoints = 0;
                
                if (pointsConfig.contains("items." + matName)) {
                    itemPoints = pointsConfig.getDouble("items." + matName) * amount;
                } else {
                    double ratio = pointsConfig.getDouble("default.ratio-per-money", 0.05);
                    itemPoints = itemTotal * ratio;
                }
                
                double globalMult = pointsConfig.getDouble("settings.global-multiplier", 1.0);
                // Поддержка любого положительного множителя (например 2.5)
                if (globalMult < 0) globalMult = 1.0; 
                totalPointsEarned += itemPoints * globalMult;

                totalMoney += itemTotal;
                totalItems += amount;

                if (getCustomItemId(item) == null) {
                    addStock(item.getType(), amount);
                }

                if (amount == item.getAmount()) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - amount);
                }
            }

            if (totalItems > 0) {
                // Анти-абьюз очков
                FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
                double maxPoints = pointsConfig.getDouble("settings.max-points-per-transaction", 500.0);
                if (totalPointsEarned > maxPoints) {
                    totalPointsEarned = maxPoints;
                }

                // Добавляем проданные предметы к статистике игрока
                multiplierManager.addSoldItems(playerUUID, totalItems);
                
                // Начисляем очки скупщика
                plugin.getShopManager().addPoints(playerUUID, totalPointsEarned);
                
                String economyType = plugin.getConfig().getString("economy.type", "money");

                if (economyType.equalsIgnoreCase("eco")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + totalMoney);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + player.getName() + " " + totalMoney);
                }

                if (plugin.getConfig().getBoolean("auto-sell.message.enabled", true)) {
                    String message = plugin.getConfigManager().colorize(
                        plugin.getConfig().getString("auto-sell.message.text", "#00FF00[Авто-скупщик] Продано %amount% предметов за %money% монет!")
                            .replace("%amount%", String.valueOf(totalItems))
                            .replace("%money%", String.format("%.2f", totalMoney))
                    );
                    player.sendMessage(message);
                }
            }
        }
    }

    public boolean isAutoSellEnabled(UUID playerUUID) {
        return autoSellPlayers.getOrDefault(playerUUID, false);
    }

    public void setAutoSell(UUID playerUUID, boolean enabled) {
        autoSellPlayers.put(playerUUID, enabled);
    }

    public void toggleAutoSell(UUID playerUUID) {
        autoSellPlayers.put(playerUUID, !isAutoSellEnabled(playerUUID));
    }

    public Set<Material> getPlayerAutoSellWhitelist(UUID playerUUID) {
        return playerAutoSellWhitelist.getOrDefault(playerUUID, new HashSet<>());
    }

    public void toggleAutoSellItem(UUID playerUUID, Material material) {
        Set<Material> whitelist = playerAutoSellWhitelist.computeIfAbsent(playerUUID, k -> new HashSet<>());
        if (whitelist.contains(material)) {
            whitelist.remove(material);
        } else {
            whitelist.add(material);
        }
    }

    public boolean isInPlayerWhitelist(UUID playerUUID, Material material) {
        Set<Material> whitelist = playerAutoSellWhitelist.get(playerUUID);
        return whitelist != null && whitelist.contains(material);
    }

    public void selectAllItemsForAutoSell(UUID playerUUID) {
        Set<Material> whitelist = playerAutoSellWhitelist.computeIfAbsent(playerUUID, k -> new HashSet<>());
        whitelist.clear();
        whitelist.addAll(buyPrices.keySet());
    }

    public void deselectAllItemsForAutoSell(UUID playerUUID) {
        Set<Material> whitelist = playerAutoSellWhitelist.get(playerUUID);
        if (whitelist != null) {
            whitelist.clear();
        }
    }

    public void processSellAll(org.bukkit.entity.Player player) {
        double totalMoney = 0;
        int totalItems = 0;
        double totalPoints = 0;
        UUID uuid = player.getUniqueId();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            if (isBuyable(item)) {
                int amount = item.getAmount();
                int canBuy = amount;
                if (getCustomItemId(item) == null) {
                    canBuy = Math.min(amount, getRemainingStock(item.getType()));
                }
                
                if (canBuy > 0) {
                    double price = getPriceWithMultiplier(item, uuid);
                    double itemTotal = price * canBuy;
                    totalMoney += itemTotal;
                    totalItems += canBuy;
                    
                    // Track stats
                    itemStats.put(item.getType(), itemStats.getOrDefault(item.getType(), 0) + canBuy);
                    
                    // Расчет очков из points.yml
                    FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
                    String matName = item.getType().name();
                    double itemPoints = 0;
                    if (pointsConfig.contains("items." + matName)) {
                        itemPoints = pointsConfig.getDouble("items." + matName) * canBuy;
                    } else {
                        double ratio = pointsConfig.getDouble("default.ratio-per-money", 0.05);
                        itemPoints = itemTotal * ratio;
                    }
                    double globalMult = pointsConfig.getDouble("settings.global-multiplier", 1.0);
                    totalPoints += itemPoints * globalMult;

                    if (getCustomItemId(item) == null) {
                        addStock(item.getType(), canBuy);
                    }
                    item.setAmount(amount - canBuy);
                }
            }
        }

        if (totalItems > 0) {
            // Анти-абьюз очков
            FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
            double maxPoints = pointsConfig.getDouble("settings.max-points-per-transaction", 500.0);
            if (totalPoints > maxPoints) {
                totalPoints = maxPoints;
            }

            multiplierManager.addSoldItems(uuid, totalItems);
            plugin.getShopManager().addPoints(uuid, totalPoints);
            
            String cmd = plugin.getConfig().getString("economy.type", "money").equalsIgnoreCase("eco") ? "eco" : "money";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd + " give " + player.getName() + " " + totalMoney);
            
            player.sendMessage(plugin.getConfigManager().getMessage("sell-success")
                .replace("%amount%", String.valueOf(totalItems))
                .replace("%money%", String.format("%.2f", totalMoney)));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("no-items"));
        }
    }

    public void addCustomItem(String id, double price, String displayName) {
        addCustomItem(id, price, displayName, null);
    }

    public void addCustomItem(String id, double price, String displayName, List<String> lore) {
        customItems.put(id, new CustomItemData(price, displayName, lore));
        customItemPrices.put(id, price);
    }

    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String name = meta.getDisplayName();
        List<String> lore = meta.getLore();
        
        for (Map.Entry<String, CustomItemData> entry : customItems.entrySet()) {
            CustomItemData data = entry.getValue();
            if (data.displayName != null && name != null && name.contains(data.displayName)) {
                if (data.lore == null) return entry.getKey();
                if (lore != null && lore.containsAll(data.lore)) return entry.getKey();
            }
        }
        return null;
    }

    public double getCustomItemPrice(String id) {
        return customItemPrices.getOrDefault(id, 0.0);
    }

    public boolean isBuyable(ItemStack item) {
        if (item == null) return false;
        
        // Block selling of "Combat Shards" (Booster currency)
        FileConfiguration boosterConfig = plugin.getConfigManager().getBoostersConfig();
        String currencyName = plugin.getConfigManager().colorize(boosterConfig.getString("currency.name"));
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            if (item.getItemMeta().getDisplayName().equals(currencyName)) {
                return false;
            }
        }

        if (getCustomItemId(item) != null) return true;
        return isBuyable(item.getType());
    }

    public double getPriceWithMultiplier(ItemStack item, UUID playerUUID) {
        String customId = getCustomItemId(item);
        double basePrice = (customId != null) ? getCustomItemPrice(customId) : getPrice(item.getType());
        double multiplier = multiplierManager.getMultiplier(playerUUID);
        
        // Wipe progression multiplier
        FileConfiguration wipeConfig = plugin.getConfigManager().getWipeConfig();
        if (wipeConfig != null && wipeConfig.getBoolean("settings.enabled", true)) {
            long wipeTime = plugin.getDatabaseManager().getWipeDate();
            long currentTime = System.currentTimeMillis();
            long diffMillis = currentTime - wipeTime;
            long diffDays = diffMillis / (1000 * 60 * 60 * 24);
            
            int startAfter = wipeConfig.getInt("progression.start-after-days", 0);
            if (diffDays >= startAfter) {
                double dayBonus = wipeConfig.getDouble("progression.daily-increase-percent", 5.0) / 100.0;
                double maxBonus = wipeConfig.getDouble("progression.max-total-bonus-percent", 100.0) / 100.0;
                
                double totalBonus = Math.min(maxBonus, diffDays * dayBonus);
                
                // Custom day multipliers
                if (wipeConfig.contains("custom-days." + diffDays)) {
                    multiplier *= wipeConfig.getDouble("custom-days." + diffDays);
                } else {
                    multiplier *= (1.0 + totalBonus);
                }
            }
        }
        
        return basePrice * multiplier;
    }

    public int getStock(Material material) {
        return currentStock.getOrDefault(material, 0);
    }

    public void rotateItems() {
        FileConfiguration config = plugin.getConfig();
        FileConfiguration catConfig = plugin.getConfigManager().getCategoriesConfig();

        ConfigurationSection allItemsSection = config.getConfigurationSection("all-items");
        if (allItemsSection == null) return;

        Map<String, Double> allItems = new HashMap<>();
        for (String key : allItemsSection.getKeys(false)) {
            allItems.put(key, allItemsSection.getDouble(key));
        }

        config.set("items", null);
        Random random = new Random();
        double minPriceMult = config.getDouble("rotation.price-fluctuation.min", 0.7);
        double maxPriceMult = config.getDouble("rotation.price-fluctuation.max", 1.3);

        boolean rotationEnabled = config.getBoolean("rotation.enabled", true);
        boolean showAllIfDisabled = catConfig.getBoolean("settings.show-all-items-if-rotation-disabled", true);

        if (catConfig.getBoolean("settings.enabled", true)) {
            ConfigurationSection categories = catConfig.getConfigurationSection("categories");
            if (categories != null) {
                int minPerCat = catConfig.getInt("settings.min-items-per-category", 10);
                for (String catKey : categories.getKeys(false)) {
                    List<String> catItems = new ArrayList<>(catConfig.getStringList("categories." + catKey + ".items"));
                    
                    int count;
                    if (!rotationEnabled && showAllIfDisabled) {
                        count = catItems.size();
                    } else {
                        Collections.shuffle(catItems);
                        count = Math.min(catItems.size(), minPerCat);
                    }

                    for (int i = 0; i < count; i++) {
                        String itemKey = catItems.get(i);
                        if (allItems.containsKey(itemKey)) {
                            addItemToRotation(itemKey, allItems.get(itemKey), minPriceMult, maxPriceMult, random);
                        }
                    }
                }
            }
        } else {
            int minItems = config.getInt("rotation.min-items", 15);
            int maxItems = config.getInt("rotation.max-items", 30);
            List<String> allKeys = new ArrayList<>(allItems.keySet());
            Collections.shuffle(allKeys);
            int totalTarget = minItems + random.nextInt(maxItems - minItems + 1);
            int count = Math.min(allKeys.size(), totalTarget);
            for (int i = 0; i < count; i++) {
                String itemKey = allKeys.get(i);
                addItemToRotation(itemKey, allItems.get(itemKey), minPriceMult, maxPriceMult, random);
            }
        }

        plugin.saveConfig();
        loadBuyPrices();
        loadLimits();
        saveRotation();
        updatePlayerWhitelistsAfterRotation();

        String message = plugin.getConfigManager().getMessage("rotation-update");
        Bukkit.broadcastMessage(message);
    }

    private void addItemToRotation(String key, double basePrice, double minMult, double maxMult, Random random) {
        double fluctuation = minMult + (maxMult - minMult) * random.nextDouble();
        double newPrice = basePrice * fluctuation;
        plugin.getConfig().set("items." + key, Math.round(newPrice * 100.0) / 100.0);
    }
    
    private void updatePlayerWhitelistsAfterRotation() {
        Set<Material> currentBuyableItems = buyPrices.keySet();
        
        for (UUID playerUUID : playerAutoSellWhitelist.keySet()) {
            Set<Material> playerWhitelist = playerAutoSellWhitelist.get(playerUUID);
            
            if (playerWhitelist != null && !playerWhitelist.isEmpty()) {
                playerWhitelist.removeIf(material -> !currentBuyableItems.contains(material));
            }
        }
    }

    public Map<Material, Double> getBuyPrices() {
        return buyPrices;
    }

    public double getPrice(Material material) {
        return buyPrices.getOrDefault(material, 0.0);
    }
    
    public double getWipeMultiplier() {
        FileConfiguration wipeConfig = plugin.getConfigManager().getWipeConfig();
        if (wipeConfig == null || !wipeConfig.getBoolean("settings.enabled", true)) {
            return 1.0;
        }
        
        long wipeTime = plugin.getDatabaseManager().getWipeDate();
        long currentTime = System.currentTimeMillis();
        long diffMillis = currentTime - wipeTime;
        long diffDays = diffMillis / (1000 * 60 * 60 * 24);
        
        int startAfter = wipeConfig.getInt("progression.start-after-days", 0);
        if (diffDays < startAfter) {
            return 1.0;
        }

        double dayBonus = wipeConfig.getDouble("progression.daily-increase-percent", 5.0) / 100.0;
        double maxBonus = wipeConfig.getDouble("progression.max-total-bonus-percent", 100.0) / 100.0;
        
        double totalBonus = Math.min(maxBonus, diffDays * dayBonus);
        
        if (wipeConfig.contains("custom-days." + diffDays)) {
            return wipeConfig.getDouble("custom-days." + diffDays);
        } else {
            return (1.0 + totalBonus);
        }
    }

    public double getPriceWithMultiplier(Material material, UUID playerUUID) {
        double basePrice = getPrice(material);
        double multiplier = multiplierManager.getMultiplier(playerUUID);
        
        // Добавляем учет множителя вайпа
        multiplier *= getWipeMultiplier();
        
        return basePrice * multiplier;
    }

    public boolean isBuyable(Material material) {
        return buyPrices.containsKey(material) && buyPrices.get(material) > 0;
    }

    public int getLimit(Material material) {
        if (!plugin.getConfig().getBoolean("limits.enabled", false)) {
            return Integer.MAX_VALUE;
        }
        int limit = buyLimits.getOrDefault(material, Integer.MAX_VALUE);
        return (limit == -1) ? Integer.MAX_VALUE : limit;
    }

    public int getRemainingStock(Material material) {
        if (!plugin.getConfig().getBoolean("limits.enabled", false)) {
            return Integer.MAX_VALUE;
        }

        int limit = getLimit(material);
        if (limit == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        
        int current = currentStock.getOrDefault(material, 0);
        return Math.max(0, limit - current);
    }

    public void addStock(Material material, int amount) {
        // Трекаем статистику продаж для категорий всегда
        itemStats.put(material, itemStats.getOrDefault(material, 0) + amount);
        saveStats(); // Сохраняем сразу для актуальности

        if (!plugin.getConfig().getBoolean("limits.enabled", false)) {
            return;
        }

        currentStock.put(material, currentStock.getOrDefault(material, 0) + amount);
    }

    public void resetStock() {
        currentStock.clear();
    }

    public Map<Material, Integer> getItemStats() {
        return itemStats;
    }

    public void loadStats() {
        plugin.getDatabaseManager().loadItemStats(itemStats);
    }

    public void saveStats() {
        plugin.getDatabaseManager().saveItemStats(itemStats);
    }
}