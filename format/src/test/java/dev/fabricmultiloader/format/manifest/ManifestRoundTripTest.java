package dev.fabricmultiloader.format.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reproducible builds require that a manifest survives read → write → read byte-for-byte. Anything
 * less means two builds of one commit can produce different jars.
 */
class ManifestRoundTripTest {

    @Test
    @DisplayName("write -> read -> write is byte-identical")
    void roundTripsThroughText() {
        ContainerManifest original = ManifestFixtures.exampleManifest();

        String once = ManifestWriter.write(original);
        ContainerManifest reread = ManifestReader.read(once);
        String twice = ManifestWriter.write(reread);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    @DisplayName("the parsed model survives the round trip, not just the text")
    void roundTripsThroughTheModel() {
        ContainerManifest original = ManifestFixtures.exampleManifest();
        ContainerManifest reread = ManifestReader.read(ManifestWriter.write(original));

        assertThat(reread.container().modId()).isEqualTo("examplemod");
        assertThat(reread.container().modVersion().toString()).isEqualTo("2.0.0");
        assertThat(reread.container().baselineJavaMajor()).isEqualTo(17);
        assertThat(reread.container().payloadAlias()).isEqualTo("examplemod-impl");
        assertThat(reread.container().commonPackaging()).isEqualTo(CommonPackaging.SHARED);
        assertThat(reread.payloadModIds())
                .containsExactly("examplemod-mc1201", "examplemod-mc1211", "examplemod-mc1214");

        PayloadDescriptor payload = reread.payloadById("mc1214");
        assertThat(payload.classfileMajor()).isEqualTo(65);
        assertThat(payload.requires().minecraft().toPredicates())
                .containsExactly(">=1.21.4 <1.21.5");
        assertThat(payload.requires().java().toPredicates()).containsExactly(">=21.0.0");
        assertThat(payload.requires().mods()).containsKey("fabric-api");
        assertThat(payload.requires().optionalMods()).containsKey("modmenu");
        assertThat(payload.accessWidener()).isEqualTo("examplemod-mc1214.accesswidener");
        assertThat(payload.mixins()).hasSize(2);
        assertThat(payload.mixins().get(1).environment()).isEqualTo(EnvironmentConstraint.CLIENT);
        assertThat(payload.capabilities()).contains("networking.v1");
    }

    @Test
    @DisplayName("payloads are sorted into the normative order regardless of insertion order")
    void sortsPayloadsCanonically() {
        ContainerManifest manifest = ContainerManifest.builder()
                .container(ManifestFixtures.exampleContainer())
                .payload(ManifestFixtures.minimalPayload("mczz", ">=1.30"))
                .payload(ManifestFixtures.minimalPayload("mcaa", ">=1.20 <1.21"))
                .payload(PayloadDescriptor.builder()
                        .id("mchi")
                        .modId("mod-mchi")
                        .modVersion("1.0.0")
                        .file(OmniFormat.NESTED_JAR_ROOT + "hi.jar")
                        .classfileMajor(61)
                        .priority(10)
                        .platformFactory("com.example.mchi.Factory")
                        .packages("com.example.mchi")
                        .requires(Requirements.builder().minecraft(">=1.21 <1.22").build())
                        .build())
                .build();

        // priority descending, then id ascending
        List<String> ids = new ArrayList<String>();
        for (PayloadDescriptor payload : manifest.payloads()) {
            ids.add(payload.id());
        }
        assertThat(ids).containsExactly("mchi", "mcaa", "mczz");
    }

    @Test
    @DisplayName("the canonical key order is not alphabetical — it groups related fields")
    void writesTheNormativeKeyOrder() {
        JsonObject root = Json.parseObject(ManifestWriter.write(ManifestFixtures.exampleManifest()));

        assertThat(root.keys()).containsExactly(
                "formatId", "schemaVersion", "generator", "container", "entrypoints",
                "payloads", "diagnostics");

        assertThat(root.getObject("container").keys()).containsExactly(
                "modId", "modVersion", "displayName", "commonPackages", "commonPackaging",
                "baselineJavaMajor", "runtime", "minRuntime", "payloadAlias", "strict",
                "verifyIntegrity");

        assertThat(root.getArray("payloads").getObject(0).keys()).containsExactly(
                "id", "modId", "modVersion", "displayName", "file", "sha256", "size",
                "classfileMajor", "priority", "platformFactory", "packages", "requires",
                "provides", "breaks", "mappings", "mixins", "refmaps", "accessWidener",
                "nestedJars", "resourcesDigest", "capabilities");
    }

    @Test
    @DisplayName("version ranges are always written as arrays, which Fabric reads as OR")
    void writesRangesAsArrays() {
        JsonObject root = Json.parseObject(ManifestWriter.write(ManifestFixtures.exampleManifest()));
        JsonObject requires = root.getArray("payloads").getObject(0).getObject("requires");

        assertThat(requires.getArray("minecraft").asStringList())
                .containsExactly(">=1.20.1 <1.20.2");
        assertThat(requires.getArray("java").asStringList()).containsExactly(">=17.0.0");
    }

    @Test
    @DisplayName("a document ends with exactly one newline")
    void documentFormIsFileReady() {
        String document = ManifestWriter.writeDocument(ManifestFixtures.exampleManifest());
        assertThat(document).endsWith("}\n").doesNotEndWith("\n\n");
        assertThat(document).startsWith("{\n  \"formatId\": \"" + OmniFormat.FORMAT_ID + "\"");
    }

    @Test
    @DisplayName("a null access widener survives the round trip as null, not as \"null\"")
    void preservesAbsentAccessWidener() {
        ContainerManifest manifest = ContainerManifest.builder()
                .container(ManifestFixtures.exampleContainer())
                .payload(ManifestFixtures.minimalPayload("mcaa", ">=1.20 <1.21"))
                .build();

        ContainerManifest reread = ManifestReader.read(ManifestWriter.write(manifest));
        assertThat(reread.payloadById("mcaa").accessWidener()).isNull();
    }
}
