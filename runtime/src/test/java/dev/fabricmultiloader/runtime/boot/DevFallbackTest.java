package dev.fabricmultiloader.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.ManifestWriter;
import dev.fabricmultiloader.testing.FakeFabricLoader;
import dev.fabricmultiloader.runtime.fixture.LifecycleFixture;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The development loop: one version module launched on its own, with no universal jar anywhere.
 *
 * <p>What these tests are really asserting is that there is no second code path. The same platform
 * is created, the same entrypoints run in the same order, and the same context answers the same
 * questions — the only difference is where the identity came from.
 */
class DevFallbackTest {

    private static final String PAYLOAD = "examplemod-mc1214";
    private static final String CONTAINER = "examplemod";

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRecorder() {
        Recorder.clear();
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty(DevFallback.SLIM_PROPERTY);
    }

    /** A payload descriptor as the Gradle plugin writes it into every payload jar. */
    private static String payloadJson(String payloadId, String platformFactory, int classfileMajor) {
        return "{\n"
                + "  \"formatId\": \"omni/1\",\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"payloadId\": \"" + payloadId + "\",\n"
                + "  \"modId\": \"" + PAYLOAD + "\",\n"
                + "  \"modVersion\": \"2.0.0+mc1.21.4\",\n"
                + "  \"platformFactory\": \"" + platformFactory + "\",\n"
                + "  \"classfileMajor\": " + classfileMajor + ",\n"
                + "  \"packages\": [\"com.example.mc1214\"],\n"
                + "  \"mappings\": {\"namespace\": \"intermediary\", \"provider\": \"yarn\","
                + " \"build\": \"1.21.4+build.8\"},\n"
                + "  \"container\": {\n"
                + "    \"modId\": \"" + CONTAINER + "\",\n"
                + "    \"modVersion\": \"2.0.0\",\n"
                + "    \"displayName\": \"Universal Example Mod\",\n"
                + "    \"commonPackages\": [\"com.example.common\"],\n"
                + "    \"entrypoints\": {\n"
                + "      \"preLaunch\": [\"com.example.common.ExampleMod\"],\n"
                + "      \"common\": [\"com.example.common.ExampleMod\"],\n"
                + "      \"client\": [\"com.example.common.ExampleModClient\"]\n"
                + "    }\n"
                + "  },\n"
                + "  \"requires\": {\n"
                + "    \"minecraft\": [\">=1.21.4 <1.21.5\"],\n"
                + "    \"fabricloader\": [\">=0.14.21\"],\n"
                + "    \"java\": [\">=21\"],\n"
                + "    \"environment\": \"*\",\n"
                + "    \"mods\": {\"fabric-api\": [\">=0.114.0\"]}\n"
                + "  },\n"
                + "  \"capabilities\": [\"registries\", \"components\"]\n"
                + "}\n";
    }

