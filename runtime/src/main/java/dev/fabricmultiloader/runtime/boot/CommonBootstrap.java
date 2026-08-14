package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.UniversalClientMod;
import dev.fabricmultiloader.api.UniversalMod;
import dev.fabricmultiloader.api.UniversalPreLaunch;
import dev.fabricmultiloader.api.UniversalServerMod;
import dev.fabricmultiloader.api.platform.PreLaunchContext;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.ContainerInfo;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.format.manifest.Identifiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the mod's own entrypoint classes.
 *
 * <p>Fabric never sees these classes. It sees {@link dev.fabricmultiloader.runtime.entrypoint
 * PayloadMain} and its siblings, which come here, and only then does the mod's code run. The
 * indirection buys three things that a direct Fabric entrypoint declaration cannot: the list lives
 * in the container and therefore serves every payload at once, the runtime controls the ordering
 * around it (the platform is created first, deferred registrations are flushed afterwards), and a
 * class named in the manifest but absent from the jar produces a sentence rather than a
 * {@code ClassNotFoundException} out of loader internals.
 *
 * <p>Instances are created once and reused across phases. A class implementing both
 * {@link UniversalMod} and {@link UniversalClientMod} is the normal way to write a small mod, and
 * state it sets up in {@code onInitialize} must still be there in {@code onInitializeClient} —
 * which would not be true if each phase constructed its own copy.
 */
public final class CommonBootstrap {

    private final ContainerInfo container;
    private final EntrypointSet entrypoints;
    private final ModLogger log;
    private final Map<String, Object> instances = new LinkedHashMap<String, Object>();
    private final Set<EntrypointSet.Phase> completed =
            new LinkedHashSet<EntrypointSet.Phase>();

    /**
     * @param container the container's identity, whose {@code commonPackages} bound what may be
     *     loaded
     * @param entrypoints the mod's entrypoint classes by phase
     * @param log the mod's logger
     */
    public CommonBootstrap(ContainerInfo container, EntrypointSet entrypoints, ModLogger log) {
        this.container = container;
        this.entrypoints = entrypoints;
        this.log = log;
    }

    /** Whether a phase has already run. */
    public boolean hasRun(EntrypointSet.Phase phase) {
        synchronized (instances) {
            return completed.contains(phase);
        }
    }

    /** Every entrypoint instance created so far, in creation order. */
    public List<Object> instances() {
        synchronized (instances) {
            return new ArrayList<Object>(instances.values());
        }
    }

    /**
     * Runs the {@code preLaunch} entrypoints.
     *
     * @param ctx the pre-launch context
     */
    public void runPreLaunch(PreLaunchContext ctx) {
        for (Object instance : resolve(EntrypointSet.Phase.PRE_LAUNCH, UniversalPreLaunch.class)) {
            invoke(EntrypointSet.Phase.PRE_LAUNCH, instance,
                    ((UniversalPreLaunch) instance), ctx);
        }
        markComplete(EntrypointSet.Phase.PRE_LAUNCH);
    }

    /**
     * Runs the entrypoints of a phase.
     *
     * @param phase {@code COMMON}, {@code CLIENT} or {@code SERVER}
     * @param ctx the mod context
     * @throws IllegalArgumentException if called with {@code PRE_LAUNCH}, which takes a different
     *     context type
     */
    public void run(EntrypointSet.Phase phase, ModContext ctx) {
        Class<?> required = interfaceFor(phase);
        for (Object instance : resolve(phase, required)) {
            invokeMain(phase, instance, ctx);
        }
        markComplete(phase);
    }

    private void invokeMain(EntrypointSet.Phase phase, Object instance, ModContext ctx) {
        try {
            if (phase == EntrypointSet.Phase.COMMON) {
                ((UniversalMod) instance).onInitialize(ctx);
            } else if (phase == EntrypointSet.Phase.CLIENT) {
                ((UniversalClientMod) instance).onInitializeClient(ctx);
            } else {
                ((UniversalServerMod) instance).onInitializeServer(ctx);
            }
        } catch (Throwable e) {
            throw entrypointFailed(phase, instance.getClass().getName(), "running", e);
        }
    }

    private void invoke(EntrypointSet.Phase phase, Object instance,
            UniversalPreLaunch hook, PreLaunchContext ctx) {
        try {
            hook.onPreLaunch(ctx);
        } catch (Throwable e) {
            throw entrypointFailed(phase, instance.getClass().getName(), "running", e);
        }
    }

    /**
     * Loads and instantiates the classes of a phase, reusing an instance created for an earlier one.
     */
    private List<Object> resolve(EntrypointSet.Phase phase, Class<?> required) {
        List<String> classNames = entrypoints.forPhase(phase);
        List<Object> resolved = new ArrayList<Object>(classNames.size());
        for (String className : classNames) {
            synchronized (instances) {
                Object existing = instances.get(className);
                if (existing != null) {
                    requireImplements(phase, className, existing.getClass(), required);
                    resolved.add(existing);
                    continue;
                }
                Object created = create(phase, className, required);
                instances.put(className, created);
                resolved.add(created);
            }
        }
        return resolved;
    }

