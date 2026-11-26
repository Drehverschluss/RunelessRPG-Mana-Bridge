package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.ArsNSpells;
import com.drehverschluss.runelessrpg_bridge.core.Overcharge;
import com.hollingsworth.arsnouveau.api.event.EffectResolveEvent;
import com.hollingsworth.arsnouveau.api.event.SpellCastEvent;
import com.hollingsworth.arsnouveau.api.event.SpellDamageEvent;
import com.hollingsworth.arsnouveau.api.spell.IContextAttachment;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.IWrappedCaster;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.TileCaster;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Overcharge for Ars spells (plan 4.10). The cost is in {@code Pool}; here is the effect.
 * <p>
 * <b>Paid?</b> A cast the bridge pays gets a mark in Ars' own {@link SpellContext#attachments} ({@link Paid}: player and
 * bonus at the cast). Ars copies the attachments when it clones a context and when it makes a child context (Linger,
 * Wall, Orbit, Rune, Burst), and a projectile keeps the context of its cast, so every later hit of the spell still knows
 * the mark. Set in {@code SpellCastEvent} (before Ars resolves Touch and Self, which happens before it takes the mana)
 * and again when the bridge really pays ({@code LivingCaster.expendMana}: bows, crossbows and scrying pay without the
 * event). Not set for turrets (Source), runes and familiars (other casters), and while Ars 'n Spells pays Ars spells
 * itself (its mana unification on). A cast that ends up costing 0 loses the mark.
 * <p>
 * <b>Hits</b> ({@code SpellDamageEvent.Pre}, lowest priority, after Ars' spell damage bonus and other addons): a marked
 * zone that ticks right now (Linger, Wall, Orbit: their snapshot from when they were made), a rune with a snapshot (its
 * block entity's persistent data, set when a paid spell places it), else the mark of the context: after a zone glyph the
 * bonus at the cast, otherwise the bonus at the hit (projectiles, Touch, Self, Burst, Delay). Healing: Heal resolving on
 * an entity. Summons and other entities a paid effect makes (Lightning, Fangs) get a snapshot; damage during a paid
 * effect that does not come as Ars spell damage (Explosion, Wind Burst) gets the bonus at the hit.
 */
public final class ArsOvercharge {
    private static final String ID = ArsAdapter.ID;
    static final ResourceLocation MARK = ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "overcharge");
    /** Key in the persistent data of a rune block entity: its snapshot (multiplier). */
    static final String RUNE_KEY = ManaBridge.MODID + ":overcharge";

    /** The bridge pays (or would pay, in creative) this cast of this player; bonus at the cast. */
    public record Paid(UUID player, double bonus) implements IContextAttachment {
        @Override
        public ResourceLocation id() {
            return MARK;
        }
    }

    /** Effects resolving right now (server thread): context of the innermost one first. */
    private static final Deque<SpellContext> RESOLVING = new ArrayDeque<>();
    /** The context whose payment runs right now ({@code SpellResolver.expendMana}), server thread. */
    private static @Nullable SpellContext expending;

    /**
     * Ars' spell entities ({@code EntityProjectileSpell}: projectiles, Linger, Wall, Orbit) and their {@code resolver()},
     * through reflection: the class implements GeckoLib interfaces, which this mod does not compile against.
     */
    private static final String SPELL_ENTITY = "com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell";
    private static @Nullable Class<?> spellEntityClass;
    private static @Nullable Method resolverMethod;
    private static boolean reflectionFailed;

    private ArsOvercharge() {}

    private static boolean isSpellEntity(@Nullable Entity entity) {
        Class<?> type = spellEntityClass();
        return entity != null && type != null && type.isInstance(entity);
    }

    private static @Nullable Class<?> spellEntityClass() {
        if (spellEntityClass == null && !reflectionFailed) {
            try {
                spellEntityClass = Class.forName(SPELL_ENTITY, false, ArsOvercharge.class.getClassLoader());
                resolverMethod = spellEntityClass.getMethod("resolver");
            } catch (ReflectiveOperationException | LinkageError e) {
                reflectionFailed = true;
                ManaBridge.LOGGER.warn("Ars overcharge: {} not found, zones of Ars spells get no snapshot", SPELL_ENTITY);
            }
        }
        return spellEntityClass;
    }

    /** @return the context of an Ars spell entity, or null */
    private static @Nullable SpellContext contextOf(Entity entity) {
        if (!isSpellEntity(entity) || resolverMethod == null) {
            return null;
        }
        try {
            return resolverMethod.invoke(entity) instanceof SpellResolver resolver ? resolver.spellContext : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static void register() {
        Overcharge.addSpawnSource(ArsOvercharge::spawn);
        Overcharge.addHitSource(ArsOvercharge::otherHit);
        Overcharge.addKindSource(entity -> isSpellEntity(entity) && ArsLeech.zoneGlyph(contextOf(entity)) != null ? Overcharge.Kind.ZONE : null);
    }

    // ------------------------------------------------------------------ paid?

    /**
     * @return true while Ars 'n Spells pays Ars spells itself: its unification is on in any mode but {@code disabled}, also
     *         in {@code separate} (then the bridge's cost hooks are not reached, so there is no surcharge and no bonus)
     */
    private static boolean arsNSpellsPays() {
        return ModList.get().isLoaded("ars_n_spells") && ArsNSpells.billsArsSpells();
    }

    /** @return the player if the bridge pays this context's casts (the player's own cast, adapter connected, overcharge on) */
    private static @Nullable ServerPlayer payer(SpellContext context) {
        IWrappedCaster caster = context.getCaster();
        if (caster instanceof TileCaster || !(caster instanceof LivingCaster living) || !(living.livingEntity instanceof ServerPlayer player)) {
            return null;
        }
        return ArsHooks.connected(player) && Overcharge.applies(player, ID) && !arsNSpellsPays() ? player : null;
    }

    /** {@code SpellCastEvent} (lowest priority, not cancelled): mark the cast before Ars resolves it. */
    static void onCast(SpellCastEvent event) {
        SpellContext context = event.context;
        if (context == null || event.getWorld().isClientSide() || context.getSpell().getCost() <= 0) {
            return;
        }
        ServerPlayer player = payer(context);
        if (player != null && paidOf(context) == null) {
            context.attachments.put(MARK, new Paid(player.getUUID(), Overcharge.bonus(player, ID)));
        }
    }

    /** {@code SpellResolver.expendMana} starts (mixin). */
    public static void expendStarts(@Nullable SpellContext context) {
        expending = context;
    }

    /** The bridge has paid this cost for the player ({@code LivingCaster.expendMana}). */
    static void onPaid(ServerPlayer player, int cost) {
        SpellContext context = expending;
        expending = null;
        if (context == null || context.getUnwrappedCaster() != player) {
            return;
        }
        if (cost <= 0) {
            context.attachments.remove(MARK); // a free cast gets no bonus
        } else if (paidOf(context) == null && payer(context) == player) {
            context.attachments.put(MARK, new Paid(player.getUUID(), Overcharge.bonus(player, ID)));
        }
    }

    /** @return the mark of this context or of a context before it, or null */
    static @Nullable Paid paidOf(@Nullable SpellContext context) {
        SpellContext current = context;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (current.attachments.get(MARK) instanceof Paid paid) {
                return paid;
            }
            current = current.getPreviousContext();
        }
        return null;
    }

    private static @Nullable ServerPlayer player(Level level, Paid paid) {
        if (!(level instanceof ServerLevel server) || !(server.getServer().getPlayerList().getPlayer(paid.player()) instanceof ServerPlayer player)) {
            return null;
        }
        return Overcharge.applies(player, ID) ? player : null;
    }

    // ------------------------------------------------------------------ multiplier of a hit

    /**
     * The multiplier for damage or healing of this context now: a rune's snapshot, the snapshot of the Ars zone that ticks
     * right now (only a spell entity: a marked summon that walks onto a rune must not lend it its own), else the mark.
     */
    private static double multiplier(@Nullable SpellContext context, Level level) {
        if (context != null) {
            double rune = runeSnapshot(context);
            if (!Double.isNaN(rune)) {
                return Overcharge.zone(rune);
            }
        }
        if (isSpellEntity(Overcharge.ticking())) {
            Overcharge.Mark zone = Overcharge.tickingMark();
            if (zone != null) {
                return Overcharge.effective(zone);
            }
        }
        if (context == null) {
            return 1.0;
        }
        Paid paid = paidOf(context);
        if (paid == null) {
            return 1.0;
        }
        Player player = player(level, paid);
        if (player == null) {
            return 1.0;
        }
        return ArsLeech.zoneGlyph(context) != null ? Overcharge.zone(paid.bonus()) : Overcharge.bonus(player, ID);
    }

    /** The snapshot of the rune that casts this context (only runes get one, see {@link #onRuneSpell}), or NaN. */
    private static double runeSnapshot(SpellContext context) {
        if (context.getCaster() instanceof TileCaster tile) {
            BlockEntity block = tile.getTile();
            if (block != null) {
                CompoundTag data = block.getPersistentData();
                if (data.contains(RUNE_KEY)) {
                    return data.getDouble(RUNE_KEY);
                }
            }
        }
        return Double.NaN;
    }

    /** {@code SpellDamageEvent.Pre} (lowest priority, not cancelled). */
    static void onDamage(SpellDamageEvent.Pre event) {
        if (event.target == null || event.target.level().isClientSide()) {
            return;
        }
        Overcharge.handled(event.damageSource);
        double multiplier = multiplier(event.context, event.target.level());
        if (multiplier != 1.0) {
            event.damage = Overcharge.apply(event.damage, multiplier);
        }
    }

    // ------------------------------------------------------------------ effects resolving

    /** {@code EffectResolveEvent.Pre} (lowest priority, not cancelled): remember the context; Heal on an entity. */
    static void effectStarts(EffectResolveEvent.Pre event) {
        if (event.world == null || event.world.isClientSide()) {
            return;
        }
        RESOLVING.push(event.context);
        if (event.resolveEffect instanceof EffectHeal && event.rayTraceResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && Overcharge.affectsHealing(event.world)) {
            double multiplier = multiplier(event.context, event.world);
            if (multiplier != 1.0) {
                Overcharge.pendingHeal(target, multiplier);
            }
        }
    }

    /** {@code EffectResolveEvent.Post}. */
    static void effectEnds(EffectResolveEvent.Post event) {
        if (event.world == null || event.world.isClientSide()) {
            return;
        }
        if (!RESOLVING.isEmpty() && RESOLVING.peek() == event.context) {
            RESOLVING.pop();
        } else {
            RESOLVING.remove(event.context);
        }
        if (event.resolveEffect instanceof EffectHeal) {
            Overcharge.clearPendingHeal();
        }
    }

    static void clearTick() {
        RESOLVING.clear();
        expending = null;
    }

    /**
     * Entities a paid spell makes: zones (spell entities after Linger, Wall, Orbit) get a snapshot of the bonus now; plain
     * projectiles none (their hits know the cast's mark); summons and other entities a paid effect makes (Lightning,
     * Fangs, Vex, Wolves, Undead, Steed, Decoy) get one too.
     */
    private static Overcharge.@Nullable Mark spawn(Entity entity) {
        if (isSpellEntity(entity)) {
            SpellContext context = contextOf(entity);
            if (context == null || ArsLeech.zoneGlyph(context) == null) {
                return null;
            }
            Paid paid = paidOf(context);
            Player player = paid == null ? null : player(entity.level(), paid);
            return player == null ? null : new Overcharge.Mark(Overcharge.bonus(player, ID), Overcharge.Kind.ZONE);
        }
        SpellContext context = RESOLVING.peek();
        if (context == null) {
            return null;
        }
        Paid paid = paidOf(context);
        Player player = paid == null ? null : player(entity.level(), paid);
        if (player == null) {
            return null;
        }
        boolean zone = entity instanceof LivingEntity || ArsLeech.zoneGlyph(context) != null;
        return new Overcharge.Mark(zone && ArsLeech.zoneGlyph(context) != null ? paid.bonus() : Overcharge.bonus(player, ID),
                zone ? Overcharge.Kind.ZONE : Overcharge.Kind.DIRECT);
    }

    /** Damage during a paid effect that does not come as Ars spell damage (Explosion, Wind Burst): the bonus now. */
    private static double otherHit(DamageSource source, LivingEntity target) {
        SpellContext context = RESOLVING.peek();
        if (context == null) {
            return Double.NaN;
        }
        double multiplier = multiplier(context, target.level());
        return multiplier == 1.0 && paidOf(context) == null && Double.isNaN(runeSnapshot(context)) ? Double.NaN : multiplier;
    }

    // ------------------------------------------------------------------ runes

    /**
     * {@code RuneTile.setSpell} (mixin): a rune that a paid spell places keeps the bonus now as its snapshot (like a zone);
     * a rune that gets its spell any other way (Runic Chalk, a turret, an unpaid cast) has none.
     */
    public static void onRuneSpell(BlockEntity tile) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide()) {
            return;
        }
        CompoundTag data = tile.getPersistentData();
        SpellContext context = RESOLVING.peek();
        Paid paid = context == null ? null : paidOf(context);
        Player player = paid == null ? null : player(tile.getLevel(), paid);
        if (player != null) {
            data.putDouble(RUNE_KEY, ArsLeech.zoneGlyph(context) != null ? paid.bonus() : Overcharge.bonus(player, ID));
        } else {
            data.remove(RUNE_KEY);
        }
        tile.setChanged();
    }
}
