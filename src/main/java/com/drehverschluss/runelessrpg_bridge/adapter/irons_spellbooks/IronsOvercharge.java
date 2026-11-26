package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg_bridge.core.Overcharge;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellHealEvent;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Overcharge for Iron's spells (plan 4.10). The cost is in {@code Pool}; here is the effect.
 * <p>
 * <b>Paid?</b> Iron's decides in {@code castSpell} whether a cast costs mana ({@code CastSource.consumesMana()}: spell
 * book and sword, not scrolls; no recast). The bridge pays in {@code SpellOnCastEvent} and knows it there; a cast that
 * costs nothing (scroll, cost 0) gets no bonus. Creative does not pay but gets the bonus, like for Spell Engine spells.
 * A recast counts like the cast that opened it.
 * <p>
 * <b>Hits.</b> {@code SpellDamageEvent} at the lowest priority (after other addons; Iron's school resistance multiplies
 * afterwards, which gives the same result as raising the finished damage). A damage source has no cast source, so a
 * projectile of a paid cast and one of a scroll cannot be told apart at the hit: everything a cast spawns (projectiles,
 * cones, zones, summons, lightning, fangs) gets a snapshot while {@code castSpell} runs (1.0 for unpaid casts). Hits whose
 * direct entity is the player himself (instant and channeled spells) use the current bonus. Hits outside a cast from
 * entities without a snapshot (Thunderstorm's strikes, Burning Dash, Volt Strike, Echoing Strikes: made by an effect) use
 * the last cast of that spell by the player. Healing: {@code SpellHealEvent}, right before Iron's {@code heal}.
 */
public final class IronsOvercharge {
    private static final String ID = IronsAdapter.ID;

    /**
     * A cast running right now ({@code castSpell}), server thread. {@code active} is false while overcharge does not apply
     * to the player (adapter off, {@code applyOvercharge = false}): the cast is still on the stack so that every end of
     * {@code castSpell} removes its own entry.
     */
    private record Cast(ServerPlayer player, String spellId, boolean paid, double bonus, boolean active) {
        double multiplier() {
            return paid ? bonus : 1.0;
        }
    }

    /** Last cast of each spell per player: paid and the bonus it was cast with. */
    private record Last(boolean paid, double bonus) {}

    private static final Deque<Cast> CASTS = new ArrayDeque<>();
    private static final Map<UUID, Map<String, Last>> LAST = new ConcurrentHashMap<>();

    private IronsOvercharge() {}

    static void register() {
        Overcharge.addSpawnSource(IronsOvercharge::spawn);
        Overcharge.addHitSource(IronsOvercharge::otherHit);
        Overcharge.addKindSource(IronsOvercharge::kind);
    }

    /** Iron's zones ({@code AoeEntity}) and summons are ZONE, spell projectiles and cones DIRECT; other entities: unknown. */
    private static Overcharge.@Nullable Kind kind(Entity entity) {
        if (entity instanceof io.redspace.ironsspellbooks.entity.spells.AoeEntity || entity instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon) {
            return Overcharge.Kind.ZONE;
        }
        return IronsLeech.isDirectType(entity) ? Overcharge.Kind.DIRECT : null;
    }

    // ------------------------------------------------------------------ casts

    /**
     * From the payment listener ({@code SpellOnCastEvent}, right before {@code onCast}): a cast of the player starts.
     *
     * @param pays the bridge pays this cast (connected, the source consumes mana, no recast) and its cost is above 0
     */
    static void castStarts(ServerPlayer player, String spellId, boolean pays, boolean recast) {
        if (!Overcharge.applies(player, ID)) {
            forgetSpell(player, spellId);
            CASTS.push(new Cast(player, spellId, false, 1.0, false));
            return;
        }
        boolean paid = pays;
        double bonus = Overcharge.bonus(player, ID);
        if (recast) {
            // a recast costs nothing: it counts like the cast that opened it
            Last opened = last(player, spellId);
            if (opened != null) {
                paid = opened.paid();
                bonus = opened.bonus();
            } else {
                RecastInstance instance = MagicData.getPlayerMagicData(player).getPlayerRecasts().getRecastInstance(spellId);
                paid = instance != null && instance.getCastSource() != null && instance.getCastSource().consumesMana();
            }
        } else {
            LAST.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>()).put(spellId, new Last(paid, bonus));
        }
        CASTS.push(new Cast(player, spellId, paid, bonus, true));
    }

    /** {@code castSpell} returned (mixin): the cast is over. */
    public static void castEnds() {
        if (!CASTS.isEmpty()) {
            CASTS.pop();
        }
    }

    private static @Nullable Last last(Player player, String spellId) {
        Map<String, Last> spells = LAST.get(player.getUUID());
        return spells == null ? null : spells.get(spellId);
    }

    private static void forgetSpell(Player player, String spellId) {
        Map<String, Last> spells = LAST.get(player.getUUID());
        if (spells != null) {
            spells.remove(spellId);
        }
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clearTick() {
        CASTS.clear(); // a cast whose onCast threw never reached the end of castSpell
    }

    /** Everything a cast spawns gets a snapshot: the bonus of a paid cast, 1.0 for an unpaid one. */
    private static Overcharge.@Nullable Mark spawn(Entity entity) {
        Cast cast = CASTS.peek();
        if (cast == null || !cast.active() || entity == cast.player()) {
            return null;
        }
        return new Overcharge.Mark(cast.multiplier(), IronsLeech.isDirectType(entity) ? Overcharge.Kind.DIRECT : Overcharge.Kind.ZONE);
    }

    // ------------------------------------------------------------------ hits and healing

    /** {@code SpellDamageEvent} (lowest priority, not cancelled): the damage × the bonus of the cast it belongs to. */
    static void onDamage(SpellDamageEvent event) {
        SpellDamageSource source = event.getSpellDamageSource();
        Overcharge.handled(source);
        double multiplier = multiplier(source, source.spell() == null ? "" : source.spell().getSpellId());
        if (multiplier != 1.0) {
            event.setAmount(Overcharge.apply(event.getAmount(), multiplier));
        }
    }

    private static double multiplier(DamageSource source, String spellId) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        Overcharge.Mark mark = Overcharge.markOf(direct);
        if (mark == null) {
            mark = Overcharge.markOf(causing);
        }
        if (mark != null) {
            return Overcharge.effective(mark);
        }
        if (!(causing instanceof ServerPlayer player) || !Overcharge.applies(player, ID)) {
            return 1.0;
        }
        Cast cast = CASTS.peek();
        if (cast != null && cast.player() == player) {
            return cast.paid() ? Overcharge.bonus(player, ID) : 1.0;
        }
        Last last = last(player, spellId);
        if (last == null || !last.paid()) {
            return 1.0;
        }
        if (direct == null || direct == player) {
            return Overcharge.bonus(player, ID); // a direct hit later (Burning Dash, Volt Strike): the value at the hit
        }
        return IronsLeech.isDirectType(direct) ? last.bonus() : Overcharge.zone(last.bonus());
    }

    /** Hits during a cast that do not come as Iron's spell damage (other damage sources in onCast). */
    private static double otherHit(DamageSource source, LivingEntity target) {
        Cast cast = CASTS.peek();
        if (cast == null || !cast.active() || source.getEntity() != cast.player()) {
            return Double.NaN;
        }
        return cast.paid() ? Overcharge.bonus(cast.player(), ID) : 1.0;
    }

    /**
     * {@code SpellHealEvent}: Iron's heals right after it. The bonus of the cast (Heal, Greater Heal, Blessing of Life,
     * Cloud of Regeneration) or the snapshot of the zone that heals while it ticks (Healing Circle, Ice Tomb).
     */
    static void onHeal(SpellHealEvent event) {
        LivingEntity target = event.getTargetEntity();
        if (target == null || target.level().isClientSide() || !Overcharge.affectsHealing(target.level())) {
            return;
        }
        double multiplier = 1.0;
        Overcharge.Mark zone = Overcharge.ticking() instanceof LivingEntity ? null : Overcharge.tickingMark();
        Cast cast = CASTS.peek();
        if (zone != null) {
            multiplier = Overcharge.effective(zone);
        } else if (cast != null && cast.player() == event.getEntity()) {
            multiplier = cast.paid() ? Overcharge.bonus(cast.player(), ID) : 1.0;
        }
        if (multiplier != 1.0) {
            Overcharge.pendingHeal(target, multiplier);
        }
    }
}
