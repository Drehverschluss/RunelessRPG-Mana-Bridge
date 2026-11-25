package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server values the client needs for its own checks and displays: leading pool, debt rule and the state of every
 * adapter. NeoForge sends the server config only once at login, so this payload is sent at login, after a config reload
 * and whenever an adapter or the leader changes.
 */
public record BridgeSettingsPayload(String leader, boolean foreignSpellsUseDebt, List<AdapterState> adapters) implements CustomPacketPayload {
    public static final Type<BridgeSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "settings"));

    /** State of one adapter: connected (leading or following), configured factor (-1 = auto) and whether mana_cost applies. */
    public record AdapterState(String modId, boolean connected, double manaFactor, boolean applyManaCost) {}

    public static final StreamCodec<FriendlyByteBuf, BridgeSettingsPayload> CODEC = StreamCodec.of(BridgeSettingsPayload::write, BridgeSettingsPayload::read);

    private static void write(FriendlyByteBuf buf, BridgeSettingsPayload p) {
        buf.writeUtf(p.leader);
        buf.writeBoolean(p.foreignSpellsUseDebt);
        buf.writeVarInt(p.adapters.size());
        for (AdapterState a : p.adapters) {
            buf.writeUtf(a.modId());
            buf.writeBoolean(a.connected());
            buf.writeDouble(a.manaFactor());
            buf.writeBoolean(a.applyManaCost());
        }
    }

    private static BridgeSettingsPayload read(FriendlyByteBuf buf) {
        String leader = buf.readUtf();
        boolean debt = buf.readBoolean();
        int n = buf.readVarInt();
        List<AdapterState> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new AdapterState(buf.readUtf(), buf.readBoolean(), buf.readDouble(), buf.readBoolean()));
        }
        return new BridgeSettingsPayload(leader, debt, List.copyOf(list));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
