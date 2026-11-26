package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * The server values last received by this client (no client classes, so common code may read it). Without a payload
 * (the server has no bridge) every adapter counts as off and RunelessRPG as the leader: the bridge does nothing here.
 */
public final class ClientBridgeState {
    private static volatile BridgeSettingsPayload current = null;
    private static volatile Map<String, BridgeSettingsPayload.AdapterState> byId = Map.of();
    /** {@code applyOvercharge} per adapter on the server; empty = server without it (0.1.0) or no server values yet. */
    private static volatile Map<String, Boolean> overcharge = Map.of();
    /** Per adapter: another bridge pays its spells itself (server value, phase C5C.1); empty = not sent (server 0.1.0). */
    private static volatile Map<String, Boolean> paidByOtherBridge = Map.of();
    /** Called on the client after new values arrived (set by the client part, which may use client classes). */
    private static volatile Runnable onUpdate = () -> {};

    private ClientBridgeState() {}

    static void accept(BridgeSettingsPayload payload) {
        byId = payload.adapters().stream().collect(Collectors.toUnmodifiableMap(BridgeSettingsPayload.AdapterState::modId, a -> a));
        current = payload;
        onUpdate.run();
    }

    /** Sets what runs on the client right after new values arrived. */
    public static void setOnUpdate(Runnable listener) {
        onUpdate = listener == null ? () -> {} : listener;
    }

    /** Opens / closes the first-start question on the client (set by the client part). */
    private static volatile java.util.function.Consumer<ManaSystemPayloads.Ask> onAsk = p -> {};
    private static volatile Runnable onCloseAsk = () -> {};

    public static void setAskHandlers(java.util.function.Consumer<ManaSystemPayloads.Ask> ask, Runnable close) {
        onAsk = ask;
        onCloseAsk = close;
    }

    static void ask(ManaSystemPayloads.Ask payload) {
        onAsk.accept(payload);
    }

    static void closeAsk() {
        onCloseAsk.run();
    }

    static void acceptOvercharge(OverchargePayload payload) {
        overcharge = Map.copyOf(payload.applyOvercharge());
        paidByOtherBridge = Map.copyOf(payload.paidByOtherBridge());
        onUpdate.run();
    }

    /**
     * @return true while the server says that another bridge pays this mod's spells itself (Ars 'n Spells): the client
     *         then shows their costs without {@code mana_cost} and overcharge of the bridge and no overcharge line
     * @since 0.2.0 (phase C5C.1)
     */
    public static boolean paidByOtherBridge(String modId) {
        return paidByOtherBridge.getOrDefault(modId, false);
    }

    /**
     * @return the server's {@code applyOvercharge} for this adapter; false while the server has not sent it (a server
     *         with the bridge 0.1.0 does not apply overcharge to spells of other mods)
     * @since 0.2.0 (phase C5B)
     */
    public static boolean applyOvercharge(String modId) {
        return overcharge.getOrDefault(modId, false);
    }

    /** Forget everything when leaving a server. */
    public static void reset() {
        current = null;
        byId = Map.of();
        overcharge = Map.of();
        paidByOtherBridge = Map.of();
    }

    /** @return true if the adapter is connected on the server (leading or following) */
    public static boolean isConnected(String modId) {
        BridgeSettingsPayload.AdapterState state = byId.get(modId);
        return state != null && state.connected();
    }

    public static @Nullable BridgeSettingsPayload.AdapterState get(String modId) {
        return byId.get(modId);
    }

    public static boolean foreignSpellsUseDebt() {
        BridgeSettingsPayload p = current;
        return p == null || p.foreignSpellsUseDebt();
    }

    /** @return the leading pool on the server, RunelessRPG without a payload */
    public static String leader() {
        BridgeSettingsPayload p = current;
        return p == null ? Adapters.RUNELESS : p.leader();
    }

    public static boolean received() {
        return current != null;
    }
}
