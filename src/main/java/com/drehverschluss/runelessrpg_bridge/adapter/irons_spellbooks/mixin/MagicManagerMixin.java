package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron's regeneration off for players, shared max for the regeneration, shared check for channeled spells. */
@Mixin(value = MagicManager.class, remap = false)
public abstract class MagicManagerMixin {
    @Inject(method = "regenPlayerMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$noRegen(ServerPlayer player, MagicData data, CallbackInfoReturnable<Boolean> cir) {
        if (IronsHooks.applies(player) && IronsHooks.disableOwnRegen()) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "regenPlayerMana", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double runelessrpg_bridge$regenMax(ServerPlayer player, Holder<Attribute> attribute, Operation<Double> original) {
        if (IronsHooks.isMaxMana(attribute) && IronsHooks.applies(player)) {
            return IronsHooks.maxMana(player);
        }
        return original.call(player, attribute);
    }

    @WrapOperation(method = "lambda$tick$0", require = 0, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"))
    private float runelessrpg_bridge$channelCheck(MagicData data, Operation<Float> original, @Local ServerPlayer player) {
        return IronsHooks.channelMana(player, data, original.call(data));
    }
}
