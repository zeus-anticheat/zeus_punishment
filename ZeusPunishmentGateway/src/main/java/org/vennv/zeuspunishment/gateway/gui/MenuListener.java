package org.vennv.zeuspunishment.gateway.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin;

public class MenuListener implements Listener {

    private final ZeusPunishmentPlugin plugin;

    public MenuListener(ZeusPunishmentPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.AQUA + "Zeus Punishment Menu")) {
            event.setCancelled(true); // Ngăn không cho kéo/thả items

            if (event.getCurrentItem() == null) return;
            ItemStack clicked = event.getCurrentItem();

            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();

                switch (event.getRawSlot()) {
                    case 11:
                        plugin.reloadPlugin();
                        player.sendMessage(ChatColor.GREEN + "[Zeus] Configuration reloaded & API synced!");
                        MenuBuilder.open(player, plugin);
                        break;
                    case 12:
                        boolean currentVerbose = plugin.getCoreConfig().isDevVerboseMode();
                        plugin.getConfig().set("verbose", !currentVerbose);
                        plugin.saveConfig();
                        plugin.getCoreConfig().setDevVerboseMode(!currentVerbose);
                        player.sendMessage(ChatColor.YELLOW + "[Zeus] Verbose Mode changed to: " + !currentVerbose);
                        MenuBuilder.open(player, plugin);
                        break;
                    case 13:
                        boolean banwave = plugin.getCoreConfig().isBanwaveEnabled();
                        plugin.getConfig().set("banwave.enabled", !banwave);
                        plugin.saveConfig();
                        plugin.getCoreConfig().setBanwaveEnabled(!banwave);
                        player.sendMessage(ChatColor.YELLOW + "[Zeus] Banwave Feature changed to: " + !banwave);
                        MenuBuilder.open(player, plugin);
                        break;
                    case 14:
                        boolean effects = plugin.getCoreConfig().isEffectsEnabled();
                        plugin.getConfig().set("effects_enabled", !effects);
                        plugin.saveConfig();
                        plugin.getCoreConfig().setEffectsEnabled(!effects);
                        player.sendMessage(ChatColor.YELLOW + "[Zeus] Lightning Effects changed to: " + !effects);
                        MenuBuilder.open(player, plugin);
                        break;
                    case 15:
                        boolean currentDevMode = plugin.getCoreConfig().isDevMode();
                        plugin.getConfig().set("dev_mode", !currentDevMode);
                        plugin.saveConfig();
                        plugin.getCoreConfig().setDevMode(!currentDevMode);
                        player.sendMessage(ChatColor.YELLOW + "[Zeus] Dev Mode changed to: " + !currentDevMode);
                        MenuBuilder.open(player, plugin);
                        break;

                    case 49:
                        player.sendMessage(ChatColor.GOLD + "[Zeus] Actioning Banwave manual toggle (if active)...");
                        if (plugin.getCoreConfig().isBanwaveEnabled()) {
                            plugin.getBanwaveManager().startCountdown();
                            player.sendMessage(ChatColor.GREEN + "[Zeus] Banwave triggered! Countdown starting.");
                        } else {
                            player.sendMessage(ChatColor.RED + "[Zeus] Banwave is disabled!");
                        }
                        player.closeInventory();
                        break;
                    default:
                        if (event.getRawSlot() >= 18 && event.getRawSlot() <= 44) {
                            if (clicked.hasItemMeta() && clicked.getItemMeta().hasLore()) {
                                java.util.List<String> lore = clicked.getItemMeta().getLore();
                                if (!lore.isEmpty()) {
                                    String lastLine = lore.get(lore.size() - 1);
                                    String modelId = ChatColor.stripColor(lastLine);
                                    if (modelId != null && !modelId.trim().isEmpty()) {
                                        cycleModelAction(player, plugin, modelId, event.getClick());
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        }
    }

    private void cycleModelAction(Player player, ZeusPunishmentPlugin plugin, String modelId, org.bukkit.event.inventory.ClickType clickType) {
        String level = "warning";
        if (clickType.isShiftClick()) {
            level = "ban";
        } else if (clickType.isRightClick()) {
            level = "kick";
        }

        String currentAction = plugin.getConfig().getString("models." + modelId + "." + level + "_action", "NONE");
        String nextAction = "MITIGATE";
        if (currentAction.equals("NONE")) nextAction = "MITIGATE";
        else if (currentAction.equals("MITIGATE")) nextAction = "SETBACK";
        else if (currentAction.equals("SETBACK")) nextAction = "KICK";
        else if (currentAction.equals("KICK")) nextAction = "BAN";
        else if (currentAction.equals("BAN")) nextAction = "NONE";
        
        plugin.getConfig().set("models." + modelId + "." + level + "_action", nextAction);
        plugin.saveConfig();
        plugin.reloadPlugin();
        player.sendMessage(ChatColor.YELLOW + "[Zeus] Model " + modelId + " (" + level.toUpperCase() + ") action changed to: " + nextAction);
        MenuBuilder.open(player, plugin);
    }
}
