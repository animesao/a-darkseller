
package com.buyerplugin;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    
    private Main plugin;
    private FileConfiguration menus;
    private FileConfiguration boosters;
    private File menusFile;
    private File boostersFile;
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    
    private FileConfiguration points;
    private File pointsFile;
    private FileConfiguration categories;
    private File categoriesFile;
    private FileConfiguration wipe;
    private File wipeFile;
    private DatabaseManager databaseManager;
    
    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
        loadConfig();
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        createMenusConfig();
        createBoostersConfig();
        createPointsConfig();
        createCategoriesConfig();
        createWipeConfig();
    }
    
    private void createPointsConfig() {
        pointsFile = new File(plugin.getDataFolder(), "points.yml");
        if (!pointsFile.exists()) {
            plugin.saveResource("points.yml", false);
        }
        points = YamlConfiguration.loadConfiguration(pointsFile);
    }
    
    private void createCategoriesConfig() {
        categoriesFile = new File(plugin.getDataFolder(), "categories.yml");
        if (!categoriesFile.exists()) {
            plugin.saveResource("categories.yml", false);
        }
        categories = YamlConfiguration.loadConfiguration(categoriesFile);
    }
    
    private void createWipeConfig() {
        wipeFile = new File(plugin.getDataFolder(), "wipe.yml");
        if (!wipeFile.exists()) {
            plugin.saveResource("wipe.yml", false);
        }
        wipe = YamlConfiguration.loadConfiguration(wipeFile);
    }
    
    public FileConfiguration getWipeConfig() {
        return wipe;
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        menus = YamlConfiguration.loadConfiguration(menusFile);
        boosters = YamlConfiguration.loadConfiguration(boostersFile);
        points = YamlConfiguration.loadConfiguration(pointsFile);
        categories = YamlConfiguration.loadConfiguration(categoriesFile);
        wipe = YamlConfiguration.loadConfiguration(wipeFile);
    }

    public void saveMenus() {
        try {
            menus.save(menusFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save menus.yml!");
        }
    }

    public FileConfiguration getCategoriesConfig() {
        return categories;
    }

    public FileConfiguration getPointsConfig() {
        return points;
    }

    public FileConfiguration getMenusConfig() {
        return menus;
    }

    public FileConfiguration getBoostersConfig() {
        return boosters;
    }

    private void createMenusConfig() {
        menusFile = new File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        menus = YamlConfiguration.loadConfiguration(menusFile);
    }

    private void createBoostersConfig() {
        boostersFile = new File(plugin.getDataFolder(), "boosters.yml");
        if (!boostersFile.exists()) {
            plugin.saveResource("boosters.yml", false);
        }
        boosters = YamlConfiguration.loadConfiguration(boostersFile);
    }
    
    public String getMessage(String path) {
        return colorize(plugin.getConfig().getString("messages." + path, ""));
    }
    
    public String getMenuTitle(String type) {
        return colorize(plugin.getConfig().getString("menu." + type + "-title", "Меню"));
    }
    
    public String getButtonName(String button) {
        return colorize(plugin.getConfig().getString("buttons." + button + ".name", ""));
    }
    
    public List<String> getButtonLore(String button) {
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("buttons." + button + ".lore")) {
            lore.add(colorize(line));
        }
        return lore;
    }
    
    public Material getButtonMaterial(String button) {
        String materialName = plugin.getConfig().getString("buttons." + button + ".material", "STONE");
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }
    
    public String colorize(String message) {
        if (message == null) return "";
        try {
            Matcher matcher = HEX_PATTERN.matcher(message);
            while (matcher.find()) {
                String color = matcher.group();
                message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color).toString());
                matcher = HEX_PATTERN.matcher(message);
            }
        } catch (Throwable ignored) {
            // Fallback for older versions or if BungeeChat API is not fully available as expected
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message);
    }

    public ItemStack createGuiItem(Material material, String name, List<String> lore, String base64) {
        ItemStack item = new ItemStack(material);
        if (material == Material.PLAYER_HEAD && base64 != null && !base64.isEmpty()) {
            item = getCustomHead(base64);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) coloredLore.add(colorize(line));
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getCustomHead(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            ItemMeta headMeta = head.getItemMeta();
            java.lang.reflect.Field profileField = headMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);

            // Use reflection for GameProfile and Property to avoid direct authlib dependency in compilation if possible
            // or just use the fact that it's provided by the server at runtime.
            Object profile = Class.forName("com.mojang.authlib.GameProfile")
                .getConstructor(UUID.class, String.class)
                .newInstance(UUID.randomUUID(), null);
            
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            Object property = Class.forName("com.mojang.authlib.properties.Property")
                .getConstructor(String.class, String.class)
                .newInstance("textures", base64);
            
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);

            profileField.set(headMeta, profile);
            head.setItemMeta(headMeta);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set custom head texture: " + e.getMessage());
        }
        return head;
    }
}
