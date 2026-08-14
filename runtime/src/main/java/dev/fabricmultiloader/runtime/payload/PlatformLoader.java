package dev.fabricmultiloader.runtime.payload;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformFactory;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.lang.reflect.Constructor;

/**
 * Turns the {@code platformFactory} class name from the manifest into a live {@link Platform}.
 *
 * <p>This is the one reflective call on the critical path, and everything about it is arranged so
 * that it cannot become an injection point. The class name is not discovered by scanning the
 * classpath and not read from a mod-supplied config; it comes out of the container manifest, which
 * the build hashes and the runtime has already checked belongs to the mod carrying it. Before the
 * class is loaded at all it must sit inside a package the payload itself declares, so a manifest
 * edited by hand cannot name {@code java.lang.Runtime} or a class belonging to a different mod.
 *
 * <p>The order of the three checks is load-bearing:
 *
 * <ol>
 *   <li>the package containment check runs <em>before</em> {@code Class.forName}, so an unapproved
 *       class is never even resolved;
 *   <li>{@code Class.forName} passes {@code initialize = false}, so the class's static initialiser
 *       has not run yet when the type is checked;
 *   <li>only then is the factory constructed, which is the first moment any mod-supplied code in
 *       this path executes.
 * </ol>
 *
 * <p>The class loader is deliberately this class's own. That is {@code KnotClassLoader}, which also
 * defines every payload class — no thread context class loader, which is a well-known way to load
 * the same type twice, and no loader of our own, which would bypass Knot's transformer chain and
 * silently disable the payload's mixins (invariant I1).
 */
public final class PlatformLoader {

    /**
     * Instantiates the payload's platform.
     *
     * @param payload the active payload
     * @param ctx the context handed to the factory
     * @return the platform, never {@code null}
     * @throws OmniException {@code OMNI-2020} when the class is absent, {@code OMNI-2021} when the
     *     factory throws, {@code OMNI-2022} when it is not a {@link PlatformFactory},
     *     {@code OMNI-2023} when it returns {@code null}, {@code OMNI-2024} when the class name
     *     lies outside the payload's declared packages
     */
    public static Platform create(PayloadDescriptor payload, ModContext ctx) {
        String fqcn = payload.platformFactory();
        requireInsidePayload(payload, fqcn);

        Class<?> raw;
        try {
            raw = Class.forName(fqcn, false, PlatformLoader.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new OmniException(ErrorCode.OMNI_2020, Messages.report(ErrorCode.OMNI_2020)
                    .detected("payload", payload.id())
                    .detected("class", fqcn)
                    .detail("The payload's platform factory is named in the manifest but is not")
                    .detail("present in the jar, so the mod has nothing to run on this version.")
                    .fix("re-download the mod from its official source")
                    .fix("if you built it yourself, run ./gradlew validateUniversalJar")
                    .build(), e);
        } catch (LinkageError e) {
            // A class file the JVM refuses outright — most plausibly bytecode compiled for a newer
            // Java than the one running, which means the matrix and depends.java have drifted apart.
            throw new OmniException(ErrorCode.OMNI_2020, Messages.report(ErrorCode.OMNI_2020)
                    .detected("payload", payload.id())
                    .detected("class", fqcn)
                    .detected("problem", e.toString())
                    .detected("payload bytecode", "class file " + payload.classfileMajor())
                    .detected("running Java", System.getProperty("java.specification.version", "?"))
                    .detail("The platform factory exists but could not be linked.")
                    .fix("check that the payload's requires.java matches its classfileMajor")
                    .build(), e);
        }

        if (!PlatformFactory.class.isAssignableFrom(raw)) {
            throw new OmniException(ErrorCode.OMNI_2022, Messages.report(ErrorCode.OMNI_2022)
                    .detected("payload", payload.id())
                    .detected("class", fqcn)
                    .detected("expected", PlatformFactory.class.getName())
                    .detail("The class named as the platform factory does not implement")
                    .detail("PlatformFactory, so the runtime has no way to obtain a Platform.")
                    .fix("make " + simpleName(fqcn) + " implement PlatformFactory")
                    .fix("or point payload.platformFactory at the class that does")
                    .build());
        }

        PlatformFactory factory = instantiate(payload, raw, fqcn);
        Platform platform = invoke(payload, factory, ctx, fqcn);
        if (platform == null) {
            throw new OmniException(ErrorCode.OMNI_2023, Messages.report(ErrorCode.OMNI_2023)
                    .detected("payload", payload.id())
                    .detected("factory", fqcn)
                    .detail("PlatformFactory#create returned null. Every payload must supply a")
                    .detail("platform; there is no meaningful way to continue without one.")
                    .fix("return a Platform instance from " + simpleName(fqcn) + "#create")
                    .build());
        }
        return platform;
    }

    /**
     * A manifest is data, and data from a jar a player downloaded is not trusted to name a class.
     * Restricting the factory to the payload's own packages means the worst a tampered manifest can
     * do is fail to find a class.
     */
    private static void requireInsidePayload(PayloadDescriptor payload, String fqcn) {
        if (payload.isPlatformFactoryInsidePackages()) {
            return;
        }
        throw new OmniException(ErrorCode.OMNI_2024, Messages.report(ErrorCode.OMNI_2024)
                .detected("payload", payload.id())
                .detected("class", fqcn)
                .detected("declared packages", payload.packages())
                .detail("The platform factory must live inside one of the packages the payload")
                .detail("declares. A manifest naming a class outside them is either mis-generated")
                .detail("or was edited after the jar was built.")
                .fix("re-download the mod from its official source")
                .fix("if you built it yourself, add the package to the payload's packages list")
                .build());
    }

    private static PlatformFactory instantiate(
            PayloadDescriptor payload, Class<?> raw, String fqcn) {
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor();
            return (PlatformFactory) constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new OmniException(ErrorCode.OMNI_2021, Messages.report(ErrorCode.OMNI_2021)
                    .detected("payload", payload.id())
                    .detected("factory", fqcn)
                    .detail("The platform factory has no public no-argument constructor.")
                    .fix("give " + simpleName(fqcn) + " a public no-argument constructor")
                    .build(), e);
        } catch (Throwable e) {
            // Covers IllegalAccess, InvocationTarget and any error a static initialiser raises.
            throw factoryFailed(payload, fqcn, "constructing", unwrap(e));
        }
    }

