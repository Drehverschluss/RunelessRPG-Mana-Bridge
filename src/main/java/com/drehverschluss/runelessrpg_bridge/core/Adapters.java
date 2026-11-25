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
 * The list of adapters. A new mana mod only needs one line here and its own package under {@code adapter/}. The
 * description of every known adapter is created (it uses no class of its mod), so its config section exists even
 * while the mod is missing; its hooks are only used while the mod is loaded.
 */
public final class Adapters {
    /** Mod id of the mod whose pool leads when {@code manaSystem} cannot be used. */
    public static final String RUNELESS = "runelessrpg";

    private record Entry(String modId, String className) {}

    private static final List<Entry> KNOWN = List.of(
            new Entry(RUNELESS, "com.drehverschluss.runelessrpg_bridge.adapter.runelessrpg.RunelessAdapter"),
            new Entry("irons_spellbooks", "com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsAdapter"),
            new Entry("ars_nouveau", "com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsAdapter"));

    private static List<AdapterHandle> handles = List.of();

    private Adapters() {}

    /** Creates the descriptions of all known adapters (mod construction). */
    static void create() {
        List<AdapterHandle> list = new ArrayList<>();
        for (Entry entry : KNOWN) {
            try {
                ManaAdapter adapter = (ManaAdapter) Class.forName(entry.className()).getDeclaredConstructor().newInstance();
                list.add(new AdapterHandle(adapter, ModList.get().isLoaded(entry.modId())));
            } catch (ReflectiveOperationException | LinkageError e) {
                ManaBridge.LOGGER.warn("Adapter for {} could not be created, it stays off: {}", entry.modId(), e.toString());
            }
        }
        handles = Collections.unmodifiableList(list);
    }

    /** @return all known adapters (also those whose mod is missing), RunelessRPG first */
    public static List<AdapterHandle> known() {
        return handles;
    }

    /** @return the adapters whose mod is loaded, RunelessRPG first */
    public static List<AdapterHandle> all() {
        return handles.stream().filter(AdapterHandle::loaded).toList();
    }

    /** @return the adapters of loaded mana mods other than RunelessRPG */
    public static List<AdapterHandle> foreign() {
        return handles.stream().filter(h -> h.loaded() && !h.isRuneless()).toList();
    }

    /** @return the loaded adapters that do not lead the pool right now */
    public static List<AdapterHandle> followers() {
        return all().stream().filter(h -> !h.isLeader()).toList();
    }

    /** @return a loaded adapter */
    public static Optional<AdapterHandle> get(String modId) {
        for (AdapterHandle handle : handles) {
            if (handle.loaded() && handle.modId().equals(modId)) {
                return Optional.of(handle);
            }
        }
        return Optional.empty();
    }

    public static @Nullable AdapterHandle getOrNull(String modId) {
        return get(modId).orElse(null);
    }

    /** @return a known adapter, loaded or not */
    public static @Nullable AdapterHandle knownOrNull(String modId) {
        for (AdapterHandle handle : handles) {
            if (handle.modId().equals(modId)) {
                return handle;
            }
        }
        return null;
    }

    /** @return the mod id of the leading pool on this side (server: {@link Leader#id()}, client: as the server sent) */
    public static String leaderId() {
        for (AdapterHandle handle : all()) {
            if (handle.isLeader()) {
                return handle.modId();
            }
        }
        return RUNELESS;
    }

    /** @return true if at least one mana mod besides RunelessRPG is loaded */
    public static boolean hasFollowers() {
        return !foreign().isEmpty();
    }
}
