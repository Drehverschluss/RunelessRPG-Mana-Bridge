package com.drehverschluss.runelessrpg_bridge.api;

import com.drehverschluss.runelessrpg.api.ManaStorage;
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
 * Ars Nouveau) is one adapter. The core of RunelessRPG Mana Bridge only talks to adapters through this interface
 * and never touches a class of another mod.
 * <p>
 * An adapter has two parts. <b>This interface is the description</b> (mod id, config section, other bridges, bar):
 * its object is created for every known mod, also while the mod is missing, so that the config section stays. It must
 * not use any class of the mod. <b>The hooks</b> (mixins, listeners, the storage, everything with the mod's classes)
 * are only reached through the methods marked "only while the mod is loaded".
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

    /** @return the mod's name for config comments and logs (the mod may be missing, so it cannot be read from it) */
    default String displayName() {
        return modId();
    }

    /**
     * Base max mana of the mod for this player, in the mod's own units (before any bonus), e.g. 100 for Iron's.
     * The automatic factor is RunelessRPG base max mana ÷ this value. Only while the mod is loaded.
     *
     * @param player the player, or {@code null} for the default (e.g. the attribute's default value)
     */
    double baseMaxMana(@Nullable Player player);

    /**
     * Adds the adapter's own options to its config section (server config); called for every known adapter, also
     * while its mod is missing. The core adds {@code enabled}, {@code manaFactor}, {@code applyManaCost} and the bridge
     * switches first.
     */
    default void defineConfig(ModConfigSpec.Builder builder) {}

    /**
     * Adds the adapter's own client options to the section {@code [display]} of the client config; called for every
     * known adapter, also while its mod is missing. Option names must not clash with the core's or other adapters'.
     *
     * @since 0.1.0 (phase C3)
     */
    default void defineClientConfig(ModConfigSpec.Builder builder) {}

    /**
     * Only while the mod is loaded: called once on the mod bus during mod construction, after the config is defined.
     * Register listeners here; they must check the {@link AdapterStatus} themselves, because the state can change at
     * any time.
     */
    default void init(IEventBus modBus, AdapterStatus status) {}

    /** Only while the mod is loaded: client part of {@link #init}, called during client setup; never on a dedicated server. */
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
     * Only while the mod is loaded: whether the stack is a casting item of this mod (spell book, spell weapon). Mana
     * bars of other mods in a "contextual" mode also show while such an item is held.
     */
    default boolean isCastingItem(ItemStack stack) {
        return false;
    }

    /** Other bridges that touch the same mana pool; the adapter turns off while one of them is active. */
    default List<ConflictCheck> conflicts() {
        return List.of();
    }

    /**
     * Server side, while the mod is loaded: whether another bridge pays this mod's spells itself right now (Ars 'n Spells
     * in a mode of its mana unification). Those spells skip {@code mana_cost}, debt and overcharge of this bridge, so the
     * client shows their costs without them and no overcharge line. The server sends the value to the clients and
     * looks at it again once a second while another bridge is installed.
     *
     * @since 0.2.0 (phase C5C.1)
     */
    default boolean spellsPaidByOtherBridge() {
        return false;
    }

    /** Whether this mod can lead the shared pool ({@code manaSystem}); then {@link #createStorage()} must work. */
    default boolean canLead() {
        return false;
    }

    /**
     * Only while the mod is loaded, server side: the storage for RunelessRPG's API while this mod leads (called each time
     * it becomes the leader). RunelessRPG's own adapter returns {@code null} (its built-in storage).
     */
    default @Nullable ManaStorage createStorage() {
        return null;
    }

    /**
     * Only while the mod is loaded, server side, and only for adapters that {@link #canLead()}: base max mana and own
     * regeneration of the mod's pool while it leads, read from the mod's real config (for the choice of
     * {@code manaSystem} in the first-start question). {@code null} if unknown.
     *
     * @param player the player who chooses, or {@code null} for the default values
     * @since 0.2.0 (phase C5A)
     */
    default @Nullable LeadInfo leadInfo(@Nullable Player player) {
        return null;
    }

    /**
     * Only while the mod is loaded: the mod's own bonuses above its base values for this player, in its own units, as
     * modifiers by operation. Modifiers of this bridge (namespace {@code runelessrpg_bridge}) must be left out, so
     * nothing counts twice. Called every server tick while the adapter is connected and does not lead.
     */
    default BridgeBonus readBonus(Player player) {
        return BridgeBonus.NONE;
    }

    /**
     * Only while the mod is loaded: bonuses that the adapter switches with options of its own, so the core does not
     * filter them with {@code bridgeMaxMana}, {@code bridgeManaRegen} and {@code bridgePercentBonuses}; added to
     * {@link #readBonus}. Same units and rules as there. Example: Ars Nouveau's progress (glyphs, book tier, switch
     * {@code bridgeProgression}) and the mana its familiars reserve ({@code applyFamiliarReserve}, as
     * {@link BridgeBonus#reserve()}). Called every server tick while the adapter is connected and does not lead.
     *
     * @since 0.1.0 (phase C3)
     */
    default BridgeBonus readExtraBonus(Player player) {
        return BridgeBonus.NONE;
    }

    /**
     * Only while the mod is loaded: the bonus of all other connected mods while this mod leads, in this mod's units;
     * {@link BridgeBonus#NONE} while it does not lead (then all modifiers of the bridge must go). Called every server tick;
     * only change something if the value changed.
     */
    default void applyBonus(Player player, BridgeBonus bonus) {}
}