    /** A loader with only the payload installed — the shape of a Loom {@code runServer}. */
    private FakeFabricLoader standaloneLoader(String descriptor) throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("game"));
        Path payloadRoot = Files.createDirectories(tempDir.resolve("payload"));

        FakeFabricLoader loader = new FakeFabricLoader(gameDir)
                .withMod("minecraft", "1.21.4")
                .withMod("fabricloader", "0.16.9")
                .withMod("fabric-api", "0.114.0")
                .withMod("fabricmultiloader", "1.0.0")
                .withMod(PAYLOAD, "2.0.0+mc1.21.4", payloadRoot)
                .onSide(Side.SERVER);
        loader.withFile(PAYLOAD, OmniFormat.PAYLOAD_DESCRIPTOR_PATH, descriptor);
        return loader;
    }

    @Test
    @DisplayName("a standalone payload in development runs the whole lifecycle")
    void runsStandaloneInDevelopment() throws IOException {
        FakeFabricLoader loader = standaloneLoader(
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 65))
                .inDevelopment();

        List<ContainerRuntime> resolved = RuntimeBootstrap.forTesting(loader).resolveAll();

        assertThat(resolved).hasSize(1);
        ContainerRuntime runtime = resolved.get(0);
        // The identity is the container's, taken from the descriptor — so log names, config paths
        // and the mod id a developer sees are the same as in a real universal jar.
        assertThat(runtime.modId()).isEqualTo(CONTAINER);
        assertThat(runtime.isActive()).isTrue();
        assertThat(runtime.activePayload().id()).isEqualTo("mc1214");

        runtime.activation().preLaunch();
        runtime.activation().initialise();
        runtime.activation().initialiseSide(Side.SERVER);

        assertThat(runtime.lifecycle().phase()).isEqualTo(LifecyclePhase.RUNNING);
        assertThat(Recorder.events()).containsSubsequence(
                "platform:constructed",
                "mod:onPreLaunch:examplemod:phase=mc1214",
                "platform:onInitialize",
                "mod:onInitialize:phase=COMMON_INIT",
                "registries:flush",
                "platform:onInitializeServer");
    }

    @Test
    @DisplayName("outside development a payload without its container is refused")
    void refusesStandaloneInProduction() throws IOException {
        FakeFabricLoader loader = standaloneLoader(
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 65));

        OmniException thrown = catchThrowableOfType(OmniException.class,
                () -> RuntimeBootstrap.forTesting(loader).resolveAll());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2003);
        // A player who ended up here took the jar apart, so the fix is to put it back — not to
        // hand them a system property that would make a broken installation start anyway.
        assertThat(thrown.getMessage())
                .contains("examplemod")
                .contains("the single jar that contains this one");
    }

    @Test
    @DisplayName("the slim property enables the same path outside development")
    void allowsStandaloneWithTheSlimProperty() throws IOException {
        System.setProperty(DevFallback.SLIM_PROPERTY, "true");
        FakeFabricLoader loader = standaloneLoader(
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 65));

        List<ContainerRuntime> resolved = RuntimeBootstrap.forTesting(loader).resolveAll();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("the synthetic manifest disables integrity checking, having nothing to check")
    void disablesIntegrityChecking() throws IOException {
        FakeFabricLoader loader = standaloneLoader(
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 65))
                .inDevelopment();

        ContainerRuntime runtime = RuntimeBootstrap.forTesting(loader).resolveAll().get(0);

        assertThat(runtime.manifest().container().verifyIntegrity()).isFalse();
        assertThat(runtime.manifest().container().baselineJavaMajor()).isEqualTo(21);
        assertThat(runtime.manifest().payloads()).hasSize(1);
    }

    @Test
    @DisplayName("with the container present the descriptor is only cross-checked")
    void crossChecksAgainstThePresentContainer() throws IOException {
        LifecycleFixture fixture = new LifecycleFixture();
        FakeFabricLoader loader = fixture.loader(tempDir);
        Path payloadRoot = Files.createDirectories(tempDir.resolve("payload"));
        loader.withMod(PAYLOAD, "2.0.0+mc1.21.4", payloadRoot);
        loader.withFile(PAYLOAD, OmniFormat.PAYLOAD_DESCRIPTOR_PATH,
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 65));

        List<ContainerRuntime> resolved = RuntimeBootstrap.forTesting(loader).resolveAll();

        // One container, not two: the descriptor belongs to a payload of a container that is here.
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).modId()).isEqualTo(CONTAINER);
    }

    @Test
    @DisplayName("a descriptor disagreeing with the container reports OMNI-2011")
    void reportsDivergence() throws IOException {
        LifecycleFixture fixture = new LifecycleFixture();
        FakeFabricLoader loader = fixture.loader(tempDir);
        Path payloadRoot = Files.createDirectories(tempDir.resolve("payload"));
        loader.withMod(PAYLOAD, "2.0.0+mc1.21.4", payloadRoot);
        // Java 21 bytecode according to the container, Java 17 according to the payload itself.
        // Both files are generated from one source, so this cannot happen to a coherent build.
        loader.withFile(PAYLOAD, OmniFormat.PAYLOAD_DESCRIPTOR_PATH,
                payloadJson("mc1214", "com.example.mc1214.Platform1214Factory", 61));

        OmniException thrown = catchThrowableOfType(OmniException.class,
                () -> RuntimeBootstrap.forTesting(loader).resolveAll());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2011);
        assertThat(thrown.getMessage())
                .contains("classfileMajor")
                .contains(OmniFormat.PAYLOAD_DESCRIPTOR_PATH);
    }

    @Test
    @DisplayName("a container carrying no payload descriptor still resolves normally")
    void toleratesAMissingDescriptor() throws IOException {
        LifecycleFixture fixture = new LifecycleFixture();
        FakeFabricLoader loader = fixture.loader(tempDir);

        List<ContainerRuntime> resolved = RuntimeBootstrap.forTesting(loader).resolveAll();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).isActive()).isTrue();
        assertThat(ManifestWriter.write(resolved.get(0).manifest()))
                .contains("\"modId\": \"examplemod\"");
    }
}
