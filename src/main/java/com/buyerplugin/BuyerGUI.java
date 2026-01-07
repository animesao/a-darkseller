
package com.buyerplugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BuyerGUI {
    
    private Main plugin;
    private BuyerManager buyerManager;
    private MultiplierManager multiplierManager;
    
    public BuyerGUI(Main plugin, BuyerManager buyerManager, MultiplierManager multiplierManager) {
        this.plugin = plugin;
        this.buyerManager = buyerManager;
        this.multiplierManager = multiplierManager;
    }
    
    public void openMainMenu(Player player) {
        openGenericMenu(player, "main_menu");
    }

    public void openGenericMenu(Player player, String configPath) {
        plugin.getConfigManager().reloadConfigs();
        FileConfiguration config = plugin.getConfigManager().getMenusConfig();
        ConfigurationSection menuSection = config.getConfigurationSection(configPath);
        if (menuSection == null) return;

        String title = plugin.getConfigManager().colorize(menuSection.getString("title", "Меню"));
        int size = menuSection.getInt("size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Decoration from menus.yml
        renderDecoration(inv, menuSection.getMapList("decoration"), size);

        // Functional buttons from menus.yml items section
        ConfigurationSection items = menuSection.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                addMenuButton(inv, player, items, key, size);
            }
        }

        // Back button if present in this section
        if (menuSection.contains("back_button")) {
            ConfigurationSection backBtn = menuSection.getConfigurationSection("back_button");
            int slot = backBtn.getInt("slot", 49);
            Material mat = Material.valueOf(backBtn.getString("material", "BARRIER"));
            String name = backBtn.getString("name", "#FF0000Назад");
            inv.setItem(slot, plugin.getConfigManager().createGuiItem(mat, name, new ArrayList<>(), null));
        }

        player.openInventory(inv);
    }

    private void addMenuButton(Inventory inv, Player player, ConfigurationSection items, String key, int size) {
        try {
            int slot = items.getInt(key + ".slot");
            Material material = Material.valueOf(items.getString(key + ".material"));
            String name = items.getString(key + ".name");
            String base64 = items.getString(key + ".texture");
            
            List<String> lore = new ArrayList<>();
            for (String line : items.getStringList(key + ".lore")) {
                lore.add(line.replace("%points%", 
                        String.format("%.1f", plugin.getShopManager().getPoints(player.getUniqueId()))));
            }
            
            if (key.equals("auto_sell")) {
                boolean enabled = buyerManager.isAutoSellEnabled(player.getUniqueId());
                name = name + (enabled ? " #00FF00[ВКЛ]" : " #FF0000[ВЫКЛ]");
            }

            ItemStack item = plugin.getConfigManager().createGuiItem(material, name, lore, base64);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                // Special handling for dynamic info
                if (key.equals("info")) {
                    List<String> currentLore = meta.getLore();
                    if (currentLore == null) currentLore = new ArrayList<>();
                    
                    UUID playerUUID = player.getUniqueId();
                    
                    // Wipe info
                    double wipeMult = buyerManager.getWipeMultiplier();
                    FileConfiguration wipeCfg = plugin.getConfigManager().getWipeConfig();
                    long wipeTime = plugin.getDatabaseManager().getWipeDate();
                    long diffDays = (System.currentTimeMillis() - wipeTime) / (1000 * 60 * 60 * 24);
                    int startAfter = wipeCfg.getInt("progression.start-after-days", 0);
                    
                    String wipeStatus = diffDays >= startAfter ? "#00FF00Активен" : "#FF0000Ожидание (" + (startAfter - diffDays) + " дн.)";
                    if (!wipeCfg.getBoolean("settings.enabled", true)) wipeStatus = "#FF0000Выключен";
                    
                    String nextIncrease = "Завтра";
                    if (wipeMult >= (1.0 + (wipeCfg.getDouble("progression.max-total-bonus-percent", 100.0) / 100.0))) {
                        nextIncrease = "#FFD700Максимум";
                    }

                    List<String> finalProcessedLore = new ArrayList<>();
                    for (String line : currentLore) {
                        finalProcessedLore.add(plugin.getConfigManager().colorize(line
                            .replace("%wipe_multiplier%", String.format("%.2f", wipeMult))
                            .replace("%wipe_status%", wipeStatus)
                            .replace("%wipe_next_increase%", nextIncrease)
                        ));
                    }

                    // Multiplier info if enabled
                    if (plugin.getConfig().getBoolean("multiplier.enabled", true)) {
                        int currentLevel = multiplierManager.getLevel(playerUUID);
                        int maxLevel = plugin.getConfig().getInt("multiplier.max-level", 10);
                        double multiplier = multiplierManager.getMultiplier(playerUUID);

                        finalProcessedLore.add(plugin.getConfigManager().colorize(""));
                        
                        List<String> formatLines;
                        if (currentLevel < maxLevel) {
                            formatLines = items.getStringList(key + ".multiplier_format");
                            if (formatLines.isEmpty()) {
                                formatLines = Arrays.asList(
                                    "#FFD700━━━ Множитель прибыли ━━━",
                                    "#00FF00Уровень: #FFFF00%level%",
                                    "#00FF00Множитель: #FFFF00x%multiplier%",
                                    "#00FF00Прогресс: #FFFF00%sold%/%required% предметов",
                                    "#00FF00Осталось: #FFFF00%needed% шт."
                                );
                            }
                        } else {
                            formatLines = items.getStringList(key + ".multiplier_max");
                            if (formatLines.isEmpty()) {
                                formatLines = Arrays.asList(
                                    "#FFD700━━━ Множитель прибыли ━━━",
                                    "#00FF00Уровень: #FFFF00%level%",
                                    "#00FF00Множитель: #FFFF00x%multiplier%",
                                    "#FFFF00Максимальный уровень достигнут!"
                                );
                            }
                        }

                        int needed = multiplierManager.getItemsToNextLevel(playerUUID);
                        int nextRequired = multiplierManager.getNextLevelRequirement(playerUUID);
                        int sold = multiplierManager.getSoldItems(playerUUID);

                        for (String line : formatLines) {
                            finalProcessedLore.add(plugin.getConfigManager().colorize(line
                                .replace("%level%", String.valueOf(currentLevel))
                                .replace("%multiplier%", String.format("%.2f", multiplier))
                                .replace("%sold%", String.valueOf(sold))
                                .replace("%required%", String.valueOf(nextRequired))
                                .replace("%needed%", String.valueOf(needed))
                            ));
                        }
                    }
                    meta.setLore(finalProcessedLore);
                    item.setItemMeta(meta);
                }
            }
            if (slot < size) inv.setItem(slot, item);
        } catch (Exception ignored) {}
    }

    public void openCatalogMenu(Player player) {
        FileConfiguration categoriesConfig = plugin.getConfigManager().getCategoriesConfig();
        if (categoriesConfig.getBoolean("settings.enabled", true)) {
            openCategoriesMenu(player);
            return;
        }
        openItemsMenu(player, null);
    }

    public void openCategoriesMenu(Player player) {
        openCategoriesMenu(player, null);
    }

    public void openCategoriesMenu(Player player, String selectedCategory) {
        plugin.getConfigManager().reloadConfigs();
        FileConfiguration config = plugin.getConfigManager().getCategoriesConfig();
        FileConfiguration menusConfig = plugin.getConfigManager().getMenusConfig();
        ConfigurationSection menuSection = menusConfig.getConfigurationSection("categories_menu");
        
        String title = plugin.getConfigManager().colorize(config.getString("settings.title", "#FF0000Выбор категории"));
        int size = config.getInt("settings.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Decoration from menus.yml and categories.yml
        if (config.contains("settings.decoration")) {
            renderDecoration(inv, config.getMapList("settings.decoration"), size);
        }
        if (menuSection != null && menuSection.contains("decoration")) {
            renderDecoration(inv, menuSection.getMapList("decoration"), size);
        }

        // Functional buttons from menus.yml items section
        if (menuSection != null) {
            ConfigurationSection items = menuSection.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    addMenuButton(inv, player, items, key, size);
                }
            }
        }

        // Categories from categories.yml
        ConfigurationSection categories = config.getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                // Skip if slot is already occupied by a custom button or decoration
                ConfigurationSection cat = categories.getConfigurationSection(key);
                int slot = cat.getInt("slot", 0);
                if (slot < size && inv.getItem(slot) != null) {
                    ItemStack existing = inv.getItem(slot);
                    if (existing != null && isButton(existing)) continue;
                }

                Material mat = Material.valueOf(cat.getString("material", "STONE"));
                String name = cat.getString("name", "Category");
                List<String> lore = cat.getStringList("lore");
                // ... rest of category logic ...

                // Top stats for category
                String topItemName = "Нет";
                int topAmount = 0;
                List<String> catMaterials = cat.getStringList("items");
                Map<Material, Integer> stats = buyerManager.getItemStats();
                
                for (String matKey : catMaterials) {
                    try {
                        Material m = Material.valueOf(matKey);
                        int amount = stats.getOrDefault(m, 0);
                        if (amount > topAmount) {
                            topAmount = amount;
                            topItemName = plugin.getConfig().getString("item-names." + m.name(), m.name());
                        }
                    } catch (Exception ignored) {}
                }

                List<String> finalLore = new ArrayList<>();
                for (String line : lore) {
                    finalLore.add(plugin.getConfigManager().colorize(line
                        .replace("%top_item%", topItemName)
                        .replace("%top_amount%", String.valueOf(topAmount))));
                }

                if (key.equals(selectedCategory)) {
                    finalLore.add("");
                    finalLore.add(plugin.getConfigManager().colorize("#00FF00► ВЫБРАНО"));
                }

                ItemStack item = plugin.getConfigManager().createGuiItem(mat, name, finalLore, null);
                if (slot < size) inv.setItem(slot, item);
            }
        }

        // Show items preview if selected
        if (selectedCategory != null) {
            ConfigurationSection cat = categories.getConfigurationSection(selectedCategory);
            List<String> materials = cat.getStringList("items");
            List<Integer> displaySlots = config.getIntegerList("settings.preview.slots");
            if (displaySlots.isEmpty()) {
                displaySlots = Arrays.asList(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
            }
            
            int itemIdx = 0;
            for (String mKey : materials) {
                if (itemIdx >= displaySlots.size()) break;
                try {
                    Material mat = Material.valueOf(mKey);
                    if (!buyerManager.isBuyable(mat)) continue;
                    
                    int slot = displaySlots.get(itemIdx++);
                    if (slot >= size || inv.getItem(slot) != null) continue;

                    double price = buyerManager.getPriceWithMultiplier(mat, player.getUniqueId());
                    String displayName = plugin.getConfig().getString("item-names." + mat.name(), mat.name());
                    
                    FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
                    double itemPoints = pointsConfig.contains("items." + mat.name()) ? 
                        pointsConfig.getDouble("items." + mat.name()) : 
                        price * pointsConfig.getDouble("default.ratio-per-money", 0.05);
                    double finalPoints = itemPoints * Math.max(0, pointsConfig.getDouble("settings.global-multiplier", 1.0));

                    String limitStr = "";
                    if (plugin.getConfig().getBoolean("limits.display.enabled", true)) {
                        int limit = buyerManager.getLimit(mat);
                        int stock = buyerManager.getStock(mat);
                        String infinity = plugin.getConfigManager().colorize(plugin.getConfig().getString("limits.display.infinity-color", "#00FF00") + 
                                         plugin.getConfig().getString("limits.display.infinity-symbol", "∞"));
                        String limitVal = (limit == Integer.MAX_VALUE) ? infinity : String.valueOf(limit);
                        limitStr = plugin.getConfigManager().colorize(plugin.getConfig().getString("limits.display.format", "#AAAAAAЛимит: %stock%/%limit%")
                                  .replace("%limit%", limitVal)
                                  .replace("%stock%", String.valueOf(stock)));
                    }
                    
                    boolean autoSellGlobal = buyerManager.isAutoSellEnabled(player.getUniqueId());
                    boolean inWL = buyerManager.isInPlayerWhitelist(player.getUniqueId(), mat);
                    
                    String prefix;
                    if (!autoSellGlobal) {
                        prefix = plugin.getConfig().getString("auto-sell.display.prefix.none", "");
                    } else {
                        prefix = inWL ? 
                            plugin.getConfig().getString("auto-sell.display.prefix.enabled", "#00FF00[✓] ") : 
                            plugin.getConfig().getString("auto-sell.display.prefix.disabled", "#FF0000[✗] ");
                    }
                    String statusPrefix = plugin.getConfigManager().colorize(prefix);
                    
                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getConfigManager().colorize("#FFFF00Ваша цена: #00FF00" + String.format("%.2f", price) + " монет"));
                    lore.add(plugin.getConfigManager().colorize("#FFFF00Стоимость: #00FF00" + String.format("%.2f", finalPoints) + " очков"));
                    if (!limitStr.isEmpty()) lore.add(limitStr);
                    
                    if (autoSellGlobal || plugin.getConfig().getBoolean("auto-sell.show-lore-when-disabled", false)) {
                        List<String> statusLore = inWL ? 
                            plugin.getConfig().getStringList("auto-sell.display.lore.enabled") : 
                            plugin.getConfig().getStringList("auto-sell.display.lore.disabled");
                        
                        for (String line : statusLore) {
                            lore.add(plugin.getConfigManager().colorize(line));
                        }
                    }
                    
                    ItemStack item = plugin.getConfigManager().createGuiItem(mat, statusPrefix + plugin.getConfigManager().colorize(displayName), lore, null);
                    inv.setItem(slot, item);
                } catch (Exception ignored) {}
            }
        }

        // Back button
        int backSlot = (menuSection != null) ? menuSection.getInt("back_button.slot", 49) : 49;
        if (inv.getItem(backSlot) == null) {
            ItemStack back = plugin.getConfigManager().createGuiItem(Material.BARRIER, "#FF0000Назад", new ArrayList<>(), null);
            inv.setItem(backSlot, back);
        }

        player.openInventory(inv);
    }

    private void renderDecoration(Inventory inv, List<Map<?, ?>> decorationList, int size) {
        if (decorationList == null) return;
        for (Map<?, ?> decoration : decorationList) {
            try {
                Material material = Material.valueOf((String) decoration.get("material"));
                String name = (String) decoration.get("name");
                String base64 = (String) decoration.get("texture");
                List<Integer> slots = (List<Integer>) decoration.get("slots");
                
                ItemStack item = plugin.getConfigManager().createGuiItem(material, name, new ArrayList<>(), base64);
                for (int slot : slots) {
                    if (slot < size) inv.setItem(slot, item);
                }
            } catch (Exception ignored) {}
        }
    }

    public void openItemsMenu(Player player, String categoryKey) {
        openItemsMenu(player, categoryKey, 0);
    }

    public void openItemsMenu(Player player, String categoryKey, int page) {
        plugin.getConfigManager().reloadConfigs();
        FileConfiguration menusConfig = plugin.getConfigManager().getMenusConfig();
        ConfigurationSection catalogSection = menusConfig.getConfigurationSection("catalog_menu");
        if (catalogSection == null) return;

        String title = plugin.getConfigManager().colorize(catalogSection.getString("title", "#FF0000Ассортимент"));
        if (page > 0) title += " (Стр. " + (page + 1) + ")";
        
        int size = catalogSection.getInt("size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Render standard menu components (items, decoration, back_button)
        renderDecoration(inv, catalogSection.getMapList("decoration"), size);
        
        ConfigurationSection menuItems = catalogSection.getConfigurationSection("items");
        if (menuItems != null) {
            for (String key : menuItems.getKeys(false)) {
                addMenuButton(inv, player, menuItems, key, size);
            }
        }

        if (catalogSection.contains("back_button")) {
            ConfigurationSection backBtn = catalogSection.getConfigurationSection("back_button");
            inv.setItem(backBtn.getInt("slot", 49), plugin.getConfigManager().createGuiItem(
                Material.valueOf(backBtn.getString("material", "BARRIER")), 
                backBtn.getString("name", "#FF0000Назад"), new ArrayList<>(), null));
        }

        List<Material> allItems = new ArrayList<>();
        if (categoryKey != null) {
            List<String> materials = plugin.getConfigManager().getCategoriesConfig().getStringList("categories." + categoryKey + ".items");
            for (String m : materials) {
                try { 
                    Material mat = Material.valueOf(m);
                    if (buyerManager.isBuyable(mat)) allItems.add(mat); 
                } catch (Exception ignored) {}
            }
        } else {
            for (Material mat : buyerManager.getBuyPrices().keySet()) {
                if (buyerManager.isBuyable(mat)) allItems.add(mat);
            }
        }

        int itemsPerPage = catalogSection.getInt("items_per_page", 45);
        int totalPages = (int) Math.ceil((double) allItems.size() / itemsPerPage);
        
        int startIdx = page * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, allItems.size());

        String displayFormat = catalogSection.getString("item_display.name", "#00FFFF%name%");
        List<String> loreFormat = catalogSection.getStringList("item_display.lore");
        
        int slotIdx = 0;
        for (int i = startIdx; i < endIdx; i++) {
            Material mat = allItems.get(i);
            // Skip if slot is already occupied by a button or decoration or out of bounds
            while (slotIdx < size && (inv.getItem(slotIdx) != null)) slotIdx++;
            if (slotIdx >= size) break;

            double finalPrice = buyerManager.getPriceWithMultiplier(mat, player.getUniqueId());
            String displayName = plugin.getConfig().getString("item-names." + mat.name(), mat.name());
            
            FileConfiguration pointsConfig = plugin.getConfigManager().getPointsConfig();
            double itemPoints = pointsConfig.contains("items." + mat.name()) ? 
                pointsConfig.getDouble("items." + mat.name()) : 
                finalPrice * pointsConfig.getDouble("default.ratio-per-money", 0.05);
            double finalPoints = itemPoints * Math.max(0, pointsConfig.getDouble("settings.global-multiplier", 1.0));

            boolean autoSellGlobal = buyerManager.isAutoSellEnabled(player.getUniqueId());
            boolean inWL = buyerManager.isInPlayerWhitelist(player.getUniqueId(), mat);
            
            String prefix;
            if (!autoSellGlobal) {
                prefix = plugin.getConfig().getString("auto-sell.display.prefix.none", "");
            } else {
                prefix = inWL ? 
                    plugin.getConfig().getString("auto-sell.display.prefix.enabled", "#00FF00[✓] ") : 
                    plugin.getConfig().getString("auto-sell.display.prefix.disabled", "#FF0000[✗] ");
            }
            String statusPrefix = plugin.getConfigManager().colorize(prefix);

            List<String> itemLore = new ArrayList<>();
            String limitStr = "";
            if (plugin.getConfig().getBoolean("limits.display.enabled", true)) {
                int limit = buyerManager.getLimit(mat);
                String infinity = plugin.getConfigManager().colorize(plugin.getConfig().getString("limits.display.infinity-color", "#00FF00") + 
                                 plugin.getConfig().getString("limits.display.infinity-symbol", "∞"));
                String limitVal = (limit == Integer.MAX_VALUE) ? infinity : String.valueOf(limit);
                limitStr = plugin.getConfigManager().colorize(plugin.getConfig().getString("limits.display.format", "#AAAAAAЛимит: %limit%")
                          .replace("%limit%", limitVal));
            }

            for (String line : loreFormat) {
                itemLore.add(plugin.getConfigManager().colorize(line
                                 .replace("%price%", String.format("%.2f", finalPrice))
                                 .replace("%points%", String.format("%.2f", finalPoints))
                                 .replace("%name%", displayName)
                                 .replace("%stock%", String.valueOf(buyerManager.getStock(mat)))
                                 .replace("%limit%", limitStr)
                                 .replace("%id%", mat.name().toLowerCase())));
            }
            
            if (autoSellGlobal || plugin.getConfig().getBoolean("auto-sell.show-lore-when-disabled", false)) {
                List<String> statusLore = inWL ? 
                    plugin.getConfig().getStringList("auto-sell.display.lore.enabled") : 
                    plugin.getConfig().getStringList("auto-sell.display.lore.disabled");
                
                for (String line : statusLore) {
                    itemLore.add(plugin.getConfigManager().colorize(line));
                }
            }
            
            ItemStack item = plugin.getConfigManager().createGuiItem(mat, statusPrefix + displayFormat.replace("%name%", displayName), itemLore, null);
            inv.setItem(slotIdx++, item);
        }

        // Pagination buttons
        if (page > 0) {
            inv.setItem(45, plugin.getConfigManager().createGuiItem(Material.ARROW, "#FFFF00Предыдущая страница", new ArrayList<>(), null));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, plugin.getConfigManager().createGuiItem(Material.ARROW, "#FFFF00Следующая страница", new ArrayList<>(), null));
        }

        player.openInventory(inv);
    }

    public void openAutoSellSettingsMenu(Player player) {
        openAutoSellSettingsMenu(player, 0);
    }

    public void openAutoSellSettingsMenu(Player player, int page) {
        plugin.getConfigManager().reloadConfigs();
        FileConfiguration config = plugin.getConfigManager().getMenusConfig();
        ConfigurationSection menuSection = config.getConfigurationSection("autosell_menu");
        if (menuSection == null) return;

        String title = plugin.getConfigManager().colorize(menuSection.getString("title", "#FFD700Настройки авто-продажи"));
        if (page > 0) title += " (Стр. " + (page + 1) + ")";
        
        int size = menuSection.getInt("size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Decoration from menus.yml
        renderDecoration(inv, menuSection.getMapList("decoration"), size);

        // Load custom items from menus.yml if present
        ConfigurationSection items = menuSection.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                addMenuButton(inv, player, items, key, size);
            }
        }

        // Toggle
        int toggleSlot = menuSection.getInt("toggle_button.slot", 4);
        if (inv.getItem(toggleSlot) == null) {
            boolean enabled = buyerManager.isAutoSellEnabled(player.getUniqueId());
            ItemStack toggle = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta tMeta = toggle.getItemMeta();
            if (tMeta != null) {
                tMeta.setDisplayName(plugin.getConfigManager().colorize(enabled ? "#00FF00Авто-продажа ВКЛ" : "#FF0000Авто-продажа ВЫКЛ"));
                toggle.setItemMeta(tMeta);
            }
            inv.setItem(toggleSlot, toggle);
        }

        // Functional buttons (Select All, Deselect All, Back) - only if slots are free
        if (menuSection.contains("select_all") && inv.getItem(menuSection.getInt("select_all.slot")) == null)
            setupButton(inv, menuSection.getConfigurationSection("select_all"));
        if (menuSection.contains("deselect_all") && inv.getItem(menuSection.getInt("deselect_all.slot")) == null)
            setupButton(inv, menuSection.getConfigurationSection("deselect_all"));
        if (menuSection.contains("back_button") && inv.getItem(menuSection.getInt("back_button.slot")) == null)
            setupButton(inv, menuSection.getConfigurationSection("back_button"));

        // Items in whitelist with pagination (Sync with current rotation/assortment)
        List<Integer> slots = menuSection.getIntegerList("item_slots");
        List<Material> allMaterials = new ArrayList<>();
        
        // Sync with current buyable items (current rotation)
        for (Material mat : buyerManager.getBuyPrices().keySet()) {
            if (buyerManager.isBuyable(mat)) {
                allMaterials.add(mat);
            }
        }
        
        int itemsPerPage = slots.size();
        int totalPages = (int) Math.ceil((double) allMaterials.size() / itemsPerPage);
        
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allMaterials.size());

        for (int i = start; i < end; i++) {
            Material mat = allMaterials.get(i);
            int slotIdx = i - start;
            if (slotIdx >= slots.size()) break;
            
            int slot = slots.get(slotIdx);
            if (slot >= size || inv.getItem(slot) != null) continue;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                boolean inWL = buyerManager.isInPlayerWhitelist(player.getUniqueId(), mat);
                String russianName = plugin.getConfig().getString("item-names." + mat.name(), mat.name());
                meta.setDisplayName(plugin.getConfigManager().colorize((inWL ? "#00FF00✓ " : "#FF0000✗ ") + russianName));
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        // Pagination buttons
        if (page > 0) {
            ItemStack prev = plugin.getConfigManager().createGuiItem(Material.ARROW, "#FFFF00Предыдущая страница", new ArrayList<>(), null);
            inv.setItem(45, prev); 
        }
        if (page < totalPages - 1 && totalPages > 1) {
            ItemStack next = plugin.getConfigManager().createGuiItem(Material.ARROW, "#FFFF00Следующая страница", new ArrayList<>(), null);
            inv.setItem(53, next);
        }

        // Back button (Force set to slot 49 if not already set or for reliability)
        int backSlot = menuSection.getInt("back_button.slot", 49);
        ItemStack backItem = plugin.getConfigManager().createGuiItem(Material.BARRIER, "#FF0000Назад", new ArrayList<>(), null);
        inv.setItem(backSlot, backItem);

        player.openInventory(inv);
    }

    private void setupButton(Inventory inv, ConfigurationSection section) {
        if (section == null) return;
        try {
            ItemStack item = new ItemStack(Material.valueOf(section.getString("material")));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getConfigManager().colorize(section.getString("name")));
                item.setItemMeta(meta);
            }
            inv.setItem(section.getInt("slot"), item);
        } catch (Exception ignored) {}
    }

    private boolean isButton(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.isEmpty() || name.equals(" ")) return true;
        
        FileConfiguration menuConfig = plugin.getConfigManager().getMenusConfig();
        for (String sectionName : menuConfig.getKeys(false)) {
            ConfigurationSection items = menuConfig.getConfigurationSection(sectionName + ".items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    String btnName = ChatColor.stripColor(plugin.getConfigManager().colorize(items.getString(key + ".name")));
                    if (name.equals(btnName)) return true;
                }
            }
            List<Map<?, ?>> decorations = menuConfig.getMapList(sectionName + ".decoration");
            for (Map<?, ?> decor : decorations) {
                String decorName = ChatColor.stripColor(plugin.getConfigManager().colorize((String) decor.get("name")));
                if (name.equals(decorName)) return true;
            }
        }
        return false;
    }

    public void openShopMenu(Player player) {
        // Shop is already configurable in shop.yml, keeping its logic
        plugin.getShopManager().openShop(player);
    }
}
