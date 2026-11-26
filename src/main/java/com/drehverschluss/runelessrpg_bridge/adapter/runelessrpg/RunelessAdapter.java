package com.drehverschluss.runelessrpg_bridge.adapter.runelessrpg;

import com.drehverschluss.runelessrpg.api.ManaBonus;
import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import com.drehverschluss.runelessrpg_bridge.api.LeadInfo;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * RunelessRPG itself as an adapter. It leads the pool by default with its built-in storage; while another mod leads,
 * its bonuses (modifiers on {@code runelessrpg:max_mana} and {@code runelessrpg:mana_regen}) go to that mod through the
 * bridge (plan 4.6). Only RunelessRPG's API and attribute ids are used, never its internal classes.
 */
public final class RunelessAdapter implements ManaAdapter {
    public static final String MAX_MANA = "runelessrpg:max_mana";
    public static final String MANA_REGEN = "runelessrpg:mana_regen";

    @Override
    public String modId() {
        return Adapters.RUNELESS;
    }

    @Override
    public String displayName() {
        return "RunelessRPG";
    }

    /** RunelessRPG sets the base value of {@code runelessrpg:max_mana} from its config {@code baseMaxMana} (default 20). */
    @Override
    public double baseMaxMana(@Nullable Player player) {
        return Attributes.baseValue(player, MAX_MANA, 20.0);
    }

    @Override
    public boolean canLead() {
        return true;
    }

    /** Base values of RunelessRPG's two attributes, which RunelessRPG sets from {@code baseMaxMana} and {@code baseManaRegen}. */
    @Override
    public LeadInfo leadInfo(@Nullable Player player) {
        return new LeadInfo(baseMaxMana(player), Attributes.baseValue(player, MANA_REGEN, 0.5), 0.0);
    }

    @Override
    public List<ResourceLocation> hudLayers() {
        return List.of(ResourceLocation.fromNamespaceAndPath("runelessrpg", "mana_bar_behind"),
                ResourceLocation.fromNamespaceAndPath("runelessrpg", "mana_bar_front"));
    }

    /** Spell Engine spell weapons and spell books: items with a valid spell container. */
    @Override
    public boolean isCastingItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        SpellContainer container = SpellContainerHelper.containerFromItemStack(stack);
        return container != null && container.isValid();
    }

    /** Modifiers on RunelessRPG's attributes without the base value and without the bridge's own (in RunelessRPG mana). */
    @Override
    public BridgeBonus readBonus(Player player) {
        ManaBonus bonus = RunelessApi.getManaBonus(player, ManaBridge.MODID);
        ManaBonus.Part max = bonus.maxMana();
        ManaBonus.Part regen = bonus.manaRegen();
        return new BridgeBonus(max.addValue(), max.addMultipliedBase(), max.multiplyTotal(),
                regen.addValue(), regen.addMultipliedBase(), regen.multiplyTotal());
    }

    /** While RunelessRPG leads: modifiers of the same operations on its two attributes (flat regeneration too). */
    @Override
    public void applyBonus(Player player, BridgeBonus bonus) {
        Attributes.applyBridge(player, MAX_MANA, MANA_REGEN, MANA_REGEN, bonus);
    }
}
