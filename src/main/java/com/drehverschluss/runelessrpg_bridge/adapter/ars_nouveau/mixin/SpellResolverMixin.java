package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A failed mana check of an Ars cast also flashes RunelessRPG's bar (Ars' own message and red flash stay). */
@Mixin(value = SpellResolver.class, remap = false)
public abstract class SpellResolverMixin {
    @ModifyReturnValue(method = "enoughMana", at = @At("RETURN"), require = 0)
    private boolean runelessrpg_bridge$afterCheck(boolean result, @Local(argsOnly = true) LivingEntity entity) {
        ArsHooks.afterManaCheck((SpellResolver) (Object) this, entity, result);
        return result;
    }
}
