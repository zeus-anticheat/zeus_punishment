package org.vennv.zeuspunishment.gateway.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

public class MenuBuilder {
    public static final int SLOT_RELOAD = 11;
    public static final int SLOT_STATUS = 12;
    public static final int SLOT_PAUSE_RESUME = 13;
    public static final int SLOT_EXECUTE = 14;
    public static final int SLOT_CLEAR = 15;
    public static final int QUEUE_START = 18;
    public static final int QUEUE_END = 44;
    public static final int SLOT_REFRESH = 49;

    public static void open(Player player, org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.AQUA + "Zeus Punishment Menu");
        BanwaveManager.QueueState queue = plugin.getBanwaveManager().getQueueState();

        inv.setItem(SLOT_RELOAD, createItem(Material.REDSTONE_BLOCK, "&cReload Configuration", "&7Validate and reload configuration."));
        inv.setItem(SLOT_STATUS, createItem(Material.COMPASS, "&bCached Status", "&7Queued reviews: &e" + queue.getQueuedCount(), "&7State: &e" + queueState(queue), "&7Duplicate suppressions: &e" + queue.getDuplicateSuppressions()));
        inv.setItem(SLOT_PAUSE_RESUME, createItem(Material.LEVER, queue.isPaused() ? "&aResume Banwave Review" : "&ePause Banwave Review", "&7Uses the shared queue manager."));
        inv.setItem(SLOT_EXECUTE, createItem(Material.TNT, "&cExecute Queued Reviews", "&7Manual approval action.", "&7Queued: &e" + queue.getQueuedCount()));
        inv.setItem(SLOT_CLEAR, createItem(Material.BARRIER, "&4Clear Queue", "&7Cancel all queued reviews."));

        int slot = QUEUE_START;
        for (BanwaveManager.QueueEntry entry : queue.entries()) {
            if (slot > QUEUE_END) break;
            inv.setItem(slot++, createItem(Material.PAPER, "&dQueued Review", "&7Key: &f" + entry.key(), "&7Player: &e" + entry.username(), "&7Policy tier: &e" + entry.severity(), "&bLeft-click &7inspect", "&cRight-click &7cancel", "&0" + entry.key()));
        }
        inv.setItem(SLOT_REFRESH, createItem(Material.SUNFLOWER, "&aRefresh", "&7Reload cached GUI state."));
        player.openInventory(inv);
    }

    private static String queueState(BanwaveManager.QueueState queue) {
        if (queue.isPaused()) return "paused";
        if (queue.isCountingDown()) return "countdown " + queue.getSecondsRemaining() + "s";
        return "review";
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            java.util.List<String> loreList = new java.util.ArrayList<>();
            for (String l : lore) loreList.add(ChatColor.translateAlternateColorCodes('&', l));
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}
