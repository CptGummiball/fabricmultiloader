package dev.fabricmultiloader.runtime.payload;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.runtime.boot.CommonBootstrap;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.context.CapabilityResolver;
import dev.fabricmultiloader.runtime.context.ModContextImpl;
import dev.fabricmultiloader.runtime.context.PreLaunchContextImpl;
import dev.fabricmultiloader.runtime.context.ServiceRegistryImpl;
import dev.fabricmultiloader.runtime.diag.CrashContextImpl;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;

/**
 * Drives one resolved container through its lifecycle.
 *
 * <p>Four steps, one per Fabric phase, each idempotent. Idempotence is not defensive programming
 * here: entrypoint invocation order is a loader detail, several universal mods can be installed at
 * once, and any one of their payload entrypoints reaches every container — so a step genuinely can
 * be requested more than once, and the second request must do nothing rather than initialise the
 * mod twice.
 *
 * <p>The ordering inside each step is fixed and load-bearing:
 *
 * <ul>
 *   <li>the platform is created before any mod code runs, so services are already registered when
 *       common code looks for them;
 *   <li>{@code Platform#onInitialize} runs before the mod's {@code onInitialize}, for the same
 *       reason;
 *   <li>{@code Registries#flush} runs <em>after</em> the mod's {@code onInitialize}, because that is
 *       the first moment at which everything the mod declares has actually been declared. Flushing
 *       earlier would register a subset and leave the rest to fail against a closed registry.
 * </ul>
 */
public final class PayloadActivation {

    private final ContainerRuntime runtime;
    private final PayloadDescriptor payload;
    private final ModLogger log;
    private final ServiceRegistryImpl services;
    private final CapabilityResolver capabilities;
    private final CommonBootstrap common;
    private final ModContextImpl context;
    private final PreLaunchContextImpl preLaunchContext;
    private final CrashContextImpl crash;

    private volatile Platform platform;
    private volatile boolean preLaunched;
    private volatile boolean initialised;
    private volatile boolean sideInitialised;

    /**
     * @param runtime the resolved container
     * @param loader the loader facade
     */
    public PayloadActivation(ContainerRuntime runtime, LoaderFacade loader) {
        if (!runtime.isActive()) {
            throw new IllegalStateException(
                    "container " + runtime.modId() + " has no active payload");
        }
        this.runtime = runtime;
        this.payload = runtime.activePayload();
        this.log = runtime.log();
        this.services = new ServiceRegistryImpl(runtime.modId());
        this.capabilities = new CapabilityResolver(runtime.modId(), payload, log);
        this.common = new CommonBootstrap(
                runtime.manifest().container(), runtime.manifest().entrypoints(), log);
        this.context = new ModContextImpl(
                runtime.manifest().container(), runtime.environment(), loader, runtime.lifecycle(),
                runtime.platformInfo(), services, capabilities, log);
        this.preLaunchContext = new PreLaunchContextImpl(context);
        this.crash = CrashContextImpl.forContainer(
                runtime.manifest(), runtime.environment(), payload);
    }

    /** The mod context handed to every entrypoint. */
    public ModContext context() {
        return context;
    }

    /** The payload's platform, or {@code null} before {@link #preLaunch()} has run. */
    public Platform platform() {
        return platform;
    }

    /** The crash report entries collected for this container. */
    public CrashContextImpl crashContext() {
        return crash;
    }

    /** The mod's entrypoint runner. */
    public CommonBootstrap commonBootstrap() {
        return common;
    }

    /**
     * Creates the platform and runs the pre-launch hooks.
     *
     * <p>Everything here happens before the first Minecraft class is loaded, which is why the
     * platform is constructed at this point at all: a failure now leaves no half-initialised
     * registry and no partially applied mixins behind, and Fabric renders the message in its error
     * dialog.
     *
     * @return {@code true} if this call did the work, {@code false} if it had already been done
     */
    public synchronized boolean preLaunch() {
        if (preLaunched) {
            return false;
        }
        platform = PlatformLoader.create(payload, context);
        context.bind(platform);

        invokePlatformHook("onPreLaunch", new Runnable() {
            @Override
            public void run() {
                platform.onPreLaunch(preLaunchContext);
            }
        });
        // Collected as early as the platform exists, so a crash between here and the main phase is
        // still reported with the payload named.
        invokePlatformHook("installCrashContext", new Runnable() {
            @Override
            public void run() {
                platform.installCrashContext(crash);
            }
        });

        common.runPreLaunch(preLaunchContext);
        runtime.lifecycle().advanceTo(LifecyclePhase.PLATFORM_READY);
        preLaunched = true;
        return true;
    }

