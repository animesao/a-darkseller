
package com.buyerplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    
    private ConfigManager configManager;
    private BuyerManager buyerManager;
    private BuyerListener buyerListener;
    private MultiplierManager multiplierManager;
    private ShopManager shopManager;
    private BoosterGUI boosterGUI;
    private DatabaseManager databaseManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        
        databaseManager = new DatabaseManager(this);
        
        shopManager = new ShopManager(this);
        multiplierManager = new MultiplierManager(this);
        boosterGUI = new BoosterGUI(this, multiplierManager);
        buyerManager = new BuyerManager(this, multiplierManager);
        buyerListener = new BuyerListener(this, buyerManager, multiplierManager);
        
        getServer().getPluginManager().registerEvents(buyerListener, this);
        getCommand("buyer").setExecutor(new BuyerCommand(this, buyerManager));
        getCommand("buyer").setTabCompleter(new BuyerTabCompleter());
        
        BuyerAPI.init(this);
        
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BuyerPlaceholderExpansion(this).register();
        }
        
        getLogger().info("BuyerPlugin успешно загружен!");
    }
    
    @Override
    public void onDisable() {
        if (buyerManager != null) {
            buyerManager.saveRotation();
            buyerManager.stopRotationTask();
            buyerManager.stopAutoSellTask();
            buyerManager.saveStats();
        }
        if (multiplierManager != null) {
            multiplierManager.saveData();
        }
        if (shopManager != null) {
            shopManager.close();
        }
        getLogger().info("BuyerPlugin выгружен!");
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public BuyerManager getBuyerManager() {
        return buyerManager;
    }
    
    public MultiplierManager getMultiplierManager() {
        return multiplierManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public BoosterGUI getBoosterGUI() {
        return boosterGUI;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
