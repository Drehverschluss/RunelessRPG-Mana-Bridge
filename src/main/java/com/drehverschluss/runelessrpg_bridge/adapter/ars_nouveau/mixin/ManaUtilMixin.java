package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code ManaUtil.getMaxMana} gives the shared max for players (spell book bar, limit of the caster tome). Ars' own
 * computation {@code calcMaxMana} stays untouched: the bridge reads its parts.
 */
@Mixin(value = ManaUtil.class, remap = false)
public abstract class ManaUtilMixin {
    @ModifyReturnValue(method = "getMaxMana", at = @At("RETURN"), require = 0)
    private static int runelessrpg_bridge$sharedMax(int original, @Local(argsOnly = true) Player player) {
        return ArsHooks.utilMaxMana(player, original);
    }
}
