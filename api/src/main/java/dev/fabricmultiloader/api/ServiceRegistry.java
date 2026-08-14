package dev.fabricmultiloader.api;

import java.util.Optional;
import java.util.Set;

/**
 * The typed escape hatch: mod-defined interfaces implemented per Minecraft version.
 *
 * <p>FabricMultiLoader abstracts what can be abstracted stably. World generation, rendering, codecs
 * and datafixers cannot be, and pretending otherwise would produce a leaky abstraction that breaks
 * on the next Minecraft release. Instead the mod author declares an interface in common code with
 * no Minecraft types in its signature, implements it in each version module, and calls it from
 * anywhere:
 *
 * <pre>
 * // common
 * public interface OreGenService { void installRubyOre(int veinSize, int perChunk); }
 *
 * // versions/mc-1.21.4
 * ctx.services().register(OreGenService.class, new OreGenService1214());
 *
 * // common, later
 * ctx.services().get(OreGenService.class).installRubyOre(6, 12);
 * </pre>
 *
 * <p>A service locator rather than a dependency injection framework, deliberately: no reflection
 * scan, no startup cost, no extra dependency inside every universal jar, and a stack trace that
 * points at the actual caller.
 */
@ImplementedByFramework
public interface ServiceRegistry {

    /**
     * Returns a registered service.
     *
     * @param type the service interface
     * @param <T> the service type
     * @return the implementation
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4010} if the
     *     service was never registered — a mod bug, not a user problem, and the message says so
     */
    <T> T get(Class<T> type);

    /**
     * Returns a service if the active payload provides one.
     *
     * <p>The right choice for anything a version may legitimately not implement; {@link #get} is
     * for services every payload must supply.
     */
    <T> Optional<T> find(Class<T> type);

    /** Whether a service is registered. */
    boolean has(Class<?> type);

    /**
     * Registers a service.
     *
     * <p>Only legal during {@code Platform#onInitialize}, which runs before mod code. Registering
     * later would mean common code could observe the registry in two different states depending on
     * call order, so it raises {@code OMNI-4002} instead.
     *
     * @param type the service interface
     * @param implementation the version-specific implementation
     * @param <T> the service type
     */
    <T> void register(Class<T> type, T implementation);

    /** Every registered service interface, in registration order. */
    Set<Class<?>> registered();
}
