package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * The list of adapters. A new mana mod only needs one line here and its own package under {@code adapter/}. The core
 * knows the adapters only by the class name, so an adapter class is never loaded while its mod is missing.
 */
public final class Adapters {
    /** Mod id of the mod whose pool leads when {@code manaSystem} cannot be used. */
    public static final String RUNELESS = "runelessrpg";

    private record Entry(String modId, String className) {}

    private static final List<Entry> KNOWN = List.of(
            new Entry(RUNELESS, "com.drehverschluss.runelessrpg_bridge.adapter.runelessrpg.RunelessAdapter"),
            new Entry("irons_spellbooks", "com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsAdapter"));

    private static List<AdapterHandle> handles = List.of();

    private Adapters() {}

    /** Creates the adapters of all loaded mods (mod construction). */
    static void create() {
        List<AdapterHandle> list = new ArrayList<>();
        for (Entry entry : KNOWN) {
            if (!ModList.get().isLoaded(entry.modId())) {
                continue;
            }
            try {
                ManaAdapter adapter = (ManaAdapter) Class.forName(entry.className()).getDeclaredConstructor().newInstance();
                list.add(new AdapterHandle(adapter));
            } catch (ReflectiveOperationException | LinkageError e) {
                ManaBridge.LOGGER.warn("Adapter for {} could not be created, it stays off: {}", entry.modId(), e.toString());
            }
        }
        handles = Collections.unmodifiableList(list);
    }

    /** @return all created adapters, RunelessRPG first */
    public static List<AdapterHandle> all() {
        return handles;
    }

    /** @return the adapters that follow the leading pool (all except the leader) */
    public static List<AdapterHandle> followers() {
        return handles.stream().filter(h -> !h.isLeader()).toList();
    }

    public static Optional<AdapterHandle> get(String modId) {
        for (AdapterHandle handle : handles) {
            if (handle.modId().equals(modId)) {
                return Optional.of(handle);
            }
        }
        return Optional.empty();
    }

    public static @Nullable AdapterHandle getOrNull(String modId) {
        return get(modId).orElse(null);
    }

    /**
     * @return the mod id of the leading pool. Only RunelessRPG can lead in this version; any other value of
     *         {@code manaSystem} falls back to it (with a warning when the config is loaded).
     */
    public static String leaderId() {
        return RUNELESS;
    }

    /** @return true if at least one mana mod besides RunelessRPG is loaded */
    public static boolean hasFollowers() {
        return handles.stream().anyMatch(h -> !h.modId().equals(RUNELESS));
    }
}
