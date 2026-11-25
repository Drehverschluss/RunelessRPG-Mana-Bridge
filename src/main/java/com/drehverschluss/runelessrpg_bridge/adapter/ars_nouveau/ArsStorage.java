package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg.api.ManaStorage;
import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ars Nouveau's mana as RunelessRPG's storage while Ars leads ({@code manaSystem = ars_nouveau}, plan 4.4). All values in
 * RunelessRPG mana: Ars mana × factor. The max is the one Ars stores, so the mana its familiars reserve is already taken
 * off. Writing goes through {@link ManaCap#setMana} (Ars' own limit 0 … max). Ars cannot go below 0 (RunelessRPG keeps the
 * debt account), regenerates itself and has its own rules for login, respawn and death (its mana is kept on death).
 * <p>
 * Ars only sends its mana to the client every 5 ticks while the pool is not full, and every 60 ticks otherwise. So that
 * Ars' bar follows a change from outside at once (Spell Engine spells, potions, orbs, leech, Iron's spells), a change of
 * the whole number, or one that reaches 0 or the max, marks the player; Ars' own sync packet then goes out once at the end
 * of the server tick. At most one extra packet per player and tick.
 */
public final class ArsStorage implements ManaStorage {
    public static final ArsStorage INSTANCE = new ArsStorage();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, ArsAdapter.ID);
    /** Players whose Ars mana changed from outside in this tick and whose client needs Ars' packet. */
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

    private ArsStorage() {}

    /** @return true while this storage is RunelessRPG's active storage (server side) */
    public static boolean isActive() {
        return RunelessApi.getStorage() == INSTANCE;
    }

    private static double factor(ServerPlayer player) {
        return Pool.factor(player, ArsAdapter.ID);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    /** Ars mana × factor; above a max that just went down Ars cuts it at its next write, here it counts as the max. */
    @Override
    public double getMana(ServerPlayer player) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(cap.getCurrentMana(), cap.getMaxMana())) * factor(player);
    }

    @Override
    public double getMaxMana(ServerPlayer player) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        return cap == null ? 0.0 : Math.max(0, cap.getMaxMana()) * factor(player);
    }

    @Override
    public void setMana(ServerPlayer player, double mana) {
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            return;
        }
        double before = cap.getCurrentMana();
        double after = cap.setMana(mana / factor(player));
        int max = cap.getMaxMana();
        boolean boundary = after != before && (after <= 0.0 || after >= max);
        if (boundary || Math.floor(after) != Math.floor(before)) {
            DIRTY.add(player.getUUID());
        }
    }

    @Override
    public boolean canGoNegative() {
        return false;
    }

    @Override
    public boolean regeneratesItself() {
        return true;
    }

    @Override
    public boolean handlesLifecycle() {
        return true;
    }

    /**
     * Nothing: flat regeneration bonuses of the other mods are modifiers on Ars' own {@code ars_nouveau.perk.mana_regen}
     * (like its gear), so Ars' regeneration pays them, its percent modifiers count on them like in RunelessRPG, and debt is
     * paid back by the same regeneration. Paying them here as well would count them twice.
     */
    @Override
    public double bonusRegenPerSecond(ServerPlayer player) {
        return 0.0;
    }

    /** End of the server tick: Ars' sync packet for every player whose Ars mana changed from outside. */
    static void flush(MinecraftServer server) {
        if (DIRTY.isEmpty()) {
            return;
        }
        for (UUID id : DIRTY.toArray(UUID[]::new)) {
            DIRTY.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.connection == null || !isActive()) {
                continue;
            }
            ManaCap cap = CapabilityRegistry.getMana(player);
            if (cap != null) {
                cap.syncToClient(player);
            }
        }
    }

    static void forget(UUID player) {
        DIRTY.remove(player);
    }
}
