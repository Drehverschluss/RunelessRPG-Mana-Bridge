package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg.api.ManaStorage;
import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Which mod leads the shared pool on the server ({@code manaSystem}, plan 4). The leading mod's storage goes to
 * RunelessRPG's API; RunelessRPG moves the mana and the debt of every online player into it. A wanted leader that cannot
 * be used (not installed, adapter off, self test failed, another bridge) falls back to RunelessRPG with one warning.
 */
public final class Leader {
    private static volatile String id = Adapters.RUNELESS;
    private static String lastNotice = "";
    /** Why the configured {@code manaSystem} cannot lead right now (empty if it leads), for the status command. */
    private static volatile String problem = "";

    private Leader() {}

    /** @return the mod id of the leading pool on the server */
    public static String id() {
        return id;
    }

    /** @return why the configured {@code manaSystem} cannot lead (empty if it leads or is RunelessRPG) */
    public static String problem() {
        return problem;
    }

    /** @return the value of {@code manaSystem} as a mod id */
    public static String configured() {
        try {
            String value = BridgeConfig.MANA_SYSTEM.get().trim().toLowerCase(Locale.ROOT);
            return value.isEmpty() ? Adapters.RUNELESS : value;
        } catch (IllegalStateException e) {
            return Adapters.RUNELESS;
        }
    }

    /** @return the mod ids that can lead (for the config comment) */
    static List<String> candidates() {
        return Adapters.known().stream().filter(h -> h.isRuneless() || h.adapter().canLead()).map(AdapterHandle::modId).toList();
    }

    /**
     * Computes the leader from the config and the adapters' states and switches the storage if it changed. Server thread
     * only.
     *
     * @return true if the leader changed
     */
    /**
     * Server state: the mod ids that can lead right now, RunelessRPG first, then every loaded adapter that can lead and is
     * connected (installed, enabled, self test passed, not blocked by another bridge). The list comes from the adapters,
     * so a new adapter needs no change here.
     *
     * @since 0.2.0 (phase C5A)
     */
    public static List<String> choices() {
        return Adapters.known().stream().filter(h -> whyNot(h.modId()).isEmpty()).map(AdapterHandle::modId).toList();
    }

    /**
     * @param modId a mod id (lower case)
     * @return why this mod cannot lead right now, empty if it can (server state)
     * @since 0.2.0 (phase C5A)
     */
    public static String whyNot(String modId) {
        if (modId.equals(Adapters.RUNELESS)) {
            return "";
        }
        AdapterHandle handle = Adapters.knownOrNull(modId);
        if (handle == null) {
            return "no adapter for this mod (possible: " + String.join(", ", candidates()) + ")";
        }
        if (!handle.adapter().canLead()) {
            return "this mod cannot lead the pool";
        }
        return handle.reasonOnServer();
    }

    static boolean update(@Nullable MinecraftServer server) {
        String wanted = configured();
        String next = Adapters.RUNELESS;
        String problem = whyNot(wanted);
        if (problem.isEmpty()) {
            next = wanted;
        }
        String notice = wanted + "|" + problem;
        Leader.problem = problem;
        if (!notice.equals(lastNotice)) {
            lastNotice = notice;
            if (!problem.isEmpty()) {
                ManaBridge.LOGGER.warn("manaSystem = \"{}\" cannot be used ({}), RunelessRPG leads the mana pool", wanted, problem);
            }
        }
        if (next.equals(id)) {
            return false;
        }
        switchTo(server, next);
        return true;
    }

    /** Back to RunelessRPG's built-in storage when the server stops (no players are online then). */
    static void reset() {
        if (!id.equals(Adapters.RUNELESS)) {
            id = Adapters.RUNELESS;
            RunelessApi.resetStorage();
        }
        lastNotice = "";
        problem = "";
    }

    private static void switchTo(@Nullable MinecraftServer server, String next) {
        String old = id;
        ManaStorage storage = null;
        if (!next.equals(Adapters.RUNELESS)) {
            AdapterHandle handle = Adapters.getOrNull(next);
            try {
                storage = handle == null ? null : handle.adapter().createStorage();
            } catch (RuntimeException | LinkageError e) {
                ManaBridge.LOGGER.warn("The mana storage of {} could not be created", next, e);
            }
            if (storage == null) {
                ManaBridge.LOGGER.warn("manaSystem = \"{}\" has no mana storage, RunelessRPG leads the mana pool", next);
                problem = "its mana storage could not be created";
                if (old.equals(Adapters.RUNELESS)) {
                    return;
                }
                next = Adapters.RUNELESS;
            }
        }
        id = next;
        // First the bonus of the new leader, so its maximum is right when RunelessRPG moves the mana over;
        // the modifiers of the old leader go in the same step.
        List<ServerPlayer> players = server == null ? List.of() : server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            BonusBridge.update(player);
        }
        if (storage == null) {
            RunelessApi.resetStorage();
        } else {
            RunelessApi.setStorage(storage);
        }
        ManaBridge.LOGGER.info("The mana pool is now led by {} (was {})", next, old);
    }
}
