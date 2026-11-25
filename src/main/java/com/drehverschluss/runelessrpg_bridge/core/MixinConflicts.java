package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.mixin.MixinRecord;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Second safety for unknown bridges (plan 9.1): mixins of other mods on the same classes as an adapter's mixins. Only a
 * warning in the log, because harmless addons change these classes too. Runs after the self test, when the target
 * classes are loaded, and reads the {@link MixinMerged} marks Mixin puts on every method it adds to a class.
 * <p>
 * Known bridges (their own line in the log and the status) and mods listed in {@code conflictingMods} get no warning. The
 * server config is not loaded yet when the scan runs, so the warnings follow later, once: at server start and, on a
 * client, at login (the server sends its config then).
 */
public final class MixinConflicts {
    /** Findings of the last scan: adapter mod id -> owner (mod id or mixin class) -> classes of the adapter it also changes. */
    private static volatile Map<String, Map<String, Set<String>>> found = Map.of();
    /** Owners already warned about per adapter, so every finding is logged once. */
    private static final Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private MixinConflicts() {}

    /**
     * @return the findings of the last scan for this adapter as "owner: [classes]", without known bridges and without mods
     * listed in {@code conflictingMods} (empty if none)
     */
    public static List<String> found(String modId) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : found.getOrDefault(modId, Map.of()).entrySet()) {
            if (!expected(entry.getKey())) {
                lines.add(describe(entry.getKey()) + ": " + entry.getValue());
            }
        }
        return lines;
    }

    /**
     * Logs the findings of mods that are neither known bridges nor listed in {@code conflictingMods}, each once. Called when
     * the server config is known (server start, client login).
     */
    public static void warnUnknown() {
        for (Map.Entry<String, Map<String, Set<String>>> adapter : found.entrySet()) {
            for (Map.Entry<String, Set<String>> entry : adapter.getValue().entrySet()) {
                if (!expected(entry.getKey()) && WARNED.add(adapter.getKey() + "|" + entry.getKey())) {
                    ManaBridge.LOGGER.warn("{} also changes classes the {} adapter hooks: {}. If it is a mana bridge, add its mod ID to conflictingMods.",
                            describe(entry.getKey()), adapter.getKey(), entry.getValue());
                }
            }
        }
    }

    /** A known bridge of any adapter or a mod listed in {@code conflictingMods}: it has its own line, no mixin warning. */
    private static boolean expected(String owner) {
        return Conflicts.knownIds().contains(owner) || Conflicts.extraMods().contains(owner);
    }

    private static String describe(String owner) {
        return ModList.get().isLoaded(owner) ? Conflicts.name(owner) + " (" + owner + ")" : owner;
    }

    public static void scan() {
        Map<String, Map<String, Set<String>>> result = new TreeMap<>();
        for (AdapterHandle handle : Adapters.foreign()) {
            // owner mod -> classes of ours its mixins also change
            Map<String, Set<String>> shared = new TreeMap<>();
            Set<String> targets = new TreeSet<>();
            for (MixinRecord.Entry entry : MixinRecord.entries(handle.modId())) {
                if (entry.applied()) {
                    targets.add(entry.target.replace('/', '.'));
                }
            }
            for (String target : targets) {
                try {
                    Class<?> type = Class.forName(target, false, MixinConflicts.class.getClassLoader());
                    for (Method method : type.getDeclaredMethods()) {
                        MixinMerged merged = method.getAnnotation(MixinMerged.class);
                        if (merged == null || merged.mixin().startsWith("com.drehverschluss.runelessrpg_bridge.")) {
                            continue;
                        }
                        String owner = ownerOf(merged.mixin());
                        if (handle.modId().equals(owner)) {
                            continue; // the mod's own mixins
                        }
                        shared.computeIfAbsent(owner == null ? merged.mixin() : owner, k -> new TreeSet<>())
                                .add(target.substring(target.lastIndexOf('.') + 1));
                    }
                } catch (ClassNotFoundException | LinkageError | RuntimeException e) {
                    ManaBridge.LOGGER.debug("Mixin conflict scan of {} not possible: {}", target, e.toString());
                }
            }
            if (!shared.isEmpty()) {
                result.put(handle.modId(), java.util.Collections.unmodifiableMap(shared));
            }
        }
        found = java.util.Collections.unmodifiableMap(result);
    }

    /** Mod id whose jar contains the class, or null. */
    private static String ownerOf(String className) {
        String path = className.replace('.', '/') + ".class";
        for (var info : ModList.get().getModFiles()) {
            try {
                if (Files.exists(info.getFile().findResource(path)) && !info.getMods().isEmpty()) {
                    return info.getMods().getFirst().getModId();
                }
            } catch (RuntimeException e) {
                // ignore this file
            }
        }
        return null;
    }
}
