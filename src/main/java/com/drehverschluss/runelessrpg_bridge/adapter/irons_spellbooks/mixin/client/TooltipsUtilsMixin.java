package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client.IronsClientHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Mana Cost" and "Mana/s" in Iron's spell tooltips in the display unit, with mana_cost and overcharge; below it the
 * overcharge line of the active spell.
 */
@Mixin(value = TooltipsUtils.class, remap = false)
public abstract class TooltipsUtilsMixin {
    @Inject(method = "getManaCostComponent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void runelessrpg_bridge$cost(CastType castType, int manaCost, CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent converted = IronsClientHooks.manaCostComponent(castType, manaCost);
        if (converted != null) {
            cir.setReturnValue(converted);
        }
    }

    @ModifyReturnValue(method = "formatActiveSpellTooltip", at = @At("RETURN"), require = 0)
    private static List<MutableComponent> runelessrpg_bridge$overcharge(List<MutableComponent> lines, @Local(argsOnly = true) SpellData spellData,
            @Local(argsOnly = true) CastSource castSource, @Local(argsOnly = true) LocalPlayer player) {
        IronsClientHooks.addOverchargeLine(lines, spellData, castSource, player);
        return lines;
    }
}
