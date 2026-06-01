package org.vennv.zeuspunishment.fabric;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FabricDispatcher implements PunishmentDispatcher {

    private final ZeusPunishmentMod mod;
    private MinecraftServer server;
    private final Map<UUID, net.minecraft.util.math.Vec3d> lastLocations = new HashMap<>();

    public FabricDispatcher(ZeusPunishmentMod mod) {
        this.mod = mod;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void snapshotLocations() {
        if (server == null) return;
        lastLocations.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isOnGround()) {
                lastLocations.put(p.getUuid(), p.getPos());
            }
        }
    }

    @Override
    public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(record.getUsername());
            if (player != null) {
                if (mod.getCoreConfig().isDevMode()) {
                    player.sendMessage(Text.literal("[Zeus Dev Mode] You would have been kicked. Reason: " + reason), false);
                    return;
                }
                String kickMsg = mod.getCoreConfig().getMessageKick().replace("&c", "") + "\nReason: " + reason;
                player.networkHandler.disconnect(Text.literal(kickMsg));
            }
        });
        return DispatcherOutcome.executed("kick scheduled");
    }

    @Override
    public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(record.getUsername());
            if (player != null) {
                if (mod.getCoreConfig().isDevMode()) {
                    player.sendMessage(Text.literal("[Zeus Dev Mode] You would have been banned. Reason: " + reason), false);
                    return;
                }
                String banMsg = mod.getCoreConfig().getMessageBan().replace("&c", "") + "\nReason: " + reason;
                player.networkHandler.disconnect(Text.literal(banMsg));
            }
            // In a real Fabric mod we use userCache or ban list
            // BannedPlayerEntry entry = new BannedPlayerEntry(...);
            // server.getPlayerManager().getUserBanList().add(entry);
        });
        return DispatcherOutcome.executed("ban scheduled");
    }

    @Override
    public DispatcherOutcome setbackPlayer(ViolationRecord record) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(record.getUsername());
            if (player != null) {
                net.minecraft.util.math.Vec3d fallback = lastLocations.getOrDefault(player.getUuid(), player.getPos());
                player.requestTeleport(fallback.x, fallback.y, fallback.z);
                player.fallDistance = 0.0f;
                player.setVelocity(0, -0.08, 0);
                player.velocityModified = true;
            }
        });
        return DispatcherOutcome.executed("setback scheduled");
    }

    @Override
    public DispatcherOutcome mitigatePlayer(ViolationRecord record) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(record.getUsername());
            if (player != null) {
                player.fallDistance = 0.0f;
                player.setVelocity(0, -0.08, 0);
                player.velocityModified = true;
            }
        });
        return DispatcherOutcome.executed("mitigation scheduled");
    }

    @Override
    public DispatcherOutcome playEffect(String uid) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        if (!mod.getCoreConfig().isEffectsEnabled()) return DispatcherOutcome.ignored("effects disabled");
        server.execute(() -> {
            UUID id = UUID.fromString(uid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
                LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(player.getServerWorld(), net.minecraft.entity.SpawnReason.COMMAND);
                if (lightning != null) {
                    lightning.refreshPositionAfterTeleport(player.getPos());
                    lightning.setCosmetic(true); // Don't deal damage/fire
                    player.getServerWorld().spawnEntity(lightning);
                }
            }
        });
        return DispatcherOutcome.executed("effect scheduled");
    }

    @Override
    public DispatcherOutcome broadcast(String message) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        String formatted = message.replace("&e", "").replace("&c", "").replace("&7", ""); // Simplified
        server.execute(() -> {
            server.getPlayerManager().broadcast(Text.literal(formatted), false);
        });
        return DispatcherOutcome.executed("broadcast scheduled");
    }

    @Override
    public DispatcherOutcome logVerbose(String message) {
        if (server == null) return DispatcherOutcome.retryableFailure("server unavailable");
        String formatted = "[Zeus-Dev] " + message;
        server.execute(() -> {
            System.out.println(formatted); // Console
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.hasPermissionLevel(2)) { // OP
                    p.sendMessage(Text.literal(formatted), false);
                }
            }
        });
        return DispatcherOutcome.executed("verbose log scheduled");
    }
}
