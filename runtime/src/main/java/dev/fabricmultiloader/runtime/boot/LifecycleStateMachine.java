package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import dev.fabricmultiloader.format.error.OmniException;

/**
 * Tracks how far initialisation has got, and refuses to go backwards.
 *
 * <p>Fabric invokes entrypoints in an order derived from the dependency graph, and every payload
 * declares a dependency on its container, so the intended order holds in practice. This does not
 * rely on that. Entrypoint ordering is a loader implementation detail, and a mod that observed a
 * half-initialised context because two entrypoints ran in an unexpected order would produce a bug
 * report nobody could reproduce.
 *
 * <p>Transitions are therefore explicit, forward-only and idempotent: repeating one is a no-op that
 * logs at debug, going backwards is a programming error, and an operation attempted in the wrong
 * phase raises {@code OMNI-4002} naming both the current and the required phase.
 */
public final class LifecycleStateMachine {

    private final String containerModId;
    private volatile LifecyclePhase phase = LifecyclePhase.DISCOVERED;

    /**
     * @param containerModId the container this tracks, named in every diagnostic
     */
    public LifecycleStateMachine(String containerModId) {
        this.containerModId = containerModId;
    }

    /** The current phase. */
    public LifecyclePhase phase() {
        return phase;
    }

    /**
     * Advances to a phase.
     *
     * @param target the new phase
     * @return {@code true} if this advanced the state, {@code false} if it was already there or
     *     beyond — which lets a caller make the work itself idempotent
     * @throws OmniException {@code OMNI-4001} on a backwards transition
     */
    public synchronized boolean advanceTo(LifecyclePhase target) {
        if (phase == LifecyclePhase.FAILED) {
            return false;
        }
        if (target == LifecyclePhase.FAILED) {
            phase = LifecyclePhase.FAILED;
            return true;
        }
        if (phase.ordinal() > target.ordinal()) {
            throw new OmniException(ErrorCode.OMNI_4001, Messages.report(ErrorCode.OMNI_4001)
                    .detected("mod", containerModId)
                    .detected("current phase", phase)
                    .detected("attempted phase", target)
                    .detail("Initialisation phases only move forward.")
                    .fix("this is a framework bug — please report it with the full log")
                    .build());
        }
        if (phase == target) {
            return false;
        }
        phase = target;
        return true;
    }

    /** Marks initialisation as failed. */
    public synchronized void fail() {
        phase = LifecyclePhase.FAILED;
    }

    /** Whether initialisation failed. */
    public boolean hasFailed() {
        return phase == LifecyclePhase.FAILED;
    }

    /**
     * Requires the current phase to be at or beyond the given one.
     *
     * @param required the minimum phase
     * @param operation what was attempted, named in the diagnostic
     * @throws OmniApiMisuseException {@code OMNI-4002} if the phase is too early
     */
    public void requireAtLeast(LifecyclePhase required, String operation) {
        LifecyclePhase current = phase;
        if (!current.isAtLeast(required)) {
            throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                    Messages.report(ErrorCode.OMNI_4002)
                            .detected("mod", containerModId)
                            .detected("operation", operation)
                            .detected("current phase", current)
                            .detected("required phase", required + " or later")
                            .detail("This call was made too early in initialisation.")
                            .fix("move it into the entrypoint that runs in " + required)
                            .build());
        }
    }

    /**
     * Requires that content may still be registered.
     *
     * @param operation what was attempted
     * @throws OmniApiMisuseException {@code OMNI-4002} once registration has closed
     */
    public void requireRegistrationOpen(String operation) {
        LifecyclePhase current = phase;
        if (!current.allowsRegistration()) {
            throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                    Messages.report(ErrorCode.OMNI_4002)
                            .detected("mod", containerModId)
                            .detected("operation", operation)
                            .detected("current phase", current)
                            .detail("Content can only be declared while the mod is initialising.")
                            .detail("Registering later would either be ignored or corrupt registry")
                            .detail("ordering, which leaks into network protocols and data packs.")
                            .fix("declare content from onInitialize instead")
                            .build());
        }
    }
}
