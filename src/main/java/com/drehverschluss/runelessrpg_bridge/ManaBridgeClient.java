package com.drehverschluss.runelessrpg_bridge;

import com.drehverschluss.runelessrpg_bridge.core.client.BridgeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Client-only entry point. This class is never loaded on a dedicated server.
@Mod(value = ManaBridge.MODID, dist = Dist.CLIENT)
public class ManaBridgeClient {
    public ManaBridgeClient(IEventBus modEventBus, ModContainer container) {
        BridgeClient.init(modEventBus, container);
    }
}
