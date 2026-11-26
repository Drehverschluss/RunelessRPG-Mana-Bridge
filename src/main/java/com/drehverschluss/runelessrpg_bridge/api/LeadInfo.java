package com.drehverschluss.runelessrpg_bridge.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a mod's own pool brings while it leads, in the mod's own units: its base max mana and its own regeneration, read
 * from the mod's real config. Shown when a player or an operator chooses the leading pool ({@code manaSystem}).
 *
 * @param baseMaxMana        base max mana before any bonus (e.g. 100 for Iron's)
 * @param regenPerSecond     flat regeneration per second (e.g. 5 for Ars Nouveau, RunelessRPG's {@code baseManaRegen}); 0 if none
 * @param regenPercentPerSecond regeneration in percent of the max per second (e.g. 2 for Iron's); 0 if none
 * @since 0.2.0 (phase C5A)
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public record LeadInfo(double baseMaxMana, double regenPerSecond, double regenPercentPerSecond) {}
