package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg.api.ManaChangeEvent;
import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.core.network.BridgeSettingsPayload;
import com.drehverschluss.runelessrpg_bridge.core.network.ClientBridgeState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Switch between a following mod and the shared pool, in the following mod's own units ("own mana"). Everything is
 * computed in RunelessRPG mana through RunelessRPG's API; adapters' hooks only call these methods.
 */
public final class Pool {
    /** Rounding guard for values like 13.4 / 0.2 = 66.99999…. */
    private static final double EPSILON = 1.0E-6;

    private Pool() {}

    /** @return true for real players: server players that are no fake players, or the local player on the client */
    public static boolean isRealPlayer(@Nullable Player player) {
        if (player == null || player instanceof FakePlayer) {
            return false;
        }
        return player instanceof ServerPlayer || player.level().isClientSide();
    }

    /** @return true if the adapter follows the leading pool and the player is a real player (the switch applies) */
    public static boolean applies(String modId, @Nullable Player player) {
        if (!isRealPlayer(player)) {
            return false;
        }
        AdapterHandle handle = Adapters.getOrNull(modId);
        return handle != null && handle.isActive();
    }

    /**
     * @return true if the adapter is connected (leading or following) and the player is a real player: costs of the
     *         mod's spells, Spell Mana Leech and converted displays use this
     */
    public static boolean connected(String modId, @Nullable Player player) {
        if (!isRealPlayer(player)) {
            return false;
        }
        AdapterHandle handle = Adapters.getOrNull(modId);
        return handle != null && handle.isConnected();
    }

    // ------------------------------------------------------------------ factor

    /** 1 own mana = factor RunelessRPG mana: the configured value, or RunelessRPG base max mana ÷ the mod's base max mana. */
    public static double factor(@Nullable Player player, String modId) {
        AdapterHandle handle = Adapters.getOrNull(modId);
        if (handle == null) {
            return 1.0;
        }
        double configured = configuredFactor(handle, player);
        if (configured > 0.0) {
            return configured;
        }
        AdapterHandle runeless = Adapters.getOrNull(Adapters.RUNELESS);
        double runelessBase = runeless == null ? 20.0 : runeless.adapter().baseMaxMana(player);
        double ownBase = handle.adapter().baseMaxMana(player);
        if (!(runelessBase > 0.0) || !(ownBase > 0.0)) {
            return 1.0;
        }
        return runelessBase / ownBase;
    }

    private static double configuredFactor(AdapterHandle handle, @Nullable Player player) {
        if (isClient(player)) {
            BridgeSettingsPayload.AdapterState state = ClientBridgeState.get(handle.modId());
            return state == null ? -1.0 : state.manaFactor();
        }
        try {
            return handle.configFactor();
        } catch (IllegalStateException e) {
            return -1.0;
        }
    }

    private static boolean isClient(@Nullable Player player) {
        return player != null ? player.level().isClientSide() : EffectiveSide.get() == LogicalSide.CLIENT;
    }

    // ------------------------------------------------------------------ reading and writing (own units)

    /**
     * Last value a following mod wrote on the server, per mod and player, with RunelessRPG's mana right after it and the
     * factor. While RunelessRPG's mana is still exactly that value, reading gives back exactly what was written: the way
     * through RunelessRPG mana and back (× factor, ÷ factor) can be off in the last bit (32 → 31.999999999999996), and
     * other bridges compare the value after a debit exactly (Ars 'n Spells cancels the cast with RESOURCE_CHANGED).
     */
    private record Echo(double own, double runeless, double factor) {}

    private static final Map<String, Map<UUID, Echo>> ECHO = new ConcurrentHashMap<>();

    /** Shared mana in own units; 0 while the player is in debt (other mods cannot show debt). */
    public static double read(Player player, String modId) {
        double mana = RunelessApi.getMana(player);
        double factor = factor(player, modId);
        if (player instanceof ServerPlayer) {
            Map<UUID, Echo> echoes = ECHO.get(modId);
            Echo echo = echoes == null ? null : echoes.get(player.getUUID());
            if (echo != null && echo.runeless() == mana && echo.factor() == factor) {
                return echo.own();
            }
        }
        return Math.max(0.0, mana) / factor;
    }

    /** Forgets the written values of a player (logout). */
    public static void forget(Player player) {
        for (Map<UUID, Echo> echoes : ECHO.values()) {
            echoes.remove(player.getUUID());
        }
    }

    /** Shared max mana in own units. */
    public static double readMax(Player player, String modId) {
        return RunelessApi.getMaxMana(player) / factor(player, modId);
    }

    /** Whole own mana for mods that show integers (rounded down, with a guard against 66.99999…). */
    public static int readInt(Player player, String modId) {
        return (int) Math.floor(read(player, modId) + EPSILON);
    }

    /** Whole own max mana (rounded down, with the same guard). */
    public static int readMaxInt(Player player, String modId) {
        return (int) Math.floor(readMax(player, modId) + EPSILON);
    }

