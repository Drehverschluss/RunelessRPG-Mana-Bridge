package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron's client mana of the local player comes from RunelessRPG's client state (shared pool, Iron's units). */
@Mixin(value = ClientMagicData.class, remap = false)
public abstract class ClientMagicDataMixin {
    @Inject(method = "getPlayerMana", at = @At("HEAD"), cancellable = true, require = 0)
    private static void runelessrpg_bridge$playerMana(CallbackInfoReturnable<Integer> cir) {
        Integer mana = IronsClientHooks.playerMana();
        if (mana != null) {
            cir.setReturnValue(mana);
        }
    }
}
