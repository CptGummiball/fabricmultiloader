package dev.fabricmultiloader.runtime.entrypoint;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * The payload's {@code preLaunch} entrypoint: creates the platform.
 *
 * <p>Declared in every payload's generated {@code fabric.mod.json}. It runs after
 * {@link ContainerPreLaunch}, because each payload declares an exact dependency on its container
 * and Fabric derives entrypoint order from the dependency graph — but nothing here relies on that.
 * If this runs first it resolves the container itself, and the container's own hook then finds the
 * work already done.
 *
 * <p>Creating the platform this early is deliberate. Pre-launch is the last moment before the first
 * Minecraft class is loaded, so a payload that cannot start fails while there is still no
 * half-initialised registry and no partially applied mixin to leave behind, and Fabric shows the
 * message in its error dialog.
 */
public final class PayloadPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        PayloadEntrypoints.preLaunch();
    }
}
