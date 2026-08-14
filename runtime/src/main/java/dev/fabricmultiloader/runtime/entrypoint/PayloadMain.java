package dev.fabricmultiloader.runtime.entrypoint;

import net.fabricmc.api.ModInitializer;

/**
 * The payload's {@code main} entrypoint: platform initialisation, then the mod's common code.
 *
 * <p>Runs on both distributions, after Minecraft's classes have been loaded and the payload's mixins
 * have applied. Three things happen in a fixed order — the payload's {@code Platform#onInitialize},
 * the mod's own {@code onInitialize}, and finally the deferred registration flush — and the order is
 * the reason mod code can rely on services being present before it runs and on every declaration it
 * makes still being accepted afterwards.
 */
public final class PayloadMain implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadEntrypoints.initialise();
    }
}
