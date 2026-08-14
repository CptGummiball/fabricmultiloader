package dev.fabricmultiloader.runtime.loader;

import dev.fabricmultiloader.format.Side;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Everything the runtime needs from Fabric Loader, and nothing else.
 *
 * <p>Two reasons this exists rather than calling {@code FabricLoader.getInstance()} from wherever it
 * is needed.
 *
 * <p>First, it makes the loader API surface <em>countable</em>. The design commits to using only
 * methods that have been stable since loader 0.14.0; with the calls scattered, that claim would be
 * unverifiable and would quietly rot. Here it is one file, and
 * {@link FabricLoaderFacade} is the only class in the entire runtime that imports
 * {@code net.fabricmc}.
 *
 * <p>Second, it makes the bootstrap testable. Payload activation, the exactly-one assertion and the
 * diagnostic report are the parts most worth testing and the parts hardest to reach inside a real
 * game launch. Against a fake facade they run in milliseconds.
 */
public interface LoaderFacade {

    /** Whether a mod with this id is loaded. */
    boolean isModLoaded(String modId);

    /** A loaded mod's version string, as the loader reports it. */
    Optional<String> modVersion(String modId);

    /** Every loaded mod id. */
    Collection<String> loadedModIds();

    /**
     * A path inside a mod's jar, if the entry exists.
     *
     * <p>The only way the runtime reads mod resources. It returns a path inside a loader-managed
     * file system, so no zip is ever opened by us — which removes the Zip Slip class of problem
     * entirely — and it is mod-scoped, so with several universal mods installed there is no question
     * which manifest was found.
     */
    Optional<Path> findPath(String modId, String pathInJar);

    /** The physical side. */
    Side side();

    /** Whether this is a development runtime. */
    boolean isDevelopment();

    /** The game directory. */
    Path gameDir();

    /** The configuration directory. */
    Path configDir();

    /**
     * Publishes a value into the loader's process-wide object share.
     *
     * <p>How third-party mods reach a universal mod's API without a compile-time dependency on the
     * runtime.
     */
    void publish(String key, Object value);

    /** Reads a value from the object share. */
    Optional<Object> lookup(String key);
}
