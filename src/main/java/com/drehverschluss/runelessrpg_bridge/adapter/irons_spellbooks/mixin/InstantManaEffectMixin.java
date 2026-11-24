package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.redspace.ironsspellbooks.effect.InstantManaEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Iron's Mana potion adds a percentage of the shared max (in Iron's units) for players. */
@Mixin(value = InstantManaEffect.class, remap = false)
public abstract class InstantManaEffectMixin {
    @WrapOperation(method = "applyInstantenousEffect", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double runelessrpg_bridge$sharedMax(LivingEntity entity, Holder<Attribute> attribute, Operation<Double> original) {
        if (entity instanceof Player player && IronsHooks.isMaxMana(attribute) && IronsHooks.applies(player)) {
            return IronsHooks.maxMana(player);
        }
        return original.call(entity, attribute);
    }
}
