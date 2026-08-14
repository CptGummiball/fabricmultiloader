package com.example.mc1214;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformFactory;
import dev.fabricmultiloader.runtime.fixture.Recorder;

/** The class the reference manifest fixture names in {@code payload.platformFactory}. */
public final class Platform1214Factory implements PlatformFactory {

    @Override
    public Platform create(ModContext ctx) {
        Recorder.record("factory:create:" + ctx.modId());
        return new Platform1214(ctx);
    }
}