    private Object create(EntrypointSet.Phase phase, String className, Class<?> required) {
        requireInsideCommonPackages(phase, className);

        Class<?> raw;
        try {
            // The same class loader that defines the container's classes — the entrypoint lives in
            // the container, so this is where it is, and no other lookup is correct (invariant I1).
            raw = Class.forName(className, false, CommonBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new OmniException(ErrorCode.OMNI_2030, Messages.report(ErrorCode.OMNI_2030)
                    .detected("mod", container.modId())
                    .detected("phase", phase.id())
                    .detected("class", className)
                    .detail("The manifest names this class as an entrypoint, but it is not in the")
                    .detail("jar. Either the build dropped it or the manifest is out of date.")
                    .fix("re-download the mod from its official source")
                    .fix("if you built it yourself, run ./gradlew validateUniversalJar")
                    .build(), e);
        } catch (LinkageError e) {
            throw new OmniException(ErrorCode.OMNI_2030, Messages.report(ErrorCode.OMNI_2030)
                    .detected("mod", container.modId())
                    .detected("phase", phase.id())
                    .detected("class", className)
                    .detected("problem", e.toString())
                    .detected("container bytecode", "class file "
                            + (container.baselineJavaMajor() + 44) + " or lower")
                    .detected("running Java", System.getProperty("java.specification.version", "?"))
                    .detail("The entrypoint class exists but could not be linked. The most common")
                    .detail("cause is common code compiled for a newer Java than the oldest version")
                    .detail("in the matrix supports.")
                    .fix("check container.baselineJavaMajor against the matrix")
                    .build(), e);
        }

        requireImplements(phase, className, raw, required);

        try {
            return raw.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new OmniException(ErrorCode.OMNI_2031, Messages.report(ErrorCode.OMNI_2031)
                    .detected("mod", container.modId())
                    .detected("phase", phase.id())
                    .detected("class", className)
                    .detail("The entrypoint class has no public no-argument constructor.")
                    .fix("give it one, or make the class non-abstract and public")
                    .build(), e);
        } catch (Throwable e) {
            throw entrypointFailed(phase, className, "constructing", e);
        }
    }

    /**
     * An entrypoint class name comes out of the manifest, so it is checked against the container's
     * declared packages before it is loaded — the same rule as for the platform factory, for the
     * same reason: a jar edited after the fact must not be able to name an arbitrary class.
     */
    private void requireInsideCommonPackages(EntrypointSet.Phase phase, String className) {
        if (Identifiers.isInsideAnyPackage(className, container.commonPackages())) {
            return;
        }
        throw new OmniException(ErrorCode.OMNI_2032, Messages.report(ErrorCode.OMNI_2032)
                .detected("mod", container.modId())
                .detected("phase", phase.id())
                .detected("class", className)
                .detected("declared common packages", container.commonPackages())
                .detail("Entrypoints must live inside the packages the container declares. A")
                .detail("manifest naming a class outside them is either mis-generated or was")
                .detail("edited after the jar was built.")
                .fix("re-download the mod from its official source")
                .fix("if you built it yourself, add the package to commonPackages")
                .build());
    }

    private void requireImplements(
            EntrypointSet.Phase phase, String className, Class<?> raw, Class<?> required) {
        if (required.isAssignableFrom(raw)) {
            return;
        }
        throw new OmniException(ErrorCode.OMNI_2033, Messages.report(ErrorCode.OMNI_2033)
                .detected("mod", container.modId())
                .detected("phase", phase.id())
                .detected("class", className)
                .detected("must implement", required.getName())
                .detail("The class is declared as an entrypoint for this phase but does not")
                .detail("implement the interface the phase runs. Nothing would be called.")
                .fix("implement " + required.getSimpleName() + ", or declare the class for the "
                        + "phase matching the interface it does implement")
                .build());
    }

    private OmniException entrypointFailed(
            EntrypointSet.Phase phase, String className, String what, Throwable thrown) {
        Throwable cause = unwrap(thrown);
        if (cause instanceof OmniException) {
            return (OmniException) cause;
        }
        return new OmniException(ErrorCode.OMNI_2031, Messages.report(ErrorCode.OMNI_2031)
                .detected("mod", container.modId())
                .detected("phase", phase.id())
                .detected("class", className)
                .detected("failed while", what + " the entrypoint")
                .detected("cause", cause.toString())
                .detail("The mod's own initialisation failed. This is a bug in the mod, not in")
                .detail("FabricMultiLoader — the full stack trace follows this report.")
                .fix("report this to the mod author with the log attached")
                .build(), cause);
    }

    private void markComplete(EntrypointSet.Phase phase) {
        synchronized (instances) {
            completed.add(phase);
        }
        int count = entrypoints.forPhase(phase).size();
        if (count > 0 && log.isDebugEnabled()) {
            log.debug("{}: ran {} {} entrypoint(s)", container.modId(), count, phase.id());
        }
    }

    private static Class<?> interfaceFor(EntrypointSet.Phase phase) {
        if (phase == EntrypointSet.Phase.COMMON) {
            return UniversalMod.class;
        }
        if (phase == EntrypointSet.Phase.CLIENT) {
            return UniversalClientMod.class;
        }
        if (phase == EntrypointSet.Phase.SERVER) {
            return UniversalServerMod.class;
        }
        throw new IllegalArgumentException(
                "phase " + phase + " takes a PreLaunchContext; use runPreLaunch instead");
    }

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
}
