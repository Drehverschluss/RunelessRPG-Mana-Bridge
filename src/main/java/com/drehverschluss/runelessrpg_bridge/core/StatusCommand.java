package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /runelessrpg_bridge status [player]}: everything a bug report needs in one place (versions, leading pool,
 * adapters, other bridges, config, the player's mana). Every player may see their own status; operators (permission
 * level 2) and the console may name a player.
 * <p>
 * Every line is a translation key with an English fallback, so clients without the bridge and the console read English.
 * A click at the end copies the same lines as plain English text to the clipboard, whatever language the client uses.
 */
public final class StatusCommand {
    static final String ISSUES = "https://github.com/Drehverschluss/RunelessRPG-Mana-Bridge/issues/new/choose";
    private static final String PREFIX = ManaBridge.MODID + ".status.";
    /** Mana mods the bridge knows by name but has no adapter for in this version. */
    private static final List<String> MANA_MODS_WITHOUT_ADAPTER = List.of();

    private StatusCommand() {}

    /** A number in a line: whole numbers as they are, others with up to three decimals (comma or dot by language). */
    record Num(double value, boolean signed) {}

    /** One translatable text with its English fallback; arguments are strings, {@link Num} or other texts. */
    record Text(String key, String english, Object... args) {
        MutableComponent chat() {
            Object[] out = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                out[i] = args[i] instanceof Text text ? text.chat() : args[i] instanceof Num num ? number(num) : String.valueOf(args[i]);
            }
            return Component.translatableWithFallback(PREFIX + key, english, out);
        }

