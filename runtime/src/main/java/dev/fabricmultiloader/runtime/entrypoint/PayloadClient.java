package dev.fabricmultiloader.runtime.entrypoint;

import dev.fabricmultiloader.format.Side;
import net.fabricmc.api.ClientModInitializer;

/**
 * The payload's {@code client} entrypoint.
 *
 * <p>Invoked by Fabric only on a client distribution, after {@link PayloadMain}. It runs the
 * payload's {@code Platform#onInitializeClient} and then the mod's own client entrypoints, and marks
 * initialisation complete.
 *
 * <p>The class is loaded only on a client, which is what allows a mod's client entrypoint to
 * reference client-only Minecraft types at all: on a dedicated server nothing resolves this
 * reference, so nothing tries to link them.
 */
public final class PayloadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadEntrypoints.initialiseSide(Side.CLIENT);
    }
}
