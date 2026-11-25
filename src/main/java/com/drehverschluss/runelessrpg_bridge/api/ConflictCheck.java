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
 * @param chained  only asked while {@code isActive} is true: the other bridge and this adapter work in a chain (each
 *                 links different pools), so the adapter stays on. An exception counts as "not chained". Since 0.1.0
 *                 (phase C3).
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public record ConflictCheck(String modId, BooleanSupplier isActive, BooleanSupplier chained) {
    private static final BooleanSupplier NEVER = () -> false;

    /** A bridge that is never chained with the adapter. */
    public ConflictCheck(String modId, BooleanSupplier isActive) {
        this(modId, isActive, NEVER);
    }

    /** A bridge that is always active while installed. */
    public static ConflictCheck always(String modId) {
        return new ConflictCheck(modId, () -> true);
    }
}
