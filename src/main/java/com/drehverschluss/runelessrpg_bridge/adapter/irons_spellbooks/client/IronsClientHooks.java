package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.client;

import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsAdapter;
import com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.IronsHooks;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.drehverschluss.runelessrpg_bridge.core.client.DisplayManager;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.config.ClientConfigs;
import io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Client hooks for Iron's: values of its bar and its mana amounts in the display unit. */
public final class IronsClientHooks {
    private static final String ID = IronsAdapter.ID;

    private IronsClientHooks() {}

    private static @Nullable LocalPlayer player() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && IronsHooks.applies(player) ? player : null;
    }

    /** {@code ClientMagicData.getPlayerMana()}: shared mana in whole Iron's units, or null to keep Iron's value. */
    public static @Nullable Integer playerMana() {
        LocalPlayer player = player();
        return player == null ? null : Pool.readInt(player, ID);
    }

    /** Max mana of Iron's bar: shared max in whole Iron's units. */
    public static double barMax(Player player, double original) {
        return player.level().isClientSide() && IronsHooks.applies(player) ? Pool.readMaxInt(player, ID) : original;
    }

    /** Iron's bar in "contextual" mode also shows while a casting item of another mod is held. */
    public static boolean showBar(Player player, boolean original) {
        if (original || !player.level().isClientSide() || !IronsHooks.connected(player)) {
            return original;
        }
        return ClientConfigs.MANA_BAR_DISPLAY.get() == ManaBarOverlay.Display.Contextual && !player.isSpectator()
                && DisplayManager.holdsOtherCastingItem(player, ID);
    }

    // ------------------------------------------------------------------ amounts in the display unit

    /** @return the player if amounts should be converted, else null */
    private static @Nullable LocalPlayer converting() {
        // leading or following: Iron's amounts are always shown in the display unit (plan 5.1)
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && IronsHooks.connected(player) && DisplayManager.convertTooltips() ? player : null;
    }

    /** A cost of Iron's (own units) as shown: × mana_cost × factor × display factor. */
    public static double displayCost(Player player, double ownCost) {
        return DisplayManager.toDisplay(player, ID, Pool.effectiveCost(player, ID, ownCost));
    }

    /** {@code TooltipsUtils.getManaCostComponent}: " 2 Mana Cost" / " 1.2 Mana/s" in the display unit, or null. */
    public static @Nullable MutableComponent manaCostComponent(CastType castType, int manaCost) {
        LocalPlayer player = converting();
        if (player == null) {
            return null;
        }
        if (castType == CastType.CONTINUOUS) {
            return DisplayManager.numberText("tooltip.irons_spellbooks.mana_cost_per_second", displayCost(player, manaCost * 2.0));
        }
        return DisplayManager.numberText("tooltip.irons_spellbooks.mana_cost", displayCost(player, manaCost));
    }

    /** Keys of Iron's spell info lines that mean the spell deals damage (directly, by a zone or by summons). */
    private static final java.util.Set<String> DAMAGE_KEYS = java.util.Set.of("ui.irons_spellbooks.damage", "ui.irons_spellbooks.aoe_damage",
            "ui.irons_spellbooks.base_damage", "ui.irons_spellbooks.impact_damage", "ui.irons_spellbooks.percent_damage",
            "ui.irons_spellbooks.shatter_damage");
    /** Keys of Iron's spell info lines that mean the spell heals. */
    private static final java.util.Set<String> HEALING_KEYS = java.util.Set.of("ui.irons_spellbooks.healing", "ui.irons_spellbooks.aoe_healing",
            "ui.irons_spellbooks.greater_healing");

    /**
     * {@code TooltipsUtils.formatActiveSpellTooltip} (spell books, staves, swords): the overcharge line right below the cost
     * line, only if the cast pays mana and the bonus is above 0. Damage or healing comes from Iron's own info lines of the
     * spell ("%d Damage", "%d Healing" ...); a spell with neither (buffs, utility) gets no line, like in RunelessRPG.
     */
    public static void addOverchargeLine(java.util.List<MutableComponent> lines, io.redspace.ironsspellbooks.api.spells.SpellData spellData,
            io.redspace.ironsspellbooks.api.spells.CastSource castSource, LocalPlayer player) {
        if (lines == null || spellData == null || !castSource.consumesMana() || !IronsHooks.connected(player)) {
            return;
        }
        io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = spellData.getSpell();
        int level = spell.getLevelFor(spellData.getLevel(), player);
        if (spell.getManaCost(level) <= 0) {
            return;
        }
        boolean damage = false;
        boolean healing = false;
        for (MutableComponent info : spell.getUniqueInfo(level, player)) {
            if (info.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
                damage |= DAMAGE_KEYS.contains(contents.getKey());
                healing |= HEALING_KEYS.contains(contents.getKey());
            }
        }
        MutableComponent line = DisplayManager.overchargeLine(player, ID, damage, healing);
        if (line == null) {
            return;
        }
        String cost = io.redspace.ironsspellbooks.util.TooltipsUtils.getManaCostComponent(spell.getCastType(), spell.getManaCost(level)).getString();
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).getString().equals(cost)) {
                // indented like Iron's own cost line (" 2.4 Mana Cost")
                int indent = cost.length() - cost.stripLeading().length();
                lines.add(i + 1, Component.literal(" ".repeat(indent)).append(line));
                return;
            }
        }
    }

    /** Spell wheel: "%d Mana" in the display unit, or null to keep Iron's text. */
    public static @Nullable MutableComponent wheelManaCost(String key, Object[] args) {
        LocalPlayer player = converting();
        if (player == null || !"ui.irons_spellbooks.mana_cost".equals(key) || args.length != 1 || !(args[0] instanceof Integer cost)) {
            return null;
        }
        return DisplayManager.numberText(key, displayCost(player, cost));
    }

    /** Inscription table: the number next to "Mana" in the display unit, or null to keep it. */
    public static @Nullable String inscriptionManaCost(String key, String value) {
        LocalPlayer player = converting();
        if (player == null || !"ui.irons_spellbooks.mana_cost".equals(key)) {
            return null;
        }
        try {
            return DisplayManager.format(displayCost(player, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Flat value of {@code irons_spellbooks:max_mana} (modifier or attribute value) in the display unit, or NaN. */
    public static double maxManaValue(double value) {
        LocalPlayer player = converting();
        return player == null ? Double.NaN : DisplayManager.toDisplay(player, ID, value);
    }
}
