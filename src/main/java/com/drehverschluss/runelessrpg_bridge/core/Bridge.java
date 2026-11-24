package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Start of the core: adapters, configs, network, self test, detection of other bridges. */
public final class Bridge {
    private static String warnedManaSystem = Adapters.RUNELESS;

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
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, event -> Conflicts.evaluate());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                if (Conflicts.evaluate()) {
                    BridgeNetwork.sendToAll();
                }
                BridgeNetwork.send(player);
                Conflicts.notifyOperator(player);
            }
        });
    }

    private static void onConfig(ModConfig config) {
        if (!config.getModId().equals(ManaBridge.MODID) || config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        String system = BridgeConfig.MANA_SYSTEM.get().trim().toLowerCase(Locale.ROOT);
        if (!system.equals(Adapters.RUNELESS) && !system.equals(warnedManaSystem)) {
            ManaBridge.LOGGER.warn("manaSystem = \"{}\" is not supported in this version, RunelessRPG leads the mana pool", system);
        }
        warnedManaSystem = system;
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            Conflicts.evaluate();
            BridgeNetwork.sendToAll();
        }
    }
}
