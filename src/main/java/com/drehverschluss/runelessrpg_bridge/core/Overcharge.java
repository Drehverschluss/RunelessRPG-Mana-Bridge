package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Overcharge for spells of other mods (plan 4.10, phase C5B), the part without classes of other mods. The same rules as
 * for Spell Engine spells: a spell the player pays through the bridge costs {@code × (1 + overcharge)} (in
 * {@link Pool#costMultiplier}) and its damage and healing go up by {@link RunelessApi#getOverchargeBonus(Player)}.
 * <p>
 * <b>Snapshot.</b> What a spell leaves in the world (summons, zones, runes; Iron's projectiles too) keeps the bonus it was
 * made with: RunelessRPG's snapshot attachment ({@link RunelessApi#setOverchargeSnapshot}, saved with the entity) plus an
 * entity tag of this bridge. The tag says that the bridge set the snapshot (Spell Engine summons carry the same attachment;
 * RunelessRPG raises their hits itself, so they must not be raised twice) and which rule applies at a hit:
 * {@link Kind#ZONE} (summons, zones: only with {@code overchargeAffectsSummons}) or {@link Kind#DIRECT} (projectiles,
 * lightning, fangs: always, it stands for the value at the hit). Entities that such an entity makes while it ticks get the
 * same snapshot and kind.
 * <p>
 * <b>Hits.</b> The adapters raise the hits of their own spell damage events and report those damage sources here
 * ({@link #handled}). Every other hit ({@link LivingIncomingDamageEvent}, lowest priority, so after the mod's own
 * multipliers such as Iron's summon damage) is raised if its attacker carries a mark of this bridge, if an adapter knows
 * it as part of a paid cast ({@link HitSource}: e.g. an Ars explosion), or if it has no attacker and happens while a
 * marked entity ticks (e.g. fangs without an owner). Healing goes through {@link #pendingHeal} and
 * {@link LivingHealEvent}.
 */
public final class Overcharge {
    public static final String TAG_ZONE = ManaBridge.MODID + ".overcharge_zone";
    public static final String TAG_DIRECT = ManaBridge.MODID + ".overcharge_direct";

    /** How the snapshot counts at a hit. */
    public enum Kind {
        /** Projectiles, lightning, fangs: always (it stands for the value at the hit). */
        DIRECT,
        /** Summons, zones, runes: only with RunelessRPG's {@code overchargeAffectsSummons}. */
        ZONE
    }

    /** The snapshot of an entity: multiplier (1.0 = nothing) and kind. */
    public record Mark(double multiplier, Kind kind) {}

    /** An adapter's answer for an entity that joins the world right now (part of a cast), or null. */
    @FunctionalInterface
    public interface SpawnSource {
        @Nullable Mark spawn(Entity entity);
    }

    /** An adapter's kind for an entity of its mod (zones and summons: ZONE), or null if it does not know the entity. */
    @FunctionalInterface
    public interface KindSource {
        @Nullable Kind kind(Entity entity);
    }

    /** An adapter's multiplier for a hit that is part of a cast running right now, or NaN if it does not know the hit. */
    @FunctionalInterface
    public interface HitSource {
        double hit(DamageSource source, LivingEntity target);
    }

    private static final List<SpawnSource> SPAWN_SOURCES = new CopyOnWriteArrayList<>();
    private static final List<HitSource> HIT_SOURCES = new CopyOnWriteArrayList<>();
    private static final List<KindSource> KIND_SOURCES = new CopyOnWriteArrayList<>();
    /** Marked entities that tick right now (server thread). */
    private static final Deque<Entity> TICKING = new ArrayDeque<>();
    /** Damage sources an adapter's spell listener has dealt with in this tick (server thread). */
    private static final Set<DamageSource> HANDLED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int MAX_HANDLED = 4096;
    private static @Nullable LivingEntity healTarget;
    private static double healMultiplier = 1.0;
    private static boolean registered;
    private static boolean errorLogged;

    private Overcharge() {}

    /** Registers the listeners (once, only if an adapter for another mod is loaded: without one the bridge does nothing). */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, Overcharge::tickPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Post.class, Overcharge::tickPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, Overcharge::join);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, Overcharge::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingHealEvent.class, Overcharge::heal);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> clearTick());
    }

    public static void addSpawnSource(SpawnSource source) {
        SPAWN_SOURCES.add(source);
    }

    public static void addHitSource(HitSource source) {
        HIT_SOURCES.add(source);
    }

    public static void addKindSource(KindSource source) {
        KIND_SOURCES.add(source);
    }

    /**
     * Kind of an entity a marked entity makes while it ticks: a zone or summon stays ZONE (also when a projectile makes
     * it, e.g. the poison cloud of Iron's Poison Splash); everything a zone or summon makes is ZONE too.
     */
    private static Kind childKind(Entity child, Kind parent) {
        if (parent == Kind.ZONE || child instanceof LivingEntity) {
            return Kind.ZONE; // summons are ZONE whoever makes them
        }
        for (KindSource source : KIND_SOURCES) {
            Kind kind = source.kind(child);
            if (kind != null) {
                return kind;
            }
        }
        return parent;
    }

    private static void error(String what, Throwable e) {
        if (!errorLogged) {
            errorLogged = true;
            ManaBridge.LOGGER.error("Overcharge for spells of other mods: {} failed", what, e);
        }
    }

    // ------------------------------------------------------------------ values

    /** @return true if overcharge applies to this mod's spells for this player (adapter connected, {@code applyOvercharge}) */
    public static boolean applies(@Nullable Player player, String modId) {
        return player != null && Pool.connected(modId, player) && Pool.applyOvercharge(player, modId);
    }

    /** The bonus for a spell of this mod the player pays now: {@code 1 + overcharge × overchargeDamageFactor}, or 1. */
    public static double bonus(@Nullable Player player, String modId) {
        if (!applies(player, modId)) {
            return 1.0;
        }
        double bonus = RunelessApi.getOverchargeBonus(player);
        return Double.isFinite(bonus) && bonus > 0.0 ? bonus : 1.0;
    }

    /** A zone or summon snapshot at a hit: the snapshot with {@code overchargeAffectsSummons}, else 1. */
    public static double zone(double snapshot) {
        return RunelessApi.overchargeAffectsSummons() ? snapshot : 1.0;
    }

    public static boolean affectsHealing(Level level) {
        return RunelessApi.overchargeAffectsHealing(level);
    }

    /** {@code amount × multiplier} (unchanged for 1, so nothing changes in the last bit without overcharge). */
    public static float apply(float amount, double multiplier) {
        return multiplier == 1.0 || !Double.isFinite(multiplier) ? amount : (float) (amount * multiplier);
    }

    // ------------------------------------------------------------------ marks

    /** Gives the entity a snapshot of this bridge (server). */
    public static void mark(Entity entity, double multiplier, Kind kind) {
        if (entity.level().isClientSide() || !Double.isFinite(multiplier) || entity instanceof Player) {
            return;
        }
        RunelessApi.setOverchargeSnapshot(entity, Math.max(0.0, multiplier));
        entity.removeTag(kind == Kind.ZONE ? TAG_DIRECT : TAG_ZONE);
        entity.addTag(kind == Kind.ZONE ? TAG_ZONE : TAG_DIRECT);
    }

    /** @return the snapshot of this bridge on the entity, or null (none, or one of Spell Engine's) */
    public static @Nullable Mark markOf(@Nullable Entity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return null;
        }
        Set<String> tags = entity.getTags();
        Kind kind = tags.contains(TAG_ZONE) ? Kind.ZONE : tags.contains(TAG_DIRECT) ? Kind.DIRECT : null;
        if (kind == null) {
            return null;
        }
        OptionalDouble snapshot = RunelessApi.getOverchargeSnapshot(entity);
        return snapshot.isPresent() ? new Mark(snapshot.getAsDouble(), kind) : null;
    }

    /** What a mark counts at a hit now. */
    public static double effective(Mark mark) {
        return mark.kind() == Kind.ZONE ? zone(mark.multiplier()) : mark.multiplier();
    }

    /** @return the marked entity that ticks right now (server), or null */
    public static @Nullable Entity ticking() {
        return TICKING.peek();
    }

    /** @return the mark of the marked entity that ticks right now, or null */
    public static @Nullable Mark tickingMark() {
        return markOf(TICKING.peek());
    }

    private static void tickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide() && (entity.getTags().contains(TAG_ZONE) || entity.getTags().contains(TAG_DIRECT))) {
            TICKING.push(entity);
        }
    }

    private static void tickPost(EntityTickEvent.Post event) {
        if (!TICKING.isEmpty() && TICKING.peek() == event.getEntity()) {
            TICKING.pop();
        }
    }

    /** A new entity: same snapshot as the marked entity that makes it while it ticks, else ask the adapters. */
    private static void join(EntityJoinLevelEvent event) {
        try {
            Entity entity = event.getEntity();
            if (event.getLevel().isClientSide() || event.loadedFromDisk() || entity instanceof Player
                    || entity instanceof net.minecraft.world.entity.item.ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb
                    || markOf(entity) != null) {
                return;
            }
            Entity parent = TICKING.peek();
            Mark mark = parent != null && parent != entity ? markOf(parent) : null;
            if (mark != null) {
                mark = new Mark(mark.multiplier(), childKind(entity, mark.kind()));
            } else {
                for (SpawnSource source : SPAWN_SOURCES) {
                    mark = source.spawn(entity);
                    if (mark != null) {
                        break;
                    }
                }
            }
            if (mark != null) {
                mark(entity, mark.multiplier(), mark.kind());
            }
        } catch (RuntimeException | LinkageError e) {
            error("marking a new entity", e);
        }
    }

    // ------------------------------------------------------------------ hits

    /** An adapter has dealt with this spell hit (raised or not): the general listener leaves it alone. */
    public static void handled(DamageSource source) {
        if (HANDLED.size() < MAX_HANDLED) {
            HANDLED.add(source);
        }
    }

    private static void damage(LivingIncomingDamageEvent event) {
        try {
            LivingEntity target = event.getEntity();
            DamageSource source = event.getSource();
            if (target.level().isClientSide() || HANDLED.contains(source)) {
                return;
            }
            double multiplier = hitMultiplier(source, target);
            if (Double.isFinite(multiplier) && multiplier != 1.0) {
                event.setAmount(apply(event.getAmount(), multiplier));
            }
        } catch (RuntimeException | LinkageError e) {
            error("raising a hit", e);
        }
    }

    private static double hitMultiplier(DamageSource source, LivingEntity target) {
        Mark mark = markOf(source.getDirectEntity());
        if (mark == null) {
            mark = markOf(source.getEntity());
        }
        if (mark != null) {
            return effective(mark);
        }
        for (HitSource hitSource : HIT_SOURCES) {
            double multiplier = hitSource.hit(source, target);
            if (!Double.isNaN(multiplier)) {
                return multiplier;
            }
        }
        Entity parent = TICKING.peek();
        // no attacker while a marked spell entity ticks (fangs without an owner): its hit (not a summon's: those have attackers)
        if (parent != null && !(parent instanceof LivingEntity) && parent != target && source.getEntity() == null
                && source.getDirectEntity() == null) {
            Mark parentMark = markOf(parent);
            if (parentMark != null) {
                return effective(parentMark);
            }
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ healing

    /** The next healing of this entity (right after this, from the same spell) is multiplied (caller checked the switch). */
    public static void pendingHeal(LivingEntity target, double multiplier) {
        healTarget = target;
        healMultiplier = multiplier;
    }

    public static void clearPendingHeal() {
        healTarget = null;
        healMultiplier = 1.0;
    }

    private static void heal(LivingHealEvent event) {
        try {
            if (healTarget == null || event.getEntity() != healTarget) {
                return;
            }
            double multiplier = healMultiplier;
            clearPendingHeal();
            if (multiplier != 1.0 && Double.isFinite(multiplier)) {
                event.setAmount(apply(event.getAmount(), multiplier));
            }
        } catch (RuntimeException | LinkageError e) {
            error("raising a heal", e);
        }
    }

    private static void clearTick() {
        TICKING.clear();
        HANDLED.clear();
        clearPendingHeal();
    }
}
