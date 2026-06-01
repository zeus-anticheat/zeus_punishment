package org.vennv.zeuspunishment.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.ZeusPunishmentEngine;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

public class ZeusPunishmentMod implements DedicatedServerModInitializer {

    private PunishmentConfig config;
    private PunishmentDispatcher dispatcher;
    private ZeusApiClient apiClient;
    private BanwaveManager banwaveManager;
    private ZeusPunishmentEngine engine;

    private int tickCounter = 0;

    @Override
    public void onInitializeServer() {
        this.config = new PunishmentConfig(); // In real scenario: load JSON config
        this.dispatcher = new FabricDispatcher(this);
        this.apiClient = new ZeusApiClient(config.getEndpointUrl());
        this.banwaveManager = new BanwaveManager(config, dispatcher);
        this.engine = new ZeusPunishmentEngine(config, dispatcher, apiClient, banwaveManager);

        registerEvents();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (engine != null) engine.stop();
        });

        engine.start();

        System.out.println("[ZeusPunishment] Fabric Mod initialized!");
    }

    private void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    private void onTick(MinecraftServer server) {
        tickCounter++;
        ((FabricDispatcher) dispatcher).setServer(server); // Give access to server instance

        // 20 ticks = 1 second
        if (tickCounter % 20 == 0) {
            banwaveManager.tickSecond();
            ((FabricDispatcher) dispatcher).snapshotLocations();
        }
    }

    public PunishmentConfig getCoreConfig() { return config; }
}
