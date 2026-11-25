package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg.api.ManaStorage;
import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.BonusBridge;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Iron's mana as RunelessRPG's storage while Iron's leads ({@code manaSystem = irons_spellbooks}, plan 4.3). All values
 * in RunelessRPG mana: Iron's mana × factor. Writing goes through {@link MagicData#setMana} (fires Iron's
 * {@code ChangeManaEvent}, limits to Iron's max) and sends Iron's own sync packet when the whole number changes, so
 * Iron's bar follows at once. Iron's cannot go below 0 (RunelessRPG keeps the debt account), regenerates itself and has
 * its own rules for login and respawn ({@code manaSpawnPercent}).
 */
public final class IronsStorage implements ManaStorage {
    public static final IronsStorage INSTANCE = new IronsStorage();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, IronsAdapter.ID);

    private IronsStorage() {}

    /** @return true while this storage is RunelessRPG's active storage (server side) */
    public static boolean isActive() {
        return RunelessApi.getStorage() == INSTANCE;
    }

    private static double factor(ServerPlayer player) {
        return Pool.factor(player, IronsAdapter.ID);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public double getMana(ServerPlayer player) {
        return MagicData.getPlayerMagicData(player).getMana() * factor(player);
    }

    @Override
    public double getMaxMana(ServerPlayer player) {
        return Math.max(0.0, player.getAttributeValue(AttributeRegistry.MAX_MANA)) * factor(player);
    }

    @Override
    public void setMana(ServerPlayer player, double mana) {
        MagicData data = MagicData.getPlayerMagicData(player);
        int before = (int) data.getMana();
        data.setMana((float) (mana / factor(player)));
        if ((int) data.getMana() != before && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new SyncManaPacket(data));
        }
    }

    @Override
    public boolean canGoNegative() {
        return false;
    }

    @Override
    public boolean regeneratesItself() {
        return true;
    }

    @Override
    public boolean handlesLifecycle() {
        return true;
    }

    /**
     * Only the flat regeneration bonuses of the other mods (RunelessRPG's {@code mana_regen} modifiers in RunelessRPG mana,
     * plan 4.6); percent bonuses are modifiers on {@code irons_spellbooks:mana_regen}, so nothing counts twice.
     * RunelessRPG's base regeneration does not count here.
     */
    @Override
    public double bonusRegenPerSecond(ServerPlayer player) {
        return BonusBridge.current(player).regenFlat() * factor(player);
    }
}
