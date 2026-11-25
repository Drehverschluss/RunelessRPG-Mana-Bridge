package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Enough mana?" and the payment of Ars casts by players (spell books, bows, crossbows, scrying; also PlayerCaster):
 * the shared pool with the effective cost and the debt rules. The cost Ars passes here is its final one (after its
 * discounts and SpellCostCalcEvent.Pre or .Post with all other listeners).
 */
@Mixin(value = LivingCaster.class, remap = false)
public abstract class LivingCasterMixin {
    @Shadow
    public LivingEntity livingEntity;

    @Inject(method = "enoughMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$enoughMana(int totalCost, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = ArsHooks.enoughMana(livingEntity, totalCost);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "expendMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$expendMana(int totalCost, CallbackInfo ci) {
        if (ArsHooks.expendMana(livingEntity, totalCost)) {
            ci.cancel();
        }
    }
}
