package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin.client;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.client.ArsClientHooks;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The cost the spell book's mana bar shows includes mana_cost (its max comes from ManaUtil.getMaxMana, the shared max). */
@Mixin(value = GuiSpellBook.class, remap = false)
public abstract class GuiSpellBookMixin {
    @ModifyReturnValue(method = "getCurrentManaCost", at = @At("RETURN"), require = 0)
    private int runelessrpg_bridge$cost(int original) {
        return ArsClientHooks.bookCost(original);
    }
}
