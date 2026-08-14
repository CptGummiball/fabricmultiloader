package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.nio.file.Path;

/**
 * The reference matrix as real files: three payloads, three Minecraft versions, two Java baselines.
 *
 * <p>The same matrix the design uses throughout (chapter 11.2), so a diagnostic produced in a test
 * is the diagnostic in the documentation. It is small enough to reason about and large enough to
 * contain every interesting case: two payloads sharing a Java version and one not, a range that
 * spans two Minecraft releases and two that do not, and a container whose baseline is therefore
 * lower than most of its payloads need.
 */
public final class JarFixtures {

    /** The container mod id used throughout the fixtures. */
    public static final String CONTAINER = "examplemod";

    /**
     * The reference manifest: 1.20.1 on Java 17, 1.21–1.21.1 and 1.21.4 on Java 21.
     *
     * @return the manifest
     */
    public static ContainerManifest referenceManifest() {
        return ManifestBuilder.manifest()
                .modId(CONTAINER)
                .modVersion("2.0.0")
                .entrypoint(EntrypointSet.Phase.COMMON, "com.example.common.ExampleMod")
                .entrypoint(EntrypointSet.Phase.CLIENT, "com.example.common.ExampleModClient")
                .payload("mc1201", "1.20.1", 17)
                .payloadRange("mc1211", ">=1.21 <1.21.2", 21)
                .payload("mc1214", "1.21.4", 21)
                .build();
    }

    /**
     * Builds the reference universal jar, with all three payloads nested and hashed.
     *
     * @param file where to write it
     * @return the container builder, whose {@link ContainerJarBuilder#manifest()} carries the real
     *     hashes
     */
    public static ContainerJarBuilder referenceContainer(Path file) {
        ContainerManifest manifest = referenceManifest();
        ContainerJarBuilder container = new ContainerJarBuilder(manifest);
        for (PayloadDescriptor payload : manifest.payloads()) {
            container.payload(new PayloadJarBuilder(manifest, payload));
        }
        container.runtime(runtimeJar());
        container.writeTo(file);
        return container;
    }

    /**
     * A stand-in for the runtime library jar.
     *
     * <p>Deliberately not the real one: a fixture that depended on the runtime's built artifact
     * would make every test using it wait for that build, and nothing here reads its contents —
     * only its presence and its hash matter.
     *
     * @return the jar bytes
     */
    public static byte[] runtimeJar() {
        return new JarWriter()
                .entry("fabric.mod.json",
                        "{\"schemaVersion\": 1, \"id\": \"fabricmultiloader\","
                                + " \"version\": \"1.0.0\", \"environment\": \"*\"}\n")
                .toBytes();
    }

    /**
     * A payload jar whose bytes differ from what the container's manifest records.
     *
     * <p>For integrity tests: the descriptor keeps the hash of the honest jar, so the check has
     * something true to compare against and something false to find.
     *
     * @param manifest the container manifest
     * @param payload the payload to corrupt
     * @return the tampered jar
     */
    public static byte[] tamperedPayload(ContainerManifest manifest, PayloadDescriptor payload) {
        return new PayloadJarBuilder(manifest, payload)
                .entry("tampered.txt", "this entry was added after the hash was taken")
                .toBytes();
    }

    private JarFixtures() {
        throw new AssertionError("no instances");
    }
}
