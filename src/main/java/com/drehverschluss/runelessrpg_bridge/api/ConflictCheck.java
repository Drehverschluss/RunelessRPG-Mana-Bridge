package com.drehverschluss.runelessrpg_bridge.api;

import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Another bridge that touches the same mana pool as an adapter.
 *
 * @param modId    mod id of the other bridge
 * @param isActive whether its mana function is on for this adapter's mod right now, usually read from its config by
 *                 reflection; it is only asked while the mod is loaded. If it cannot tell (exception), the bridge counts
 *                 as active.
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public record ConflictCheck(String modId, BooleanSupplier isActive) {
    /** A bridge that is always active while installed. */
    public static ConflictCheck always(String modId) {
        return new ConflictCheck(modId, () -> true);
    }
}
