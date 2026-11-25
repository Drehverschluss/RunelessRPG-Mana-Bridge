package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.client.ArsClientHooks;
import com.hollingsworth.arsnouveau.common.items.curios.DiscountRing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** "Reduce the cost of all spells by %d." on Rings of Discount in the display unit, with mana_cost. */
@Mixin(value = DiscountRing.class, remap = false)
public abstract class DiscountRingMixin {
    @WrapOperation(method = "appendHoverText", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent runelessrpg_bridge$discount(String key, Object[] args, Operation<MutableComponent> original) {
        MutableComponent converted = ArsClientHooks.discountText(key, args);
        return converted != null ? converted : original.call(key, args);
    }
}
