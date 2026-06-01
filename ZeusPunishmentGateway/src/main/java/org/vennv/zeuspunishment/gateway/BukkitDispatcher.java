package org.vennv.zeuspunishment.gateway;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BukkitDispatcher implements PunishmentDispatcher {

    private final ZeusPunishmentPlugin plugin;
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    public BukkitDispatcher(ZeusPunishmentPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            lastLocations.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (((org.bukkit.entity.Entity) p).isOnGround()) {
                    lastLocations.put(p.getUniqueId(), p.getLocation());
                }
            }
        }, 20L, 20L); // Snapshot locations every 1 second
    }

    @Override
    public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(record.getUsername());
            if (player != null && player.isOnline()) {
                if (plugin.getCoreConfig().isDevMode()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[Zeus Dev Mode] &cYou would have been kicked. Reason: &f" + reason));
                    return;
                }
                String kickMsg = ChatColor.translateAlternateColorCodes('&', plugin.getCoreConfig().getMessageKick() + "\nReason: " + reason);
                player.kickPlayer(kickMsg);
            }
        });
        return DispatcherOutcome.executed("kick scheduled");
    }

    @Override
    public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(record.getUsername());
            if (player != null && player.isOnline()) {
                if (plugin.getCoreConfig().isDevMode()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e[Zeus Dev Mode] &cYou would have been banned. Reason: &f" + reason));
                    return;
                }
                String banMsg = ChatColor.translateAlternateColorCodes('&', plugin.getCoreConfig().getMessageBan() + "\nReason: " + reason);
                player.kickPlayer(banMsg);
            }
            // In a real plugin, we would use Bukkit.getBanList to actually ban them.
            if (!plugin.getCoreConfig().isDevMode()) {
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(record.getUsername(), reason, null, "Zeus");
            }
        });
        return DispatcherOutcome.executed("ban scheduled");
    }

    @Override
    public DispatcherOutcome setbackPlayer(ViolationRecord record) {
        // Teleport player back to their past location (1s ago) to cancel momentum and revert speed hacks
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(record.getUsername());
            if (player != null && player.isOnline()) {
                Location fallback = lastLocations.getOrDefault(player.getUniqueId(), player.getLocation());
                player.teleport(fallback);
                player.setFallDistance(0.0f);
                player.setVelocity(new org.bukkit.util.Vector(0, -0.08, 0)); // Pull them down
            }
        });
        return DispatcherOutcome.executed("setback scheduled");
    }

    @Override
    public DispatcherOutcome mitigatePlayer(ViolationRecord record) {
        // Smoothly cancel violation without hard lagback teleport
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(record.getUsername());
            if (player != null && player.isOnline()) {
                player.setFallDistance(0.0f);
                player.setVelocity(new org.bukkit.util.Vector(0, -0.08, 0));
            }
        });
        return DispatcherOutcome.executed("mitigation scheduled");
    }

    @Override
    public DispatcherOutcome playEffect(String uid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Find player by matching Uid string or we'd just find by name if we passed name.
            // Since offline UUIDs might not match online ones perfectly depending on mode, looping helps or UUID.fromString.
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().toString().replace("-", "").equalsIgnoreCase(uid.replace("-", ""))) {
                    if (plugin.getCoreConfig().isEffectsEnabled()) {
                        // Strike lightning effect (purely visual)
                        p.getWorld().strikeLightningEffect(p.getLocation());
                    }
                    break;
                }
            }
        });
        return DispatcherOutcome.executed("effect scheduled");
    }

    @Override
    public DispatcherOutcome broadcast(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(formatted);
        });
        return DispatcherOutcome.executed("broadcast scheduled");
    }

    @Override
    public DispatcherOutcome logVerbose(String message) {
        String formattedMsg = ChatColor.translateAlternateColorCodes('&', "&b[Zeus-Dev] &7" + message);
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Log to console
            Bukkit.getConsoleSender().sendMessage(formattedMsg);
            // Broadcast to online admins
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("zpunish.admin")) {
                    p.sendMessage(formattedMsg);
                }
            }
        });
        return DispatcherOutcome.executed("log recorded");
    }
}
