package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Iron's bar shows the shared max, and in "contextual" mode also while a casting item of another mod is held. */
@Mixin(value = ManaBarOverlay.class, remap = false)
public abstract class ManaBarOverlayMixin {
    @WrapOperation(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double runelessrpg_bridge$renderMax(LocalPlayer player, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(player, attribute);
        return IronsHooks.isMaxMana(attribute) ? IronsClientHooks.barMax(player, value) : value;
    }

    @WrapOperation(method = "shouldShowManaBar", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private static double runelessrpg_bridge$showMax(Player player, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(player, attribute);
        return IronsHooks.isMaxMana(attribute) ? IronsClientHooks.barMax(player, value) : value;
    }

    @ModifyReturnValue(method = "shouldShowManaBar", at = @At("RETURN"), require = 0)
    private static boolean runelessrpg_bridge$show(boolean original, @Local(argsOnly = true) Player player) {
        return IronsClientHooks.showBar(player, original);
    }
}
