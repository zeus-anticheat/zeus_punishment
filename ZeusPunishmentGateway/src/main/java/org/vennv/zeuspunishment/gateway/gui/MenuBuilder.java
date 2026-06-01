package org.vennv.zeuspunishment.gateway.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class MenuBuilder {

    public static void open(Player player, org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.AQUA + "Zeus Punishment Menu");

        boolean verbose = plugin.getCoreConfig().isDevVerboseMode();
        boolean banwave = plugin.getCoreConfig().isBanwaveEnabled();
        boolean effects = plugin.getCoreConfig().isEffectsEnabled();
        boolean devMode = plugin.getCoreConfig().isDevMode();
        inv.setItem(11, createItem(Material.REDSTONE_BLOCK, "&cReload Configuration", "&7Click to reload rules", "&7and sync with API."));
        inv.setItem(12, createItem(Material.WITHER_SKELETON_SKULL, "&eToggle Verbose Mode", "&7Current: " + (verbose ? "&aON" : "&cOFF"), "&7Enable to see AI explanations", "&7and model scores in chat."));
        inv.setItem(13, createItem(Material.DIAMOND_SWORD, "&aBanwave Feature", "&7Current: " + (banwave ? "&aON" : "&cOFF"), "&7Toggle banwave functionality."));
        inv.setItem(14, createItem(Material.GLOWSTONE_DUST, "&6Toggle Effects", "&7Current: " + (effects ? "&aON" : "&cOFF"), "&7Enable Lightning strike effects."));
        inv.setItem(15, createItem(Material.COMMAND_BLOCK, "&5Toggle Dev Mode", "&7Current: " + (devMode ? "&aON" : "&cOFF"), "&7If enabled, actions like kick/ban", "&7will only warn the player instead."));

        java.util.List<String> activeModels = plugin.getEngine().getCachedModels();
        int slot = 18;
        for (String modelId : activeModels) {
            if (slot >= 45) break; // Do not overflow bottom bar
            String warnAction = plugin.getConfig().getString("models." + modelId + ".warning_action", "NONE");
            String kickAction = plugin.getConfig().getString("models." + modelId + ".kick_action", "NONE");
            String banAction = plugin.getConfig().getString("models." + modelId + ".ban_action", "NONE");
            // Hidden modelId in the lore via ChatColor magic or just a straight text line
            inv.setItem(slot, createItem(Material.PAPER, "&dModel: &f" + modelId, 
                "&7Warning Action: &e" + warnAction, 
                "&7Kick Action: &e" + kickAction, 
                "&7Ban Action: &e" + banAction, 
                "", 
                "&bLeft-Click &7to cycle Warning", 
                "&bRight-Click &7to cycle Kick", 
                "&bShift-Left &7to cycle Ban", 
                "&0" + modelId));
            slot++;
        }

        inv.setItem(49, createItem(Material.TNT, "&cExecute Banwave Now", "&7Force trigger the banwave if active."));

        player.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            java.util.List<String> loreList = new java.util.ArrayList<>();
            for (String l : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', l));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}
