package com.drehverschluss.runelessrpg_bridge.core;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Attributes of other mods by id only, so no class of those mods is needed. */
public final class Attributes {
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
}
