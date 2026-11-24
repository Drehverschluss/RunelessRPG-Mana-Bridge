package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** "%d Mana" in the spell wheel in the display unit, with mana_cost. */
@Mixin(value = SpellWheelOverlay.class, remap = false)
public abstract class SpellWheelOverlayMixin {
    @WrapOperation(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent runelessrpg_bridge$manaCost(String key, Object[] args, Operation<MutableComponent> original) {
        MutableComponent converted = IronsClientHooks.wheelManaCost(key, args);
        return converted != null ? converted : original.call(key, args);
    }
}
