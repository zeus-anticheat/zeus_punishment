package org.vennv.zeuspunishment.gateway.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin;

public class ZPunishCommand implements CommandExecutor {

    private final ZeusPunishmentPlugin plugin;

    public ZPunishCommand(ZeusPunishmentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zpunish.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.AQUA + "Zeus Punishment Gateway " + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "/zpunish reload" + ChatColor.GRAY + " - Reload configurations");
            sender.sendMessage(ChatColor.YELLOW + "/zpunish gui" + ChatColor.GRAY + " - Open config GUI");
            sender.sendMessage(ChatColor.YELLOW + "/zpunish verbose" + ChatColor.GRAY + " - Toggle Verbose Mode");
            sender.sendMessage(ChatColor.YELLOW + "/zpunish banwave" + ChatColor.GRAY + " - View/Execute Banwave");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(ChatColor.GREEN + "ZeusPunishment reloaded!");
                break;
            case "verbose":
                boolean verbose = plugin.getCoreConfig().isDevVerboseMode();
                plugin.getCoreConfig().setDevVerboseMode(!verbose);
                sender.sendMessage(ChatColor.GREEN + "Zeus Verbose mode toggled to: " + !verbose);
                break;
            case "gui":
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    org.vennv.zeuspunishment.gateway.gui.MenuBuilder.open(p, plugin);
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can use the GUI.");
                }
                break;
            case "banwave":
                sender.sendMessage(ChatColor.AQUA + "Current Queued Bans: " + plugin.getBanwaveManager().getQueuedBans().size());
                if (args.length > 1 && args[1].equalsIgnoreCase("execute")) {
                    plugin.getBanwaveManager().startCountdown();
                    sender.sendMessage(ChatColor.GREEN + "Banwave countdown started!");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown argument.");
                break;
        }

        return true;
    }
}