        String plain() {
            Object[] out = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                out[i] = args[i] instanceof Text text ? text.plain() : args[i] instanceof Num num ? plainNumber(num) : String.valueOf(args[i]);
            }
            return String.format(Locale.ROOT, english, out);
        }
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ManaBridge.MODID)
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int status(CommandSourceStack source, @Nullable ServerPlayer target) {
        ServerPlayer self = source.getPlayer();
        if (target != null && target != self && !source.hasPermission(2)) {
            source.sendSystemMessage(t("no_permission", "You need operator rights (permission level 2) to see the status of another player.").chat()
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer player = target != null ? target : self;
        List<Text> lines = lines(player);
        // sendSystemMessage, not sendSuccess: the answer must also show on servers with the gamerule sendCommandFeedback = false
        for (Text line : lines) {
            source.sendSystemMessage(line.chat());
        }
        if (source.isPlayer()) {
            String plain = lines.stream().map(Text::plain).collect(Collectors.joining("\n"));
            MutableComponent copy = t("copy", "[Copy as text for a bug report]").chat().withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, plain))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            t("copy.hover", "Copies this status as English text to the clipboard").chat())));
            MutableComponent report = t("report", "[Report a bug]").chat().withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ISSUES))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(ISSUES))));
            source.sendSystemMessage(Component.empty().append(copy).append(" ").append(report));
        }
        return 1;
    }

    /**
     * All lines of the status, for this player or without player values (console without a name). Kept short so that the
     * whole output fits into the chat (at most 20 lines) with all three mods, a chain with another bridge and a familiar.
     */
    static List<Text> lines(@Nullable ServerPlayer player) {
        List<Text> lines = new ArrayList<>();
        lines.add(t("header", "===== RunelessRPG Mana Bridge status ====="));
        lines.add(t("versions", "Versions: %s", versions()));

        String leader = Leader.id();
        String problem = Leader.problem();
        if (problem.isEmpty()) {
            lines.add(t("leader", "Leading pool: %s (manaSystem = \"%s\")", name(leader), Leader.configured()));
        } else {
            lines.add(t("leader.fallback", "Leading pool: %s, because manaSystem = \"%s\" cannot be used: %s", name(leader), Leader.configured(), problem));
        }

        lines.add(t("adapters", "Adapters:"));
        for (AdapterHandle handle : Adapters.known()) {
            lines.add(adapterLine(handle, player));
        }
        for (String modId : MANA_MODS_WITHOUT_ADAPTER) {
            if (ModList.get().isLoaded(modId) && Adapters.knownOrNull(modId) == null) {
                lines.add(t("adapter.unsupported", " - %s: installed, but this version has no adapter for it (nothing changes for it)", nameAndVersion(modId)));
            }
        }

        List<Text> bridges = bridgeLines();
        if (bridges.isEmpty()) {
            lines.add(t("bridges.none", "Other mana bridges: none found"));
        } else {
            lines.add(t("bridges", "Other mana bridges:"));
            lines.addAll(bridges);
        }

        lines.add(configLine());

        if (player == null) {
            lines.add(t("no_player", "No player values: add a player name to see them."));
        } else {
            playerLines(player, leader, lines);
        }
        return lines;
    }

    /** One line per adapter, without the version (it is in the versions line). */
    private static Text adapterLine(AdapterHandle handle, @Nullable ServerPlayer player) {
        String name = handle.adapter().displayName();
        if (handle.isRuneless()) {
            return handle.isLeader()
                    ? t("adapter.runeless.leads", " - %s: leads the pool (its own mana storage)", name)
                    : t("adapter.runeless.follows", " - %s: follows; its own bonuses go to the leading pool", name);
        }
        if (!handle.loaded()) {
            return t("adapter.missing", " - %s: not installed", name);
        }
        if (!handle.isConnectedOnServer()) {
            return t("adapter.off", " - %s: installed, off (%s)", name, handle.reasonOnServer());
        }
        Num factor = new Num(Pool.factor(player, handle.modId()), false);
        Text kind = handle.configFactor() > 0.0 ? t("factor.config", "set in the config") : t("factor.auto", "automatic");
        return handle.isLeader()
                ? t("adapter.leads", " - %s: connected, leads the pool (factor %s, %s)", name, factor, kind)
                : t("adapter.follows", " - %s: connected, follows (factor %s, %s)", name, factor, kind);
    }

    /**
     * One line per installed known bridge with its effect on every adapter it touches; mixins of other mods on the hooked
     * classes only for mods that are not known bridges (those are listed anyway); entries of conflictingMods.
     */
    private static List<Text> bridgeLines() {
        List<Text> lines = new ArrayList<>();
        boolean ignore = BridgeConfig.get(BridgeConfig.IGNORE_CONFLICTS, false);
        Map<String, List<Text>> parts = new LinkedHashMap<>();
        Map<String, Boolean> allInactive = new LinkedHashMap<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            String adapter = handle.adapter().displayName();
            for (ConflictCheck check : handle.adapter().conflicts()) {
                if (!ModList.get().isLoaded(check.modId())) {
                    continue;
                }
                List<Text> list = parts.computeIfAbsent(check.modId(), k -> new ArrayList<>());
                boolean active = Conflicts.isActive(check);
                allInactive.merge(check.modId(), !active, Boolean::logicalAnd);
                if (!active) {
                    list.add(t("bridge.part.inactive", "its mana function for %s is off", adapter));
                } else if (Conflicts.isChained(check)) {
                    list.add(t("bridge.part.chained", "chained with the %s adapter, which stays on", adapter));
                } else if (ignore) {
                    list.add(t("bridge.part.ignored", "the %s adapter stays on (ignoreConflicts = true)", adapter));
                } else {
                    list.add(t("bridge.part.off", "the %s adapter is off", adapter));
                }
            }
        }
        for (Map.Entry<String, List<Text>> entry : parts.entrySet()) {
            String bridge = nameAndVersion(entry.getKey()) + " (" + entry.getKey() + ")";
            lines.add(allInactive.get(entry.getKey())
                    ? t("bridge.inactive", " - %s: installed, its mana function is off", bridge)
                    : t("bridge.active", " - %s: active; %s", bridge, join(entry.getValue())));
        }
        for (AdapterHandle handle : Adapters.foreign()) {
            for (String found : MixinConflicts.found(handle.modId())) {
                if (parts.keySet().stream().noneMatch(id -> found.contains("(" + id + ")"))) {
                    lines.add(t("bridge.mixins", " - mixins on the classes of the %s adapter (warning only): %s", handle.adapter().displayName(), found));
                }
            }
        }
        for (String modId : Conflicts.extraMods()) {
            if (ModList.get().isLoaded(modId)) {
                lines.add(t("bridge.extra", " - %s: listed in conflictingMods; the adapters are off", nameAndVersion(modId) + " (" + modId + ")"));
            }
        }
        return lines;
    }

    /** Texts joined with "; " (nested, so every part stays translatable). */
    private static Object join(List<Text> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        Object joined = parts.get(parts.size() - 1);
        for (int i = parts.size() - 2; i >= 0; i--) {
            joined = t("join", "%s; %s", parts.get(i), joined);
        }
        return joined;
    }

    private static void playerLines(ServerPlayer player, String leader, List<Text> lines) {
        double mana = RunelessApi.getMana(player);
        double max = RunelessApi.getMaxMana(player);
        double debt = Math.max(0.0, -mana);
        Object regen = leader.equals(Adapters.RUNELESS)
                ? t("player.regen", ", regeneration %s per second", new Num(RunelessApi.getManaRegen(player), false))
                : "";
        lines.add(t("player", "Player %s (%s): %s / %s RunelessRPG mana, debt %s%s", player.getGameProfile().getName(),
                player.gameMode.getGameModeForPlayer().getName(), new Num(mana, false), new Num(max, false), new Num(debt, false), regen));
        List<Text> units = new ArrayList<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            if (!handle.isConnectedOnServer()) {
                continue;
            }
            double factor = Pool.factor(player, handle.modId());
            units.add(t("player.unit", "%s %s / %s, debt %s", handle.adapter().displayName(), new Num(Math.max(0.0, mana) / factor, false),
                    new Num(max / factor, false), new Num(debt / factor, false)));
        }
        if (!units.isEmpty()) {
            lines.add(t("player.units", " - In the units of the other mods: %s", join(units)));
        }
        BridgeBonus bonus = BonusBridge.current(player);
        List<Text> parts = new ArrayList<>();
        if (bonus.maxFlat() != 0.0) {
            parts.add(t("player.bonus.max_flat", "max mana %s", new Num(bonus.maxFlat(), true)));
        }
        if (bonus.maxBase() != 0.0) {
            parts.add(t("player.bonus.max_base", "max mana %s %% of base", new Num(bonus.maxBase() * 100.0, true)));
        }
        if (bonus.maxTotal() != 1.0) {
            parts.add(t("player.bonus.max_total", "max mana x%s on the total", new Num(bonus.maxTotal(), false)));
        }
        if (bonus.regenFlat() != 0.0) {
            parts.add(t("player.bonus.regen_flat", "regeneration %s per second", new Num(bonus.regenFlat(), true)));
        }
        if (bonus.regenBase() != 0.0) {
            parts.add(t("player.bonus.regen_base", "regeneration %s %%", new Num(bonus.regenBase() * 100.0, true)));
        }
        if (bonus.regenTotal() != 1.0) {
            parts.add(t("player.bonus.regen_total", "regeneration x%s", new Num(bonus.regenTotal(), false)));
        }
        if (bonus.reserve() > 0.0) {
            parts.add(t("player.bonus.reserve", "reserved (e.g. by Ars familiars) %s %% of the max mana", new Num(bonus.reserve() * 100.0, false)));
        }
        lines.add(t("player.bonus", " - Bridge bonus (in %s mana): %s", name(leader), parts.isEmpty() ? t("player.bonus.none", "none") : join(parts)));
        lines.add(BridgeNetwork.hasChannel(player)
                ? t("player.client.yes", " - Client has the bridge: yes")
                : t("player.client.no", " - Client has the bridge: no (pool and costs are right; the bar choice and the converted displays are missing)"));
    }

    // ------------------------------------------------------------------ helpers

    private static Text t(String key, String english, Object... args) {
        return new Text(key, english, args);
    }

    private static String versions() {
        List<String> parts = new ArrayList<>();
        // without Minecraft (this mod only runs on 1.21.1) and with a short own name, so the line takes two chat lines at most
        for (String modId : List.of("neoforge", ManaBridge.MODID, "runelessrpg", "spell_engine", "irons_spellbooks", "irons_lib", "ars_nouveau")) {
            if (ModList.get().isLoaded(modId)) {
                parts.add(modId.equals(ManaBridge.MODID)
                        ? "Mana Bridge " + ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse("?")
                        : nameAndVersion(modId));
            }
        }
        return String.join(", ", parts);
    }

    private static String nameAndVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName() + " " + c.getModInfo().getVersion())
                .orElse(modId);
    }

    /** Name of a mana system for the text (known adapters also while their mod is missing). */
    private static String name(String modId) {
        AdapterHandle handle = Adapters.knownOrNull(modId);
        return handle != null ? handle.adapter().displayName() : modId;
    }

    /**
     * The server config in one line: always manaSystem and foreignSpellsUseDebt; applyManaCost and the bridge switches only
     * where they are off; then every other option that differs from its default (file order). Options of {@code [general]}
     * without the section name, the others as {@code section.key}.
     */
    private static Text configLine() {
        List<String> main = new ArrayList<>();
        List<String> switchesOff = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        if (BridgeConfig.SPEC != null) {
            for (UnmodifiableConfig.Entry entry : BridgeConfig.SPEC.getValues().entrySet()) {
                if (!(entry.getValue() instanceof UnmodifiableConfig section)) {
                    continue;
                }
                for (UnmodifiableConfig.Entry value : section.entrySet()) {
                    if (!(value.getValue() instanceof ModConfigSpec.ConfigValue<?> configValue)) {
                        continue;
                    }
                    String key = value.getKey();
                    String path = entry.getKey().equals("general") ? key : entry.getKey() + "." + key;
                    Object current = configValue(configValue);
                    String text = path + " = " + (current instanceof String s ? "\"" + s + "\"" : String.valueOf(current));
                    if (key.equals("manaSystem") || key.equals("foreignSpellsUseDebt")) {
                        main.add(text);
                    } else if (key.startsWith("bridge") || key.equals("applyManaCost")) {
                        if (!Boolean.TRUE.equals(current)) {
                            switchesOff.add(text);
                        }
                    } else if (!Objects.equals(current, configValue.getDefault())) {
                        changed.add(text);
                    }
                }
            }
        }
        return t("config", "Config: %s; applyManaCost and bridge switches: %s; %s", String.join(", ", main),
                switchesOff.isEmpty() ? t("config.switches_on", "all on") : String.join(", ", switchesOff),
                changed.isEmpty()
                        ? t("config.defaults", "other options at their defaults")
                        : t("config.changed", "other options changed from the defaults: %s", String.join(", ", changed)));
    }

    private static @Nullable Object configValue(ModConfigSpec.ConfigValue<?> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return "?";
        }
    }

    private static Component number(Num num) {
        String text = plainNumber(num);
        int dot = text.indexOf('.');
        if (dot < 0) {
            return Component.literal(text);
        }
        return Component.translatableWithFallback(PREFIX + "decimal", "%s.%s", text.substring(0, dot), text.substring(dot + 1));
    }

    /** Up to three decimals without trailing zeros ("4", "13.4", "0.025"), with "+" for signed positive values. */
    static String plainNumber(Num num) {
        double value = num.value();
        String text;
        if (!Double.isFinite(value)) {
            text = String.valueOf(value);
        } else {
            BigDecimal rounded = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
            text = rounded.signum() == 0 ? "0" : rounded.toPlainString();
        }
        return num.signed() && !text.startsWith("-") ? "+" + text : text;
    }
}
