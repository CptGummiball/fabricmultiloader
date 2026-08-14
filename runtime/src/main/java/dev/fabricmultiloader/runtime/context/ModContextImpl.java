package dev.fabricmultiloader.runtime.context;

import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.ServiceRegistry;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import dev.fabricmultiloader.format.manifest.ContainerInfo;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.runtime.boot.LifecycleStateMachine;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The context every entrypoint receives.
 *
 * <p>Identity, environment and infrastructure are answered here; the four subsystems are the
 * payload's and are delegated to its {@link Platform}. The split is the whole architecture in
 * miniature — what can be answered without naming a Minecraft type is answered once for every
 * version, and what cannot is handed to the one adapter that is allowed to name them.
 *
 * <p>The context exists slightly before the platform does, because {@code PlatformFactory#create}
 * takes one. A subsystem call in that window is a mod bug with an unhelpful natural failure mode —
 * a {@code NullPointerException} inside framework code — so it is turned into {@code OMNI-4002}
 * naming the call and the phase instead.
 */
public final class ModContextImpl implements ModContext {

    private final ContainerInfo container;
    private final Environment environment;
    private final LoaderFacade loader;
    private final LifecycleStateMachine lifecycle;
    private final PlatformInfo platformInfo;
    private final ServiceRegistryImpl services;
    private final CapabilityResolver capabilities;
    private final ModLogger log;

    private volatile Platform platform;
    private volatile Path modConfigDir;

    /**
     * @param container the container's identity from the manifest
     * @param environment the detected environment
     * @param loader the loader facade
     * @param lifecycle the container's lifecycle tracker
     * @param platformInfo what the mod is running on
     * @param services the mod's service registry
     * @param capabilities the capability resolver for the active payload
     * @param log the mod's logger
     */
    public ModContextImpl(ContainerInfo container, Environment environment, LoaderFacade loader,
            LifecycleStateMachine lifecycle, PlatformInfo platformInfo,
            ServiceRegistryImpl services, CapabilityResolver capabilities, ModLogger log) {
        this.container = container;
        this.environment = environment;
        this.loader = loader;
        this.lifecycle = lifecycle;
        this.platformInfo = platformInfo;
        this.services = services;
        this.capabilities = capabilities;
        this.log = log;
    }

    /** Supplies the platform once the factory has produced it. */
    public void bind(Platform platform) {
        this.platform = platform;
        this.capabilities.bind(platform);
    }

    /** The platform, or {@code null} while the factory is still running. */
    public Platform platformOrNull() {
        return platform;
    }

    // ---- identity ---------------------------------------------------------

    @Override
    public String modId() {
        return container.modId();
    }

    @Override
    public SemVer modVersion() {
        return container.modVersion();
    }

    @Override
    public String displayName() {
        String name = container.displayName();
        return name == null || name.isEmpty() ? container.modId() : name;
    }

    // ---- environment ------------------------------------------------------

    @Override
    public PlatformInfo platform() {
        return platformInfo;
    }

    @Override
    public Side side() {
        return environment.side();
    }

    @Override
    public boolean isDevelopment() {
        return environment.isDevelopment();
    }

    @Override
    public LifecyclePhase phase() {
        return lifecycle.phase();
    }

    // ---- infrastructure ---------------------------------------------------

    @Override
    public ModLogger log() {
        return log;
    }

    @Override
    public Path gameDir() {
        return loader.gameDir();
    }

    @Override
    public Path configDir() {
        return loader.configDir();
    }

    @Override
    public Path modConfigDir() {
        Path existing = modConfigDir;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (modConfigDir == null) {
                modConfigDir = createModConfigDir();
            }
            return modConfigDir;
        }
    }

    /**
     * Creating the directory can fail on a read-only or full disk. The path is returned anyway: the
     * mod's own file operation will then fail with an error naming the file it wanted, which is far
     * more useful than the framework aborting a launch over a directory nobody may ever write to.
     */
    private Path createModConfigDir() {
        Path dir = loader.configDir().resolve(container.modId());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("could not create the config directory {} ({}) — {} will have to handle a "
                    + "missing directory itself", dir, e.toString(), container.modId());
        }
        return dir;
    }

    // ---- subsystems -------------------------------------------------------

    @Override
    public Registries registries() {
        return requirePlatform("registries()").registries();
    }

    @Override
    public Networking networking() {
        return requirePlatform("networking()").networking();
    }

    @Override
    public Commands commands() {
        return requirePlatform("commands()").commands();
    }

    @Override
    public Events events() {
        return requirePlatform("events()").events();
    }

    @Override
    public ServiceRegistry services() {
        return services;
    }

    // ---- capabilities and other mods --------------------------------------

    @Override
    public <T> Optional<T> capability(Capability<T> capability) {
        return capabilities.resolve(capability);
    }

    @Override
    public boolean has(Capability<?> capability) {
        return capabilities.declares(capability) && capabilities.resolve(capability).isPresent();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && loader.isModLoaded(modId);
    }

    @Override
    public Optional<SemVer> modVersionOf(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        Optional<String> version = loader.modVersion(modId);
        if (!version.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(SemVer.parseLenient(version.get()));
    }

    @Override
    public String toString() {
        return container.modId() + " " + container.modVersion()
                + " on " + platformInfo + " (" + lifecycle.phase() + ")";
    }

    private Platform requirePlatform(String operation) {
        Platform current = platform;
        if (current != null) {
            return current;
        }
        throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                Messages.report(ErrorCode.OMNI_4002)
                        .detected("mod", container.modId())
                        .detected("operation", "ModContext#" + operation)
                        .detected("current phase", lifecycle.phase())
                        .detail("The subsystems come from the payload's Platform, which does not")
                        .detail("exist yet. This call happened inside PlatformFactory#create or in a")
                        .detail("pre-launch hook, both of which run before the platform is ready.")
                        .fix("move the call into Platform#onInitialize or the mod's onInitialize")
                        .build());
    }
}
