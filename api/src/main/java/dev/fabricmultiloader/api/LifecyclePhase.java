package dev.fabricmultiloader.api;

/**
 * How far along the mod's initialisation is.
 *
 * <p>Exposed because several API calls are only legal in a particular phase, and a clear
 * {@code OMNI-4002} naming the current and required phase is far more useful than whatever
 * Minecraft throws when a registry is written too late. Phases only ever move forward.
 */
public enum LifecyclePhase {

    /** The container manifest has been read; no payload has been activated yet. */
    DISCOVERED,

    /** Exactly one payload has been selected and verified. */
    RESOLVED,

    /** The version-specific platform exists. Services may be registered. */
    PLATFORM_READY,

    /** {@link UniversalMod#onInitialize} has run; deferred registrations are being flushed. */
    COMMON_INIT,

    /** The side-specific entrypoint has run. */
    SIDE_INIT,

    /** Initialisation is complete and the game is running. Registries are closed. */
    RUNNING,

    /** Initialisation failed; the mod is inactive. Only reachable in non-strict mode. */
    FAILED;

    /** Whether this phase is at or beyond the given one. */
    public boolean isAtLeast(LifecyclePhase other) {
        return this != FAILED && other != FAILED && ordinal() >= other.ordinal();
    }

    /** Whether content may still be registered. */
    public boolean allowsRegistration() {
        return this == PLATFORM_READY || this == COMMON_INIT;
    }
}
