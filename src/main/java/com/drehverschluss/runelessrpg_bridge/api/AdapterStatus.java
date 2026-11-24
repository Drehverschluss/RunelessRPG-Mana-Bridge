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
     * @return true if the adapter works right now: its mod is loaded, it is enabled in the config, its self test passed,
     *         no other bridge blocks it and it does not lead the pool itself. All hooks of a following adapter check this
     *         first and do nothing otherwise.
     */
    boolean isActive();

    /** @return why the adapter is off (empty while it is active), for logs */
    String reason();
}