    private static Platform invoke(
            PayloadDescriptor payload, PlatformFactory factory, ModContext ctx, String fqcn) {
        try {
            return factory.create(ctx);
        } catch (Throwable e) {
            throw factoryFailed(payload, fqcn, "calling", unwrap(e));
        }
    }

    private static OmniException factoryFailed(
            PayloadDescriptor payload, String fqcn, String what, Throwable cause) {
        if (cause instanceof OmniException) {
            // A payload that reports a framework-shaped problem of its own says it better than a
            // generic wrapper would, so it is passed through unchanged.
            return (OmniException) cause;
        }
        return new OmniException(ErrorCode.OMNI_2021, Messages.report(ErrorCode.OMNI_2021)
                .detected("payload", payload.id())
                .detected("factory", fqcn)
                .detected("failed while", what + " the factory")
                .detected("cause", cause.toString())
                .detail("The payload's own initialisation failed. This is a bug in the mod, not")
                .detail("in FabricMultiLoader — the full stack trace follows this report.")
                .fix("report this to the mod author with the log attached")
                .build(), cause);
    }

    /** Reflection wraps the interesting exception one level deep; the wrapper helps nobody. */
    private static Throwable unwrap(Throwable thrown) {
        if (thrown instanceof java.lang.reflect.InvocationTargetException
                && thrown.getCause() != null) {
            return thrown.getCause();
        }
        if (thrown instanceof ExceptionInInitializerError && thrown.getCause() != null) {
            return thrown.getCause();
        }
        return thrown;
    }

    private static String simpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? fqcn : fqcn.substring(dot + 1);
    }

    private PlatformLoader() {
        throw new AssertionError("no instances");
    }
}
