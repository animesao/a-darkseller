
package com.buyerplugin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.util.Map;
import java.util.List;

public class BuyerAPI {
    private static Main plugin;

    public static void init(Main instance) {
        plugin = instance;
    }

    public static double getPlayerMultiplier(UUID uuid) {
        return plugin.getMultiplierManager().getMultiplier(uuid);
    }

    public static int getPlayerLevel(UUID uuid) {
        return plugin.getMultiplierManager().getLevel(uuid);
    }

    public static double getPoints(UUID uuid) {
        return plugin.getShopManager().getPoints(uuid);
    }

    public static void addPoints(UUID uuid, double amount) {
        plugin.getShopManager().addPoints(uuid, amount);
    }

    public static Map<Material, Double> getActiveAssortment() {
        return plugin.getBuyerManager().getBuyPrices();
    }
    
    public static double getItemPrice(Material material) {
        return plugin.getBuyerManager().getPrice(material);
    }

    public static double getPriceWithMultiplier(Material material, UUID uuid) {
        return plugin.getBuyerManager().getPriceWithMultiplier(material, uuid);
    }

    public static boolean isAutoSellEnabled(UUID uuid) {
        return plugin.getBuyerManager().isAutoSellEnabled(uuid);
    }

    public static void setAutoSell(UUID uuid, boolean enabled) {
        plugin.getBuyerManager().setAutoSell(uuid, enabled);
    }

    public static void addCustomItem(String id, double price, String displayName) {
        plugin.getBuyerManager().addCustomItem(id, price, displayName);
    }

    public static void addCustomItem(String id, double price, String displayName, List<String> lore) {
        plugin.getBuyerManager().addCustomItem(id, price, displayName, lore);
    }

    public static boolean isCustomItem(ItemStack item) {
        return plugin.getBuyerManager().getCustomItemId(item) != null;
    }
}
