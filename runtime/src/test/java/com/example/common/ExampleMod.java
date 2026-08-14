package com.example.common;

import com.example.mc1214.Platform1214;
import dev.fabricmultiloader.api.Capabilities;
import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.UniversalMod;
import dev.fabricmultiloader.api.UniversalPreLaunch;
import dev.fabricmultiloader.api.platform.PreLaunchContext;
import dev.fabricmultiloader.runtime.fixture.Recorder;

/**
 * The mod's common half, as the reference manifest fixture declares it.
 *
 * <p>Implements two phases in one class, which is how a small mod is actually written and which is
 * exactly the case that would break if the runtime constructed a fresh instance per phase: the
 * pre-launch hook stores something the main hook reads back.
 */
public final class ExampleMod implements UniversalMod, UniversalPreLaunch {

    private String configuredIn;

    @Override
    public void onPreLaunch(PreLaunchContext ctx) {
        this.configuredIn = ctx.modId();
        Recorder.record("mod:onPreLaunch:" + ctx.modId() + ":phase=" + ctx.platform().payloadId());
    }

    @Override
    public void onInitialize(ModContext ctx) {
        Recorder.record("mod:onInitialize:phase=" + ctx.phase());
        Recorder.record("mod:sawPreLaunchState=" + (configuredIn != null));

        // The escape hatch: a service the payload registered before this ran.
        Recorder.record("mod:greeting="
                + ctx.services().get(Platform1214.Greeting.class).greet());
        Recorder.record("mod:hasComponents=" + ctx.has(Capabilities.COMPONENTS));
        Recorder.record("mod:hasTags=" + ctx.has(Capabilities.TAGS));

        if (ctx.phase() != LifecyclePhase.COMMON_INIT) {
            throw new AssertionError("common code ran in phase " + ctx.phase());
        }
    }
}
