package com.drehverschluss.runelessrpg_bridge.core.client;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.AdapterHandle;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.Leader;
import com.drehverschluss.runelessrpg_bridge.core.ManaSystemChoice;
import com.drehverschluss.runelessrpg_bridge.core.network.ManaSystemPayloads;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge's config screen with a choice button instead of a text field for {@code manaSystem} (server config, section
 * {@code [general]}) and {@code manaDisplay} (client config, section {@code [display]}), plan 5.6. A click goes through
 * the values like for an enum; the file keeps mod ids. Offered are the values that work now; the lists come from the
 * adapters. A value in the file that does not work now (mod missing, adapter off) stays and is shown as not available.
 * <p>
 * NeoForge 21.1 lets the server config be edited only in a singleplayer world that is not open to LAN (in the main menu
 * it is not loaded, on a server or with LAN open it is read-only); elsewhere the command {@code /runelessrpg_bridge
 * manaSystem} does it. Every other option is NeoForge's own element.
 */
public final class BridgeConfigScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    private static final String SECTION = "neoforge.configuration.uitext.section";
    private static final String SECTION_TEXT = "neoforge.configuration.uitext.sectiontext";
    private static final String VALUE = ManaBridge.MODID + ".configuration.value.";

    private enum Kind { SYSTEM, DISPLAY }

    /** Values that were in the file when the button was made: they stay selectable while this screen lives. */
    private final Set<String> seen = new LinkedHashSet<>();

    /** Top section of a config file (factory for {@link ConfigurationScreen}). */
    public BridgeConfigScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    private BridgeConfigScreen(Context parentContext, Screen parent, Map<String, Object> valueSpecs, String key,
            Set<? extends UnmodifiableConfig.Entry> entrySet, Component title) {
        super(parentContext, parent, valueSpecs, key, entrySet, title);
    }

    /** Same button as NeoForge's, but the sub-section is this class too (NeoForge would open its own class). */
    @Override
    protected @Nullable Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
        if (subconfig.isEmpty()) {
            return null;
        }
        String buttonKey = getTranslationKey(key) + ".button";
        Component text = Component.translatable(SECTION, I18n.exists(buttonKey) ? Component.translatable(buttonKey) : Component.translatable(SECTION_TEXT));
        Button button = Button.builder(text, b -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                        k -> new BridgeConfigScreen(context, this, subconfig.valueMap(), key, subsection.entrySet(),
                                Component.translatable(getTranslationKey(key))).rebuild())))
                .tooltip(Tooltip.create(getTooltipComponent(key, null)))
                .width(Button.DEFAULT_WIDTH)
                .build();
        return new Element(Component.translatable(SECTION, getTranslationComponent(key)), getTooltipComponent(key, null), button, false);
    }

    @Override
    protected @Nullable Element createStringValue(String key, Predicate<String> tester, Supplier<String> source, Consumer<String> target) {
        Kind kind = kind(key);
        if (kind == null) {
            return super.createStringValue(key, tester, source, target);
        }
        String current = source.get();
        seen.add(current);
        List<String> values = new ArrayList<>(available(kind));
        for (String value : seen) {
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        if (!values.contains(current)) {
            values.add(current);
        }
        OptionInstance<String> option = new OptionInstance<>(getTranslationKey(key), value -> Tooltip.create(valueTooltip(kind, value)),
                (caption, value) -> label(kind, value), new Custom<>(List.copyOf(values)), current, newValue -> {
                    // same change detection as NeoForge's enum buttons: a cycle button always brings a new value
                    undoManager.add(v -> {
                        target.accept(v);
                        onChanged(key);
                    }, newValue, v -> {
                        target.accept(v);
                        onChanged(key);
                    }, source.get());
                });
        return new Element(getTranslationComponent(key), getTooltipComponent(key, null), option);
    }

    /**
     * {@code displayUnit} (client config, {@code [display]}): NeoForge's enum button with readable names, and a tooltip for
     * the selected value (its name, what it does and its name in the file). Other enums stay NeoForge's own.
     */
    @Override
    protected <T extends Enum<T>> @Nullable Element createEnumValue(String key, ModConfigSpec.ValueSpec spec, Supplier<T> source, Consumer<T> target) {
        if (!context.modId().equals(ManaBridge.MODID) || context.modConfig().getType() != ModConfig.Type.CLIENT || context.keylist().size() != 1
                || !context.keylist().get(0).equals("display") || !key.equals("displayUnit")) {
            return super.createEnumValue(key, spec, source, target);
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) spec.getClazz();
        List<T> list = java.util.Arrays.stream(type.getEnumConstants()).filter(spec::test).toList();
        OptionInstance<T> option = new OptionInstance<>(getTranslationKey(key), value -> Tooltip.create(unitTooltip(value)),
                (caption, value) -> value instanceof net.neoforged.neoforge.common.TranslatableEnum named ? named.getTranslatedName()
                        : Component.literal(value.name()),
                new Custom<>(list), source.get(), newValue -> undoManager.add(v -> {
                    target.accept(v);
                    onChanged(key);
                }, newValue, v -> {
                    target.accept(v);
                    onChanged(key);
                }, source.get()));
        return new Element(getTranslationComponent(key), getTooltipComponent(key, null), option);
    }

    private static Component unitTooltip(Enum<?> value) {
        String name = value.name().toLowerCase(Locale.ROOT);
        Component title = value instanceof net.neoforged.neoforge.common.TranslatableEnum named ? named.getTranslatedName() : Component.literal(value.name());
        return Component.empty().append(title.copy().withStyle(ChatFormatting.BOLD)).append("\n")
                .append(Component.translatable(ManaBridge.MODID + ".configuration.displayUnit." + name + ".tooltip")).append("\n")
                .append(Component.translatable(ManaBridge.MODID + ".configuration.displayUnit.file", value.name()).withStyle(ChatFormatting.GRAY));
    }

    private @Nullable Kind kind(String key) {
        if (!context.modId().equals(ManaBridge.MODID) || context.keylist().size() != 1) {
            return null;
        }
        String section = context.keylist().get(0);
        ModConfig.Type type = context.modConfig().getType();
        if (type == ModConfig.Type.SERVER && section.equals("general") && key.equals("manaSystem")) {
            return Kind.SYSTEM;
        }
        if (type == ModConfig.Type.CLIENT && section.equals("display") && key.equals("manaDisplay")) {
            return Kind.DISPLAY;
        }
        return null;
    }

    /** Values that work now: leading pools that can lead (server state of the world), or bars of installed mods. */
    private static List<String> available(Kind kind) {
        if (kind == Kind.SYSTEM) {
            return Leader.choices();
        }
        List<String> list = new ArrayList<>(List.of("auto", "all", Adapters.RUNELESS));
        for (AdapterHandle handle : Adapters.foreign()) {
            if (!handle.adapter().hudLayers().isEmpty()) {
                list.add(handle.modId());
            }
        }
        return list;
    }

    private static Component label(Kind kind, String value) {
        if (!available(kind).contains(value)) {
            return Component.translatable(VALUE + "unavailable", value).withStyle(ChatFormatting.GRAY);
        }
        return switch (value) {
            case "auto" -> Component.translatable(VALUE + "auto");
            case "all" -> Component.translatable(VALUE + "all");
            default -> Component.literal(ManaSystemChoice.name(value));
        };
    }

    private Component valueTooltip(Kind kind, String value) {
        MutableComponent tip = Component.empty().append(label(kind, value).copy().withStyle(ChatFormatting.BOLD)).append("\n");
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (kind == Kind.SYSTEM) {
            if (!available(kind).contains(value)) {
                return tip.append(Component.translatable(VALUE + "system.unavailable", Leader.whyNot(id)));
            }
            for (ManaSystemPayloads.Option option : ManaSystemChoice.options(serverPlayer())) {
                if (option.id().equals(value)) {
                    tip.append(ManaSystemChoice.describe(option, ManaSystemScreen.comma()));
                    if (option.current()) {
                        tip.append("\n").append(Component.translatable(VALUE + "system.current").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            return tip;
        }
        if (!available(kind).contains(value)) {
            return tip.append(Component.translatable(VALUE + "display.unavailable"));
        }
        return switch (value) {
            case "auto" -> tip.append(Component.translatable(VALUE + "display.auto"));
            case "all" -> tip.append(Component.translatable(VALUE + "display.all"));
            case Adapters.RUNELESS -> tip.append(Component.translatable(VALUE + "display.runelessrpg"));
            default -> tip.append(Component.translatable(VALUE + "display.mod", ManaSystemChoice.name(value)));
        };
    }

    /** The player on the integrated server (the screen only edits the server config in a singleplayer world). */
    private @Nullable ServerPlayer serverPlayer() {
        if (minecraft == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return null;
        }
        return minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
    }
}
