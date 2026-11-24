package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/** The server values last received by this client (no client classes, so common code may read it). */
public final class ClientBridgeState {
    private static volatile BridgeSettingsPayload current = null;
    private static volatile Map<String, BridgeSettingsPayload.AdapterState> byId = Map.of();

    private ClientBridgeState() {}

    static void accept(BridgeSettingsPayload payload) {
        byId = payload.adapters().stream().collect(Collectors.toUnmodifiableMap(BridgeSettingsPayload.AdapterState::modId, a -> a));
        current = payload;
    }

    /** Forget everything when leaving a server. */
    public static void reset() {
        current = null;
        byId = Map.of();
    }

    public static boolean isActive(String modId) {
        BridgeSettingsPayload.AdapterState state = byId.get(modId);
        return state != null && state.active();
    }

    public static @Nullable BridgeSettingsPayload.AdapterState get(String modId) {
        return byId.get(modId);
    }

    public static boolean foreignSpellsUseDebt() {
        BridgeSettingsPayload p = current;
        return p == null || p.foreignSpellsUseDebt();
    }

    public static String leader() {
        BridgeSettingsPayload p = current;
        return p == null ? Adapters.RUNELESS : p.leader();
    }

    public static boolean received() {
        return current != null;
    }
}
