package com.drehverschluss.runelessrpg_bridge.core.client;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.ManaSystemChoice;
import com.drehverschluss.runelessrpg_bridge.core.network.ManaSystemPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The question at the first login (plan 5.6): which mana system leads. One button per possible pool with one line on its
 * base max mana and regeneration (from the server's real configs), "Decide later", and how to change it later. Any
 * choice goes to the server, which checks the permission again; "Decide later" and Esc only close the window (the server
 * asks again at the next login). Fits into 854 × 480 with the automatic GUI scale.
 */
public final class ManaSystemScreen extends Screen {
    private static final String KEY = ManaBridge.MODID + ".ask.";
    private static final int TEXT = 0xFFFFFF;
    private static final int GRAY = 0xA0A0A0;
    private static final int DARK = 0x808080;

    /** A question that arrived while another screen was open (e.g. the world was still loading). */
    private static @Nullable ManaSystemPayloads.Ask pending;

    private final List<ManaSystemPayloads.Option> options;
    /** Text lines with their position, drawn in {@link #render}. */
    private final List<Line> lines = new ArrayList<>();

    private record Line(FormattedCharSequence text, int y, int color) {}

    ManaSystemScreen(List<ManaSystemPayloads.Option> options) {
        super(Component.translatable(KEY + "title"));
        this.options = List.copyOf(options);
    }

    // ------------------------------------------------------------------ opening and closing

    static void register() {
        com.drehverschluss.runelessrpg_bridge.core.network.ClientBridgeState.setAskHandlers(payload -> {
            pending = payload;
            tryOpen();
        }, () -> {
            pending = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ManaSystemScreen) {
                mc.setScreen(null);
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            if (pending != null) {
                tryOpen();
            }
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> pending = null);
    }

    /** Opens the question once the player is in the world and no other screen is open. */
    private static void tryOpen() {
        Minecraft mc = Minecraft.getInstance();
        ManaSystemPayloads.Ask ask = pending;
        if (ask == null || mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        pending = null;
        mc.setScreen(new ManaSystemScreen(ask.options()));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        lines.clear();
        int textWidth = Math.min(width - 16, 400);
        int buttonWidth = Math.min(textWidth, 220);
        int cx = width / 2;
        boolean comma = comma();

        List<FormattedCharSequence> title = font.split(getTitle().copy().withStyle(ChatFormatting.BOLD), textWidth);
        List<FormattedCharSequence> intro = font.split(Component.translatable(KEY + "intro"), textWidth);
        List<List<FormattedCharSequence>> descriptions = new ArrayList<>();
        for (ManaSystemPayloads.Option option : options) {
            descriptions.add(font.split(ManaSystemChoice.describe(option, comma), textWidth));
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable(KEY + "hint"), textWidth);
        List<FormattedCharSequence> pack = font.split(Component.translatable(KEY + "pack"), textWidth);

        int lineHeight = font.lineHeight + 1;
        int descLines = descriptions.stream().mapToInt(List::size).sum();
        int fixed = (title.size() + intro.size() + descLines + hint.size() + pack.size()) * lineHeight + (options.size() + 1) * 20;
        // gaps: after title, after intro, between options (n - 1), before "later", before the hint, before the pack line
        int gapCount = 4 + options.size();
        int gap = Math.max(1, Math.min(8, (height - 8 - fixed) / gapCount));
        int total = fixed + gap * gapCount;
        int y = Math.max(4, (height - total) / 2);

        y = addLines(title, y, TEXT, lineHeight) + gap;
        y = addLines(intro, y, GRAY, lineHeight) + gap;
        for (int i = 0; i < options.size(); i++) {
            ManaSystemPayloads.Option option = options.get(i);
            MutableComponent label = option.current()
                    ? Component.translatable(KEY + "current", option.name())
                    : Component.literal(option.name());
            addRenderableWidget(Button.builder(label, b -> answer(option.id())).bounds(cx - buttonWidth / 2, y, buttonWidth, 20).build());
            y += 20;
            y = addLines(descriptions.get(i), y + 1, GRAY, lineHeight) - 1;
            if (i < options.size() - 1) {
                y += gap;
            }
        }
        y += gap;
        addRenderableWidget(Button.builder(Component.translatable(KEY + "later"), b -> onClose())
                .bounds(cx - buttonWidth / 2, y, buttonWidth, 20).build());
        y += 20 + gap;
        y = addLines(hint, y, DARK, lineHeight) + gap;
        addLines(pack, y, DARK, lineHeight);
    }

    private int addLines(List<FormattedCharSequence> texts, int y, int color, int lineHeight) {
        for (FormattedCharSequence text : texts) {
            lines.add(new Line(text, y, color));
            y += lineHeight;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Line line : lines) {
            graphics.drawCenteredString(font, line.text(), width / 2, line.y(), line.color());
        }
    }

    private void answer(String id) {
        PacketDistributor.sendToServer(new ManaSystemPayloads.Answer(id));
        ManaBridge.LOGGER.info("Mana system chosen in the window: {}", id);
        onClose();
    }

    /** @return true if the client language writes a decimal comma (from the bridge's own number format) */
    static boolean comma() {
        return I18n.get(ManaBridge.MODID + ".status.decimal", "1", "5").equals("1,5");
    }
}
