
package com.buyerplugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuyerListener implements Listener {

    private Main plugin;
    private BuyerManager buyerManager;
    private MultiplierManager multiplierManager;
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    public BuyerListener(Main plugin, BuyerManager buyerManager, MultiplierManager multiplierManager) {
        this.plugin = plugin;
        this.buyerManager = buyerManager;
        this.multiplierManager = multiplierManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle);
        UUID playerUUID = player.getUniqueId();
        FileConfiguration menuConfig = plugin.getConfigManager().getMenusConfig();

        // Shop logic
        String shopTitle = colorize(plugin.getShopManager().getShopConfig().getString("title", "#FFD700Магазин Скупщика"));
        if (rawTitle.equals(shopTitle)) {
            event.setCancelled(true);
            handleShopClick(player, event.getRawSlot());
            return;
        }

        // Dynamic Menu Handling
        if (title.contains("Скупщик") || title.contains("Ассортимент") || title.contains("Выбор категории") || title.contains("Настройки авто-продажи")) {
            int slot = event.getRawSlot();
            Inventory clickedInventory = event.getClickedInventory();
            
            // Если клик был по инвентарю игрока
            if (clickedInventory != null && clickedInventory.equals(player.getInventory())) {
                // В главном меню разрешаем взаимодействие с вещами в инвентаре игрока
                if (title.contains("Скупщик") && !title.contains("Магазин") && !title.contains("Ассортимент") && !title.contains("Настройки")) {
                    return;
                }
                // В остальных меню отменяем клики по инвентарю (предотвращаем продажу через Shift-click)
                event.setCancelled(true);
                return;
            }

            // Проверка на декорации (стеклянные панели и т.д.)
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && (clickedItem.getType().name().contains("GLASS_PANE") || clickedItem.getType() == Material.BARRIER)) {
                event.setCancelled(true);
                return;
            }

            // Если клик по верхней части меню (слоты 0-44 для размера 54) в главном меню
            if (title.contains("Скупщик") && !title.contains("Ассортимент") && !title.contains("Магазин") && !title.contains("Настройки")) {
                if (slot >= 0 && slot < 45) {
                    return; // Разрешаем класть/забирать предметы для продажи
                }
            }
            
            event.setCancelled(true);
            
            // Обработка общих кнопок из конфига для всех меню
            ConfigurationSection[] sections = {
                menuConfig.getConfigurationSection("main_menu.items"),
                menuConfig.getConfigurationSection("catalog_menu.items"),
                menuConfig.getConfigurationSection("categories_menu.items"),
                menuConfig.getConfigurationSection("autosell_menu.items")
            };

            for (ConfigurationSection section : sections) {
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        if (section.getInt(key + ".slot") == slot) {
                            handleSharedButtonClick(player, key, event);
                            return;
                        }
                    }
                }
            }

            // 1. ПЕРВООЧЕРЕДНАЯ ПРОВЕРКА КНОПОК НАВИГАЦИИ (НАЗАД)
            if (title.contains("Выбор категории")) {
                // Больше нет кнопки назад в категориях по слоту 31
            }

            // Специфичная логика для меню
            if (title.contains("Выбор категории")) {
                FileConfiguration catConfig = plugin.getConfigManager().getCategoriesConfig();
                ConfigurationSection categories = catConfig.getConfigurationSection("categories");
                
                // Обработка кликов по категориям
                if (categories != null) {
                    for (String key : categories.getKeys(false)) {
                        if (categories.getInt(key + ".slot") == slot) {
                            if (event.isRightClick()) {
                                new BuyerGUI(plugin, buyerManager, multiplierManager).openItemsMenu(player, key);
                            } else {
                                new BuyerGUI(plugin, buyerManager, multiplierManager).openCategoriesMenu(player, key);
                            }
                            return;
                        }
                    }
                }

                // Обработка кликов по предметам в превью (выбор для авто-скупки)
                List<Integer> displaySlots = catConfig.getIntegerList("settings.preview.slots");
                if (displaySlots.isEmpty()) {
                    displaySlots = java.util.Arrays.asList(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
                }
                
                // Если слот в списке превью, НО это не кнопка "Назад"
                if (displaySlots.contains(slot) && slot != 31) {
                    if (clickedItem != null && clickedItem.getType() != Material.AIR && !isButton(clickedItem)) {
                        buyerManager.toggleAutoSellItem(player.getUniqueId(), clickedItem.getType());
                        
                        String selectedCat = null;
                        if (categories != null) {
                            for (String catKey : categories.getKeys(false)) {
                                ItemStack catItem = event.getClickedInventory().getItem(categories.getInt(catKey + ".slot"));
                                if (catItem != null && catItem.hasItemMeta() && catItem.getItemMeta().hasLore()) {
                                    for (String line : catItem.getItemMeta().getLore()) {
                                        if (ChatColor.stripColor(line).contains("ВЫБРАНО")) {
                                            selectedCat = catKey;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        new BuyerGUI(plugin, buyerManager, multiplierManager).openCategoriesMenu(player, selectedCat);
                    }
                    return;
                }
            } else if (title.contains("Ассортимент")) {
                handleCatalogPageClick(player, slot, event);
                return;
            } else if (title.contains("Настройки авто-продажи")) {
                handleAutoSellClick(player, slot, menuConfig.getConfigurationSection("autosell_menu"), event);
                return;
            }
            return;
        }

        if (title.contains("Магазин Бустеров")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            FileConfiguration boosterConfig = plugin.getConfigManager().getBoostersConfig();
            
            // Back button check
            if (boosterConfig.contains("gui.back_button") && slot == boosterConfig.getInt("gui.back_button.slot", 49)) {
                new BuyerGUI(plugin, buyerManager, multiplierManager).openMainMenu(player);
                return;
            }
            
            plugin.getBoosterGUI().buyBooster(player, slot);
            return;
        }

        if (title.contains("Управление бустерами")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 49 && player.hasPermission("buyer.admin")) {
                player.sendMessage(plugin.getConfigManager().colorize("#FFFF00Режим настройки бустеров в разработке. Используйте config.yml."));
            }
            return;
        }
    }

    private void handleMainMenuClick(Player player, int slot, ConfigurationSection section, InventoryClickEvent event) {
        if (section == null) return;
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            if (items.getInt(key + ".slot") == slot) {
                handleSharedButtonClick(player, key, event);
                return;
            }
        }
    }

    private void handleSharedButtonClick(Player player, String key, InventoryClickEvent event) {
        BuyerGUI gui = new BuyerGUI(plugin, buyerManager, multiplierManager);
        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle);
        int slot = event.getRawSlot();
        
        // Dynamic search for button key by slot in the current menu config
        FileConfiguration menuConfig = plugin.getConfigManager().getMenusConfig();
        String currentMenuSection = null;
        if (title.contains("Скупщик") && !title.contains("Ассортимент")) currentMenuSection = "main_menu";
        else if (title.contains("Ассортимент")) currentMenuSection = "catalog_menu";
        else if (title.contains("Выбор категории")) currentMenuSection = "categories_menu";
        else if (title.contains("Настройки авто-продажи")) currentMenuSection = "autosell_menu";

        String actualKey = key;
        if (currentMenuSection != null) {
            ConfigurationSection items = menuConfig.getConfigurationSection(currentMenuSection + ".items");
            if (items != null) {
                for (String itemKey : items.getKeys(false)) {
                    if (items.getInt(itemKey + ".slot") == slot) {
                        actualKey = itemKey;
                        break;
                    }
                }
            }
        }

        // Execute custom commands if defined
        if (currentMenuSection != null) {
            ConfigurationSection items = menuConfig.getConfigurationSection(currentMenuSection + ".items");
            if (items != null && items.contains(actualKey + ".commands")) {
                for (String cmd : items.getStringList(actualKey + ".commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }
            }
        }

        switch (actualKey) {
            case "catalog": gui.openCatalogMenu(player); break;
            case "shop": gui.openShopMenu(player); break;
            case "sell": sellItems(player, player.getOpenInventory().getTopInventory()); break;
            case "auto_sell": 
                buyerManager.toggleAutoSell(player.getUniqueId());
                if (title.contains("Ассортимент")) gui.openItemsMenu(player, null);
                else if (title.contains("Выбор категории")) gui.openCategoriesMenu(player);
                else gui.openMainMenu(player);
                break;
            case "boosters": plugin.getBoosterGUI().openBoosterMenu(player); break;
            case "back_button":
                if (title.contains("Выбор категории")) gui.openMainMenu(player);
                else if (title.contains("Ассортимент")) {
                    if (plugin.getConfigManager().getCategoriesConfig().getBoolean("settings.enabled", true)) {
                        gui.openCategoriesMenu(player);
                    } else {
                        gui.openMainMenu(player);
                    }
                }
                break;
        }
    }

    private void handleCatalogClick(Player player, int slot, ConfigurationSection section) {
        if (section == null) return;
        if (slot == section.getInt("back_button.slot")) {
            new BuyerGUI(plugin, buyerManager, multiplierManager).openMainMenu(player);
        }
    }

    private void handleCatalogPageClick(Player player, int slot, InventoryClickEvent event) {
        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle);
        int page = 0;
        if (title.contains("Стр. ")) {
            try {
                page = Integer.parseInt(title.split("Стр. ")[1].replace(")", "")) - 1;
            } catch (Exception ignored) {}
        }
        
        FileConfiguration menuConfig = plugin.getConfigManager().getMenusConfig();
        int backBtnSlot = menuConfig.getInt("catalog_menu.items.back_button.slot", 49);

        BuyerGUI gui = new BuyerGUI(plugin, buyerManager, multiplierManager);
        if (slot == backBtnSlot) {
            if (plugin.getConfigManager().getCategoriesConfig().getBoolean("settings.enabled", true)) {
                gui.openCategoriesMenu(player);
            } else {
                gui.openMainMenu(player);
            }
            return;
        } else if (slot == 45 && page > 0) {
            gui.openItemsMenu(player, null, page - 1);
        } else if (slot == 53) {
            gui.openItemsMenu(player, null, page + 1);
        } else {
            // Logic for toggling auto-sell item directly from catalog
            Inventory inv = event.getInventory();
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR && !isButton(item)) {
                buyerManager.toggleAutoSellItem(player.getUniqueId(), item.getType());
                gui.openItemsMenu(player, null, page);
            }
        }
    }

    private void handleAutoSellClick(Player player, int slot, ConfigurationSection section, InventoryClickEvent event) {
        if (section == null) return;
        UUID uuid = player.getUniqueId();
        BuyerGUI gui = new BuyerGUI(plugin, buyerManager, multiplierManager);
        
        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle);
        int page = 0;
        if (title.contains("Стр. ")) {
            try {
                page = Integer.parseInt(title.split("Стр. ")[1].replace(")", "")) - 1;
            } catch (Exception ignored) {}
        }

        List<Material> allMaterials = new ArrayList<>();
        // Sync with current buyable items (current rotation)
        for (Material mat : buyerManager.getBuyPrices().keySet()) {
            if (buyerManager.isBuyable(mat)) {
                allMaterials.add(mat);
            }
        }

        List<Integer> itemSlots = section.getIntegerList("item_slots");
        int itemsPerPage = itemSlots.size();
        int totalPages = (int) Math.ceil((double) allMaterials.size() / itemsPerPage);

        if (slot == section.getInt("toggle_button.slot")) {
            buyerManager.toggleAutoSell(uuid);
            gui.openAutoSellSettingsMenu(player, page);
        } else if (slot == section.getInt("select_all.slot")) {
            buyerManager.selectAllItemsForAutoSell(uuid);
            gui.openAutoSellSettingsMenu(player, page);
        } else if (slot == section.getInt("deselect_all.slot")) {
            buyerManager.deselectAllItemsForAutoSell(uuid);
            gui.openAutoSellSettingsMenu(player, page);
        } else if (slot == 49 || slot == section.getInt("back_button.slot", 49)) {
            gui.openMainMenu(player);
        } else if (slot == 45 && page > 0) {
            gui.openAutoSellSettingsMenu(player, page - 1);
        } else if (slot == 53 && page < totalPages - 1) {
            gui.openAutoSellSettingsMenu(player, page + 1);
        } else if (itemSlots.contains(slot)) {
            int slotIdx = itemSlots.indexOf(slot);
            int materialIdx = (page * itemsPerPage) + slotIdx;
            if (materialIdx < allMaterials.size()) {
                Material mat = allMaterials.get(materialIdx);
                buyerManager.toggleAutoSellItem(uuid, mat);
                gui.openAutoSellSettingsMenu(player, page);
            }
        }
    }

    private String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group()).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    private void handleShopClick(Player player, int slot) {
        FileConfiguration shopConfig = plugin.getShopManager().getShopConfig();
        if (shopConfig == null || !shopConfig.contains("items")) return;
        
        for (String key : shopConfig.getConfigurationSection("items").getKeys(false)) {
            if (shopConfig.getInt("items." + key + ".slot") == slot) {
                double cost = shopConfig.getDouble("items." + key + ".cost");
                if (plugin.getShopManager().removePoints(player.getUniqueId(), cost)) {
                    List<String> commands = shopConfig.getStringList("items." + key + ".commands");
                    if (commands != null) {
                        for (String cmd : commands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                        }
                    }
                    String successMsg = shopConfig.getString("messages.buy-success", "&aВы успешно купили товар!");
                    player.sendMessage(colorize(successMsg));
                    new BuyerGUI(plugin, buyerManager, multiplierManager).openShopMenu(player);
                } else {
                    String noPointsMsg = shopConfig.getString("messages.no-points", "&cНедостаточно очков скупщика!");
                    player.sendMessage(colorize(noPointsMsg));
                }
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        String rawTitle = event.getView().getTitle();
        String title = ChatColor.stripColor(rawTitle);

        // При закрытии главного меню возвращаем предметы игроку
        if (title.contains("Скупщик") && !title.contains("Магазин") && !title.contains("Ассортимент") && !title.contains("Настройки")) {
            Inventory inv = event.getInventory();
            for (int i = 0; i < 45; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    // Проверяем, не является ли предмет кнопкой из конфига
                    if (isButton(item)) continue;

                    HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(item);
                    for (ItemStack drop : notAdded.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }
    }

    private boolean isButton(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.isEmpty() || name.equals(" ")) return true; // Декорации тоже считаем кнопками
        
        FileConfiguration menuConfig = plugin.getConfigManager().getMenusConfig();
        for (String sectionName : menuConfig.getKeys(false)) {
            ConfigurationSection items = menuConfig.getConfigurationSection(sectionName + ".items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    String btnName = ChatColor.stripColor(colorize(items.getString(key + ".name")));
                    if (name.equals(btnName)) return true;
                }
            }
            // Также проверяем декорации
            List<Map<?, ?>> decorations = menuConfig.getMapList(sectionName + ".decoration");
            for (Map<?, ?> decor : decorations) {
                String decorName = ChatColor.stripColor(colorize((String) decor.get("name")));
                if (name.equals(decorName)) return true;
            }
        }
        return false;
    }

    private void sellItems(Player player, Inventory inv) {
        String title = ChatColor.stripColor(player.getOpenInventory().getTitle());
        // Блокируем продажу, если открыто любое меню, кроме главного меню скупщика
        if (title.contains("Магазин") || title.contains("Ассортимент") || title.contains("Настройки") || title.contains("Выбор категории")) {
            return;
        }

        // Сначала продаем из GUI
        double totalMoney = 0;
        int totalItems = 0;
        double totalPoints = 0;
        UUID uuid = player.getUniqueId();

        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            if (buyerManager.isBuyable(item)) {
                int amount = item.getAmount();
                // Limits check for material (if not custom)
                int canBuy = amount;
                if (buyerManager.getCustomItemId(item) == null) {
                    canBuy = Math.min(amount, buyerManager.getRemainingStock(item.getType()));
                }
                
                if (canBuy > 0) {
                    double price = buyerManager.getPriceWithMultiplier(item, uuid);
                    totalMoney += price * canBuy;
                    totalItems += canBuy;
                    
                    String matName = item.getType().name();
                    double pointMult = plugin.getConfig().getDouble("points.per-item." + matName, 
                                      plugin.getConfig().getDouble("points.default-per-money", 0.1));
                    totalPoints += (price * canBuy) * pointMult;

                    if (buyerManager.getCustomItemId(item) == null) {
                        buyerManager.addStock(item.getType(), canBuy);
                    }
                    
                    if (canBuy == amount) {
                        inv.setItem(i, null);
                    } else {
                        item.setAmount(amount - canBuy);
                    }
                }
            }
        }

        if (totalItems > 0) {
            multiplierManager.addSoldItems(uuid, totalItems);
            plugin.getShopManager().addPoints(uuid, totalPoints);
            
            String cmd = plugin.getConfig().getString("economy.type", "money").equalsIgnoreCase("eco") ? "eco" : "money";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd + " give " + player.getName() + " " + totalMoney);
            
            player.sendMessage(plugin.getConfigManager().getMessage("sell-success")
                .replace("%amount%", String.valueOf(totalItems))
                .replace("%money%", String.format("%.2f", totalMoney)));
        } else {
            // Если в GUI пусто, пробуем продать всё из инвентаря игрока
            buyerManager.processSellAll(player);
        }
    }
}
