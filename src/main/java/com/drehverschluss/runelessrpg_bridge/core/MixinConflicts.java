package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.mixin.MixinRecord;
import java.lang.reflect.Method;
import java.nio.file.Files;
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
 */
public final class MixinConflicts {
    private MixinConflicts() {}

    public static void scan() {
        for (AdapterHandle handle : Adapters.followers()) {
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
            for (Map.Entry<String, Set<String>> entry : shared.entrySet()) {
                ManaBridge.LOGGER.warn("{} also changes classes the {} adapter hooks: {}. If it is a mana bridge, add its mod ID to conflictingMods.",
                        ModList.get().isLoaded(entry.getKey()) ? Conflicts.name(entry.getKey()) + " (" + entry.getKey() + ")" : entry.getKey(),
                        handle.modId(), entry.getValue());
            }
        }
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
