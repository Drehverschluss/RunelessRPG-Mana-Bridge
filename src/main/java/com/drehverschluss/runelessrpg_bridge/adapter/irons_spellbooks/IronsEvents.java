package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus listeners for Iron's (registered only while Iron's is loaded). Every listener catches its own errors. */
public final class IronsEvents {
    private static boolean errorLogged;
    private static boolean overchargeErrorLogged;

    private IronsEvents() {}

    /**
     * Payment of an Iron's cast (every tick of a channeled spell too): at the lowest priority, so changes of other addons
     * count. Iron's own deduction is set to 0 and RunelessRPG pays the effective cost with the debt rules (reason
     * {@code EXTERNAL}). Same conditions as Iron's: only sources that consume mana, not for a recast. Creative is decided
     * by RunelessRPG ({@code creativeFreeCasting}). Then the cast starts for overcharge (paid or not, {@link IronsOvercharge}).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean pays = false;
        boolean recast = false;
        try {
            recast = MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(event.getSpellId());
            if (IronsHooks.connected(player) && event.getCastSource().consumesMana() && !recast) {
                int cost = event.getManaCost();
                event.setManaCost(0);
                if (cost > 0) {
                    Pool.pay(player, IronsAdapter.ID, cost);
                    pays = true;
                }
            }
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                ManaBridge.LOGGER.error("Iron's cast payment failed, Iron's pays itself for this cast", e);
            }
        }
        try {
            IronsOvercharge.castStarts(player, event.getSpellId(), pays, recast);
        } catch (RuntimeException e) {
            overchargeError("starting a cast", e);
        }
    }

    /** Overcharge on Iron's spell damage (after every other listener, before Iron's school resistance). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellDamage(SpellDamageEvent event) {
        try {
            if (!event.getEntity().level().isClientSide()) {
                IronsOvercharge.onDamage(event);
            }
        } catch (RuntimeException e) {
            overchargeError("SpellDamageEvent", e);
        }
    }

    /** Overcharge on Iron's spell healing (the heal follows right after this event). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellHeal(SpellHealEvent event) {
        try {
            IronsOvercharge.onHeal(event);
        } catch (RuntimeException e) {
            overchargeError("SpellHealEvent", e);
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        IronsOvercharge.clearTick();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        IronsOvercharge.forget(event.getEntity().getUUID());
    }

    private static void overchargeError(String what, Throwable e) {
        if (!overchargeErrorLogged) {
            overchargeErrorLogged = true;
            ManaBridge.LOGGER.error("Iron's adapter: overcharge ({}) failed", what, e);
        }
    }
}
