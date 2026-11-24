package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Switch of Iron's mana for players on the server: reading gives the shared pool in Iron's units, writing sends the
 * difference to RunelessRPG. Mobs (no server player) keep their own mana.
 */
@Mixin(value = MagicData.class, remap = false)
public abstract class MagicDataMixin {
    @Shadow
    private ServerPlayer serverPlayer;

    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$getMana(CallbackInfoReturnable<Float> cir) {
        if (serverPlayer != null && IronsHooks.applies(serverPlayer)) {
            cir.setReturnValue(IronsHooks.getMana(serverPlayer));
        }
    }

    /** Iron's addMana adds to its own field, not to getMana(): route it through the switch too (Gluttony, addons). */
    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$addMana(float amount, CallbackInfo ci) {
        if (serverPlayer != null && IronsHooks.applies(serverPlayer)) {
            IronsHooks.setMana((MagicData) (Object) this, serverPlayer, IronsHooks.getMana(serverPlayer) + amount);
            ci.cancel();
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$setMana(float mana, CallbackInfo ci) {
        if (serverPlayer != null && IronsHooks.applies(serverPlayer)) {
            IronsHooks.setMana((MagicData) (Object) this, serverPlayer, mana);
            ci.cancel();
        }
    }
}
