package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuyerTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("reload");
            suggestions.add("rotate");
            suggestions.add("reset");
            suggestions.add("clearmultipliers");
            suggestions.add("clearboosters");
            suggestions.add("giveitem");
            suggestions.add("points");
            StringUtil.copyPartialMatches(args[0], suggestions, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("giveitem")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
            } else if (args[0].equalsIgnoreCase("points")) {
                suggestions.add("give");
            }
            StringUtil.copyPartialMatches(args[1], suggestions, completions);
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("points") && args[1].equalsIgnoreCase("give")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
            }
            StringUtil.copyPartialMatches(args[2], suggestions, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
