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
    private final boolean leaderCapable;
    // generic config values (null for the adapter of RunelessRPG, which always leads in this version)
    @Nullable ModConfigSpec.BooleanValue enabled;
    @Nullable ModConfigSpec.DoubleValue manaFactor;
    @Nullable ModConfigSpec.BooleanValue applyManaCost;

    // server state
    private volatile String selfTestProblem = "";
    private volatile String conflict = "";

    AdapterHandle(ManaAdapter adapter) {
        this.adapter = adapter;
        this.leaderCapable = adapter.canLead();
    }

    public ManaAdapter adapter() {
        return adapter;
    }

    public String modId() {
        return adapter.modId();
    }

    /** @return true for the adapter that leads the pool (RunelessRPG in this version) */
    public boolean isLeader() {
        return leaderCapable && modId().equals(Adapters.leaderId());
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

    /** Server state: active as a following adapter. */
    public boolean isActiveOnServer() {
        return !isLeader() && reasonOnServer().isEmpty();
    }

    /** Why the adapter is off on the server (empty = active, or it leads). */
    public String reasonOnServer() {
        if (isLeader()) {
            return "leads the mana pool";
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

    @Override
    public boolean isActive() {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            return ClientBridgeState.isActive(modId());
        }
        return isActiveOnServer();
    }

    @Override
    public String reason() {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            return ClientBridgeState.isActive(modId()) ? "" : "off on the server";
        }
        return reasonOnServer();
    }
}
