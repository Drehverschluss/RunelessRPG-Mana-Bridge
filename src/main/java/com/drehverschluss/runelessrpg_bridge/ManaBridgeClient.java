package com.drehverschluss.runelessrpg_bridge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

// Client-only entry point. This class is never loaded on a dedicated server.
@Mod(value = ManaBridge.MODID, dist = Dist.CLIENT)
public class ManaBridgeClient {
    public ManaBridgeClient() {
    }
}
