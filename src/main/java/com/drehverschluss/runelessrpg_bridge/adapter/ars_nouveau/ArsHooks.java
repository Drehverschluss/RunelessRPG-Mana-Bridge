package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.hollingsworth.arsnouveau.api.client.IDisplayMana;
import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.setup.config.ServerConfig;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Server and common hooks for Ars Nouveau, called from the mixins and listeners. Every method checks that the adapter applies. */
public final class ArsHooks {
    static final String ID = ArsAdapter.ID;
    /** Ars' own modifier on its max mana attribute: base max + glyphs + book tier (progress, not gear). */
    private static final ResourceLocation PROGRESS_MAX = ResourceLocation.fromNamespaceAndPath(ID, "max_mana_mod");
    /** Ars' own modifier on its mana regen attribute: base regen + glyphs + book tier. */
    private static final ResourceLocation PROGRESS_REGEN = ResourceLocation.fromNamespaceAndPath(ID, "max_regen_mod");

    /** What addons changed in Ars' max mana and regeneration events, and the familiars' reserve, per player (server). */
    record Calc(double maxDelta, double regenDelta, float reserve) {}

    static final Map<UUID, Calc> CALC = new ConcurrentHashMap<>();
    /** Last cost Ars checked for a player (for the flash of RunelessRPG's bar after a failed check), server thread only. */
    private static final Map<UUID, Integer> LAST_CHECK = new ConcurrentHashMap<>();

    private ArsHooks() {}

    /** Server players, or the local player on the client (Ars reads other players' data on the client only in rare cases). */
    private static boolean ownSide(Player player) {
        return player.level().isClientSide() ? player.isLocalPlayer() : player instanceof ServerPlayer;
    }

    /**
     * @return true if the switch of Ars' own mana applies to this player: real player, Ars follows the leading pool, and
     *         Ars' storage is not RunelessRPG's storage (while the storage changes, RunelessRPG reads and writes Ars' own
     *         value, which must not go through the switch again)
     */
    public static boolean applies(@Nullable Player player) {
        return ArsAdapter.active() && Pool.applies(ID, player) && ownSide(player) && !ArsStorage.isActive();
    }

    /** @return true if costs, leech and converted displays apply to this player (Ars connected, leading or following) */
    public static boolean connected(@Nullable Player player) {
        return ArsAdapter.connected() && Pool.connected(ID, player) && ownSide(player);
    }

    public static boolean disableOwnRegen() {
        return ArsAdapter.disableOwnRegen();
    }

    // ------------------------------------------------------------------ config values of Ars

    /** Ars' {@code baseMax} (default 100). */
    static double baseMaxMana() {
        try {
            return ServerConfig.INIT_MAX_MANA.get();
        } catch (RuntimeException e) {
            return 100.0;
        }
    }

    /** Ars' {@code baseRegen} per second (default 5). */
    static double baseRegen() {
        return config(ServerConfig.INIT_MANA_REGEN::get, 5.0);
    }

