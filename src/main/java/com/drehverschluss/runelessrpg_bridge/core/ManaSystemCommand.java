package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /runelessrpg_bridge manaSystem [<id>]} (plan 5.6). Without an id: the leading pool and the possible values (every
 * player may look). With an id: sets {@code manaSystem}, saves the server config and switches the leading pool at once
 * without losing mana; operators (permission level 2), the console and the owner of a singleplayer or LAN world. Works
 * on servers where the config screen cannot edit the server config. Suggestions only offer values that can lead now.
 */
public final class ManaSystemCommand {
    private ManaSystemCommand() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ManaBridge.MODID)
                .then(Commands.literal("manaSystem")
                        .executes(ctx -> show(ctx.getSource()))
                        .then(Commands.argument("id", StringArgumentType.word())
                                // only values that can lead now, and only for those who may set them
                                .suggests((ctx, builder) -> ManaSystemChoice.mayChange(ctx.getSource())
                                        ? SharedSuggestionProvider.suggest(Leader.choices(), builder)
                                        : builder.buildFuture())
                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));
    }

    private static int show(CommandSourceStack source) {
        String configured = Leader.configured();
        String problem = Leader.problem();
        String leader = ManaSystemChoice.name(Leader.id());
        // sendSystemMessage, not sendSuccess: the answer must also show with the gamerule sendCommandFeedback = false
        source.sendSystemMessage(problem.isEmpty()
                ? ManaSystemChoice.text("command.current", "Leading pool: %s (manaSystem = \"%s\")", leader, configured)
                : ManaSystemChoice.text("command.fallback", "Leading pool: %s, because manaSystem = \"%s\" cannot be used: %s", leader, configured,
                        problem));
        MutableComponent values = Component.empty();
        boolean clickable = ManaSystemChoice.mayChange(source) && source.isPlayer();
        List<String> choices = Leader.choices();
        for (int i = 0; i < choices.size(); i++) {
            String id = choices.get(i);
            MutableComponent value = Component.literal(id).append(" (" + ManaSystemChoice.name(id) + ")");
            if (clickable) {
                String command = "/" + ManaBridge.MODID + " manaSystem " + id;
                value = value.withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
            }
            values.append(i == 0 ? Component.empty() : Component.literal(", ")).append(value);
        }
        source.sendSystemMessage(ManaSystemChoice.text("command.possible", "Possible values: %s", values));
        List<String> impossible = new ArrayList<>();
        for (AdapterHandle handle : Adapters.known()) {
            if (handle.adapter().canLead() && !choices.contains(handle.modId())) {
                impossible.add(handle.modId() + " (" + Leader.whyNot(handle.modId()) + ")");
            }
        }
        if (!impossible.isEmpty()) {
            source.sendSystemMessage(ManaSystemChoice.text("command.impossible_list", "Not possible right now: %s", String.join("; ", impossible))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!ManaSystemChoice.mayChange(source)) {
            source.sendSystemMessage(ManaSystemChoice.text("command.operators", "Only operators can change it.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int set(CommandSourceStack source, String id) {
        if (!ManaSystemChoice.mayChange(source)) {
            source.sendSystemMessage(ManaSystemChoice.noPermission());
            return 0;
        }
        ManaSystemChoice.Result result = ManaSystemChoice.set(source.getServer(), id);
        if (result.error() != null) {
            source.sendSystemMessage(result.error());
            return 0;
        }
        ManaSystemChoice.answered(source.getPlayer(), result);
        if (source.getPlayer() == null) {
            source.sendSystemMessage(ManaSystemChoice.confirmation(result));
        }
        return 1;
    }
}
