package com.drehverschluss.runelessrpg_bridge.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Bonuses on max mana and mana regeneration that one mana mod hands to the leading pool ("bridge", plan 4.1 and 4.6),
 * split by how they are computed. Read with {@link ManaAdapter#readBonus}, applied with {@link ManaAdapter#applyBonus}.
 * <p>
 * Flat values are in the units of the mod that reports them ({@code readBonus}) or of the leading mod
 * ({@code applyBonus}); the core converts between them with the mana factors. Percent values are fractions and stay as
 * they are in every unit.
 *
 * @param maxFlat       flat max mana (like {@code ADD_VALUE})
 * @param maxBase       percent of the base value (like {@code ADD_MULTIPLIED_BASE}, 0.1 = +10 %)
 * @param maxTotal      factor on the total (like {@code ADD_MULTIPLIED_TOTAL}, product of {@code 1 + amount}; 1 = none)
 * @param regenFlat     flat mana per second, paid into the pool besides the leading mod's own regeneration
 * @param regenBase     percent on the leading mod's regeneration, added to its other percent bonuses (0.15 = +15 %)
 * @param regenTotal    factor on the leading mod's regeneration (1 = none)
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public record BridgeBonus(double maxFlat, double maxBase, double maxTotal, double regenFlat, double regenBase, double regenTotal) {
    /** No bonus. */
    public static final BridgeBonus NONE = new BridgeBonus(0.0, 0.0, 1.0, 0.0, 0.0, 1.0);

    /** @return both bonuses together: flat and percent values add up, total factors multiply */
    public BridgeBonus plus(BridgeBonus other) {
        return new BridgeBonus(maxFlat + other.maxFlat, maxBase + other.maxBase, maxTotal * other.maxTotal,
                regenFlat + other.regenFlat, regenBase + other.regenBase, regenTotal * other.regenTotal);
    }

    /** @return the same bonus with the flat values multiplied by {@code factor} (unit conversion) */
    public BridgeBonus scaleFlat(double factor) {
        return new BridgeBonus(maxFlat * factor, maxBase, maxTotal, regenFlat * factor, regenBase, regenTotal);
    }

    /**
     * @return the bonus with only the chosen parts (switches {@code bridgeMaxMana}, {@code bridgeManaRegen},
     *         {@code bridgePercentBonuses})
     */
    public BridgeBonus filter(boolean maxMana, boolean manaRegen, boolean percentMax) {
        return new BridgeBonus(maxMana ? maxFlat : 0.0, maxMana && percentMax ? maxBase : 0.0, maxMana && percentMax ? maxTotal : 1.0,
                manaRegen ? regenFlat : 0.0, manaRegen ? regenBase : 0.0, manaRegen ? regenTotal : 1.0);
    }
}
