package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.FabricMultiLoader;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every universal mod in the process, keyed by container mod id.
 *
 * <p>There is one runtime per game — Fabric deduplicates the nested library by mod id and picks the
 * newest — but arbitrarily many containers using it. Each is independent: one failing to resolve
 * says nothing about the others, and they must not be able to interfere with each other's state.
 *
 * <p>Also serves as the {@link FabricMultiLoader.Provider}, which is how third-party mods and crash
 * reporters ask about a container without a compile-time dependency on the runtime.
 */
public final class RuntimeRegistry implements FabricMultiLoader.Provider {

    private final Map<String, ContainerRuntime> containers =
            new ConcurrentHashMap<String, ContainerRuntime>();

    /**
     * Registers a container, or returns the one already registered under that id.
     *
     * <p>Idempotent because entrypoint invocation order is a loader detail: the container's own
     * pre-launch hook and its payload's may both reach here, and neither should be able to replace
     * an already-initialised runtime.
     *
     * @param runtime the container runtime
     * @return the registered instance, which may be an earlier one
     */
    public ContainerRuntime register(ContainerRuntime runtime) {
        ContainerRuntime existing = containers.putIfAbsent(runtime.modId(), runtime);
        return existing == null ? runtime : existing;
    }

    /** A registered container, or {@code null}. */
    public ContainerRuntime get(String containerModId) {
        return containerModId == null ? null : containers.get(containerModId);
    }

    /** Whether a container is registered. */
    public boolean contains(String containerModId) {
        return containerModId != null && containers.containsKey(containerModId);
    }

    /** Every registered container, ordered by mod id. */
    public Map<String, ContainerRuntime> all() {
        Map<String, ContainerRuntime> ordered = new LinkedHashMap<String, ContainerRuntime>();
        List<String> ids = new ArrayList<String>(containers.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            ordered.put(id, containers.get(id));
        }
        return Collections.unmodifiableMap(ordered);
    }

    // ------------------------------------------------------------------ FabricMultiLoader.Provider

    @Override
    public boolean isActive(String containerModId) {
        ContainerRuntime runtime = get(containerModId);
        return runtime != null && runtime.isActive();
    }

    @Override
    public Optional<String> activePayload(String containerModId) {
        ContainerRuntime runtime = get(containerModId);
        if (runtime == null || !runtime.isActive()) {
            return Optional.empty();
        }
        return Optional.of(runtime.activePayload().id());
    }

    @Override
    public Optional<PlatformInfo> platformInfo(String containerModId) {
        ContainerRuntime runtime = get(containerModId);
        if (runtime == null || !runtime.isActive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(runtime.platformInfo());
    }

    @Override
    public List<String> containers() {
        return Collections.unmodifiableList(new ArrayList<String>(all().keySet()));
    }

    @Override
    public String diagnosticReport(String containerModId) {
        ContainerRuntime runtime = get(containerModId);
        if (runtime == null) {
            return "No FabricMultiLoader container with mod id '" + containerModId + "' is installed.";
        }
        if (runtime.resolution() == null) {
            return "Container '" + containerModId + "' has not been resolved yet.";
        }
        return dev.fabricmultiloader.runtime.diag.DiagnosticReport.render(
                runtime.manifest(), runtime.environment(), runtime.resolution(), null);
    }
}
