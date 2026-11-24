package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Mana Cost" and "Mana/s" in Iron's spell tooltips in the display unit, with mana_cost. */
@Mixin(value = TooltipsUtils.class, remap = false)
public abstract class TooltipsUtilsMixin {
    @Inject(method = "getManaCostComponent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void runelessrpg_bridge$cost(CastType castType, int manaCost, CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent converted = IronsClientHooks.manaCostComponent(castType, manaCost);
        if (converted != null) {
            cir.setReturnValue(converted);
        }
    }
}
