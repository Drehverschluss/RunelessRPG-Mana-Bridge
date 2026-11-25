package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent;
import com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent;
import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus listeners for Ars (registered only while Ars is loaded). Every listener catches its own errors. */
public final class ArsEvents {
    private record Start(Object event, double value) {}

    /** Value of the event when the first listener saw it; read again by the last listener. Server thread. */
    private static final ThreadLocal<Start> START = new ThreadLocal<>();
    private static boolean errorLogged;

    private ArsEvents() {}

    private static void error(String what, Throwable e) {
        if (!errorLogged) {
            errorLogged = true;
            ManaBridge.LOGGER.error("Ars Nouveau adapter: {} failed", what, e);
        }
    }

    // ------------------------------------------------------------------ what addons change in Ars' max and regeneration

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void maxStart(MaxManaCalcEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            START.set(new Start(event, event.getMax()));
        }
    }

    /** Last listener: the change addons made to Ars' max (flat) and the reserve the familiars set. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void maxEnd(MaxManaCalcEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            Start start = START.get();
            START.remove();
            double delta = start != null && start.event() == event ? event.getMax() - start.value() : 0.0;
            ArsHooks.Calc old = ArsHooks.CALC.get(player.getUUID());
            ArsHooks.CALC.put(player.getUUID(), new ArsHooks.Calc(delta, old == null ? 0.0 : old.regenDelta(), event.getReserve()));
        } catch (RuntimeException e) {
            error("reading MaxManaCalcEvent", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void regenStart(ManaRegenCalcEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            START.set(new Start(event, event.getRegen()));
        }
    }

    /** Last listener: the change addons made to Ars' regeneration (flat, Ars mana per second). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void regenEnd(ManaRegenCalcEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            Start start = START.get();
            START.remove();
            double delta = start != null && start.event() == event ? event.getRegen() - start.value() : 0.0;
            ArsHooks.Calc old = ArsHooks.CALC.get(player.getUUID());
            ArsHooks.CALC.put(player.getUUID(), new ArsHooks.Calc(old == null ? 0.0 : old.maxDelta(), delta, old == null ? 0.0F : old.reserve()));
        } catch (RuntimeException e) {
            error("reading ManaRegenCalcEvent", e);
        }
    }

    // ------------------------------------------------------------------ Spell Mana Leech: which Ars hit is happening right now

    /** Last listener before the damage: remember the hit for {@link ArsLeech} (the damage events run inside it). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damagePre(SpellDamageEvent.Pre event) {
        try {
            if (event.caster instanceof ServerPlayer player && Pool.isRealPlayer(player)) {
                ArsLeech.push(event, player);
            }
        } catch (RuntimeException e) {
            error("Spell Mana Leech (SpellDamageEvent.Pre)", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damagePost(SpellDamageEvent.Post event) {
        try {
            ArsLeech.pop(event.damageSource);
        } catch (RuntimeException e) {
            error("Spell Mana Leech (SpellDamageEvent.Post)", e);
        }
    }

    /** A hit Ars cancelled after Pre (or whose damage did not go through) never reaches Post: never keep one longer than a tick. */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        ArsLeech.clear();
        try {
            ArsStorage.flush(event.getServer());
        } catch (RuntimeException e) {
            error("sending Ars' mana to the client", e);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ArsHooks.forget(event.getEntity().getUUID());
    }
}
