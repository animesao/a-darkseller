
package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BoosterGUI {
    
    private Main plugin;
    private MultiplierManager multiplierManager;
    
    public BoosterGUI(Main plugin, MultiplierManager multiplierManager) {
        this.plugin = plugin;
        this.multiplierManager = multiplierManager;
    }
    
    public void openBoosterMenu(Player player) {
        FileConfiguration config = plugin.getConfigManager().getBoostersConfig();
        String title = plugin.getConfigManager().colorize(config.getString("gui.title", "#FFD700Магазин Бустеров"));
        int size = config.getInt("gui.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        // Декорации
        if (config.contains("gui.decoration")) {
            for (Map<?, ?> decoration : config.getMapList("gui.decoration")) {
                try {
                    Material material = Material.valueOf((String) decoration.get("material"));
                    String name = (String) decoration.get("name");
                    List<Integer> slots = (List<Integer>) decoration.get("slots");
                    ItemStack item = plugin.getConfigManager().createGuiItem(material, name, new ArrayList<>(), null);
                    for (int slot : slots) {
                        if (slot < size) inv.setItem(slot, item);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Бустеры из конфига
        ConfigurationSection boosters = config.getConfigurationSection("boosters");
        if (boosters != null) {
            for (String key : boosters.getKeys(false)) {
                int slot = boosters.getInt(key + ".slot");
                Material material = Material.valueOf(boosters.getString(key + ".material", "GOLD_INGOT"));
                String name = boosters.getString(key + ".name");
                List<String> lore = boosters.getStringList(key + ".lore");
                
                ItemStack item = plugin.getConfigManager().createGuiItem(material, name, lore, null);
                inv.setItem(slot, item);
            }
        }

        // Кнопка назад
        ConfigurationSection back = config.getConfigurationSection("gui.back_button");
        if (back != null) {
            try {
                int slot = back.getInt("slot", 49);
                Material material = Material.valueOf(back.getString("material", "BARRIER"));
                String name = back.getString("name", "&cНазад");
                ItemStack item = plugin.getConfigManager().createGuiItem(material, name, new ArrayList<>(), null);
                inv.setItem(slot, item);
            } catch (Exception ignored) {}
        }

        player.openInventory(inv);
    }

    public void buyBooster(Player player, int slot) {
        FileConfiguration config = plugin.getConfigManager().getBoostersConfig();
        ConfigurationSection boosters = config.getConfigurationSection("boosters");
        if (boosters == null) return;

        for (String key : boosters.getKeys(false)) {
            if (boosters.getInt(key + ".slot") == slot) {
                String permission = boosters.getString(key + ".permission");
                if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                    player.sendMessage(plugin.getConfigManager().colorize("&cУ вас нет прав для покупки этого бустера!"));
                    return;
                }

                int cost = boosters.getInt(key + ".cost");
                if (takeCurrency(player, cost)) {
                    double mult = boosters.getDouble(key + ".multiplier");
                    int durationMinutes = boosters.getInt(key + ".duration_minutes", 0);
                    int durationDays = boosters.getInt(key + ".duration_days", 0);
                    
                    int totalMinutes = durationMinutes + (durationDays * 24 * 60);
                    plugin.getMultiplierManager().addBooster(player.getUniqueId(), mult, totalMinutes);
                    
                    String durationStr = durationDays > 0 ? durationDays + " дней" : durationMinutes + " мин.";
                    
                    player.sendMessage(plugin.getConfigManager().colorize("&aВы успешно купили " + boosters.getString(key + ".name") + " на " + durationStr + "!"));
                } else {
                    player.sendMessage(plugin.getConfigManager().colorize("&cНедостаточно осколков для покупки!"));
                }
                return;
            }
        }
    }

    private boolean takeCurrency(Player player, int amount) {
        FileConfiguration config = plugin.getConfigManager().getBoostersConfig();
        Material currencyMat = Material.valueOf(config.getString("currency.material", "NETHER_STAR"));
        String currencyName = plugin.getConfigManager().colorize(config.getString("currency.name"));
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currencyMat) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(currencyName)) {
                    count += item.getAmount();
                }
            }
        }

        if (count < amount) return false;

        int toRemove = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currencyMat) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(currencyName)) {
                    int itemAmount = item.getAmount();
                    if (itemAmount > toRemove) {
                        item.setAmount(itemAmount - toRemove);
                        break;
                    } else {
                        player.getInventory().remove(item);
                        toRemove -= itemAmount;
                        if (toRemove <= 0) break;
                    }
                }
            }
        }
        return true;
    }
}
