package dev.fabricmultiloader.runtime.entrypoint;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.boot.RuntimeBootstrap;

/**
 * The shared body of the four payload entrypoints.
 *
 * <p>Each of them does the same thing: resolve every universal mod in the process, then advance the
 * ones that resolved by one phase. They drive <em>every</em> container rather than their own,
 * because a Fabric entrypoint is not told which mod declared it — {@code EntrypointMetadata} lives
 * under {@code net.fabricmc.loader.impl}, which this project does not touch.
 *
 * <p>That turns out to be the better design rather than a concession. Every step is idempotent, so
 * the first payload entrypoint of a phase initialises all universal mods and the remaining ones are
 * no-ops; the outcome therefore does not depend on the order Fabric picked, and with two universal
 * mods installed neither can end up half-initialised because the other's entrypoint ran first.
 */
final class PayloadEntrypoints {

    /** Creates the platform and runs the pre-launch hooks of every resolved container. */
    static void preLaunch() {
        for (ContainerRuntime runtime : active()) {
            runtime.activation().preLaunch();
        }
    }

    /** Runs platform initialisation, the common entrypoints and the registry flush. */
    static void initialise() {
        for (ContainerRuntime runtime : active()) {
            runtime.activation().initialise();
        }
    }

    /** Runs the side-specific hooks and completes initialisation. */
    static void initialiseSide(Side side) {
        for (ContainerRuntime runtime : active()) {
            runtime.activation().initialiseSide(side);
        }
    }

    /**
     * Every container with a payload to run.
     *
     * <p>A container that failed to resolve is skipped rather than retried. In the default strict
     * mode the launch has already aborted by this point; reaching here with an inactive container
     * means the operator chose {@code fabricmultiloader.strict=false}, and honouring that choice
     * means staying quiet rather than reporting the same failure once per phase.
     */
    private static java.util.List<ContainerRuntime> active() {
        java.util.List<ContainerRuntime> running = new java.util.ArrayList<ContainerRuntime>();
        for (ContainerRuntime runtime : RuntimeBootstrap.get().resolveAll()) {
            if (runtime.isActive()) {
                running.add(runtime);
            }
        }
        return running;
    }

    private PayloadEntrypoints() {
        throw new AssertionError("no instances");
    }
}
