package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg.api.SpellHitPredicate;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Spell Mana Leech for Iron's spells (plan 4.9): a hit counts if it is an Iron's spell ({@link SpellDamageSource}) cast
 * by the player himself and its direct entity is the player (instant and channeled spells), a spell projectile or a
 * cone. Zones, summons and damage over time do not count. Entity tags change the rule:
 * {@code runelessrpg_bridge:spell_leech/excluded} never counts, {@code runelessrpg_bridge:spell_leech/direct} always
 * counts. RunelessRPG applies all its own rules (attribute, falloff per target with the same counter as Spell Engine,
 * cap on the target's health, {@code #runelessrpg:no_mana_leech}).
 */
public final class IronsLeech implements SpellHitPredicate {
    public static final TagKey<EntityType<?>> DIRECT = tag("spell_leech/direct");
    public static final TagKey<EntityType<?>> EXCLUDED = tag("spell_leech/excluded");

    private static boolean registered;

    private IronsLeech() {}

    static void register() {
        if (!registered) {
            registered = true;
            RunelessApi.registerSpellHitPredicate(new IronsLeech());
        }
    }

    @Override
    public boolean isDirectSpellHit(DamageSource source, LivingEntity target, ServerPlayer player) {
        if (!IronsAdapter.spellManaLeech() || !IronsHooks.applies(player) || !(source instanceof SpellDamageSource)) {
            return false;
        }
        if (source.getEntity() != player) {
            return false; // cast by someone else, or by a summon
        }
        Entity direct = source.getDirectEntity();
        if (direct == null) {
            return false;
        }
        if (direct == player) {
            return true; // instant and channeled spells (Ray of Frost, Eldritch Blast ... use indirect(), but hit directly)
        }
        EntityType<?> type = direct.getType();
        if (type.is(EXCLUDED)) {
            return false;
        }
        if (type.is(DIRECT)) {
            return true;
        }
        if (direct instanceof AoeEntity) {
            return false;
        }
        return direct instanceof AbstractMagicProjectile || direct instanceof AbstractConeProjectile;
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, path));
    }
}
