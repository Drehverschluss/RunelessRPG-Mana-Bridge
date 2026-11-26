package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * Other bridges at the same mana pools (decision E7, plan 9.1): an adapter turns off while one of them is active, with one
 * warning in the log and a chat message for operators at login. {@code ignoreConflicts} keeps all adapters on.
 */
public final class Conflicts {
    /** Server ticks between two looks at the switches of the other bridges while one is installed. */
    private static final int POLL_TICKS = 20;
    private static int tick;
    private static Boolean knownBridgeInstalled;

    private Conflicts() {}

    /**
     * Once a second while another bridge is installed: its switches can change without a config reload of this mod
     * (Ars 'n Spells' {@code /ans mode set}, its config screen, a reload of its own config). Then the leader and the
     * clients follow at once. Without another bridge nothing runs.
     */
    static void poll(net.minecraft.server.MinecraftServer server) {
        if (++tick < POLL_TICKS) {
            return;
        }
        tick = 0;
        if (knownBridgeInstalled == null) {
            knownBridgeInstalled = knownIds().stream().anyMatch(ModList.get()::isLoaded);
        }
        if ((knownBridgeInstalled || !extraMods().isEmpty()) && evaluate()) {
            Leader.update(server);
            com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork.sendToAll();
        }
    }

    /**
     * Looks at all bridges again (their switches can change at runtime).
     *
     * @return true if the state of an adapter changed
     */
    static boolean evaluate() {
        boolean changed = false;
        List<String> extra = extraMods();
        for (AdapterHandle handle : Adapters.foreign()) {
            // mod ids in order, each once: a known bridge that is also listed in conflictingMods is named once (C4B.1 4.2)
            Set<String> found = new LinkedHashSet<>();
            Set<String> chain = new LinkedHashSet<>();
            for (ConflictCheck check : handle.adapter().conflicts()) {
                if (ModList.get().isLoaded(check.modId()) && isActive(check)) {
                    (isChained(check) ? chain : found).add(check.modId());
                }
            }
            for (String modId : extra) {
                if (ModList.get().isLoaded(modId)) { // listed by the pack: always a conflict, also for a chained bridge (as before)
                    found.add(modId);
                }
            }
            handle.setChainedIds(List.copyOf(chain));
            handle.setConflictIds(List.copyOf(found));
            String chainText = names(chain);
            if (!chainText.equals(handle.chained())) {
                handle.setChained(chainText);
                changed = true;
                if (!chainText.isEmpty()) {
                    ManaBridge.LOGGER.info("Adapter {} stays on in a chain with {}: that bridge keeps its own link, this adapter links the "
                            + "same mod to the leading pool", handle.modId(), chainText);
                }
            }
            String conflict = names(found);
            if (!conflict.equals(handle.conflict())) {
                boolean wasActive = handle.isConnectedOnServer();
                handle.setConflict(conflict);
                changed = true;
                if (!conflict.isEmpty()) {
                    if (BridgeConfig.get(BridgeConfig.IGNORE_CONFLICTS, false)) {
                        ManaBridge.LOGGER.warn("Adapter {} stays on although {} is active (ignoreConflicts = true)", handle.modId(), conflict);
                    } else {
                        ManaBridge.LOGGER.warn("Adapter {} is turned off because {} is active (another bridge at the same mana pool). "
                                + "Set ignoreConflicts = true in runelessrpg_bridge-server.toml if your pack handles this.", handle.modId(), conflict);
                    }
                } else if (!wasActive && handle.isConnectedOnServer()) {
                    ManaBridge.LOGGER.info("Adapter {} is on again: no other bridge is active any more", handle.modId());
                }
            }
        }
        return changed;
    }

    /** Only for active bridges: works in a chain with the adapter (an exception counts as "not chained", the safe side). */
    static boolean isChained(ConflictCheck check) {
        try {
            return check.chained().getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static boolean isActive(ConflictCheck check) {
        try {
            return check.isActive().getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    static List<String> extraMods() {
        try {
            return BridgeConfig.CONFLICTING_MODS.get().stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IllegalStateException e) {
            return List.of();
        }
    }

    private static String names(Set<String> modIds) {
        return modIds.stream().map(Conflicts::name).collect(java.util.stream.Collectors.joining(", "));
    }

    /** @return the display name of a loaded mod (or its id) */
    public static String name(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getDisplayName()).orElse(modId);
    }

    /** Chat message for an operator at login: which adapter is off because of which bridge. */
    static void notifyOperator(ServerPlayer player) {
        if (!BridgeConfig.get(BridgeConfig.NOTIFY_CONFLICTS_IN_CHAT, true) || !player.hasPermissions(2)
                || BridgeConfig.get(BridgeConfig.IGNORE_CONFLICTS, false)) {
            return;
        }
        for (AdapterHandle handle : Adapters.foreign()) {
            if (!handle.conflict().isEmpty() && handle.configEnabled()) {
                player.sendSystemMessage(Component.translatable("runelessrpg_bridge.chat.conflict", name(handle.modId()), handle.conflict())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Known mod ids of bridges, for logs. */
    static Set<String> knownIds() {
        Set<String> ids = new java.util.TreeSet<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            handle.adapter().conflicts().forEach(c -> ids.add(c.modId()));
        }
        return ids;
    }
}
