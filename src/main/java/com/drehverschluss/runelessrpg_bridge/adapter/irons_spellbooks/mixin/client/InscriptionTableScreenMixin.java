package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The mana cost on the Inscription Table in the display unit, with mana_cost. */
@Mixin(value = InscriptionTableScreen.class, remap = false)
public abstract class InscriptionTableScreenMixin {
    @WrapOperation(method = "renderLorePage", require = 0, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/gui/inscription_table/InscriptionTableScreen;drawStatText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/GuiGraphics;IILjava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/MutableComponent;Lnet/minecraft/network/chat/Style;F)I"))
    private int runelessrpg_bridge$manaCost(InscriptionTableScreen screen, Font font, GuiGraphics graphics, int x, int y, String key,
            Style keyStyle, MutableComponent value, Style valueStyle, float scale, Operation<Integer> original) {
        String converted = IronsClientHooks.inscriptionManaCost(key, value.getString());
        MutableComponent shown = converted != null ? Component.literal(converted) : value;
        return original.call(screen, font, graphics, x, y, key, keyStyle, shown, valueStyle, scale);
    }
}
