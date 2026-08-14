package dev.fabricmultiloader.api;

import dev.fabricmultiloader.format.Side;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as one of the mod's entrypoints, so it does not have to be declared twice.
 *
 * <p>The annotation processor collects these at compile time and writes them into the container
 * manifest. Declaring them in the Gradle DSL instead works identically — the annotation exists
 * because an entrypoint that is renamed or moved should not require remembering to update a build
 * file, which is exactly the kind of thing that is discovered at runtime three weeks later.
 *
 * <p>The annotated class must be public, non-abstract, have a public no-argument constructor, and
 * implement the interface matching its phase. All four are compile-time errors reported on the
 * element itself, so they show up in the IDE rather than in a build log.
 *
 * <pre>
 * &#64;UniversalEntrypoint
 * public final class ExampleMod implements UniversalMod { … }
 *
 * &#64;UniversalEntrypoint(Side.CLIENT)
 * public final class ExampleModClient implements UniversalClientMod { … }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface UniversalEntrypoint {

    /**
     * Which side this entrypoint runs on. Omit for both.
     *
     * <p>A {@link Side#CLIENT} entrypoint must implement {@link UniversalClientMod} and a
     * {@link Side#SERVER} one {@link UniversalServerMod}; the processor checks that rather than
     * letting a mismatch fail silently at launch.
     */
    Side[] value() default {};

    /**
     * Set for a {@link UniversalPreLaunch} entrypoint.
     *
     * <p>Separate from {@link #value()} because pre-launch is a phase, not a side: it runs before
     * Minecraft classes are loaded and therefore before either side means anything.
     */
    boolean preLaunch() default false;
}
