package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Attributes of other mods by id only, so no class of those mods is needed. */
public final class Attributes {
    /** Below this difference a modifier amount counts as unchanged. */
    private static final double SAME = 1.0E-9;

    private Attributes() {}

    public static @Nullable Holder<Attribute> holder(String id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).orElse(null);
    }

    /** Base value of the attribute on the player, or its default value without a player (or if the player lacks it). */
    public static double baseValue(@Nullable Player player, String id, double fallback) {
        Holder<Attribute> holder = holder(id);
        if (holder == null) {
            return fallback;
        }
        if (player != null) {
            AttributeInstance instance = player.getAttribute(holder);
            if (instance != null) {
                return instance.getBaseValue();
            }
        }
        return holder.value().getDefaultValue();
    }

    /** @return true if {@code holder} is the attribute with this id */
    public static boolean is(@Nullable Holder<Attribute> holder, String id) {
        return holder != null && holder.unwrapKey().map(k -> k.location().toString().equals(id)).orElse(false);
    }

    public static @Nullable AttributeInstance instance(Player player, String id) {
        Holder<Attribute> holder = holder(id);
        return holder == null ? null : player.getAttribute(holder);
    }

    /** Id of a modifier of this bridge, e.g. {@code runelessrpg_bridge:bridge_max_flat}. */
    public static ResourceLocation bridgeId(String path) {
        return ResourceLocation.fromNamespaceAndPath(ManaBridge.MODID, path);
    }

    /**
     * Modifiers of one attribute by operation, without the base value and without modifiers whose id has the
     * namespace {@code excludedNamespace}.
     *
     * @return {add, base, total} (sum of ADD_VALUE, sum of ADD_MULTIPLIED_BASE, product of 1 + ADD_MULTIPLIED_TOTAL),
     *         or {0, 0, 1} if the player lacks the attribute
     */
    public static double[] modifiersByOperation(Player player, String id, String excludedNamespace) {
        return modifiersByOperation(player, id, excludedNamespace, null);
    }

    /** Same as {@link #modifiersByOperation(Player, String, String)}, also without the modifier {@code excludedId} (may be null). */
    public static double[] modifiersByOperation(Player player, String id, String excludedNamespace, @Nullable ResourceLocation excludedId) {
        AttributeInstance instance = instance(player, id);
        double add = 0.0;
        double base = 0.0;
        double total = 1.0;
        if (instance != null) {
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.id().getNamespace().equals(excludedNamespace) || modifier.id().equals(excludedId)) {
                    continue;
                }
                switch (modifier.operation()) {
                    case ADD_VALUE -> add += modifier.amount();
                    case ADD_MULTIPLIED_BASE -> base += modifier.amount();
                    case ADD_MULTIPLIED_TOTAL -> total *= 1.0 + modifier.amount();
                }
            }
        }
        return new double[] {add, base, total};
    }

    /**
     * Sets the modifiers of the bridge on the leading mod's attributes: flat and percent max mana on {@code maxManaId},
     * percent and total regeneration on {@code manaRegenId}, and flat regeneration there too if {@code flatRegenId} is
     * not null. {@link com.drehverschluss.runelessrpg_bridge.api.BridgeBonus#NONE} removes them all.
     *
     * @return true if a modifier was added, changed or removed
     */
    public static boolean applyBridge(Player player, String maxManaId, String manaRegenId, @Nullable String flatRegenId,
            com.drehverschluss.runelessrpg_bridge.api.BridgeBonus bonus) {
        boolean changed = setBridgeModifier(player, maxManaId, bridgeId("bridge_max_flat"), bonus.maxFlat(), AttributeModifier.Operation.ADD_VALUE);
        changed |= setBridgeModifier(player, maxManaId, bridgeId("bridge_max_base"), bonus.maxBase(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        changed |= setBridgeModifier(player, maxManaId, bridgeId("bridge_max_total"), bonus.maxTotal() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (flatRegenId != null) {
            changed |= setBridgeModifier(player, flatRegenId, bridgeId("bridge_regen_flat"), bonus.regenFlat(), AttributeModifier.Operation.ADD_VALUE);
        }
        changed |= setBridgeModifier(player, manaRegenId, bridgeId("bridge_regen_base"), bonus.regenBase(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        changed |= setBridgeModifier(player, manaRegenId, bridgeId("bridge_regen_total"), bonus.regenTotal() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // reserved share (e.g. Ars familiars) as its own total modifier, so it shows on the leading bar and in tooltips
        changed |= setBridgeModifier(player, maxManaId, bridgeId("bridge_max_reserve"), -Math.max(0.0, Math.min(1.0, bonus.reserve())),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return changed;
    }

    /**
     * Sets a transient modifier of this bridge: removed if {@code amount} is 0 (no effect), replaced only if the amount
     * changed. Transient modifiers are never saved, so nothing stays on a player once the bridge is gone.
     *
     * @return true if the modifier was added, changed or removed
     */
    public static boolean setBridgeModifier(Player player, String attributeId, ResourceLocation modifierId, double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = instance(player, attributeId);
        if (instance == null) {
            return false;
        }
        AttributeModifier current = instance.getModifier(modifierId);
        boolean none = Math.abs(amount) < SAME || !Double.isFinite(amount);
        if (none) {
            if (current != null) {
                instance.removeModifier(modifierId);
                return true;
            }
            return false;
        }
        if (current != null) {
            if (current.operation() == operation && Math.abs(current.amount() - amount) < SAME) {
                return false;
            }
            instance.removeModifier(modifierId);
        }
        instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
        return true;
    }
}
