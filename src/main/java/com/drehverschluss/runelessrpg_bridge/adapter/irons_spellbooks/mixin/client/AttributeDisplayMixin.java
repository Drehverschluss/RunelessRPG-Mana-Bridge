package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.extensions.IAttributeExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Flat values of {@code irons_spellbooks:max_mana} in the display unit: "+50 Max Mana" on Iron's gear shows "+10" with
 * RunelessRPG's bar. Hooks the attribute's own text ({@code toValueComponent}), so mods that use it (e.g. Gearscope)
 * follow too. Percent values stay as they are.
 */
@Mixin(value = IAttributeExtension.class, remap = false)
public interface AttributeDisplayMixin {
    @ModifyReturnValue(method = "toValueComponent", at = @At("RETURN"), require = 0)
    private MutableComponent runelessrpg_bridge$maxMana(MutableComponent original,
            @Local(argsOnly = true) AttributeModifier.Operation operation, @Local(argsOnly = true) double value) {
        if (operation != null && operation != AttributeModifier.Operation.ADD_VALUE) {
            return original;
        }
        if (!((Object) this instanceof Attribute attribute) || !IronsHooks.isMaxMana(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute))) {
            return original;
        }
        double converted = IronsClientHooks.maxManaValue(value);
        if (Double.isNaN(converted)) {
            return original;
        }
        return Component.literal(ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(converted));
    }
}
