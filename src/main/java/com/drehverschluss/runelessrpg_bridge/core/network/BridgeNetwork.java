package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.core.AdapterHandle;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.BridgeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network channel of the bridge (one payload, server → client). */
public final class BridgeNetwork {
    public static final String VERSION = "1";

    private BridgeNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(BridgeSettingsPayload.TYPE, BridgeSettingsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientBridgeState.accept(payload)));
    }

    /** The current server values. */
    public static BridgeSettingsPayload snapshot() {
        List<BridgeSettingsPayload.AdapterState> states = new ArrayList<>();
        for (AdapterHandle handle : Adapters.followers()) {
            states.add(new BridgeSettingsPayload.AdapterState(handle.modId(), handle.isActiveOnServer(), handle.configFactor(),
                    handle.configApplyManaCost()));
        }
        return new BridgeSettingsPayload(Adapters.leaderId(), BridgeConfig.get(BridgeConfig.FOREIGN_SPELLS_USE_DEBT, true), List.copyOf(states));
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot());
    }

    public static void sendToAll() {
        PacketDistributor.sendToAllPlayers(snapshot());
    }
}
