package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.common.capability.ManaCap;
import com.hollingsworth.arsnouveau.common.capability.ManaData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Switch of Ars' mana for players: reading gives the shared pool in Ars units (server and local client), the max the
 * shared max; writing on the server sends the difference to RunelessRPG ({@code addMana} and {@code removeMana} go
 * through {@code setMana}). Ars' stored values stay as they are.
 */
@Mixin(value = ManaCap.class, remap = false)
public abstract class ManaCapMixin {
    @Shadow
    LivingEntity entity;

    @Inject(method = "getCurrentMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$getCurrentMana(CallbackInfoReturnable<Double> cir) {
        if (entity instanceof Player player && ArsHooks.applies(player)) {
            cir.setReturnValue(ArsHooks.getMana(player));
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$getMaxMana(CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Player player && ArsHooks.applies(player)) {
            cir.setReturnValue(ArsHooks.getMaxMana(player));
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, require = 0)
    private void runelessrpg_bridge$setMana(double mana, CallbackInfoReturnable<Double> cir) {
        if (entity instanceof ServerPlayer player && ArsHooks.applies(player)) {
            cir.setReturnValue(ArsHooks.setMana(player, mana));
        }
    }

    /**
     * Ars' sync packet carries the shared values (Ars units) instead of Ars' own stored ones, so a client without this
     * bridge shows the shared pool too; clients with the bridge read the shared pool anyway.
     */
    @WrapOperation(method = "syncToClient", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/hollingsworth/arsnouveau/common/capability/ManaData;serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag runelessrpg_bridge$syncShared(ManaData data, HolderLookup.Provider provider, Operation<CompoundTag> original,
            @Local(argsOnly = true) ServerPlayer player) {
        CompoundTag tag = original.call(data, provider);
        if (ArsHooks.applies(player)) {
            tag.putDouble("current", ArsHooks.getMana(player));
            tag.putInt("max", ArsHooks.getMaxMana(player));
        }
        return tag;
    }
}
