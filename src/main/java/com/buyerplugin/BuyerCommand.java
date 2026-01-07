
package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BuyerCommand implements CommandExecutor {
    
    private Main plugin;
    private BuyerManager buyerManager;
    
    public BuyerCommand(Main plugin, BuyerManager buyerManager) {
        this.plugin = plugin;
        this.buyerManager = buyerManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length >= 3 && args[0].equalsIgnoreCase("giveitem")) {
            if (!sender.hasPermission("buyer.admin")) {
                sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().colorize("&cИгрок не найден!"));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getConfigManager().colorize("&cНеверное количество!"));
                return true;
            }

            FileConfiguration config = plugin.getConfigManager().getBoostersConfig();
            Material mat = Material.valueOf(config.getString("currency.material", "NETHER_STAR"));
            String name = config.getString("currency.name", "#FF5555Боевой осколок");
            List<String> lore = config.getStringList("currency.lore");
            
            ItemStack item = plugin.getConfigManager().createGuiItem(mat, name, lore, null);
            item.setAmount(amount);
            target.getInventory().addItem(item);
            sender.sendMessage(plugin.getConfigManager().colorize("&aВыдано " + amount + " осколков игроку " + target.getName()));
            return true;
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("points") && args[1].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("buyer.admin")) {
                sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().colorize("&cИгрок не найден!"));
                return true;
            }
            try {
                double amount = Double.parseDouble(args[3]);
                plugin.getShopManager().addPoints(target.getUniqueId(), amount);
                sender.sendMessage(plugin.getConfigManager().colorize("&aВы выдали &f" + amount + " &aочков игроку &f" + target.getName()));
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getConfigManager().colorize("&cНеверное количество!"));
            }
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                    return true;
                }
                
                plugin.reloadConfig();
                plugin.getConfigManager().loadConfig();
                buyerManager.loadBuyPrices();
                buyerManager.loadLimits();
                buyerManager.stopRotationTask();
                buyerManager.startRotationTask();
                buyerManager.stopAutoSellTask();
                buyerManager.startAutoSellTask();
                sender.sendMessage(plugin.getConfigManager().getMessage("reload-success"));
                return true;
            }
            
            if (args[0].equalsIgnoreCase("rotate")) {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                    return true;
                }
                
                buyerManager.rotateItems();
                sender.sendMessage(plugin.getConfigManager().getMessage("rotate-success"));
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reset")) {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                    return true;
                }
                
                buyerManager.resetStock();
                sender.sendMessage(plugin.getConfigManager().getMessage("reset-success"));
                return true;
            }
            
            if (args[0].equalsIgnoreCase("clearmultipliers")) {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                    return true;
                }
                
                plugin.getMultiplierManager().clearAllData();
                sender.sendMessage(plugin.getConfigManager().colorize("#00FF00База данных множителей очищена!"));
                return true;
            }

            if (args[0].equalsIgnoreCase("clearboosters")) {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage(plugin.getConfigManager().colorize("#FF0000У вас нет прав!"));
                    return true;
                }
                
                plugin.getMultiplierManager().clearAllBoosters();
                sender.sendMessage(plugin.getConfigManager().colorize("#00FF00Все активные бустеры были удалены у всех игроков!"));
                return true;
            }
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("players-only"));
            return true;
        }
        
        Player player = (Player) sender;
        BuyerGUI gui = new BuyerGUI(plugin, buyerManager, plugin.getMultiplierManager());
        gui.openMainMenu(player);
        return true;
    }
}
