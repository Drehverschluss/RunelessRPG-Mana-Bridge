package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Start of the core: adapters, configs, network, self test, detection of other bridges, leader, bridge. */
public final class Bridge {
    private Bridge() {}

    public static void init(IEventBus modBus, ModContainer container) {
        Adapters.create();
        container.registerConfig(ModConfig.Type.SERVER, BridgeConfig.build());
        container.registerConfig(ModConfig.Type.CLIENT, BridgeClientConfig.build());
        for (AdapterHandle handle : Adapters.all()) {
            handle.adapter().init(modBus, handle);
        }
        modBus.addListener(BridgeNetwork::register);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            SelfTest.runCommon();
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
                MixinConflicts.scan(); // the client scans after its own self test, with the client targets
            }
        }));
        modBus.addListener(ModConfigEvent.Loading.class, event -> onConfig(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> onConfig(event.getConfig()));
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> {
            MixinConflicts.warnUnknown(); // the server config (conflictingMods) is loaded now
            Conflicts.evaluate();
            Leader.update(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> Leader.reset());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            try {
                Conflicts.poll(event.getServer());
            } catch (RuntimeException | LinkageError e) {
                ManaBridge.LOGGER.debug("Looking at the other bridges failed", e);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                if (Conflicts.evaluate() | Leader.update(player.server)) {
                    BridgeNetwork.sendToAll();
                }
                BridgeNetwork.send(player);
                Conflicts.notifyOperator(player);
            }
        });
        // First-start question (plan 5.6) at the lowest priority: the listener above has evaluated the self test, the other
        // bridges and the leader, and every mod has run its own login handler. RunelessRPG sets the base values of max_mana
        // and mana_regen from its config there; the question shows those, not the values saved with the player.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                try {
                    ManaSystemChoice.onLogin(player);
                } catch (RuntimeException e) {
                    ManaBridge.LOGGER.warn("The mana system question could not be sent", e);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ManaSystemChoice.onLogout(player);
                Pool.forget(player);
            }
        });
        BonusBridge.register();
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, StatusCommand::register);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ManaSystemCommand::register);
    }

    /** The config watcher runs on its own thread: everything that follows is done on the server thread. */
    private static void onConfig(ModConfig config) {
        if (!config.getModId().equals(ManaBridge.MODID) || config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isRunning()) {
            return; // the server start evaluates everything itself
        }
        server.execute(() -> {
            Conflicts.evaluate();
            Leader.update(server);
            BridgeNetwork.sendToAll();
        });
    }
}
