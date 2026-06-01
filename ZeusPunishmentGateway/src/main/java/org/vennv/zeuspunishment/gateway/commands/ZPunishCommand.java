package org.vennv.zeuspunishment.gateway.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.vennv.zeuspunishment.core.model.EngineStatusSnapshot;
import org.vennv.zeuspunishment.core.network.ApiStatusSnapshot;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;
import org.vennv.zeuspunishment.gateway.ZeusPunishmentPlugin;

public class ZPunishCommand implements CommandExecutor {
    private final ZeusPunishmentPlugin plugin;

    public ZPunishCommand(ZeusPunishmentPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zpunish.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(plugin.reloadPlugin() ? ChatColor.GREEN + "ZeusPunishment configuration reloaded." : ChatColor.RED + "ZeusPunishment config invalid; existing services remain active.");
            case "reconnect" -> { plugin.reconnectStream(); sender.sendMessage(ChatColor.GREEN + "ZeusPunishment stream reconnect requested."); }
            case "verbose" -> toggleVerbose(sender);
            case "gui" -> openGui(sender);
            case "banwave" -> handleBanwave(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Zeus Punishment Gateway " + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "/zpunish status" + ChatColor.GRAY + " - Show cached health and policy state");
        sender.sendMessage(ChatColor.YELLOW + "/zpunish reload" + ChatColor.GRAY + " - Validate and reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/zpunish reconnect" + ChatColor.GRAY + " - Restart the violation stream");
        sender.sendMessage(ChatColor.YELLOW + "/zpunish gui" + ChatColor.GRAY + " - Open operator GUI");
        sender.sendMessage(ChatColor.YELLOW + "/zpunish banwave list|details <key>|execute all|cancel <key>|clear|pause|resume" + ChatColor.GRAY + " - Control queued reviews");
    }

    private void toggleVerbose(CommandSender sender) {
        boolean verbose = plugin.getCoreConfig().isDevVerboseMode();
        plugin.getCoreConfig().setDevVerboseMode(!verbose);
        sender.sendMessage(ChatColor.GREEN + "Zeus verbose mode toggled to: " + !verbose);
    }

    private void openGui(CommandSender sender) {
        if (sender instanceof Player p) org.vennv.zeuspunishment.gateway.gui.MenuBuilder.open(p, plugin);
        else sender.sendMessage(ChatColor.RED + "Only players can use the GUI.");
    }

    private void handleBanwave(CommandSender sender, String[] args) {
        BanwaveManager manager = plugin.getBanwaveManager();
        if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
            BanwaveManager.QueueState state = manager.getQueueState();
            sender.sendMessage(ChatColor.AQUA + "Banwave queue: " + state.getQueuedCount() + " queued | " + (state.isPaused() ? "paused" : state.isCountingDown() ? "countdown " + state.getSecondsRemaining() + "s" : "review"));
            for (BanwaveManager.QueueEntry entry : state.entries()) sender.sendMessage(ChatColor.GRAY + entry.key() + " | " + entry.username() + " | " + entry.severity());
            return;
        }
        if (args[1].equalsIgnoreCase("details") && args.length > 2) {
            BanwaveManager.QueueEntry entry = manager.details(args[2]);
            sender.sendMessage(entry == null ? ChatColor.RED + "Queued entry not found." : ChatColor.GRAY + "Entry " + entry.key() + " player=" + entry.username() + " severity=" + entry.severity());
            return;
        }
        if (args[1].equalsIgnoreCase("execute")) {
            BanwaveManager.ControlResult result = manager.executeAll();
            sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            return;
        }
        if (args[1].equalsIgnoreCase("cancel") && args.length > 2) {
            BanwaveManager.ControlResult result = args[2].equalsIgnoreCase("all") ? manager.clearQueue() : manager.cancel(args[2]);
            sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) { sender.sendMessage(ChatColor.GREEN + manager.clearQueue().message()); return; }
        if (args[1].equalsIgnoreCase("pause")) { sender.sendMessage(ChatColor.GREEN + manager.pause().message()); return; }
        if (args[1].equalsIgnoreCase("resume")) { sender.sendMessage(ChatColor.GREEN + manager.resume().message()); return; }
        sendHelp(sender);
    }

    private void sendStatus(CommandSender sender) {
        EngineStatusSnapshot status = plugin.getEngine().getStatusSnapshot();
        ApiStatusSnapshot api = status.getApiStatus();
        sender.sendMessage(ChatColor.AQUA + "Zeus Punishment Status");
        sender.sendMessage(ChatColor.GRAY + "Plugin: " + (status.isLifecycleRunning() ? "running" : "stopped") + " | Enforcement: " + (status.isEnforcementActive() ? "active" : "safe"));
        sender.sendMessage(ChatColor.GRAY + "API: " + (api.isApiHealthy() ? "healthy" : "unavailable") + " | Stream: " + (api.isStreamRunning() ? "running" : (api.isReconnecting() ? "reconnecting" : "stopped")) + " | Generation: " + api.getGeneration());
        sender.sendMessage(ChatColor.GRAY + "Backoff: " + api.getCurrentBackoffMs() + "ms | Last error: " + api.getLastError());
        sender.sendMessage(ChatColor.GRAY + "Policy: " + status.getPolicyPreset() + " | Mode: " + (status.isDryRun() ? "dry-run" : "enforcing") + " | High impact: " + (status.isHighImpactEnabled() ? "enabled" : "disabled"));
        sender.sendMessage(ChatColor.GRAY + "Banwave: queued=" + status.getQueueState().getQueuedCount() + " state=" + (status.getQueueState().isPaused() ? "paused" : status.getQueueState().isCountingDown() ? "countdown" : "review") + " duplicates=" + status.getQueueState().getDuplicateSuppressions());
        sender.sendMessage(ChatColor.GRAY + "Recent outcomes: " + (status.getRecentOutcomes().isEmpty() ? "none" : String.join(", ", status.getRecentOutcomes())));
    }
}
