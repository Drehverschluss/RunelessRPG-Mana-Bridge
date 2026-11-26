package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A rune that a paid spell places keeps the overcharge bonus as its snapshot (like a zone), plan 4.10. */
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.RuneTile", remap = false)
public abstract class RuneTileMixin {
    @Inject(method = "setSpell", at = @At("HEAD"), require = 0)
    private void runelessrpg_bridge$runeSpell(Spell spell, CallbackInfo ci) {
        ArsHooks.runeSpell((BlockEntity) (Object) this); // by name: RuneTile implements GeckoLib interfaces
    }
}
