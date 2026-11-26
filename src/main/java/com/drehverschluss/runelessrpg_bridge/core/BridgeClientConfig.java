package com.drehverschluss.runelessrpg_bridge.core;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** Client config {@code runelessrpg_bridge-client.toml}, section {@code [display]}. Every player chooses here. */
public final class BridgeClientConfig {
    /**
     * Unit for all displayed mana amounts. The config file keeps the names (AUTO, LEADER, RUNELESS); the config screen shows
     * readable, translated names.
     */
    public enum DisplayUnit implements TranslatableEnum {
        /** Unit of the leading mod; if exactly one bar of another mod is shown, that mod's unit. */
        AUTO,
        /** Always the unit of the leading mod. */
        LEADER,
        /** Always RunelessRPG mana. */
        RUNELESS;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(BridgeConfig.key("displayUnit.") + name().toLowerCase(Locale.ROOT));
        }
    }

    public static ModConfigSpec.ConfigValue<String> MANA_DISPLAY;
    public static ModConfigSpec.BooleanValue SHOW_RUNELESS_BAR_ON_DEBT;
    public static ModConfigSpec.EnumValue<DisplayUnit> DISPLAY_UNIT;
    public static ModConfigSpec.BooleanValue CONVERT_FOREIGN_TOOLTIPS;
    public static ModConfigSpec.BooleanValue SHOW_FOREIGN_BAR_WITH_SPELL_WEAPONS;

    private BridgeClientConfig() {}

    static ModConfigSpec build() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("display");
        MANA_DISPLAY = b.comment(
                        "Which mana bar is shown: \"auto\" (bar of the leading mod), \"all\", or a mod ID such as \"runelessrpg\",",
                        "\"irons_spellbooks\" or \"ars_nouveau\". If that mod's adapter is off, the leading mod's bar is shown.")
                .translation(BridgeConfig.key("manaDisplay"))
                .define("manaDisplay", "auto");
        SHOW_RUNELESS_BAR_ON_DEBT = b.comment("Also show RunelessRPG's bar while you are in mana debt (other bars cannot show debt).")
                .translation(BridgeConfig.key("showRunelessBarOnDebt"))
                .define("showRunelessBarOnDebt", true);
        DISPLAY_UNIT = b.comment(
                        "Unit for all displayed mana amounts: AUTO (unit of the leading mod, or of the one other bar you show),",
                        "LEADER (unit of the leading mod) or RUNELESS (RunelessRPG mana).")
                .translation(BridgeConfig.key("displayUnit"))
                .defineEnum("displayUnit", DisplayUnit.AUTO);
        CONVERT_FOREIGN_TOOLTIPS = b.comment("Convert mana amounts in tooltips of other mods (spell costs, max mana) to the display unit.")
                .translation(BridgeConfig.key("convertForeignTooltips"))
                .define("convertForeignTooltips", true);
        SHOW_FOREIGN_BAR_WITH_SPELL_WEAPONS = b.comment(
                        "Bars of other mods in their \"contextual\" mode also show while you hold a casting item of another mod",
                        "(e.g. a Spell Engine spell weapon).")
                .translation(BridgeConfig.key("showForeignBarWithSpellWeapons"))
                .define("showForeignBarWithSpellWeapons", true);
        for (AdapterHandle handle : Adapters.known()) {
            handle.adapter().defineClientConfig(b);
        }
        b.pop();
        return b.build();
    }
}
