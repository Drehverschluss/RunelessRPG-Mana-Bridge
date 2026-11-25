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

    /** Forget everything when leaving a server. */
    public static void reset() {
        current = null;
        byId = Map.of();
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
