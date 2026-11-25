package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.client.ArsClientHooks;
import com.hollingsworth.arsnouveau.client.gui.GuiManaHUD;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ars' bar also shows while a casting item of another mod is held (Spell Engine spell weapons, Iron's spell books). */
@Mixin(value = GuiManaHUD.class, remap = false)
public abstract class GuiManaHUDMixin {
    @ModifyReturnValue(method = "shouldDisplayBar", at = @At("RETURN"), require = 0)
    private static boolean runelessrpg_bridge$show(boolean original) {
        return ArsClientHooks.showBar(original);
    }
}
