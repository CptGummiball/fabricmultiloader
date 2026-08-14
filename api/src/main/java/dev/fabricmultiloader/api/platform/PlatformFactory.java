package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.ImplementedByMod;
import dev.fabricmultiloader.api.ModContext;

/**
 * Creates the {@link Platform} for a payload.
 *
 * <p>Named in the container manifest and instantiated reflectively — the one reflective call on the
 * critical path. The class name comes from the hashed manifest rather than a classpath scan, and
 * the runtime checks before instantiating that it implements this interface and sits inside a
 * package the payload declares ({@code OMNI-2022}, {@code OMNI-2024}), so a tampered manifest
 * cannot name an arbitrary class to construct.
 *
 * <p>Implementations need a public no-argument constructor.
 */
@ImplementedByMod
public interface PlatformFactory {

    /**
     * Creates the platform.
     *
     * @param ctx the mod context, already populated with identity and environment
     * @return the platform; must not be {@code null}
     */
    Platform create(ModContext ctx);
}
