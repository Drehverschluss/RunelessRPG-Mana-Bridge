package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/** Game-bus listeners for Iron's (registered only while Iron's is loaded). */
public final class IronsEvents {
    private static boolean errorLogged;

    private IronsEvents() {}

    /**
     * Payment of an Iron's cast (every tick of a channeled spell too): at the lowest priority, so changes of other addons
     * count. Iron's own deduction is set to 0 and RunelessRPG pays the effective cost with the debt rules (reason
     * {@code EXTERNAL}). Same conditions as Iron's: only sources that consume mana, not for a recast. Creative is decided
     * by RunelessRPG ({@code creativeFreeCasting}).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellCast(SpellOnCastEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || !IronsHooks.connected(player)) {
                return;
            }
            if (!event.getCastSource().consumesMana()
                    || MagicData.getPlayerMagicData(player).getPlayerRecasts().hasRecastForSpell(event.getSpellId())) {
                return;
            }
            int cost = event.getManaCost();
            event.setManaCost(0);
            if (cost > 0) {
                Pool.pay(player, IronsAdapter.ID, cost);
            }
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                ManaBridge.LOGGER.error("Iron's cast payment failed, Iron's pays itself for this cast", e);
            }
        }
    }
}
