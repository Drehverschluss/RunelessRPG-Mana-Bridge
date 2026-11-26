package com.drehverschluss.runelessrpg_bridge.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Live state of an adapter, readable on both sides (on the client the server's state, synchronized).
 *
 * @apiNote Experimental: may still change before version 1.0.
 */
@ApiStatus.Experimental
public interface AdapterStatus {
    /**
     * @return true if the adapter is connected to the shared pool right now: its mod is loaded, it is enabled in the
     *         config, its self test passed and no other bridge blocks it. Whether it leads or follows does not matter.
     *         Costs of the mod's spells, Spell Mana Leech and converted displays check this.
     */
    boolean isConnected();

    /** @return true if the adapter's mod leads the shared pool right now ({@code manaSystem}) */
    boolean isLeading();

    /**
     * @return true if the adapter follows the leading pool right now: connected and not leading. The switch of the mod's
     *         own mana (reading, writing, own regeneration and respawn rule off, max redirected) checks this.
     */
    default boolean isActive() {
        return isConnected() && !isLeading();
    }

    /** @return why the adapter is not connected (empty while it is), for logs */
    String reason();

    /**
     * Server side: the adapter is off only because another adapter runs in a chain with the bridge that blocks this one
     * (e.g. Ars 'n Spells in {@code ars_primary}: the Ars adapter runs, the Iron's adapter is off). Then the mod's own
     * regeneration still flows into the shared pool through the other bridge; the adapter may turn it off anyway. False
     * while the adapter is connected or off for any other reason.
     *
     * @since 0.2.0 (phase C5A)
     */
    default boolean isOffInChain() {
        return false;
    }
}
