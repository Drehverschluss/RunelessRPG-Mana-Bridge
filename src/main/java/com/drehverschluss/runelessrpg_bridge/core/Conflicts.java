package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import java.util.ArrayList;
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
    private Conflicts() {}

    /**
     * Looks at all bridges again (their switches can change at runtime).
     *
     * @return true if the state of an adapter changed
     */
    static boolean evaluate() {
        boolean changed = false;
        List<String> extra = extraMods();
        for (AdapterHandle handle : Adapters.foreign()) {
            List<String> found = new ArrayList<>();
            for (ConflictCheck check : handle.adapter().conflicts()) {
                if (ModList.get().isLoaded(check.modId()) && isActive(check)) {
                    found.add(name(check.modId()));
                }
            }
            for (String modId : extra) {
                if (ModList.get().isLoaded(modId)) {
                    found.add(name(modId));
                }
            }
            String conflict = String.join(", ", found);
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

    private static boolean isActive(ConflictCheck check) {
        try {
            return check.isActive().getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    private static List<String> extraMods() {
        try {
            return BridgeConfig.CONFLICTING_MODS.get().stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IllegalStateException e) {
            return List.of();
        }
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
