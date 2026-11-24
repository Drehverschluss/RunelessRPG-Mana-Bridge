package com.drehverschluss.runelessrpg_bridge.adapter.runelessrpg;

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
 * RunelessRPG itself as an adapter. It leads the pool in this version: its built-in storage holds the mana, all others
 * pay through RunelessRPG's API. Only RunelessRPG's API and attribute ids are used, never its internal classes.
 */
public final class RunelessAdapter implements ManaAdapter {
    public static final String MAX_MANA = "runelessrpg:max_mana";

    @Override
    public String modId() {
        return Adapters.RUNELESS;
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
}
