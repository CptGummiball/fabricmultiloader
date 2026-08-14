package dev.fabricmultiloader.runtime.entrypoint;

import dev.fabricmultiloader.format.Side;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * The payload's {@code server} entrypoint.
 *
 * <p>Invoked by Fabric only on a dedicated server, after {@link PayloadMain}. Note that this is the
 * <em>physical</em> server: a single-player world runs an integrated server but the distribution is
 * a client, so this does not run there and {@link PayloadClient} does. That distinction is the one
 * mod authors most often get wrong, which is why {@code ModContext#side()} reports the physical side
 * and says so.
 */
public final class PayloadServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        PayloadEntrypoints.initialiseSide(Side.SERVER);
    }
}
