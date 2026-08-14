package dev.fabricmultiloader.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.hash.Sha256;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.ManifestFixtures;
import dev.fabricmultiloader.format.manifest.ManifestWriter;
import dev.fabricmultiloader.runtime.FakeLoader;
import dev.fabricmultiloader.runtime.diag.DiagnosticReport;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapTest {

    private static final String CONTAINER = "examplemod";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty(ContainerRuntime.STRICT_PROPERTY);
        System.clearProperty(ContainerRuntime.STRICT_PROPERTY + "." + CONTAINER);
        System.clearProperty(IntegrityChecker.DISABLE_PROPERTY);
        System.clearProperty(ContainerRuntime.REPORT_PROPERTY);
    }

    /** A fake loader with the reference container installed and one payload selected. */
    private FakeLoader loaderWith(String minecraft, int java, String activePayloadModId)
            throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("game"));
        Path containerRoot = Files.createDirectories(tempDir.resolve("container"));

        FakeLoader loader = new FakeLoader(gameDir)
                .withMod("minecraft", minecraft)
                .withMod("fabricloader", "0.16.9")
                .withMod("fabric-api", "0.114.0")
                .withMod("fabricmultiloader", "1.0.0")
                .withMod(CONTAINER, "2.0.0", containerRoot)
                .onSide(Side.SERVER);

        if (activePayloadModId != null) {
            loader.withMod(activePayloadModId, "2.0.0");
        }
        loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                ManifestWriter.write(ManifestFixtures.exampleManifest()));
        return loader;
    }

    @Nested
    @DisplayName("discovery and resolution")
    class Resolution {

        @Test
        @DisplayName("the loader's selection is what the runtime activates")
        void activatesTheSelectedPayload() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");

            ContainerRuntime runtime = RuntimeBootstrap.forTesting(loader)
                    .resolveContainer(CONTAINER);

            assertThat(runtime.isActive()).isTrue();
            assertThat(runtime.activePayload().id()).isEqualTo("mc1214");
            assertThat(runtime.platformInfo().payloadId()).isEqualTo("mc1214");
            assertThat(runtime.platformInfo().minecraftOrdinal()).isEqualTo(12104);
        }

        @Test
        @DisplayName("resolving twice returns the same runtime instead of redoing the work")
        void resolutionIsIdempotent() throws IOException {
            RuntimeBootstrap bootstrap = RuntimeBootstrap.forTesting(
                    loaderWith("1.21.4", 21, "examplemod-mc1214"));

            ContainerRuntime first = bootstrap.resolveContainer(CONTAINER);
            ContainerRuntime second = bootstrap.resolveContainer(CONTAINER);

            assertThat(second).isSameAs(first);
        }

        @Test
        @DisplayName("no selected payload aborts with the full diagnostic")
        void noPayloadAborts() throws IOException {
            FakeLoader loader = loaderWith("1.22.3", 21, null);

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2003);
            assertThat(thrown.getMessage())
                    .contains("Minecraft      1.22.3")
                    .contains("Supported Minecraft versions")
                    .contains("A full report was written to");
        }

        @Test
        @DisplayName("two selected payloads abort as ambiguous — a validated build cannot do this")
        void twoPayloadsAbort() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214")
                    .withMod("examplemod-mc1211", "2.0.0");

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2004);
        }

        @Test
        @DisplayName("lenient mode keeps the game running with the mod inactive")
        void lenientModeContinues() throws IOException {
            System.setProperty(ContainerRuntime.STRICT_PROPERTY, "false");
            FakeLoader loader = loaderWith("1.22.3", 21, null);

            ContainerRuntime runtime = RuntimeBootstrap.forTesting(loader)
                    .resolveContainer(CONTAINER);

            assertThat(runtime.isActive()).isFalse();
            assertThat(runtime.lifecycle().hasFailed()).isTrue();
        }

        @Test
        @DisplayName("leniency can be granted to one mod without loosening the rest")
        void lenientModeIsPerMod() throws IOException {
            System.setProperty(ContainerRuntime.STRICT_PROPERTY + "." + CONTAINER, "false");

            ContainerRuntime runtime = RuntimeBootstrap.forTesting(loaderWith("1.22.3", 21, null))
                    .resolveContainer(CONTAINER);

            assertThat(runtime.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("manifest handling")
    class Manifests {

        @Test
        @DisplayName("a missing manifest reports a corrupted jar, not a class error")
        void missingManifest() throws IOException {
            Path gameDir = Files.createDirectories(tempDir.resolve("game"));
            FakeLoader loader = new FakeLoader(gameDir)
                    .withMod("minecraft", "1.21.4")
                    .withMod(CONTAINER, "2.0.0", Files.createDirectories(tempDir.resolve("empty")));

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2001);
            assertThat(thrown.getMessage()).contains("re-download");
        }

        @Test
        @DisplayName("a manifest belonging to another mod is refused")
        void mismatchedModId() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            Path gameDir = loader.gameDir();
            Path otherRoot = Files.createDirectories(tempDir.resolve("other"));
            loader.withMod("othermod", "1.0.0", otherRoot);
            loader.withFile("othermod", OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(ManifestFixtures.exampleManifest()));
            assertThat(gameDir).exists();

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer("othermod"));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2012);
            assertThat(thrown.getMessage()).contains("belongs to a different mod");
        }

        @Test
        @DisplayName("a container needing a newer runtime says which mod to update")
        void tooOldRuntime() throws IOException {
            ContainerManifest demanding = ContainerManifest.builder()
                    .container(dev.fabricmultiloader.format.manifest.ContainerInfo.builder()
                            .modId(CONTAINER)
                            .modVersion("2.0.0")
                            .commonPackages("com.example.common")
                            .baselineJavaMajor(17)
                            .payloadAlias("examplemod-impl")
                            .minRuntime(dev.fabricmultiloader.format.version.SemVer.of(9, 0, 0))
                            .runtime(ManifestFixtures.exampleContainer().runtime())
                            .build())
                    .payload(ManifestFixtures.payload(
                            "mc1214", "1.21.4", ">=1.21.4 <1.21.5", ">=21", 65, "*"))
                    .build();

            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(demanding));

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2002);
            assertThat(thrown.getMessage()).contains("9.0.0 or newer").contains("1.0.0");
        }

        @Test
        void discoveryFindsEveryContainer() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            assertThat(RuntimeBootstrap.forTesting(loader).discoverContainers())
                    .containsExactly(CONTAINER);
        }
    }

    @Nested
    @DisplayName("integrity")
    class Integrity {

        private static final String JAR_PATH = "META-INF/jars/examplemod-mc1214.jar";

        /** A manifest whose single payload declares the checksum of the given bytes. */
        private ContainerManifest manifestFor(byte[] jarBytes) {
            return ContainerManifest.builder()
                    .container(ManifestFixtures.exampleContainer())
                    .payload(dev.fabricmultiloader.format.manifest.PayloadDescriptor.builder()
                            .id("mc1214")
                            .modId("examplemod-mc1214")
                            .modVersion("2.0.0")
                            .file(JAR_PATH)
                            .integrity(Sha256.of(jarBytes), jarBytes.length)
                            .classfileMajor(65)
                            .platformFactory("com.example.mc1214.Factory")
                            .packages("com.example.mc1214")
                            .requires(dev.fabricmultiloader.format.manifest.Requirements.builder()
                                    .minecraft(">=1.21.4 <1.21.5").java(">=21").build())
                            .build())
                    .build();
        }

        @Test
        @DisplayName("a payload whose bytes do match passes")
        void matchingPayloadPasses() throws IOException {
            byte[] jarBytes = "the real payload jar".getBytes(UTF_8);
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(manifestFor(jarBytes)));
            loader.withFile(CONTAINER, JAR_PATH, jarBytes);

            assertThat(RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER).isActive())
                    .isTrue();
        }

        @Test
        @DisplayName("a payload whose bytes do not match its checksum stops the launch")
        void tamperedPayloadIsRefused() throws IOException {
            byte[] expected = "the real payload jar".getBytes(UTF_8);
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(manifestFor(expected)));
            loader.withFile(CONTAINER, JAR_PATH, "not the jar that was built".getBytes(UTF_8));

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2013);
            assertThat(thrown.getMessage())
                    .contains("re-download")
                    .contains(IntegrityChecker.DISABLE_PROPERTY);
        }

        @Test
        @DisplayName("a declared payload jar that is missing entirely is refused too")
        void missingPayloadJarIsRefused() throws IOException {
            byte[] expected = "the real payload jar".getBytes(UTF_8);
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(manifestFor(expected)));

            OmniException thrown = catchThrowableOfType(OmniException.class,
                    () -> RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER));

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2013);
            assertThat(thrown.getMessage()).contains("missing from the container");
        }

        @Test
        @DisplayName("verification can be disabled for modpack tools that recompress jars")
        void verificationCanBeDisabled() throws IOException {
            System.setProperty(IntegrityChecker.DISABLE_PROPERTY, "false");
            byte[] expected = "the real payload jar".getBytes(UTF_8);
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(manifestFor(expected)));
            loader.withFile(CONTAINER, JAR_PATH, "recompressed".getBytes(UTF_8));

            assertThat(RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER).isActive())
                    .isTrue();
        }

        @Test
        @DisplayName("a payload without checksum data is not verified — the fixture case")
        void absentChecksumSkipsVerification() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");

            assertThat(RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER).isActive())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("reports")
    class Reports {

        @Test
        @DisplayName("a successful launch records what ran, for the next support question")
        void writesLastLaunch() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214");
            RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER);

            Path record = loader.gameDir()
                    .resolve(dev.fabricmultiloader.runtime.diag.ReportWriter.DIRECTORY)
                    .resolve(DiagnosticReport.lastLaunchFileName(CONTAINER));

            assertThat(record).exists();
            String content = new String(Files.readAllBytes(record), UTF_8);
            assertThat(content)
                    .contains("payload     mc1214")
                    .contains("minecraft   1.21.4")
                    .contains("java        21");
        }

        @Test
        @DisplayName("a failed launch writes the long report next to the short message")
        void writesFailureReport() throws IOException {
            System.setProperty(ContainerRuntime.STRICT_PROPERTY, "false");
            FakeLoader loader = loaderWith("1.22.3", 21, null);
            RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER);

            Path report = loader.gameDir()
                    .resolve(dev.fabricmultiloader.runtime.diag.ReportWriter.DIRECTORY)
                    .resolve(DiagnosticReport.failureFileName(CONTAINER));

            assertThat(report).exists();
            String content = new String(Files.readAllBytes(report), UTF_8);
            assertThat(content)
                    .contains("FabricMultiLoader diagnostic report")
                    .contains("outcome        UNRESOLVED")
                    .contains("[rejected] mc1201")
                    .contains("Loaded mods")
                    .contains("Where to get help");
        }

        @Test
        @DisplayName("an unwritable directory logs and continues rather than failing the launch")
        void reportFailureIsNotFatal() throws IOException {
            Path gameDir = tempDir.resolve("not-a-directory");
            Files.write(gameDir, "blocking file".getBytes(UTF_8));

            FakeLoader loader = new FakeLoader(gameDir)
                    .withMod("minecraft", "1.21.4")
                    .withMod("fabricmultiloader", "1.0.0")
                    .withMod("examplemod-mc1214", "2.0.0")
                    .withMod(CONTAINER, "2.0.0",
                            Files.createDirectories(tempDir.resolve("container2")));
            loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                    ManifestWriter.write(ManifestFixtures.exampleManifest()));

            assertThat(RuntimeBootstrap.forTesting(loader).resolveContainer(CONTAINER).isActive())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("environment detection")
    class EnvironmentDetection {

        @Test
        void readsEverythingFromLoaderMetadata() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214").inDevelopment();

            dev.fabricmultiloader.format.payload.Environment environment =
                    RuntimeBootstrap.forTesting(loader).environment();

            assertThat(environment.minecraft().toString()).isEqualTo("1.21.4");
            assertThat(environment.fabricLoader().toString()).isEqualTo("0.16.9");
            assertThat(environment.fabricApi().toString()).isEqualTo("0.114.0");
            assertThat(environment.side()).isEqualTo(Side.SERVER);
            assertThat(environment.isDevelopment()).isTrue();
            assertThat(environment.isModLoaded("fabricmultiloader")).isTrue();
        }

        @Test
        @DisplayName("without Minecraft there is nothing to resolve against, and it says so")
        void refusesOutsideAGame() throws IOException {
            FakeLoader loader = new FakeLoader(Files.createDirectories(tempDir.resolve("g")))
                    .withMod("fabricloader", "0.16.9");

            assertThatThrownBy(() -> RuntimeBootstrap.forTesting(loader))
                    .isInstanceOf(OmniException.class)
                    .satisfies(thrown -> assertThat(((OmniException) thrown).code())
                            .isEqualTo(ErrorCode.OMNI_2010));
        }

        @Test
        @DisplayName("a development runtime reports the named namespace, not what the manifest said")
        void developmentRuntimeUsesNamedMappings() throws IOException {
            FakeLoader loader = loaderWith("1.21.4", 21, "examplemod-mc1214").inDevelopment();

            ContainerRuntime runtime = RuntimeBootstrap.forTesting(loader)
                    .resolveContainer(CONTAINER);

            assertThat(runtime.platformInfo().mappingNamespace()).isEqualTo("named");
        }
    }
}
