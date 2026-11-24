package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Iron's respawn rule off for players; client check before casting from an item asks the shared pool. */
@Mixin(value = ServerPlayerEvents.class, remap = false)
public abstract class ServerPlayerEventsMixin {
    @WrapOperation(method = "onPlayerRespawn", require = 0, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"))
    private static void runelessrpg_bridge$respawn(MagicData data, float mana, Operation<Void> original,
            @Local(argsOnly = true) PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && IronsHooks.applies(player) && IronsHooks.ignoreOwnRespawnRule()) {
            return;
        }
        original.call(data, mana);
    }

    @WrapOperation(method = "onUseItem", require = 0, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int runelessrpg_bridge$clientCheck(AbstractSpell spell, int level, Operation<Integer> original,
            @Local(argsOnly = true) PlayerInteractEvent.RightClickItem event) {
        return IronsHooks.clientCastCost(event.getEntity(), original.call(spell, level));
    }
}
