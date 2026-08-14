package dev.fabricmultiloader.api;

/**
 * Client-only initialisation, run after {@link UniversalMod#onInitialize} on a client distribution.
 *
 * <p>Never invoked on a dedicated server, which matters for more than tidiness: a class reachable
 * only from here may safely touch client-only concerns, whereas the same code called from
 * {@link UniversalMod} would be loaded on a server where {@code net.minecraft.client} does not
 * exist. The validator enforces that separation statically ({@code OMNI-1150}), because a
 * {@code NoClassDefFoundError} at server start is a poor way to discover it.
 */
@ImplementedByMod
public interface UniversalClientMod {

    /**
     * Initialises the client-side part of the mod.
     *
     * @param ctx the same context the common entrypoint received
     */
    void onInitializeClient(ModContext ctx);
}
