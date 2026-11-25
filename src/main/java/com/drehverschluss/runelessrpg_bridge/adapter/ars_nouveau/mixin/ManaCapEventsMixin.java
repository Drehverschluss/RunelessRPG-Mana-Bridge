package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.event.ManaCapEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ars' regeneration off for players while Ars follows; recomputing and sending its max in the same tick stays. */
@Mixin(value = ManaCapEvents.class, remap = false)
public abstract class ManaCapEventsMixin {
    @WrapOperation(method = "playerOnTick", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/hollingsworth/arsnouveau/common/capability/ManaCap;addMana(D)D"))
    private static double runelessrpg_bridge$noRegen(ManaCap cap, double amount, Operation<Double> original,
            @Local(argsOnly = true) PlayerTickEvent.Pre event) {
        if (ArsHooks.applies(event.getEntity()) && ArsHooks.disableOwnRegen()) {
            return cap.getCurrentMana();
        }
        return original.call(cap, amount);
    }
}
