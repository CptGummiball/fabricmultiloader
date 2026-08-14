package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.ImplementedByMod;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.registry.Registries;
import java.util.Optional;

/**
 * The version-specific half of a mod: one implementation per payload.
 *
 * <p>This is where Minecraft types are allowed and expected. The platform supplies the four
 * subsystem implementations, registers services, and answers capability queries — everything the
 * common half asks for without being able to name it.
 *
 * <p>Adapters stay small in practice. In the reference example mod each payload is 18–22 classes
 * against 142 shared ones, because the handle-and-specification design keeps the version-specific
 * part to translation rather than logic. Extend {@link AbstractPlatform} rather than implementing
 * this directly; the runtime supplies working {@code Commands} and {@code Events} implementations
 * for the versions where those APIs are stable, and a payload overrides them only where they are
 * not.
 *
 * <p>Ordering is fixed by the runtime and matters: {@link #onInitialize} runs <em>before</em> mod
 * code so services are available, and deferred registrations are flushed <em>after</em> it so all
 * declared content has been collected.
 */
@ImplementedByMod
public interface Platform {

    /** What this payload is running on. */
    PlatformInfo info();

    /** Declares items, blocks, sounds and item groups. */
    Registries registries();

    /** Registers network channels. */
    Networking networking();

    /** Registers commands. */
    Commands commands();

    /** Subscribes to game events. */
    Events events();

    /** Runs before Minecraft classes load. Must not touch any of them. */
    default void onPreLaunch(PreLaunchContext ctx) {
    }

    /** Runs before the mod's common entrypoint. The only place services may be registered. */
    default void onInitialize(ModContext ctx) {
    }

    /** Runs before the mod's client entrypoint, on a client distribution only. */
    default void onInitializeClient(ModContext ctx) {
    }

    /** Runs before the mod's server entrypoint, on a dedicated server only. */
    default void onInitializeServer(ModContext ctx) {
    }

    /**
     * Resolves a capability.
     *
     * <p>A payload must return a value for exactly the capabilities it declares in the matrix; the
     * validator checks the two agree ({@code OMNI-1130}), because a capability declared but not
     * implemented turns into an empty {@code Optional} that common code silently skips.
     *
     * @param capability the requested capability
     * @param <T> the capability interface
     * @return the implementation, or empty
     */
    default <T> Optional<T> capability(Capability<T> capability) {
        return Optional.empty();
    }

    /** Adds payload-specific lines to crash reports. */
    default void installCrashContext(CrashContext ctx) {
    }
}
