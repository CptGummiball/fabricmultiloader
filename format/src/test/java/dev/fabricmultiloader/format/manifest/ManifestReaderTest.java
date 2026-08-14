package dev.fabricmultiloader.format.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.json.JsonFormatException;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManifestReaderTest {

    private static String manifestJson() {
        return ManifestWriter.write(ManifestFixtures.exampleManifest());
    }

    @Test
    void readsFromTextAndFromAStream() {
        String json = manifestJson();
        assertThat(ManifestReader.read(json).container().modId()).isEqualTo("examplemod");

        ByteArrayInputStream stream =
                new ByteArrayInputStream(json.getBytes(Charset.forName("UTF-8")));
        assertThat(ManifestReader.read(stream).payloads()).hasSize(3);
    }

    @Test
    @DisplayName("a missing required field is reported with its JSON pointer")
    void missingFieldReportsPointer() {
        String broken = manifestJson().replace("\"payloadAlias\": \"examplemod-impl\",", "");

        JsonFormatException thrown =
                catchThrowableOfType(JsonFormatException.class, () -> ManifestReader.read(broken));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3001);
        assertThat(thrown.pointer()).isEqualTo("/container/payloadAlias");
    }

    @Test
    @DisplayName("a wrongly typed field names both the expected and the actual type")
    void typeErrorReportsBothTypes() {
        String broken = manifestJson().replace("\"baselineJavaMajor\": 17", "\"baselineJavaMajor\": \"17\"");

        JsonFormatException thrown =
                catchThrowableOfType(JsonFormatException.class, () -> ManifestReader.read(broken));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3002);
        assertThat(thrown.pointer()).isEqualTo("/container/baselineJavaMajor");
        assertThat(thrown.getMessage()).contains("number").contains("string");
    }

    @Test
    @DisplayName("a newer schema is refused with the update instruction, not half-read")
    void refusesNewerSchema() {
        String newer = manifestJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        OmniException thrown =
                catchThrowableOfType(OmniException.class, () -> ManifestReader.read(newer));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2002);
        assertThat(thrown.getMessage())
                .contains("newer FabricMultiLoader")
                .contains("update the mod");
    }

    @Test
    @DisplayName("a document that is not an Omni container at all says so plainly")
    void refusesForeignDocuments() {
        String foreign = "{\"formatId\":\"something/1\",\"schemaVersion\":1,"
                + "\"container\":{},\"payloads\":[]}";

        OmniException thrown =
                catchThrowableOfType(OmniException.class, () -> ManifestReader.read(foreign));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2002);
        assertThat(thrown.getMessage()).contains("does not look like an Omni container");
    }

    @Test
    @DisplayName("an unsafe path in the manifest is refused before it is used for anything")
    void refusesUnsafePaths() {
        String broken = manifestJson().replace(
                "META-INF/jars/examplemod-mc1201.jar", "../../../etc/passwd");

        OmniException thrown =
                catchThrowableOfType(OmniException.class, () -> ManifestReader.read(broken));

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_3004);
    }

    @Test
    @DisplayName("an invalid mod id is refused")
    void refusesInvalidModIds() {
        String broken = manifestJson().replace("\"modId\": \"examplemod\"", "\"modId\": \"Example Mod\"");
        assertThatThrownBy(() -> ManifestReader.read(broken))
                .isInstanceOf(OmniException.class)
                .satisfies(thrown -> assertThat(((OmniException) thrown).code())
                        .isEqualTo(ErrorCode.OMNI_3004));
    }

    @Test
    @DisplayName("unknown fields are ignored when reading — that is what keeps the format extensible")
    void ignoresUnknownFieldsWhenReading() {
        String extended = manifestJson().replace(
                "\"formatId\": \"omni/1\",",
                "\"formatId\": \"omni/1\",\n  \"futureField\": {\"anything\": true},");

        ContainerManifest manifest = ManifestReader.read(extended);
        assertThat(manifest.container().modId()).isEqualTo("examplemod");
    }

    @Test
    @DisplayName("the validator sees the same unknown fields the reader ignored")
    void reportsUnknownFieldsForTheValidator() {
        String extended = manifestJson()
                .replace("\"formatId\": \"omni/1\",",
                        "\"formatId\": \"omni/1\",\n  \"futureField\": 1,")
                .replace("\"payloadAlias\": \"examplemod-impl\",",
                        "\"payloadAlias\": \"examplemod-impl\",\n    \"typo\": 2,");

        assertThat(ManifestReader.findUnknownFields(extended))
                .containsExactly("/futureField", "/container/typo");
    }

    @Test
    void aCleanManifestHasNoUnknownFields() {
        assertThat(ManifestReader.findUnknownFields(manifestJson())).isEmpty();
    }

    @Test
    @DisplayName("reserved fields are recognised, so an experiment does not read as a typo")
    void reservedFieldsAreKnown() {
        String withReserved = manifestJson().replace(
                "\"payloadAlias\": \"examplemod-impl\",",
                "\"payloadAlias\": \"examplemod-impl\",\n    \"signatures\": [],");
        assertThat(ManifestReader.findUnknownFields(withReserved)).isEmpty();
    }

    @Test
    @DisplayName("a range may be a bare string as well as Fabric's OR array")
    void acceptsScalarAndArrayRanges() {
        String scalar = manifestJson().replace(
                "\"minecraft\": [\n          \">=1.20.1 <1.20.2\"\n        ]",
                "\"minecraft\": \">=1.20.1 <1.20.2\"");

        ContainerManifest manifest = ManifestReader.read(scalar);
        assertThat(manifest.payloadById("mc1201").requires().minecraft().toPredicates())
                .containsExactly(">=1.20.1 <1.20.2");
    }

    @Test
    @DisplayName("an unknown entrypoint phase is ignored rather than fatal")
    void ignoresUnknownEntrypointPhases() {
        String extended = manifestJson().replace(
                "\"common\": [",
                "\"futurePhase\": [\"com.example.Future\"],\n    \"common\": [");

        ContainerManifest manifest = ManifestReader.read(extended);
        assertThat(manifest.entrypoints().forPhase(EntrypointSet.Phase.COMMON))
                .containsExactly("com.example.common.ExampleMod");
        assertThat(ManifestReader.findUnknownFields(extended))
                .containsExactly("/entrypoints/futurePhase");
    }
}
