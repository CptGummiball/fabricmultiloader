package dev.fabricmultiloader.api;

import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.version.SemVer;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Everything a mod needs, handed to every entrypoint.
 *
 * <p>Passed in rather than reachable from a static holder, deliberately. Common code that takes its
 * context as a parameter is testable without Minecraft — {@code FakeModContext} records every
 * registration, channel, command and subscription, so the bulk of a mod's logic can be verified in
 * milliseconds. That is a by-product of the no-Minecraft-types rule, and in practice the single
 * biggest day-to-day benefit of this architecture.
 *
 * <p>A framework-implemented interface, so it may gain accessors in a minor release.
 */
@ImplementedByFramework
public interface ModContext {

    // ---- identity ---------------------------------------------------------

    /** The mod id, as players and other mods see it. */
    String modId();

    /** The mod version. */
    SemVer modVersion();

    /** The human-readable mod name. */
    String displayName();

    // ---- environment ------------------------------------------------------

    /** What the mod is running on, including which payload is active. */
    PlatformInfo platform();

    /** The physical side. Not the logical one: a single-player game reports {@code CLIENT}. */
    Side side();

    /** Whether this is a development runtime. */
    boolean isDevelopment();

    /** The current lifecycle phase. */
    LifecyclePhase phase();

    // ---- infrastructure ---------------------------------------------------

    /** A logger named after the mod. */
    ModLogger log();

    /** The game directory. */
    Path gameDir();

    /** The shared {@code config} directory. */
    Path configDir();

    /** The mod's own directory under {@code config}, created on first access. */
    Path modConfigDir();

    // ---- subsystems -------------------------------------------------------

    /** Declares items, blocks, sounds and item groups. */
    Registries registries();

    /** Registers network channels. */
    Networking networking();

    /** Registers commands. */
    Commands commands();

    /** Subscribes to game events. */
    Events events();

    /** Mod-defined services implemented per Minecraft version. */
    ServiceRegistry services();

    // ---- capabilities and other mods --------------------------------------

    /**
     * A capability, if the active payload provides it.
     *
     * @param capability the capability
     * @param <T> the capability interface
     * @return the implementation, or empty
     */
    <T> Optional<T> capability(Capability<T> capability);

    /** Whether the active payload provides a capability. */
    boolean has(Capability<?> capability);

    /** Whether another mod is loaded. */
    boolean isModLoaded(String modId);

    /** Another mod's version, if it is loaded. */
    Optional<SemVer> modVersionOf(String modId);
}
