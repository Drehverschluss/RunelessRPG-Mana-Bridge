package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.LeadInfo;
import com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork;
import com.drehverschluss.runelessrpg_bridge.core.network.ManaSystemPayloads;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Choosing the leading pool ({@code manaSystem}) at runtime, plan 5.6: the command {@code /runelessrpg_bridge manaSystem}
 * and the question at the first login ({@code askForManaSystem}). Both write the server config and save it; the switch
 * then runs at once like after a config reload (no mana is lost, {@link Leader}). Server thread only.
 * <p>
 * Who may change it: operators (permission level 2), the console, and in singleplayer the owner of the world (also the
 * host of a LAN world), even without cheats.
 */
public final class ManaSystemChoice {
    static final String PREFIX = ManaBridge.MODID + ".choice.";
    /** Players who were asked at this login and have not answered (to close their question once someone answered). */
    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

    private ManaSystemChoice() {}

    // ------------------------------------------------------------------ permission

    /** @return true if this player may change {@code manaSystem}: operator, or owner of a singleplayer / LAN world */
    public static boolean mayChange(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    /** @return true if this command source may change {@code manaSystem} (console, command blocks with level 2 too) */
    public static boolean mayChange(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return source.hasPermission(2) || player != null && mayChange(player);
    }

    // ------------------------------------------------------------------ setting the value

    /** Result of {@link #set}: an error text, or the leader before and after. */
    record Result(@Nullable Component error, String before, String after) {}

    /**
     * Sets {@code manaSystem} to a mod id that can lead right now, turns {@code askForManaSystem} off and saves the config;
     * the leader switches at once. Every answer of the question and every use of the command counts as a choice.
     */
    static Result set(MinecraftServer server, String rawId) {
        String id = rawId.trim().toLowerCase(Locale.ROOT);
        String before = Leader.id();
        List<String> choices = Leader.choices();
        if (!choices.contains(id)) {
            if (Adapters.knownOrNull(id) == null) {
                return new Result(text("unknown", "Unknown mana system \"%s\". Possible values: %s", id, String.join(", ", choices))
                        .withStyle(ChatFormatting.RED), before, before);
            }
            return new Result(text("impossible", "\"%s\" cannot lead right now: %s", id, Leader.whyNot(id)).withStyle(ChatFormatting.RED),
                    before, before);
        }
        boolean change = !id.equals(Leader.configured()) || BridgeConfig.get(BridgeConfig.ASK_FOR_MANA_SYSTEM, true);
        if (change) {
            try {
                BridgeConfig.MANA_SYSTEM.set(id);
                BridgeConfig.ASK_FOR_MANA_SYSTEM.set(false);
                BridgeConfig.SPEC.save(); // writes the file and fires the reload event (Bridge.onConfig runs on this thread)
            } catch (RuntimeException e) {
                ManaBridge.LOGGER.warn("The server config could not be saved", e);
                return new Result(text("save_failed", "The config could not be saved: %s", e.toString()).withStyle(ChatFormatting.RED),
                        before, before);
            }
            Conflicts.evaluate();
            Leader.update(server);
            BridgeNetwork.sendToAll();
            ManaBridge.LOGGER.info("manaSystem set to \"{}\" (askForManaSystem = false); leading pool: {} (was {})", id, Leader.id(), before);
        }
        return new Result(null, before, Leader.id());
    }

    // ------------------------------------------------------------------ question at login

    /**
     * At login, once the bridge knows its state (self test, other bridges and leader are evaluated before): asks the player
     * which mana system leads while {@code askForManaSystem} is on, the player may change it and at least one adapter of
     * another mod is connected. A client with the bridge gets a window, any other a clickable chat line.
     */
    static void onLogin(ServerPlayer player) {
        if (!BridgeConfig.get(BridgeConfig.ASK_FOR_MANA_SYSTEM, true) || !mayChange(player)
                || Adapters.foreign().stream().noneMatch(AdapterHandle::isConnectedOnServer)) {
            return;
        }
        List<ManaSystemPayloads.Option> options = options(player);
        if (options.size() < 2) {
            return;
        }
        ASKED.add(player.getUUID());
        boolean window = BridgeNetwork.canAsk(player);
        ManaBridge.LOGGER.info("Asking {} which mana system leads ({}; askForManaSystem = true)", player.getName().getString(),
                window ? "window" : "chat line");
        if (window) {
            PacketDistributor.sendToPlayer(player, new ManaSystemPayloads.Ask(options));
        } else {
            sendChatQuestion(player, options);
        }
    }

    static void onLogout(ServerPlayer player) {
        ASKED.remove(player.getUUID());
    }

    /** Answer from the window: the permission and the state are checked again, the first answer counts. */
    public static void onAnswer(ServerPlayer player, String id) {
        if (!mayChange(player)) {
            player.sendSystemMessage(noPermission());
            ManaBridge.LOGGER.info("{} answered the mana system question without permission, ignored", player.getName().getString());
            return;
        }
        if (!BridgeConfig.get(BridgeConfig.ASK_FOR_MANA_SYSTEM, true)) {
            ASKED.remove(player.getUUID());
            player.sendSystemMessage(tag().append(text("already", "The mana system was chosen already: %s leads the shared pool.",
                    name(Leader.id()))).withStyle(ChatFormatting.GOLD));
            return;
        }
        Result result = set(player.server, id);
        if (result.error() != null) {
            player.sendSystemMessage(result.error());
            return;
        }
        answered(player, result);
    }

    /** After a choice: confirmation for the one who chose, the others' questions close with a message. */
    static void answered(@Nullable ServerPlayer chooser, Result result) {
        if (chooser != null) {
            ASKED.remove(chooser.getUUID());
            chooser.sendSystemMessage(confirmation(result));
        }
        MinecraftServer server = chooser != null ? chooser.server : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        String who = chooser == null ? "Server" : chooser.getName().getString();
        for (UUID uuid : List.copyOf(ASKED)) {
            ServerPlayer other = server.getPlayerList().getPlayer(uuid);
            ASKED.remove(uuid);
            if (other == null) {
                continue;
            }
            if (BridgeNetwork.canAsk(other)) {
                PacketDistributor.sendToPlayer(other, ManaSystemPayloads.Close.INSTANCE);
            }
            other.sendSystemMessage(tag().append(text("decided", "%s chose the mana system: %s leads the shared pool.", who,
                    name(result.after()))).withStyle(ChatFormatting.GOLD));
        }
    }

    static MutableComponent confirmation(Result result) {
        MutableComponent line = result.before().equals(result.after())
                ? text("kept", "%s keeps leading the shared mana pool.", name(result.after()))
                : text("changed", "%s now leads the shared mana pool (was %s).", name(result.after()), name(result.before()));
        return tag().append(line).append(" ").append(text("later", "You can change it later with /runelessrpg_bridge manaSystem <id> or in the config."))
                .withStyle(ChatFormatting.GOLD);
    }

    /** The choices for the question, with base max mana and regeneration from the real configs. */
    public static List<ManaSystemPayloads.Option> options(@Nullable ServerPlayer player) {
        List<ManaSystemPayloads.Option> list = new ArrayList<>();
        String leader = Leader.id();
        for (String id : Leader.choices()) {
            AdapterHandle handle = Adapters.getOrNull(id);
            LeadInfo info = null;
            try {
                info = handle == null ? null : handle.adapter().leadInfo(player);
            } catch (RuntimeException | LinkageError e) {
                ManaBridge.LOGGER.debug("No lead info for {}", id, e);
            }
            if (info == null) {
                // unknown regeneration: NaN, so the line names only the base max mana instead of claiming "no regeneration"
                info = new LeadInfo(handle == null ? 0.0 : handle.adapter().baseMaxMana(player), Double.NaN, Double.NaN);
            }
            list.add(new ManaSystemPayloads.Option(id, name(id), id.equals(leader), info.baseMaxMana(), info.regenPerSecond(),
                    info.regenPercentPerSecond()));
        }
        return list;
    }

    /** Chat question for clients without the bridge: one clickable value per choice, running the command. */
    private static void sendChatQuestion(ServerPlayer player, List<ManaSystemPayloads.Option> options) {
        player.sendSystemMessage(tag().append(text("chat.title", "Which mana system should lead the shared mana pool? Click one:"))
                .withStyle(ChatFormatting.GOLD));
        MutableComponent values = Component.empty();
        for (ManaSystemPayloads.Option option : options) {
            String command = "/" + ManaBridge.MODID + " manaSystem " + option.id();
            MutableComponent label = option.current() ? text("current", "%s (current)", option.name()) : Component.literal(option.name());
            MutableComponent button = Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, describe(option, false).append("\n" + command))));
            values.append(" ").append(button);
        }
        player.sendSystemMessage(values);
        player.sendSystemMessage(text("chat.hint", "The leading mod's base max mana and regeneration apply to everyone. Not now: just do nothing, "
                + "you will be asked at the next login. Pack makers: set askForManaSystem = false in runelessrpg_bridge-server.toml.")
                .withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ texts

    static MutableComponent text(String key, String english, Object... args) {
        return Component.translatableWithFallback(PREFIX + key, english, args);
    }

    static MutableComponent tag() {
        return Component.literal("[RunelessRPG Mana Bridge] ");
    }

    static MutableComponent noPermission() {
        return text("no_permission", "You need operator rights (permission level 2) to change the mana system.").withStyle(ChatFormatting.RED);
    }

    /** @return the display name of a mana system (RunelessRPG, or the mod's name) */
    public static String name(String modId) {
        AdapterHandle handle = Adapters.knownOrNull(modId);
        if (handle != null && handle.isRuneless()) {
            return handle.adapter().displayName();
        }
        if (handle != null && handle.loaded()) {
            return Conflicts.name(modId);
        }
        return handle != null ? handle.adapter().displayName() : modId;
    }

    /**
     * One line on what this pool brings while it leads (own units), e.g. "Base max mana 100, regenerates 2% of the max per
     * second". Same keys on the client window and in the chat hover.
     *
     * @param comma decimal comma instead of point (client language)
     */
    public static MutableComponent describe(ManaSystemPayloads.Option option, boolean comma) {
        String max = number(option.baseMaxMana(), comma);
        if (Double.isNaN(option.regen()) || Double.isNaN(option.regenPercent())) {
            return text("max_only", "Base max mana %s", max);
        }
        if (option.regenPercent() != 0.0) {
            return text("percent", "Base max mana %s, regenerates %s%% of the max per second", max, number(option.regenPercent(), comma));
        }
        if (option.regen() != 0.0) {
            return text("flat", "Base max mana %s, regenerates %s per second", max, number(option.regen(), comma));
        }
        // only when the real config says 0 (e.g. RunelessRPG's baseManaRegen = 0)
        return text("none", "Base max mana %s, no own regeneration", max);
    }

    /** Up to two decimals, without trailing zeros. */
    public static String number(double value, boolean comma) {
        String text = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return comma ? text.replace('.', ',') : text;
    }
}
