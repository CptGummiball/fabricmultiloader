package dev.fabricmultiloader.testing.conformance;

import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.FabricModJsonWriter;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A universal mod as the loader would see it: one container with nested payloads and a nested
 * runtime, expressed as {@code fabric.mod.json} documents.
 *
 * <p>The metadata is <b>generated</b> by {@link FabricModJsonWriter}, not written by hand. That is
 * the difference between a conformance test and a test of a fixture: what has to hold is that the
 * solver treats the files the assembler emits in a particular way, so hand-tuning them would prove
 * something about a jar nobody ships.
 *
 * <p>Real zip files are deliberately not built. The property under test lives in the solver, and
 * reaching it through {@code ModDiscoverer} would mean constructing a {@code FabricLoaderImpl} and a
 * {@code GameProvider} — a large surface with no bearing on the question, whose breakage across
 * loader versions would look like the assumption failing.
 */
public final class SyntheticContainer {

    private final String name;
    private final Map<String, String> containers = new LinkedHashMap<String, String>();
    private final Map<String, List<String>> nested = new LinkedHashMap<String, List<String>>();

    private SyntheticContainer(String name) {
        this.name = name;
    }

    /**
     * Builds the mod set for one universal mod.
     *
     * @param manifest the container manifest
     * @return the synthetic mod set
     */
    public static SyntheticContainer of(ContainerManifest manifest) {
        SyntheticContainer container = new SyntheticContainer(manifest.container().modId());
        container.add(manifest);
        return container;
    }

    /** Builds a mod set holding several universal mods at once, for deduplication tests. */
    public static SyntheticContainer of(ContainerManifest... manifests) {
        SyntheticContainer container = new SyntheticContainer(manifests.length + " containers");
        for (ContainerManifest manifest : manifests) {
            container.add(manifest);
        }
        return container;
    }

    private void add(ContainerManifest manifest) {
        String containerModId = manifest.container().modId();
        containers.put(containerModId, FabricModJsonWriter.container(manifest));

        List<String> children = new ArrayList<String>();
        for (PayloadDescriptor payload : manifest.payloads()) {
            children.add(FabricModJsonWriter.payload(manifest, payload));
        }
        // The runtime library is nested exactly like a payload, which is what makes loader
        // deduplication across several universal mods observable at all.
        children.add(runtimeModJson(manifest));
        nested.put(containerModId, children);
    }

    /**
     * The runtime's own {@code fabric.mod.json}.
     *
     * <p>Written here rather than read from the runtime module's resources so that a test can nest
     * two different versions of it in one process — the deduplication case — which the real
     * resource file cannot express.
     */
    private static String runtimeModJson(ContainerManifest manifest) {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": \"" + manifest.container().runtime().modId() + "\",\n"
                + "  \"version\": \"" + manifest.container().runtime().version() + "\",\n"
                + "  \"name\": \"FabricMultiLoader\",\n"
                + "  \"environment\": \"*\",\n"
                + "  \"depends\": { \"java\": \">=8\" }\n"
                + "}\n";
    }

    /** A name for the test's display. */
    public String name() {
        return name;
    }

    /** The container mod ids in this set. */
    public List<String> containerModIds() {
        return Collections.unmodifiableList(new ArrayList<String>(containers.keySet()));
    }

    /** A container's {@code fabric.mod.json}. */
    public String containerModJson(String containerModId) {
        return containers.get(containerModId);
    }

    /** The {@code fabric.mod.json} of everything nested inside a container. */
    public List<String> nestedModJson(String containerModId) {
        List<String> children = nested.get(containerModId);
        return children == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(children);
    }

    @Override
    public String toString() {
        return name;
    }
}
