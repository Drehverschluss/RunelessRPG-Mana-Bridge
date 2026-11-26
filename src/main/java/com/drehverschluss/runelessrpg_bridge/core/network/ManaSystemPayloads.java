package com.drehverschluss.runelessrpg_bridge.core.network;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payloads of the first-start question "which mana system leads" (plan 5.6). All three are in the bridge's optional
 * channel; the server only sends them to clients that have them, other clients get a clickable chat line instead.
 */
public final class ManaSystemPayloads {
    private ManaSystemPayloads() {}

    /**
     * One mana system the player can choose, with what it brings while it leads (own units, from the real configs).
     *
     * @param id           mod id, the value of {@code manaSystem}
     * @param name         display name of the mod
     * @param current      true for the pool that leads right now
     * @param baseMaxMana  base max mana
     * @param regen        flat regeneration per second
     * @param regenPercent regeneration in percent of the max per second
     */
    public record Option(String id, String name, boolean current, double baseMaxMana, double regen, double regenPercent) {}

    /** Server → client: open the question with these choices. */
    public record Ask(List<Option> options) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "ask_mana_system"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = StreamCodec.of(Ask::write, Ask::read);

        private static void write(FriendlyByteBuf buf, Ask p) {
            buf.writeVarInt(p.options.size());
            for (Option o : p.options) {
                buf.writeUtf(o.id());
                buf.writeUtf(o.name());
                buf.writeBoolean(o.current());
                buf.writeDouble(o.baseMaxMana());
                buf.writeDouble(o.regen());
                buf.writeDouble(o.regenPercent());
            }
        }

        private static Ask read(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 64);
            List<Option> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Option(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
            }
            return new Ask(List.copyOf(list));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: the player chose this mana system (the server checks the permission again). */
    public record Answer(String id) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "answer_mana_system"));
        public static final StreamCodec<FriendlyByteBuf, Answer> CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.id, 64),
                buf -> new Answer(buf.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: someone has answered, close the question if it is open (the reason comes as a chat message). */
    public record Close() implements CustomPacketPayload {
        public static final Close INSTANCE = new Close();
        public static final Type<Close> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, "close_mana_system"));
        public static final StreamCodec<FriendlyByteBuf, Close> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
