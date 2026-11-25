package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.api.AdapterStatus;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import com.drehverschluss.runelessrpg_bridge.core.network.ClientBridgeState;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/** The core's view of one adapter: the adapter itself, its generic config values and its state on the server. */
public final class AdapterHandle implements AdapterStatus {
    private final ManaAdapter adapter;
    private final boolean loaded;
    // generic config values; enabled, manaFactor and applyManaCost are null for RunelessRPG (it is always there)
    @Nullable ModConfigSpec.BooleanValue enabled;
    @Nullable ModConfigSpec.DoubleValue manaFactor;
    @Nullable ModConfigSpec.BooleanValue applyManaCost;
    @Nullable ModConfigSpec.BooleanValue bridgeMaxMana;
    @Nullable ModConfigSpec.BooleanValue bridgeManaRegen;
    @Nullable ModConfigSpec.BooleanValue bridgePercentBonuses;

    // server state
    private volatile String selfTestProblem = "";
    private volatile String conflict = "";
    private volatile String chained = "";

    AdapterHandle(ManaAdapter adapter, boolean loaded) {
        this.adapter = adapter;
        this.loaded = loaded;
    }

    public ManaAdapter adapter() {
        return adapter;
    }

    public String modId() {
        return adapter.modId();
    }

    /** @return true if the adapter's mod is loaded (only then are its hooks used) */
    public boolean loaded() {
        return loaded;
    }

    public boolean isRuneless() {
        return modId().equals(Adapters.RUNELESS);
    }

    void setSelfTestProblem(String problem) {
        selfTestProblem = problem == null ? "" : problem;
    }

    public String selfTestProblem() {
        return selfTestProblem;
    }

    /** @return the other bridge that blocks this adapter right now (empty if none), server side */
    public String conflict() {
        return conflict;
    }

    void setConflict(String conflict) {
        this.conflict = conflict == null ? "" : conflict;
    }

    /** @return other bridges that are active but chained with this adapter (empty if none), server side */
    public String chained() {
        return chained;
    }

    void setChained(String chained) {
        this.chained = chained == null ? "" : chained;
    }

    public boolean configEnabled() {
        return BridgeConfig.get(enabled, true);
    }

    /** Configured factor (server value, -1 = auto). */
    public double configFactor() {
        return manaFactor == null ? -1.0 : manaFactor.get();
    }

    public boolean configApplyManaCost() {
        return BridgeConfig.get(applyManaCost, true);
    }

    public boolean bridgeMaxMana() {
        return BridgeConfig.get(bridgeMaxMana, true);
    }

    public boolean bridgeManaRegen() {
        return BridgeConfig.get(bridgeManaRegen, true);
    }

    public boolean bridgePercentBonuses() {
        return BridgeConfig.get(bridgePercentBonuses, true);
    }

    // ------------------------------------------------------------------ state

    /** Server state: connected to the shared pool (leading or following). RunelessRPG is always connected. */
    public boolean isConnectedOnServer() {
        return reasonOnServer().isEmpty();
    }

    /** Why the adapter is not connected on the server (empty = connected). */
    public String reasonOnServer() {
        if (isRuneless()) {
            return "";
        }
        if (!loaded) {
            return "mod not installed";
        }
        if (!selfTestProblem.isEmpty()) {
            return "self test failed: " + selfTestProblem;
        }
        if (!configEnabled()) {
            return "disabled in the config";
        }
        if (!conflict.isEmpty() && !BridgeConfig.get(BridgeConfig.IGNORE_CONFLICTS, false)) {
            return "another bridge is active: " + conflict;
        }
        return "";
    }

    private static boolean client() {
        return EffectiveSide.get() == LogicalSide.CLIENT;
    }

    @Override
    public boolean isConnected() {
        if (isRuneless()) {
            return true;
        }
        // on a client the server decides; a failed client self test turns the client hooks off on this client only
        return client() ? ClientBridgeState.isConnected(modId()) && selfTestProblem.isEmpty() : isConnectedOnServer();
    }

    /** @return true if this adapter's mod leads the pool (client: as the server said; without a bridge on the server RunelessRPG) */
    @Override
    public boolean isLeading() {
        String leader = client() ? ClientBridgeState.leader() : Leader.id();
        return modId().equals(leader);
    }

    /** Same as {@link #isLeading()}. */
    public boolean isLeader() {
        return isLeading();
    }

    /** Server state: follows the leading pool. */
    public boolean isActiveOnServer() {
        return isConnectedOnServer() && !modId().equals(Leader.id());
    }

    @Override
    public String reason() {
        if (client()) {
            if (!ClientBridgeState.received()) {
                return "the server has no RunelessRPG Mana Bridge";
            }
            return isConnected() ? "" : "off on the server";
        }
        return reasonOnServer();
    }
}