    private static double config(java.util.function.Supplier<? extends Number> value, double fallback) {
        try {
            return value.get().doubleValue();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ ManaCap: read and write

    /** Shared mana in Ars units (0 in debt). */
    public static double getMana(Player player) {
        return Pool.read(player, ID);
    }

    /** Shared max mana in whole Ars units (Ars keeps the max as an integer). */
    public static int getMaxMana(Player player) {
        return Pool.readMaxInt(player, ID);
    }

    /**
     * Replaces {@code ManaCap.setMana} for players on the server (also {@code addMana} and {@code removeMana}, which go
     * through it: Ring of Jumping, Repairing perk, Void Jar, addons): the difference to the shared pool, limited like Ars
     * does (0 to max). {@code EXTERNAL} in RunelessRPG: more mana pays debt first, less never creates debt.
     *
     * @return the shared mana afterwards in Ars units, as Ars' method returns it
     */
    public static double setMana(ServerPlayer player, double mana) {
        double old = getMana(player);
        double target = Double.isFinite(mana) ? Math.max(0.0, Math.min(mana, Pool.readMax(player, ID))) : old;
        Pool.write(player, ID, old, target);
        return getMana(player);
    }

    /** {@code ManaUtil.getMaxMana} (spell book bar, tome limit): the shared max for players. */
    public static int utilMaxMana(@Nullable Player player, int original) {
        return applies(player) ? getMaxMana(player) : original;
    }

    // ------------------------------------------------------------------ casting

    /**
     * {@code LivingCaster.enoughMana}: for real players the check is "can RunelessRPG pay the effective cost" (Ars cost
     * after its discounts and the cost events × max(lower limit, mana_cost) × factor, with the debt rules), on the client
     * and the server alike. Creative follows RunelessRPG ({@code creativeFreeCasting}).
     *
     * @return the result, or null to keep Ars' own check
     */
    public static @Nullable Boolean enoughMana(LivingEntity entity, int cost) {
        if (!(entity instanceof Player player) || !connected(player)) {
            return null;
        }
        if (player instanceof ServerPlayer) {
            LAST_CHECK.put(player.getUUID(), cost);
        }
        return Pool.canAfford(player, ID, cost);
    }

    /**
     * {@code LivingCaster.expendMana}: RunelessRPG pays the effective cost with the debt rules (reason {@code EXTERNAL});
     * Ars' own deduction is skipped.
     *
     * @return true if paid here (Ars' own deduction must not run)
     */
    public static boolean expendMana(LivingEntity entity, int cost) {
        if (!(entity instanceof ServerPlayer player) || !connected(player)) {
            return false;
        }
        try {
            if (cost > 0) {
                Pool.pay(player, ID, cost);
            }
            try {
                ArsOvercharge.onPaid(player, cost);
            } catch (RuntimeException e) {
                ManaBridge.LOGGER.debug("Ars overcharge: marking a paid cast failed", e);
            }
            return true;
        } catch (RuntimeException e) {
            ManaBridge.LOGGER.error("Ars cast payment failed, Ars pays itself for this cast", e);
            return false;
        }
    }

    /** {@code SpellResolver.expendMana} starts (overcharge: this context is the one being paid). */
    public static void expendStarts(@Nullable com.hollingsworth.arsnouveau.api.spell.SpellContext context) {
        ArsOvercharge.expendStarts(context);
    }

    /** {@code RuneTile.setSpell} (overcharge: snapshot of a rune a paid spell places). */
    public static void runeSpell(net.minecraft.world.level.block.entity.BlockEntity tile) {
        try {
            ArsOvercharge.onRuneSpell(tile);
        } catch (RuntimeException e) {
            ManaBridge.LOGGER.debug("Ars overcharge: rune snapshot failed", e);
        }
    }

    /** After {@code SpellResolver.enoughMana} failed on the server (and is not silent): RunelessRPG's bar flashes too. */
    public static void afterManaCheck(SpellResolver resolver, LivingEntity entity, boolean result) {
        if (result || resolver.silent || !(entity instanceof ServerPlayer player) || !connected(player)) {
            return;
        }
        Integer cost = LAST_CHECK.get(player.getUUID());
        if (cost != null) {
            Pool.signalInsufficient(player, ID, cost);
        }
    }

    // ------------------------------------------------------------------ bridge

    /**
     * Gear: modifiers on {@code ars_nouveau.perk.max_mana} and {@code ars_nouveau.perk.mana_regen} by operation, without
     * Ars' own progress modifiers and this bridge's; plus what addons add in {@code MaxManaCalcEvent} and
     * {@code ManaRegenCalcEvent} (flat, Ars units). Enchantments Mana Boost and Mana Regen, armor tiers and perks, curios
     * and the Mana Regeneration potion are all attribute modifiers.
     */
    static BridgeBonus readGearBonus(Player player) {
        double[] max = Attributes.modifiersByOperation(player, ArsAdapter.MAX_MANA, ManaBridge.MODID, PROGRESS_MAX);
        double[] regen = Attributes.modifiersByOperation(player, ArsAdapter.MANA_REGEN, ManaBridge.MODID, PROGRESS_REGEN);
        Calc calc = CALC.get(player.getUUID());
        double maxDelta = calc == null ? 0.0 : calc.maxDelta();
        double regenDelta = calc == null ? 0.0 : calc.regenDelta();
        return new BridgeBonus(max[0] + maxDelta, max[1], max[2], regen[0] + regenDelta, regen[1], regen[2]);
    }

    /**
     * Progress (glyphs × glyphmax + book tier × tierMax, glyphs × glyphRegen + tier × tierRegen, Ars units) and the
     * reserve the familiars set in {@code MaxManaCalcEvent}; each with its own switch.
     */
    static BridgeBonus readExtraBonus(Player player, boolean progression, boolean reserve) {
        double progressMax = 0.0;
        double progressRegen = 0.0;
        if (progression) {
            ManaCap cap = CapabilityRegistry.getMana(player);
            if (cap != null) {
                int glyphs = cap.getGlyphBonus();
                int tier = cap.getBookTier();
                progressMax = glyphs * config(ServerConfig.GLYPH_MAX_BONUS, 15.0) + tier * config(ServerConfig.TIER_MAX_BONUS, 50.0);
                progressRegen = glyphs * config(ServerConfig.GLYPH_REGEN_BONUS, 0.33) + tier * config(ServerConfig.TIER_REGEN_BONUS, 1.0);
            }
        }
        Calc calc = reserve ? CALC.get(player.getUUID()) : null;
        double reserved = calc == null ? 0.0 : Math.max(0.0, Math.min(1.0, calc.reserve()));
        return new BridgeBonus(progressMax, 0.0, 1.0, progressRegen, 0.0, 1.0, reserved);
    }

    /** Ars' max and reserve computed again and sent, as Ars does every few ticks ({@code ManaCapEvents.playerOnTick}). */
    static void refreshMax(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ManaCap cap = CapabilityRegistry.getMana(player);
        if (cap == null) {
            return;
        }
        ManaUtil.Mana mana = ManaUtil.calcMaxMana(player);
        cap.setMaxMana(mana.getRealMax());
        cap.setReserve(mana.Reserve());
        if (serverPlayer.connection != null) {
            cap.syncToClient(serverPlayer);
        }
    }

    // ------------------------------------------------------------------ items

    /** Ars casting items: everything that shows Ars' mana bar (spell books, wands, ...) and caster tools. */
    public static boolean isCastingItem(ItemStack stack) {
        return stack.getItem() instanceof IDisplayMana display && display.shouldDisplay(stack) || stack.getItem() instanceof ICasterTool;
    }

    static void forget(UUID player) {
        CALC.remove(player);
        LAST_CHECK.remove(player);
        ArsAdapter.LEADING.remove(player);
        ArsStorage.forget(player);
    }
}