    /**
     * Applies a change the following mod made to its own value: the difference × factor goes to RunelessRPG with the reason
     * {@code EXTERNAL} (more mana pays debt first and never exceeds the max; less never creates debt).
     */
    public static void write(ServerPlayer player, String modId, double oldOwn, double newOwn) {
        double factor = factor(player, modId);
        double delta = (newOwn - oldOwn) * factor;
        if (delta > 0.0) {
            RunelessApi.giveMana(player, delta, ManaChangeEvent.Reason.EXTERNAL);
        } else if (delta < 0.0) {
            RunelessApi.takeMana(player, -delta, ManaChangeEvent.Reason.EXTERNAL);
        }
        // remember the written value only if the pool really holds it (not capped at the max, not stopped at 0 or in debt)
        double mana = RunelessApi.getMana(player);
        Map<UUID, Echo> echoes = ECHO.computeIfAbsent(modId, id -> new ConcurrentHashMap<>());
        if (mana >= 0.0 && Math.abs(mana / factor - newOwn) <= EPSILON) {
            echoes.put(player.getUUID(), new Echo(newOwn, mana, factor));
        } else {
            echoes.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------ costs

    /**
     * The cost multiplier of RunelessRPG for spells of this mod (plan 4.2, 4.10): {@code max(lower limit, mana_cost)}
     * with {@code applyManaCost}, then {@code × (1 + overcharge)} with {@code applyOvercharge} (overcharge after the
     * lower limit, as for Spell Engine spells). Both off: 1.
     */
    public static double costMultiplier(Player player, String modId) {
        if (isClient(player) && ClientBridgeState.paidByOtherBridge(modId)) {
            // another bridge (Ars 'n Spells) pays these spells itself, without mana_cost and overcharge of this bridge:
            // the client shows and checks what it really takes (phase C5C.1). The server never reaches its hooks then.
            return 1.0;
        }
        boolean manaCost = applyManaCost(player, modId);
        boolean overcharge = applyOvercharge(player, modId);
        if (manaCost && overcharge) {
            return RunelessApi.getCostMultiplierWithOvercharge(player);
        }
        if (manaCost) {
            return RunelessApi.getCostMultiplier(player);
        }
        return overcharge ? RunelessApi.getOverchargeCostFactor(player) : 1.0;
    }

    /**
     * @return true if {@code runelessrpg:overcharge} applies to this mod's spells ({@code applyOvercharge}; on the client
     *         the server's value, false without it)
     * @since 0.2.0 (phase C5B)
     */
    public static boolean applyOvercharge(Player player, String modId) {
        if (isClient(player)) {
            return ClientBridgeState.applyOvercharge(modId);
        }
        AdapterHandle handle = Adapters.getOrNull(modId);
        return handle != null && handle.configApplyOvercharge();
    }

    private static boolean applyManaCost(Player player, String modId) {
        if (isClient(player)) {
            BridgeSettingsPayload.AdapterState state = ClientBridgeState.get(modId);
            return state == null || state.applyManaCost();
        }
        AdapterHandle handle = Adapters.getOrNull(modId);
        return handle == null || handle.configApplyManaCost();
    }

    /** Effective cost in own units: own cost × max(lower limit, mana_cost) × (1 + overcharge), see {@link #costMultiplier}. */
    public static double effectiveCost(Player player, String modId, double ownCost) {
        return ownCost * costMultiplier(player, modId);
    }

    /** What RunelessRPG pays for an own cost: effective cost × factor. */
    public static double runelessCost(Player player, String modId, double ownCost) {
        return effectiveCost(player, modId, ownCost) * factor(player, modId);
    }

    public static boolean foreignSpellsUseDebt(Player player) {
        if (isClient(player)) {
            return ClientBridgeState.foreignSpellsUseDebt();
        }
        return BridgeConfig.get(BridgeConfig.FOREIGN_SPELLS_USE_DEBT, true);
    }

    /** Same check on client and server: can the player pay this own cost from the shared pool? */
    public static boolean canAfford(Player player, String modId, double ownCost) {
        return RunelessApi.canAfford(player, runelessCost(player, modId, ownCost), foreignSpellsUseDebt(player));
    }

    /** @return the missing RunelessRPG mana for this own cost (0 if affordable) */
    public static double missing(Player player, String modId, double ownCost) {
        return RunelessApi.getMissingMana(player, runelessCost(player, modId, ownCost), foreignSpellsUseDebt(player));
    }

    /**
     * Pays an own cost from the shared pool with the debt rules of Spell Engine spells. If the player cannot pay, the
     * pool goes down to 0 at most (like the mod would do itself) and RunelessRPG's bar flashes.
     *
     * @return true if paid in full
     */
    public static boolean pay(ServerPlayer player, String modId, double ownCost) {
        double amount = runelessCost(player, modId, ownCost);
        boolean debt = foreignSpellsUseDebt(player);
        if (RunelessApi.spend(player, amount, debt)) {
            return true;
        }
        double missing = RunelessApi.getMissingMana(player, amount, debt);
        RunelessApi.takeMana(player, amount, ManaChangeEvent.Reason.EXTERNAL);
        RunelessApi.signalInsufficientMana(player, missing);
        return false;
    }

    /** Red flash of RunelessRPG's bar and the client event for a failed cast of an own cost. */
    public static void signalInsufficient(ServerPlayer player, String modId, double ownCost) {
        RunelessApi.signalInsufficientMana(player, missing(player, modId, ownCost));
    }
}
