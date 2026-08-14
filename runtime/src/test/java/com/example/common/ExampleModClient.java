package com.example.common;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.UniversalClientMod;
import dev.fabricmultiloader.runtime.fixture.Recorder;

/** The mod's client half, as the reference manifest fixture declares it. */
public final class ExampleModClient implements UniversalClientMod {

    @Override
    public void onInitializeClient(ModContext ctx) {
        Recorder.record("mod:onInitializeClient:side=" + ctx.side().id()
                + ":phase=" + ctx.phase());
    }
}
