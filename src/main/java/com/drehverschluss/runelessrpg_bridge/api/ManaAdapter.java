package com.drehverschluss.runelessrpg_bridge.api;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Connection of one mana mod to the shared mana pool. Every mana mod (RunelessRPG itself, Iron's Spells 'n Spellbooks,
 * later Ars Nouveau) is one adapter. The core of RunelessRPG Mana Bridge only talks to adapters through this interface
 * and never touches a class of another mod.
 * <p>
 * An adapter object is only created when its mod is loaded. Its own classes that use the mod's classes must only be
 * loaded from methods of this interface that run while the mod is present.
 * <p>
 * All amounts are in the adapter's own mana units unless a method says otherwise. The core converts them with the
 * factor "1 own mana = factor RunelessRPG mana".
 *
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public interface ManaAdapter {
    /** @return the mod id of the mana mod; also the name of the adapter's config section */
    String modId();

    /**
     * Base max mana of the mod for this player, in the mod's own units (before any bonus), e.g. 100 for Iron's.
     * The automatic factor is RunelessRPG base max mana ÷ this value.
     *
     * @param player the player, or {@code null} for the default (e.g. the attribute's default value)
     */
    double baseMaxMana(@Nullable Player player);

    /**
     * Adds the adapter's own options to its config section (server config). The core adds {@code enabled},
     * {@code manaFactor} and {@code applyManaCost} for every adapter that does not lead; these come after them.
     */
    default void defineConfig(ModConfigSpec.Builder builder) {}

    /**
     * Called once on the mod bus during mod construction, after the config is defined. Register listeners here; they
     * must check {@link AdapterStatus#isActive()} themselves, because an adapter can be switched off at any time.
     */
    default void init(IEventBus modBus, AdapterStatus status) {}

    /** Client-only part of {@link #init}, called during client setup; never called on a dedicated server. */
    default void initClient(IEventBus modBus, AdapterStatus status) {}

    /**
     * Classes of the mod this adapter needs. If one is missing, the adapter turns off with one warning.
     * Mixins of the adapter are checked by the core itself (see {@link #mixinConfig()}).
     */
    default List<String> requiredClasses() {
        return List.of();
    }

    /** @return the file name of the adapter's mixin config (e.g. {@code runelessrpg_bridge.irons_spellbooks.mixins.json}), or {@code null} */
    default @Nullable String mixinConfig() {
        return null;
    }

    /** HUD layers that draw this mod's mana bar; hidden or shown by the client option {@code manaDisplay}. */
    default List<ResourceLocation> hudLayers() {
        return List.of();
    }

    /**
     * Whether the stack is a casting item of this mod (spell book, spell weapon). Mana bars of other mods in a
     * "contextual" mode also show while such an item is held.
     */
    default boolean isCastingItem(ItemStack stack) {
        return false;
    }

    /** Other bridges that touch the same mana pool; the adapter turns off while one of them is active. */
    default List<ConflictCheck> conflicts() {
        return List.of();
    }

    /**
     * Whether this mod can lead the shared pool ({@code manaSystem}). Only RunelessRPG can lead so far; other mods get
     * a storage for RunelessRPG's API in a later version.
     */
    default boolean canLead() {
        return false;
    }
}
