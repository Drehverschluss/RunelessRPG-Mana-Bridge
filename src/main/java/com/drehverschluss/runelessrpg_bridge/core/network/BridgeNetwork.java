package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.core.AdapterHandle;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.BridgeConfig;
import com.drehverschluss.runelessrpg_bridge.core.Leader;
import com.drehverschluss.runelessrpg_bridge.core.ManaSystemChoice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Network channel of the bridge (one payload, server → client). The channel is <b>optional</b>: a client with the bridge
 * can join a server without it (the bridge stays off on that client), and a client without it can join a server with
 * it (pool, costs and debt are right because the server computes them; the client only lacks the bar choice and the
 * converted displays). The payload is only sent to clients that have the channel.
 */
public final class BridgeNetwork {
    public static final String VERSION = "2";

    private BridgeNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(BridgeSettingsPayload.TYPE, BridgeSettingsPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientBridgeState.accept(payload)));
        // first-start question (0.2.0): new payload ids, so clients and servers of 0.1.0 just do not have them
        registrar.playToClient(ManaSystemPayloads.Ask.TYPE, ManaSystemPayloads.Ask.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientBridgeState.ask(payload)));
        registrar.playToClient(ManaSystemPayloads.Close.TYPE, ManaSystemPayloads.Close.CODEC,
                (payload, context) -> context.enqueueWork(ClientBridgeState::closeAsk));
        registrar.playToServer(ManaSystemPayloads.Answer.TYPE, ManaSystemPayloads.Answer.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ManaSystemChoice.onAnswer(player, payload.id());
                    }
                }));
    }

    /** @return true if the player's client can show the first-start question as a window */
    public static boolean canAsk(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(ManaSystemPayloads.Ask.TYPE);
    }

    /** The current server values. */
    public static BridgeSettingsPayload snapshot() {
        List<BridgeSettingsPayload.AdapterState> states = new ArrayList<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            states.add(new BridgeSettingsPayload.AdapterState(handle.modId(), handle.isConnectedOnServer(), handle.configFactor(),
                    handle.configApplyManaCost()));
        }
        return new BridgeSettingsPayload(Leader.id(), BridgeConfig.get(BridgeConfig.FOREIGN_SPELLS_USE_DEBT, true), List.copyOf(states));
    }

    /** @return true if the player's client has the bridge's channel */
    public static boolean hasChannel(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(BridgeSettingsPayload.TYPE);
    }

    public static void send(ServerPlayer player) {
        if (hasChannel(player)) {
            PacketDistributor.sendToPlayer(player, snapshot());
        }
    }

    public static void sendToAll() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        BridgeSettingsPayload payload = snapshot();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasChannel(player)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
