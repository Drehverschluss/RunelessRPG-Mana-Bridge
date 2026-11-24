package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** "Enough mana?" of Iron's casts asks the shared pool with the effective cost (same check as on the client). */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellMixin {
    @ModifyReturnValue(method = "canBeCastedBy", at = @At("RETURN"), require = 0)
    private CastResult runelessrpg_bridge$check(CastResult result, @Local(argsOnly = true) int spellLevel,
            @Local(argsOnly = true) CastSource castSource, @Local(argsOnly = true) MagicData data, @Local(argsOnly = true) Player player) {
        return IronsHooks.checkCast((AbstractSpell) (Object) this, spellLevel, castSource, data, player, result);
    }
}
