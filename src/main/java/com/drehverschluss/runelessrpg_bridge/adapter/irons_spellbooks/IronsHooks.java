package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg_bridge.core.Pool;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Server and common hooks for Iron's, called from the mixins. Every method first checks that the adapter applies. */
public final class IronsHooks {
    static final String ID = IronsAdapter.ID;
    private static final String CAST_ERROR_MANA = "ui.irons_spellbooks.cast_error_mana";
    /** Returned to Iron's channel check instead of its own mana: far above or below any cost. */
    private static final float ENOUGH = 1.0E9F;

    private IronsHooks() {}

    /**
     * @return true if the switch of Iron's own mana applies to this player: real player, Iron's follows the leading pool,
     *         and Iron's storage is not RunelessRPG's storage (while the storage changes, RunelessRPG reads and writes
     *         Iron's own value, which must not go through the switch again)
     */
    public static boolean applies(@Nullable Player player) {
        return IronsAdapter.active() && Pool.applies(ID, player) && !IronsStorage.isActive();
    }

    /** @return true if costs, leech and converted displays apply to this player: real player, Iron's connected (leading or following) */
    public static boolean connected(@Nullable Player player) {
        return IronsAdapter.connected() && Pool.connected(ID, player);
    }

    public static boolean disableOwnRegen() {
        return IronsAdapter.disableOwnRegen();
    }

    public static boolean ignoreOwnRespawnRule() {
        return IronsAdapter.ignoreOwnRespawnRule();
    }

    /** @return true if the attribute is {@code irons_spellbooks:max_mana} */
    public static boolean isMaxMana(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return com.drehverschluss.runelessrpg_bridge.core.Attributes.is(attribute, IronsAdapter.MAX_MANA);
    }

    // ------------------------------------------------------------------ MagicData: read and write

    /** Shared mana in Iron's units (0 in debt). */
    public static float getMana(ServerPlayer player) {
        return (float) Pool.read(player, ID);
    }

    /**
     * Replaces {@code MagicData.setMana} for players: fires Iron's {@link ChangeManaEvent} as before (other Iron's addons
     * listen to it), then sends the difference to the shared pool.
     */
    public static void setMana(MagicData data, ServerPlayer player, float newMana) {
        float oldMana = getMana(player);
        ChangeManaEvent event = new ChangeManaEvent(player, data, oldMana, newMana);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return;
        }
        double target = Math.max(0.0, Math.min(event.getNewMana(), Pool.readMax(player, ID)));
        Pool.write(player, ID, oldMana, target);
    }

    /** Shared max mana in Iron's units, for places where Iron's limits or scales the pool. */
    public static double maxMana(Player player) {
        return Pool.readMax(player, ID);
    }

    // ------------------------------------------------------------------ casting

    /**
     * Final word on {@code AbstractSpell.canBeCastedBy}: for casts that cost mana the check is "can RunelessRPG pay the
     * effective cost" (with the debt rules), the same check the client makes. Other failures (cooldown, not learned,
     * adventure mode) stay. A failure because of mana also flashes RunelessRPG's bar.
     */
    public static CastResult checkCast(AbstractSpell spell, int spellLevel, CastSource source, MagicData data, Player player, CastResult result) {
        if (!connected(player) || !source.consumesMana() || data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId())) {
            return result;
        }
        boolean manaFailure = !result.isSuccess() && isManaError(result);
        if (!result.isSuccess() && !manaFailure) {
            return result;
        }
        int cost = spell.getManaCost(spellLevel);
        if (Pool.canAfford(player, ID, cost)) {
            return manaFailure ? new CastResult(CastResult.Type.SUCCESS) : result;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Pool.signalInsufficient(serverPlayer, ID, cost);
        }
        return manaFailure ? result : new CastResult(CastResult.Type.FAILURE,
                Component.translatable(CAST_ERROR_MANA, spell.getDisplayName(player)).withStyle(ChatFormatting.RED));
    }

    private static boolean isManaError(CastResult result) {
        return result.message != null && result.message.getContents() instanceof TranslatableContents contents
                && CAST_ERROR_MANA.equals(contents.getKey());
    }

    /**
     * Iron's channel check {@code mana - 2 × cost < 0} ends the channel after this tick. Here: the channel goes on while
     * RunelessRPG could pay two more ticks of the effective cost (same look-ahead as Iron's, with the debt rules).
     */
    public static float channelMana(ServerPlayer player, MagicData data, float ownMana) {
        if (!connected(player)) {
            return ownMana;
        }
        AbstractSpell spell = SpellRegistry.getSpell(data.getCastingSpellId());
        if (spell == null) {
            return ownMana;
        }
        int cost = spell.getManaCost(data.getCastingSpellLevel());
        return Pool.canAfford(player, ID, 2.0 * cost) ? ENOUGH : -ENOUGH;
    }

    /** Client check before casting from the item (only decides the hand animation; the server checks again). */
    public static int clientCastCost(Player player, int cost) {
        if (!connected(player)) {
            return cost;
        }
        return Pool.canAfford(player, ID, cost) ? 0 : Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------ items

    /** Iron's casting items: spell books, staves and other {@link CastingItem}s, items with a spell container. */
    public static boolean isCastingItem(ItemStack stack) {
        return stack.getItem() instanceof CastingItem
                || ISpellContainer.isSpellContainer(stack) && !ISpellContainer.get(stack).mustEquip();
    }

    /** @return true if the entity is a real player for whom the switch applies */
    public static boolean appliesTo(LivingEntity entity) {
        return entity instanceof Player player && applies(player);
    }
}
