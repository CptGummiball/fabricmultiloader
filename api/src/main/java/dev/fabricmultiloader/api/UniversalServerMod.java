package dev.fabricmultiloader.api;

/**
 * Dedicated-server-only initialisation, run after {@link UniversalMod#onInitialize}.
 *
 * <p>Not called for the integrated server inside a single-player game — that runs on a client
 * distribution, and {@link UniversalClientMod} is what fires there. The split follows the physical
 * side, because that is what determines which classes exist, not which logical side is simulated.
 */
@ImplementedByMod
public interface UniversalServerMod {

    /**
     * Initialises the dedicated-server part of the mod.
     *
     * @param ctx the same context the common entrypoint received
     */
    void onInitializeServer(ModContext ctx);
}
