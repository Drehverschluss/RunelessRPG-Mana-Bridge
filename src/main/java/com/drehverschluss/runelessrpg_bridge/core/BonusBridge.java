package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The bridge (plan 4.1, 4.6): bonuses on max mana and regeneration of every connected mod that does not lead are read
 * by operation, converted into the leading mod's units and handed to the leading adapter, which sets them as modifiers
 * of this bridge. All other adapters remove theirs. Runs every server tick for every real player.
 * <p>
 * The tick listener runs at the highest priority of {@link PlayerTickEvent.Post}: after the equipment of this tick is
 * applied (vanilla in the player tick, Curios in the entity tick before it) and before RunelessRPG limits the mana to the
 * maximum, so a relog or respawn never cuts the mana to a maximum without the bridged bonus.
 */
public final class BonusBridge {
    private static final Map<UUID, BridgeBonus> CURRENT = new ConcurrentHashMap<>();
    private static boolean errorLogged;

    private BonusBridge() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                update(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> CURRENT.remove(event.getEntity().getUUID()));
    }

    /** Recomputes the bonus of the leading pool for this player and sets or removes the modifiers. */
    public static void update(ServerPlayer player) {
        try {
            String leader = Leader.id();
            double leaderFactor = Pool.factor(player, leader);
            BridgeBonus sum = BridgeBonus.NONE;
            for (AdapterHandle handle : Adapters.all()) {
                if (handle.modId().equals(leader) || !handle.isConnectedOnServer()) {
                    continue;
                }
                BridgeBonus own = handle.adapter().readBonus(player)
                        .filter(handle.bridgeMaxMana(), handle.bridgeManaRegen(), handle.bridgePercentBonuses());
                sum = sum.plus(own.scaleFlat(Pool.factor(player, handle.modId()) / leaderFactor));
            }
            CURRENT.put(player.getUUID(), sum);
            for (AdapterHandle handle : Adapters.all()) {
                handle.adapter().applyBonus(player, handle.modId().equals(leader) ? sum : BridgeBonus.NONE);
            }
        } catch (RuntimeException | LinkageError e) {
            if (!errorLogged) {
                errorLogged = true;
                ManaBridge.LOGGER.error("Mana bridge bonus could not be updated", e);
            }
        }
    }

    /** @return the bonus the leading pool currently gets for this player, in the leading mod's units */
    public static BridgeBonus current(Player player) {
        return CURRENT.getOrDefault(player.getUUID(), BridgeBonus.NONE);
    }
}
