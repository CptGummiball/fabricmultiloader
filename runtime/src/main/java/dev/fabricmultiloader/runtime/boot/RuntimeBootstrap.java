package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.FabricMultiLoader;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.json.JsonLimits;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.ManifestReader;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.runtime.RuntimeInfo;
import dev.fabricmultiloader.runtime.env.EnvironmentDetector;
import dev.fabricmultiloader.runtime.loader.FabricLoaderFacade;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import dev.fabricmultiloader.runtime.log.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds every universal mod in the process and resolves each one.
 *
 * <p>Initialised lazily on first use and shared by every entrypoint, because entrypoint ordering is
 * a loader detail: whichever of the container's or a payload's hook runs first should get the same
 * initialised state, and the second should be a no-op.
 *
 * <p>Discovery is a scan of the loaded mods for a container manifest — about three milliseconds with
 * three hundred mods installed, one file lookup each. Reading is mod-scoped through
 * {@code ModContainer#findPath}, never through the classpath, so with several universal mods
 * installed there is no ambiguity about which manifest belongs to which mod.
 */
public final class RuntimeBootstrap {

    private static volatile RuntimeBootstrap instance;

    private final LoaderFacade loader;
    private final Environment environment;
    private final RuntimeRegistry registry;
    private final ModLogger log;

    private RuntimeBootstrap(LoaderFacade loader) {
        this.loader = loader;
        this.log = Log.framework();
        this.environment = EnvironmentDetector.detect(loader);
        this.registry = new RuntimeRegistry();
        FabricMultiLoader.install(registry);
    }

    /**
     * The shared instance, created on first call.
     *
     * @return the bootstrap
     */
    public static RuntimeBootstrap get() {
        RuntimeBootstrap local = instance;
        if (local == null) {
            synchronized (RuntimeBootstrap.class) {
                local = instance;
                if (local == null) {
                    local = new RuntimeBootstrap(new FabricLoaderFacade());
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Creates an instance against a supplied loader facade, for tests.
     *
     * @param loader the facade
     * @return a fresh bootstrap, not the shared instance
     */
    public static RuntimeBootstrap forTesting(LoaderFacade loader) {
        return new RuntimeBootstrap(loader);
    }

    /** Resets the shared instance. Tests only. */
    static void reset() {
        synchronized (RuntimeBootstrap.class) {
            instance = null;
            FabricMultiLoader.install(null);
        }
    }

    /** The detected environment. */
    public Environment environment() {
        return environment;
    }

    /** The container registry. */
    public RuntimeRegistry registry() {
        return registry;
    }

    /** The framework logger. */
    public ModLogger log() {
        return log;
    }

    /**
     * Discovers, registers and resolves a container.
     *
     * <p>Idempotent: called from the container's own pre-launch hook and again from its payload's,
     * and only the first call does the work.
     *
     * @param containerModId the mod id of the container
     * @return the container runtime
     * @throws OmniException {@code OMNI-2001} if the manifest is missing or unreadable
     */
    public ContainerRuntime resolveContainer(String containerModId) {
        ContainerRuntime existing = registry.get(containerModId);
        if (existing != null) {
            return existing;
        }
        ContainerManifest manifest = readManifest(containerModId);
        requireMatchingModId(containerModId, manifest);
        requireSupportedRuntime(containerModId, manifest);

        ContainerRuntime runtime = registry.register(
                new ContainerRuntime(manifest, environment, loader));
        if (runtime.resolution() == null) {
            runtime.resolve();
        }
        return runtime;
    }

    /**
     * Every loaded mod that carries a container manifest.
     *
     * <p>Used for diagnostics and by tooling; the normal path resolves a specific container from its
     * own entrypoint rather than scanning.
     */
    public java.util.List<String> discoverContainers() {
        java.util.List<String> found = new java.util.ArrayList<String>();
        for (String modId : loader.loadedModIds()) {
            if (loader.findPath(modId, OmniFormat.CONTAINER_MANIFEST_PATH).isPresent()) {
                found.add(modId);
            }
        }
        java.util.Collections.sort(found);
        return found;
    }

    private ContainerManifest readManifest(String containerModId) {
        Optional<Path> path = loader.findPath(containerModId, OmniFormat.CONTAINER_MANIFEST_PATH);
        if (!path.isPresent()) {
            throw new OmniException(ErrorCode.OMNI_2001, Messages.report(ErrorCode.OMNI_2001)
                    .detected("mod", containerModId)
                    .detected("expected entry", OmniFormat.CONTAINER_MANIFEST_PATH)
                    .detail("The mod declares a FabricMultiLoader entrypoint but carries no")
                    .detail("container manifest, so its jar is incomplete.")
                    .fix("re-download the mod from its official source")
                    .fix("if you built it yourself, run ./gradlew buildUniversalJar")
                    .build());
        }
        InputStream in = null;
        try {
            in = Files.newInputStream(path.get());
            return ManifestReader.read(in);
        } catch (IOException e) {
            throw new OmniException(ErrorCode.OMNI_2001, Messages.report(ErrorCode.OMNI_2001)
                    .detected("mod", containerModId)
                    .detected("entry", OmniFormat.CONTAINER_MANIFEST_PATH)
                    .detected("problem", e.toString())
                    .detail("The container manifest could not be read.")
                    .fix("re-download the mod — the jar is likely corrupted")
                    .build(), e);
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * The manifest must belong to the mod carrying it. A mismatch means the jar was assembled from
     * parts of two different mods, or edited by hand.
     */
    private void requireMatchingModId(String containerModId, ContainerManifest manifest) {
        if (!containerModId.equals(manifest.container().modId())) {
            throw new OmniException(ErrorCode.OMNI_2012, Messages.report(ErrorCode.OMNI_2012)
                    .detected("carrying mod", containerModId)
                    .detected("manifest says", manifest.container().modId())
                    .detail("The container manifest belongs to a different mod than the jar it")
                    .detail("was found in.")
                    .fix("re-download the mod from its official source")
                    .build());
        }
    }

    /** A container may require a newer runtime than the one the loader deduplicated to. */
    private void requireSupportedRuntime(String containerModId, ContainerManifest manifest) {
        Optional<String> installed = loader.modVersion(RuntimeInfo.MOD_ID);
        if (!installed.isPresent()) {
            return;
        }
        dev.fabricmultiloader.format.version.SemVer running =
                dev.fabricmultiloader.format.version.SemVer.parseLenient(installed.get());
        if (running.isLowerThan(manifest.container().minRuntime())) {
            throw new OmniException(ErrorCode.OMNI_2002, Messages.report(ErrorCode.OMNI_2002)
                    .detected("mod", containerModId)
                    .detected("requires runtime", manifest.container().minRuntime() + " or newer")
                    .detected("installed runtime", running)
                    .detail("Fabric loads one FabricMultiLoader runtime per game — the newest among")
                    .detail("all installed universal mods. Here that one is too old for this mod.")
                    .fix("update " + containerModId + ", or any other mod that ships a newer runtime")
                    .build());
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // Read-only stream from the loader's file system.
        }
    }

    /** The JSON limits applied to manifests. Exposed so tests can assert they are the defaults. */
    public static JsonLimits manifestLimits() {
        return JsonLimits.DEFAULT;
    }
}
