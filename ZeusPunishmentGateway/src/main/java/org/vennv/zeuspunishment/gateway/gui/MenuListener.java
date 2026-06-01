package org.vennv.zeuspunishment.gateway.gui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;
import org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin;

public class MenuListener implements Listener {
    private final ZeusPunishmentPlugin plugin;

    public MenuListener(ZeusPunishmentPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.AQUA + "Zeus Punishment Menu")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("zpunish.admin")) return;
        if (event.getCurrentItem() == null) return;

        switch (event.getRawSlot()) {
            case MenuBuilder.SLOT_RELOAD -> {
                player.sendMessage(plugin.reloadPlugin() ? ChatColor.GREEN + "[Zeus] Configuration reloaded." : ChatColor.RED + "[Zeus] Config invalid; services unchanged.");
                MenuBuilder.open(player, plugin);
            }
            case MenuBuilder.SLOT_STATUS, MenuBuilder.SLOT_REFRESH -> MenuBuilder.open(player, plugin);
            case MenuBuilder.SLOT_PAUSE_RESUME -> {
                BanwaveManager.ControlResult result = plugin.getBanwaveManager().getQueueState().isPaused() ? plugin.getBanwaveManager().resume() : plugin.getBanwaveManager().pause();
                player.sendMessage(ChatColor.YELLOW + "[Zeus] " + result.message());
                MenuBuilder.open(player, plugin);
            }
            case MenuBuilder.SLOT_EXECUTE -> {
                BanwaveManager.ControlResult result = plugin.getBanwaveManager().executeAll();
                player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + "[Zeus] " + result.message());
                MenuBuilder.open(player, plugin);
            }
            case MenuBuilder.SLOT_CLEAR -> {
                player.sendMessage(ChatColor.YELLOW + "[Zeus] " + plugin.getBanwaveManager().clearQueue().message());
                MenuBuilder.open(player, plugin);
            }
            default -> handleQueueClick(player, event);
        }
    }

    private void handleQueueClick(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() < MenuBuilder.QUEUE_START || event.getRawSlot() > MenuBuilder.QUEUE_END) return;
        String key = hiddenKey(event.getCurrentItem());
        if (key == null || key.isBlank()) return;
        if (event.getClick().isRightClick()) {
            BanwaveManager.ControlResult result = plugin.getBanwaveManager().cancel(key);
            player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + "[Zeus] " + result.message());
            MenuBuilder.open(player, plugin);
            return;
        }
        BanwaveManager.QueueEntry entry = plugin.getBanwaveManager().details(key);
        player.sendMessage(entry == null ? ChatColor.RED + "[Zeus] Queued entry not found." : ChatColor.GRAY + "[Zeus] Review " + entry.key() + " player=" + entry.username() + " tier=" + entry.severity());
    }

    private String hiddenKey(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        java.util.List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return null;
        return ChatColor.stripColor(lore.get(lore.size() - 1));
    }
}
