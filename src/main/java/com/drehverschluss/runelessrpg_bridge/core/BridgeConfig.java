package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config {@code runelessrpg_bridge-server.toml}: section {@code [general]} and one section per adapter of a loaded
 * mana mod. Values are read when used, so changes work without a restart; the client gets the values it needs through
 * {@link com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork}.
 */
public final class BridgeConfig {
    public static ModConfigSpec.ConfigValue<String> MANA_SYSTEM;
    public static ModConfigSpec.BooleanValue FOREIGN_SPELLS_USE_DEBT;
    public static ModConfigSpec.ConfigValue<List<? extends String>> CONFLICTING_MODS;
    public static ModConfigSpec.BooleanValue IGNORE_CONFLICTS;
    public static ModConfigSpec.BooleanValue NOTIFY_CONFLICTS_IN_CHAT;

    private BridgeConfig() {}

    static ModConfigSpec build() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        MANA_SYSTEM = b.comment(
                        "Mod ID of the mana system that leads the shared pool, e.g. \"runelessrpg\".",
                        "Only \"runelessrpg\" is supported in this version; any other value falls back to it with a warning.")
                .translation(key("manaSystem"))
                .define("manaSystem", Adapters.RUNELESS);
        FOREIGN_SPELLS_USE_DEBT = b.comment(
                        "Spells of other mods may go into mana debt like Spell Engine spells (RunelessRPG's DEBT and DEBT_LIMIT",
                        "modes, Borrowed Power). Off: they always need the full mana.")
                .translation(key("foreignSpellsUseDebt"))
                .define("foreignSpellsUseDebt", true);
        CONFLICTING_MODS = b.comment(
                        "More mod IDs of bridges that touch the same mana pools. While one of them is installed, every adapter",
                        "except RunelessRPG's turns off with a warning. Known bridges (Ars 'n Spells, Ars Mana Bridge, Mana Transform,",
                        "Unified Mana) are detected anyway, with a look at their own switches.")
                .translation(key("conflictingMods"))
                .defineListAllowEmpty("conflictingMods", List.of(), () -> "", o -> o instanceof String);
        IGNORE_CONFLICTS = b.comment("Keep all adapters on even if another bridge is active (only if the pack solves it otherwise).")
                .translation(key("ignoreConflicts"))
                .define("ignoreConflicts", false);
        NOTIFY_CONFLICTS_IN_CHAT = b.comment("Tell operators in chat on login which adapter is off because of another bridge.")
                .translation(key("notifyConflictsInChat"))
                .define("notifyConflictsInChat", true);
        b.pop();

        for (AdapterHandle handle : Adapters.all()) {
            if (handle.adapter().canLead() && handle.modId().equals(Adapters.RUNELESS)) {
                continue; // RunelessRPG always leads in this version, no section needed
            }
            b.comment("Adapter for " + handle.modId()).push(handle.modId());
            handle.enabled = b.comment("Connect this mod to the shared mana pool. Off: the mod keeps its own mana as without this bridge.")
                    .translation(key("enabled"))
                    .define("enabled", true);
            handle.manaFactor = b.comment(
                            "1 mana of this mod = this much RunelessRPG mana. -1 = auto: RunelessRPG base max mana / this mod's base max mana",
                            "(20 / 100 = 0.2 with the default values), so it follows changed base values.")
                    .translation(key("manaFactor"))
                    .defineInRange("manaFactor", -1.0, -1.0, 1000.0);
            handle.applyManaCost = b.comment("runelessrpg:mana_cost (with its lower limit and Spell Infinity) applies to this mod's spells.")
                    .translation(key("applyManaCost"))
                    .define("applyManaCost", true);
            handle.adapter().defineConfig(b);
            b.pop();
        }
        return b.build();
    }

    public static String key(String option) {
        return ManaBridge.MODID + ".configuration." + option;
    }

    /** Reads a value and falls back while the config is not loaded (e.g. on the client before joining). */
    public static boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
