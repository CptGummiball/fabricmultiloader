package dev.fabricmultiloader.api;

import dev.fabricmultiloader.api.platform.PreLaunchContext;

/**
 * Optional hook that runs before Minecraft classes are loaded.
 *
 * <p>Most mods do not need it. It exists for configuration that later initialisation depends on,
 * and for early diagnostics — the two things that genuinely have to happen before the game starts
 * building itself.
 *
 * <p>Touching a Minecraft class here is a mistake even when it appears to work: classes are
 * transformed as they load, so loading one early can stop another mod's mixin from ever applying to
 * it. The narrow {@link PreLaunchContext} makes that hard to do by accident.
 */
@ImplementedByMod
public interface UniversalPreLaunch {

    /**
     * Runs before the game starts loading.
     *
     * @param ctx the reduced pre-launch context
     */
    void onPreLaunch(PreLaunchContext ctx);
}
