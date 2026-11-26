package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server value {@code applyOvercharge} of every adapter and whether another bridge pays that mod's spells itself
 * ({@code paidByOtherBridge}, since phase C5C.1: Ars 'n Spells) (server → client), for the client's own cost check, the
 * converted cost displays and the overcharge line in tooltips. A payload id of its own (0.2.0): clients and servers of
 * 0.1.0 just do not have it, so {@link BridgeSettingsPayload} stays unchanged and both versions still connect. Without
 * this payload (server 0.1.0) the client counts overcharge as off for spells of other mods, as that server does.
 *
 * @since 0.2.0 (phase C5B)
 */
public record OverchargePayload(Map<String, Boolean> applyOvercharge, Map<String, Boolean> paidByOtherBridge) implements CustomPacketPayload {
    public static final Type<OverchargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "overcharge"));

    public static final StreamCodec<FriendlyByteBuf, OverchargePayload> CODEC = StreamCodec.of(OverchargePayload::write, OverchargePayload::read);

    private static void write(FriendlyByteBuf buf, OverchargePayload p) {
        writeMap(buf, p.applyOvercharge);
        writeMap(buf, p.paidByOtherBridge);
    }

    private static OverchargePayload read(FriendlyByteBuf buf) {
        return new OverchargePayload(readMap(buf), readMap(buf));
    }

    private static void writeMap(FriendlyByteBuf buf, Map<String, Boolean> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, Boolean> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeBoolean(entry.getValue());
        }
    }

    private static Map<String, Boolean> readMap(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(buf.readUtf(), buf.readBoolean());
        }
        return Map.copyOf(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
