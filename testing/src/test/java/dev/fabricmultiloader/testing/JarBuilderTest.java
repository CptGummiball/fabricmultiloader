package dev.fabricmultiloader.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.hash.Sha256;
import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.ManifestReader;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.PayloadManifest;
import dev.fabricmultiloader.format.manifest.PayloadManifestReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixtures have to be real jars, or every test built on them proves nothing about the format.
 */
class JarBuilderTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @TempDir
    Path tempDir;

    private static Map<String, byte[]> read(byte[] jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = zip.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }
                entries.put(entry.getName(), buffer.toByteArray());
            }
        } finally {
            zip.close();
        }
        return entries;
    }

    private static String text(Map<String, byte[]> entries, String path) {
        byte[] content = entries.get(path);
        if (content == null) {
            throw new AssertionError("no entry " + path + " in " + entries.keySet());
        }
        return new String(content, UTF_8);
    }

    @Nested
    @DisplayName("payload jars")
    class PayloadJars {

        @Test
        @DisplayName("a payload carries the metadata the solver reads and its own descriptor")
        void buildsAPayload() throws IOException {
            ContainerManifest manifest = JarFixtures.referenceManifest();
            PayloadDescriptor payload = manifest.payloads().get(2);

            Map<String, byte[]> entries =
                    read(new PayloadJarBuilder(manifest, payload).toBytes());

            assertThat(entries.keySet())
                    .containsExactly("fabric.mod.json", OmniFormat.PAYLOAD_DESCRIPTOR_PATH);
            JsonObject modJson = Json.parseObject(text(entries, "fabric.mod.json"));
            assertThat(modJson.getString("id")).isEqualTo("examplemod-mc1214");
            // Normalised to full SemVer by the version algebra, and equivalent: the loader's
            // synthetic java mod reports 21 or 21.0.5, both of which satisfy >=21.0.0.
            assertThat(modJson.getObject("depends").getString("java")).isEqualTo(">=21.0.0");
            // The exact binding to the container, which enforces load ordering and stops a payload
            // of one build from being mixed with a container of another.
            assertThat(modJson.getObject("depends").getString("examplemod")).isEqualTo("=2.0.0");
            assertThat(modJson.getObject("breaks").keys())
                    .containsExactlyInAnyOrder("examplemod-mc1201", "examplemod-mc1211");
            assertThat(modJson.getArray("provides").asStringList())
                    .containsExactly("examplemod-impl");
            assertThat(modJson.getObject("entrypoints").getArray("main").asStringList())
                    .containsExactly("dev.fabricmultiloader.runtime.entrypoint.PayloadMain");
        }

        @Test
        @DisplayName("the descriptor a payload carries reads back into the same payload")
        void descriptorRoundTrips() throws IOException {
            ContainerManifest manifest = JarFixtures.referenceManifest();
            PayloadDescriptor payload = manifest.payloads().get(0);

            Map<String, byte[]> entries =
                    read(new PayloadJarBuilder(manifest, payload).toBytes());
            PayloadManifest descriptor = PayloadManifestReader.read(
                    text(entries, OmniFormat.PAYLOAD_DESCRIPTOR_PATH));

            assertThat(descriptor.payload().id()).isEqualTo("mc1201");
            assertThat(descriptor.payload().platformFactory())
                    .isEqualTo(payload.platformFactory());
            assertThat(descriptor.payload().classfileMajor()).isEqualTo(61);
            assertThat(descriptor.payload().packages()).isEqualTo(payload.packages());
            assertThat(descriptor.container().modId()).isEqualTo("examplemod");
            // The dev fallback's whole job: this must reconstruct a runnable container.
            assertThat(descriptor.toContainerManifest(
                    dev.fabricmultiloader.format.version.SemVer.of(1, 0, 0))
                    .payloads()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("container jars")
    class ContainerJars {

        @Test
        @DisplayName("the reference container nests three payloads and the runtime")
        void buildsTheReferenceContainer() throws IOException {
            Path file = tempDir.resolve("examplemod-2.0.0-universal.jar");
            ContainerJarBuilder container = JarFixtures.referenceContainer(file);

            Map<String, byte[]> entries = read(Files.readAllBytes(file));

            assertThat(entries).containsKeys(
                    "fabric.mod.json",
                    OmniFormat.CONTAINER_MANIFEST_PATH,
                    OmniFormat.NESTED_JAR_ROOT + "examplemod-mc1201.jar",
                    OmniFormat.NESTED_JAR_ROOT + "examplemod-mc1211.jar",
                    OmniFormat.NESTED_JAR_ROOT + "examplemod-mc1214.jar");
            assertThat(container.manifest().payloads()).hasSize(3);
        }

        @Test
        @DisplayName("the container's depends are derived from its payloads")
        void derivesContainerMetadata() throws IOException {
            Path file = tempDir.resolve("container.jar");
            JarFixtures.referenceContainer(file);
            JsonObject modJson =
                    Json.parseObject(text(read(Files.readAllBytes(file)), "fabric.mod.json"));

            JsonObject depends = modJson.getObject("depends");
            // The minimum across payloads: the container must load on the oldest JVM any payload
            // supports, or the diagnostic explaining why none matched would never run.
            assertThat(depends.getString("java")).isEqualTo(">=17");
            assertThat(depends.getArray("minecraft").asStringList())
                    .containsExactly(">=1.20.1 <1.20.2", ">=1.21.0 <1.21.2", ">=1.21.4 <1.21.5");
            assertThat(modJson.getObject("entrypoints").getArray("preLaunch").asStringList())
                    .containsExactly("dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch");
            // No hard dependency on the payload alias — that is what lets the container load and
            // produce a readable diagnostic instead of the loader's "requires examplemod-impl".
            assertThat(depends.has("examplemod-impl")).isFalse();
        }

        @Test
        @DisplayName("the written manifest carries the real hash and size of every payload")
        void recordsRealIntegrity() throws IOException {
            Path file = tempDir.resolve("container.jar");
            ContainerJarBuilder container = JarFixtures.referenceContainer(file);
            Map<String, byte[]> entries = read(Files.readAllBytes(file));

            ContainerManifest written =
                    ManifestReader.read(text(entries, OmniFormat.CONTAINER_MANIFEST_PATH));

            for (PayloadDescriptor payload : written.payloads()) {
                byte[] nested = entries.get(payload.file());
                assertThat(nested).as(payload.file()).isNotNull();
                assertThat(payload.sha256()).isEqualTo(Sha256.of(nested));
                assertThat(payload.size()).isEqualTo(nested.length);
            }
            assertThat(container.manifest().payloads().get(0).sha256()).isNotEmpty();
        }

        @Test
        @DisplayName("a tampered payload no longer matches the recorded hash")
        void tamperingIsDetectable() {
            ContainerManifest manifest = JarFixtures.referenceManifest();
            PayloadDescriptor payload = manifest.payloads().get(0);

            byte[] honest = new PayloadJarBuilder(manifest, payload).toBytes();
            byte[] tampered = JarFixtures.tamperedPayload(manifest, payload);

            assertThat(Sha256.of(tampered)).isNotEqualTo(Sha256.of(honest));
        }
    }

    @Nested
    @DisplayName("reproducibility")
    class Reproducibility {

        @Test
        @DisplayName("building the same container twice gives byte-identical jars")
        void isReproducible() {
            ContainerManifest manifest = JarFixtures.referenceManifest();

            byte[] first = build(manifest);
            byte[] second = build(manifest);

            // Chapter 10.5: fixed timestamps, sorted entries, no directory entries. Without this a
            // golden-file test over a whole jar would be impossible, and so would verifying that a
            // release artifact was built from the source it claims.
            assertThat(first).isEqualTo(second);
        }

        private byte[] build(ContainerManifest manifest) {
            ContainerJarBuilder container = new ContainerJarBuilder(manifest);
            for (PayloadDescriptor payload : manifest.payloads()) {
                container.payload(new PayloadJarBuilder(manifest, payload));
            }
            container.runtime(JarFixtures.runtimeJar());
            return container.toBytes();
        }

        @Test
        @DisplayName("entries are sorted and carry the fixed timestamp")
        void writesCanonicalEntries() throws IOException {
            byte[] jar = new JarWriter()
                    .entry("z.txt", "last")
                    .entry("a.txt", "first")
                    .storedEntry("META-INF/jars/nested.jar", new byte[] {1, 2, 3})
                    .toBytes();

            List<String> names = new ArrayList<String>();
            ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar));
            try {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    names.add(entry.getName());
                    assertThat(entry.getTime()).isEqualTo(JarWriter.FIXED_TIME);
                    if (entry.getName().startsWith(OmniFormat.NESTED_JAR_ROOT)) {
                        assertThat(entry.getMethod()).isEqualTo(ZipEntry.STORED);
                    }
                }
            } finally {
                zip.close();
            }
            assertThat(names).containsExactly("META-INF/jars/nested.jar", "a.txt", "z.txt");
        }
    }
}
