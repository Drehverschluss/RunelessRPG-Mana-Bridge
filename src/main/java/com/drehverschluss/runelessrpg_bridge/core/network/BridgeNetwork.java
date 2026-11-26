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
 * Network channel of the bridge (settings, overcharge and the first-start question). The channel is <b>optional</b>: a client with the bridge
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
        // applyOvercharge (0.2.0, phase C5B): a payload id of its own for the same reason
        registrar.playToClient(OverchargePayload.TYPE, OverchargePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientBridgeState.acceptOvercharge(payload)));
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

    /** The current {@code applyOvercharge} of every adapter (server values). */
    public static OverchargePayload overchargeSnapshot() {
        java.util.Map<String, Boolean> map = new java.util.LinkedHashMap<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            map.put(handle.modId(), handle.configApplyOvercharge());
        }
        return new OverchargePayload(java.util.Map.copyOf(map));
    }

    /** @return true if the player's client has the bridge's channel */
    public static boolean hasChannel(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(BridgeSettingsPayload.TYPE);
    }

    public static void send(ServerPlayer player) {
        if (hasChannel(player)) {
            // overcharge first: the client applies the settings right away and then already knows applyOvercharge
            sendOvercharge(player, overchargeSnapshot());
            PacketDistributor.sendToPlayer(player, snapshot());
        }
    }

    private static void sendOvercharge(ServerPlayer player, OverchargePayload payload) {
        if (player.connection != null && player.connection.hasChannel(OverchargePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendToAll() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        BridgeSettingsPayload payload = snapshot();
        OverchargePayload overcharge = overchargeSnapshot();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasChannel(player)) {
                sendOvercharge(player, overcharge);
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
