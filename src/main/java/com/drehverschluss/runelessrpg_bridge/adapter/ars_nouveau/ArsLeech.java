package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg.api.SpellHitPredicate;
import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Spell Mana Leech for Ars spells (plan 4.9). Ars deals spell damage in {@code IDamageEffect.attemptDamage}: first
 * {@code SpellDamageEvent.Pre}, then {@code entity.hurt}, then {@code SpellDamageEvent.Post}. RunelessRPG asks this
 * predicate during the hurt (in {@code LivingDamageEvent.Post}), so the hit Pre announced is the one being asked about
 * (same damage source object and target).
 * <p>
 * A hit is direct if the player cast the spell himself ({@link PlayerCaster} with the player as caster; runes, turrets,
 * familiars and other entities have other casters) and no glyph of {@code leechZoneGlyphs} (default Linger, Wall,
 * Orbit) came before the damage, also in the spell parts that ran before a child context (Linger, Wall and Orbit go on
 * in a new context with the rest of the spell). Delay goes on in the same context and counts as direct. RunelessRPG
 * applies all its own rules (attribute, falloff per target with the counter shared with Spell Engine and Iron's spells,
 * cap on the target's health, {@code #runelessrpg:no_mana_leech}).
 */
public final class ArsLeech implements SpellHitPredicate {
    /** Hits that are running right now (nested hits are possible), server thread. */
    private static final Deque<Hit> HITS = new ArrayDeque<>();
    private static final int MAX_HITS = 64;
    private static final int MAX_CONTEXT_DEPTH = 32;
    private static boolean registered;

    /** One Ars hit between its Pre and Post event; {@code origin} describes it for logs and tests. */
    public record Hit(DamageSource source, Entity target, ServerPlayer player, boolean direct, String origin) {}

    private ArsLeech() {}

    static void register() {
        if (!registered) {
            registered = true;
            RunelessApi.registerSpellHitPredicate(new ArsLeech());
        }
    }

    static void push(SpellDamageEvent.Pre event, ServerPlayer player) {
        if (HITS.size() >= MAX_HITS) {
            HITS.removeLast();
        }
        SpellContext context = event.context;
        String origin = origin(context, player);
        HITS.push(new Hit(event.damageSource, event.target, player, origin.startsWith("direct"), origin));
    }

    static void pop(DamageSource source) {
        for (Iterator<Hit> it = HITS.iterator(); it.hasNext(); ) {
            if (it.next().source() == source) {
                it.remove();
                return;
            }
        }
    }

    static void clear() {
        HITS.clear();
    }

    /** @return the Ars hit running right now with this damage source and target, or null (for tests and logs too) */
    public static @Nullable Hit current(DamageSource source, Entity target) {
        for (Hit hit : HITS) {
            if (hit.source() == source && hit.target() == target) {
                return hit;
            }
        }
        return null;
    }

    @Override
    public boolean isDirectSpellHit(DamageSource source, LivingEntity target, ServerPlayer player) {
        if (!ArsAdapter.spellManaLeech() || !ArsHooks.connected(player)) {
            return false;
        }
        Hit hit = current(source, target);
        return hit != null && hit.player() == player && hit.direct();
    }

    /**
     * @return the first glyph of {@code leechZoneGlyphs} (Linger, Wall, Orbit by default) before the current part, in
     *         this context or in the contexts before it (Linger, Wall and Orbit go on in a child context), or null. Spell
     *         Mana Leech and overcharge (snapshot of a zone) use the same rule.
     */
    static @Nullable String zoneGlyph(@Nullable SpellContext context) {
        Set<String> zones = ArsAdapter.zoneGlyphs();
        SpellContext current = context;
        for (int depth = 0; current != null && depth < MAX_CONTEXT_DEPTH; depth++) {
            List<AbstractSpellPart> parts = current.getSpell().unsafeList();
            int upTo = Math.min(current.getCurrentIndex(), parts.size());
            for (int i = 0; i < upTo; i++) {
                String id = parts.get(i).getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (zones.contains(id)) {
                    return id;
                }
            }
            current = current.getPreviousContext();
        }
        return null;
    }

    /**
     * Where the hit comes from: "direct: glyphs" or "not direct: reason". Direct means the player's own cast and no zone
     * glyph before the damage.
     */
    public static String origin(@Nullable SpellContext context, ServerPlayer player) {
        if (context == null) {
            return "not direct: no spell context";
        }
        if (!(context.getCaster() instanceof PlayerCaster caster) || caster.player != player) {
            return "not direct: caster " + (context.getCaster() == null ? "none" : context.getCaster().getClass().getSimpleName());
        }
        Set<String> zones = ArsAdapter.zoneGlyphs();
        StringBuilder glyphs = new StringBuilder();
        SpellContext current = context;
        for (int depth = 0; current != null && depth < MAX_CONTEXT_DEPTH; depth++) {
            List<AbstractSpellPart> parts = current.getSpell().unsafeList();
            int upTo = Math.min(current.getCurrentIndex(), parts.size());
            StringBuilder here = new StringBuilder();
            for (int i = 0; i < upTo; i++) {
                String id = parts.get(i).getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (zones.contains(id)) {
                    return "not direct: after " + id;
                }
                here.append(here.isEmpty() ? "" : " ").append(id.replace("ars_nouveau:glyph_", ""));
            }
            glyphs.insert(0, glyphs.isEmpty() ? here : here + " | ");
            current = current.getPreviousContext();
        }
        return "direct: " + glyphs;
    }
}
