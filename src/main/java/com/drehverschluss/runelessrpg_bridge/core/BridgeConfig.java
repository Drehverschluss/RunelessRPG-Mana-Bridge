package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config {@code runelessrpg_bridge-server.toml}: section {@code [general]} and one section per known adapter,
 * also for mods that are not installed (so their settings stay when the mod is added or removed). Values are read when
 * used, so changes work without a restart; the client gets the values it needs through
 * {@link com.drehverschluss.runelessrpg_bridge.core.network.BridgeNetwork}.
 */
public final class BridgeConfig {
    public static ModConfigSpec.ConfigValue<String> MANA_SYSTEM;
    public static ModConfigSpec.BooleanValue FOREIGN_SPELLS_USE_DEBT;
    public static ModConfigSpec.ConfigValue<List<? extends String>> CONFLICTING_MODS;
    public static ModConfigSpec.BooleanValue IGNORE_CONFLICTS;
    public static ModConfigSpec.BooleanValue NOTIFY_CONFLICTS_IN_CHAT;
    public static ModConfigSpec.BooleanValue CHAIN_WITH_ARS_N_SPELLS;
    /** The whole server config, read section by section by the status command. */
    static ModConfigSpec SPEC;

    private BridgeConfig() {}

    static ModConfigSpec build() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        MANA_SYSTEM = b.comment(
                        "Mod ID of the mana system that leads the shared pool: " + quoted(Leader.candidates()) + ".",
                        "The leading mod keeps its own base max mana, regeneration and respawn rule; the others pay from its pool",
                        "and add their bonuses to it. Falls back to \"runelessrpg\" with a warning if the chosen mod is not installed,",
                        "its adapter is off (enabled = false, self test, another bridge) or it cannot lead. Can be changed at runtime.",
                        "While another mod leads, RunelessRPG's baseMaxMana and baseManaRegen have no effect on the pool (baseMaxMana",
                        "still sets the automatic manaFactor). With \"irons_spellbooks\" the mana after a respawn follows Iron's",
                        "manaSpawnPercent (default 0 = empty).")
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
        CHAIN_WITH_ARS_N_SPELLS = b.comment(
                        "With Ars 'n Spells in its mana unification mode \"iss_primary\" (Iron's is its one pool and Ars Nouveau reads it),",
                        "only the Iron's adapter runs (Iron's <-> leading pool) and Ars 'n Spells keeps Ars <-> Iron's; the Ars adapter is off.",
                        "Off: any active mana unification of Ars 'n Spells turns both adapters off. Other modes (ars_primary, hybrid) always do.")
                .translation(key("chainWithArsNSpells"))
                .define("chainWithArsNSpells", true);
        b.pop();

        for (AdapterHandle handle : Adapters.known()) {
            String name = handle.adapter().displayName();
            if (handle.isRuneless()) {
                b.comment("RunelessRPG's own bonuses while another mod leads the pool (manaSystem).").push(handle.modId());
                handle.bridgeMaxMana = b.comment(
                                "Modifiers on runelessrpg:max_mana (gear, enchantments, affixes) count for the leading pool: flat values",
                                "converted into its unit (+25 -> +125 Iron's mana with the default factor 0.2).")
                        .translation(key("bridgeMaxMana"))
                        .define("bridgeMaxMana", true);
                handle.bridgeManaRegen = b.comment(
                                "Modifiers on runelessrpg:mana_regen count for the leading pool: flat values are paid into the pool",
                                "besides its own regeneration (+0.25/s -> +1.25 Iron's mana/s), percent values multiply its regeneration.")
                        .translation(key("bridgeManaRegen"))
                        .define("bridgeManaRegen", true);
                handle.bridgePercentBonuses = b.comment(
                                "Percent modifiers on runelessrpg:max_mana (of the base value or of the total) count as the same percent",
                                "on the leading pool. Off: only flat max mana counts.")
                        .translation(key("bridgePercentBonuses"))
                        .define("bridgePercentBonuses", true);
                b.pop();
                continue;
            }
            b.comment("Adapter for " + name + ". Only has an effect while " + name + " is installed.").push(handle.modId());
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
            handle.bridgeMaxMana = b.comment(
                            "Max mana bonuses of this mod's gear (modifiers on its max mana above the base value: armor, enchantments,",
                            "rings and other curios, upgrades, effects) count for the leading pool while this mod does not lead; flat",
                            "values converted with manaFactor (+50 -> +10).")
                    .translation(key("bridgeMaxMana"))
                    .define("bridgeMaxMana", true);
            handle.bridgeManaRegen = b.comment(
                            "Mana regeneration bonuses of this mod's gear count for the leading pool while this mod does not lead",
                            "(Iron's mana_regen above 1.0 multiplies it: +15 % -> regeneration x 1.15; flat regeneration such as",
                            "Ars Nouveau's is converted with manaFactor: +4/s -> +0.8/s).")
                    .translation(key("bridgeManaRegen"))
                    .define("bridgeManaRegen", true);
            handle.bridgePercentBonuses = b.comment(
                            "Percent modifiers on this mod's max mana (of the base value or of the total) count as the same percent",
                            "on the leading pool. Off: only flat max mana counts.")
                    .translation(key("bridgePercentBonuses"))
                    .define("bridgePercentBonuses", true);
            handle.adapter().defineConfig(b);
            b.pop();
        }
        SPEC = b.build();
        return SPEC;
    }

    private static String quoted(List<String> ids) {
        return String.join(" or ", ids.stream().map(id -> "\"" + id + "\"").toList());
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
