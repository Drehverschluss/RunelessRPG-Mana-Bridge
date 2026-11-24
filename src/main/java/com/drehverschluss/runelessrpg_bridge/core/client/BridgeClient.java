package com.drehverschluss.runelessrpg_bridge.core.client;

import com.drehverschluss.runelessrpg_bridge.core.AdapterHandle;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.SelfTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client start of the core: display, config screen, client self test. */
public final class BridgeClient {
    private BridgeClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        // in game: mod list > RunelessRPG Mana Bridge > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // adapters exist once the common entry point ran; the order of the two entry points is not fixed
        modBus.addListener(FMLClientSetupEvent.class, event -> {
            for (AdapterHandle handle : Adapters.all()) {
                handle.adapter().initClient(modBus, handle);
            }
            event.enqueueWork(() -> {
                SelfTest.runClient();
                com.drehverschluss.runelessrpg_bridge.core.MixinConflicts.scan();
            });
        });
        NeoForge.EVENT_BUS.addListener(DisplayManager::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(DisplayManager::onClientTick);
        NeoForge.EVENT_BUS.addListener(DisplayManager::onLoggingOut);
    }
}
