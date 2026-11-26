package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.client;

import com.drehverschluss.runelessrpg.api.ManaInsufficientEvent;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsAdapter;
import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.ArsHooks;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.drehverschluss.runelessrpg_bridge.core.client.DisplayManager;
import com.hollingsworth.arsnouveau.client.ClientInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Client hooks for Ars: visibility of its bar, the red flash for every failed cast, its mana amounts in the display unit. */
public final class ArsClientHooks {
    private static final String ID = ArsAdapter.ID;
    /** Ticks Ars' bar flashes red after a failed cast (same as Ars' own NotEnoughManaPacket). */
    private static final int RED_TICKS = 35;
    private static boolean errorLogged;

    private ArsClientHooks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ManaInsufficientEvent.class, ArsClientHooks::onInsufficient);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent.class, ArsClientHooks::onTooltip);
    }

    /** Glyphs that deal damage without being an {@code IDamageEffect} (through an entity or an explosion of their own). */
    private static final java.util.Set<String> OTHER_DAMAGE_GLYPHS = java.util.Set.of("ars_nouveau:glyph_lightning",
            "ars_nouveau:glyph_wind_burst", "ars_nouveau:glyph_summon_vex", "ars_nouveau:glyph_summon_wolves",
            "ars_nouveau:glyph_summon_undead");

    /**
     * Tooltip of an Ars casting item (spell book, caster tome, wand, bow ...): the overcharge line for its selected spell,
     * like below the cost of a Spell Engine spell (Ars shows no cost numbers there, plan 4.10). Damage: any damaging glyph;
     * healing: Heal.
     */
    private static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        try {
            LocalPlayer player = connectedPlayer();
            if (player == null || event.getEntity() != player || !ArsHooks.isCastingItem(event.getItemStack())) {
                return;
            }
            com.hollingsworth.arsnouveau.api.spell.AbstractCaster<?> caster =
                    com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry.from(event.getItemStack());
            if (caster == null || caster.getSpell() == null || caster.getSpell().isEmpty()) {
                return;
            }
            boolean damage = false;
            boolean healing = false;
            for (com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart part : caster.getSpell().unsafeList()) {
                if (part instanceof com.hollingsworth.arsnouveau.common.spell.effect.EffectHeal) {
                    healing = true;
                } else if (part instanceof com.hollingsworth.arsnouveau.api.spell.IDamageEffect
                        || OTHER_DAMAGE_GLYPHS.contains(part.getRegistryName().toString())) {
                    damage = true;
                }
            }
            MutableComponent line = DisplayManager.overchargeLine(player, ID, damage, healing);
            if (line != null) {
                event.getToolTip().add(line);
            }
        } catch (RuntimeException | LinkageError e) {
            if (!errorLogged) {
                errorLogged = true;
                ManaBridge.LOGGER.error("Ars Nouveau adapter: overcharge line in the tooltip failed", e);
            }
        }
    }

    private static @Nullable LocalPlayer connectedPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && ArsHooks.connected(player) ? player : null;
    }

    /**
     * RunelessRPG reports every failed cast with the missing mana (Spell Engine, Iron's, Ars spells): Ars' bar flashes red
     * like after an Ars spell, with the missing amount in Ars units ({@code forwardInsufficientFlash}).
     */
    private static void onInsufficient(ManaInsufficientEvent event) {
        try {
            LocalPlayer player = connectedPlayer();
            if (player == null || !ArsAdapter.forwardInsufficientFlash()) {
                return;
            }
            float missing = (float) (Math.max(0.0, event.getMissingMana()) / Pool.factor(player, ID));
            // Ars itself may have set the whole cost of an Ars spell in the same moment: keep the larger amount
            ClientInfo.redOverlayMana = ClientInfo.redOverlayTicks > 0 ? Math.max(ClientInfo.redOverlayMana, missing) : missing;
            ClientInfo.redOverlayTicks = RED_TICKS;
        } catch (RuntimeException | LinkageError e) {
            if (!errorLogged) {
                errorLogged = true;
                ManaBridge.LOGGER.error("Ars Nouveau adapter: red flash of Ars' bar failed", e);
            }
        }
    }

    /** {@code GuiManaHUD.shouldDisplayBar}: Ars' bar also shows while a casting item of another mod is held. */
    public static boolean showBar(boolean original) {
        if (original) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = connectedPlayer();
        return player != null && !minecraft.options.hideGui && !player.isSpectator() && DisplayManager.holdsOtherCastingItem(player, ID);
    }

    // ------------------------------------------------------------------ amounts in the display unit

    private static @Nullable LocalPlayer converting() {
        LocalPlayer player = connectedPlayer();
        return player != null && DisplayManager.convertTooltips() ? player : null;
    }

    /**
     * Spell book: the cost its mana bar shows (in Ars units, against the shared max) with {@code mana_cost}, like the cast
     * will pay. Only the share is drawn, so the unit does not matter here.
     */
    public static int bookCost(int cost) {
        LocalPlayer player = connectedPlayer();
        return player == null ? cost : (int) Math.round(Pool.effectiveCost(player, ID, cost));
    }

    /** Discount ring: "Reduce the cost of all spells by %d." in the display unit, with {@code mana_cost}; or null to keep it. */
    public static @Nullable MutableComponent discountText(String key, Object[] args) {
        LocalPlayer player = converting();
        if (player == null || !"tooltip.discount_item".equals(key) || args.length != 1 || !(args[0] instanceof Integer discount)) {
            return null;
        }
        return DisplayManager.numberText(key, DisplayManager.toDisplay(player, ID, Pool.effectiveCost(player, ID, discount)));
    }

    /** @return true for Ars' max mana and mana regen attributes (flat values are mana amounts) */
    public static boolean isManaAttribute(Holder<Attribute> attribute) {
        return Attributes.is(attribute, ArsAdapter.MAX_MANA) || Attributes.is(attribute, ArsAdapter.MANA_REGEN);
    }

    /** A flat value of Ars' max mana or mana regen in the display unit, or NaN to keep it. */
    public static double attributeValue(double value) {
        LocalPlayer player = converting();
        return player == null ? Double.NaN : DisplayManager.toDisplay(player, ID, value);
    }
}
