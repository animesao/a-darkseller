package com.buyerplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class BuyerPlaceholderExpansion extends PlaceholderExpansion {

    private final Main plugin;

    public BuyerPlaceholderExpansion(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "BuyerPlugin";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "darkseller";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.4.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("points")) {
            return String.format("%.1f", plugin.getShopManager().getPoints(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("multiplier")) {
            return String.format("%.2f", plugin.getMultiplierManager().getMultiplier(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("level")) {
            return String.valueOf(plugin.getMultiplierManager().getLevel(player.getUniqueId()));
        }

        return null;
    }
}