    /**
     * Runs the main phase: platform initialisation, the mod's common entrypoints, then the deferred
     * registration flush.
     *
     * @return {@code true} if this call did the work
     */
    public synchronized boolean initialise() {
        if (initialised) {
            return false;
        }
        requirePreLaunched("initialise");

        services.openRegistration();
        try {
            invokePlatformHook("onInitialize", new Runnable() {
                @Override
                public void run() {
                    platform.onInitialize(context);
                }
            });
        } finally {
            // Sealed even when the platform threw: if the launch continues in lenient mode, a
            // half-populated registry must not keep accepting entries from somewhere else.
            services.sealRegistration();
        }

        runtime.lifecycle().advanceTo(LifecyclePhase.COMMON_INIT);
        common.run(EntrypointSet.Phase.COMMON, context);
        flushRegistries();
        initialised = true;
        return true;
    }

    /**
     * Runs the side-specific phase and completes initialisation.
     *
     * @param side the physical side Fabric is invoking for
     * @return {@code true} if this call did the work
     */
    public synchronized boolean initialiseSide(Side side) {
        if (sideInitialised) {
            return false;
        }
        if (side != runtime.environment().side()) {
            // Fabric only invokes the entrypoint matching the distribution, so this means a payload
            // declared the wrong one. Skipping is right: running client initialisation on a
            // dedicated server would load client-only classes that are not there.
            log.warn("{}: the {} entrypoint ran on a {} distribution — ignoring it",
                    runtime.modId(), side.id(), runtime.environment().side().id());
            return false;
        }
        requirePreLaunched("initialiseSide");
        if (!initialised) {
            // Fabric runs 'main' before 'client'/'server', but the framework does not depend on
            // that: the mod must never observe its side hook before its common one.
            initialise();
        }

        final boolean client = side == Side.CLIENT;
        invokePlatformHook(client ? "onInitializeClient" : "onInitializeServer", new Runnable() {
            @Override
            public void run() {
                if (client) {
                    platform.onInitializeClient(context);
                } else {
                    platform.onInitializeServer(context);
                }
            }
        });

        runtime.lifecycle().advanceTo(LifecyclePhase.SIDE_INIT);
        common.run(client ? EntrypointSet.Phase.CLIENT : EntrypointSet.Phase.SERVER, context);
        runtime.lifecycle().advanceTo(LifecyclePhase.RUNNING);
        sideInitialised = true;
        log.info("{} is ready ({} phase, payload {})",
                runtime.modId(), side.id(), payload.id());
        return true;
    }

    /**
     * Deferred registrations are the payload's business, and a payload that registers eagerly does
     * not implement {@link dev.fabricmultiloader.api.registry.Registries#flush()} at all — the
     * default is empty. Calling it unconditionally keeps the ordering guarantee in one place instead
     * of leaving each adapter to remember it.
     */
    private void flushRegistries() {
        invokePlatformHook("registries().flush", new Runnable() {
            @Override
            public void run() {
                platform.registries().flush();
            }
        });
    }

    private void requirePreLaunched(String operation) {
        if (preLaunched) {
            return;
        }
        // Reachable when a payload declares only some of the four entrypoints, or when the loader
        // invokes them in an order the dependency graph did not imply. Recovering is correct and
        // cheap; failing would turn a metadata oversight into an unlaunchable game.
        log.debug("{}: {} ran before pre-launch — creating the platform now",
                runtime.modId(), operation);
        preLaunch();
    }

    /** Wraps a platform hook so a failure names the payload, the hook and the mod. */
    private void invokePlatformHook(String hook, Runnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            Throwable cause = thrown instanceof ExceptionInInitializerError
                    && thrown.getCause() != null ? thrown.getCause() : thrown;
            if (cause instanceof OmniException) {
                throw (OmniException) cause;
            }
            throw new OmniException(ErrorCode.OMNI_2040, Messages.report(ErrorCode.OMNI_2040)
                    .detected("mod", runtime.modId())
                    .detected("payload", payload.id())
                    .detected("hook", "Platform#" + hook)
                    .detected("implementation", platform == null
                            ? "(not created)" : platform.getClass().getName())
                    .detected("cause", cause.toString())
                    .detail("The payload's adapter failed. This is a bug in the mod's version")
                    .detail("layer, not in FabricMultiLoader — the stack trace follows.")
                    .fix("report this to the mod author, quoting the payload id and this log")
                    .build(), cause);
        }
    }
}
