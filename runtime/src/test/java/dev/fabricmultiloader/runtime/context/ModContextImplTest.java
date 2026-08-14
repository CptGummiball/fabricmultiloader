package dev.fabricmultiloader.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.fixture.LifecycleFixture;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModContextImplTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRecorder() {
        Recorder.clear();
    }

    private ModContext activeContext() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().side(Side.SERVER).resolve(tempDir);
        runtime.activation().preLaunch();
        return runtime.activation().context();
    }

    @Test
    @DisplayName("identity comes from the container, not from the payload")
    void reportsContainerIdentity() throws IOException {
        ModContext ctx = activeContext();

        // The payload is examplemod-mc1214 2.0.0+mc1.21.4, but a mod's identity is the mod's, not
        // the implementation's — otherwise config paths and log names would change per version.
        assertThat(ctx.modId()).isEqualTo("examplemod");
        assertThat(ctx.modVersion()).isEqualTo(SemVer.parse("2.0.0"));
        assertThat(ctx.displayName()).isEqualTo("Universal Example Mod");
        assertThat(ctx.platform().payloadId()).isEqualTo("mc1214");
    }

    @Test
    @DisplayName("the environment is reported as detected")
    void reportsTheEnvironment() throws IOException {
        ModContext ctx = activeContext();

        assertThat(ctx.side()).isEqualTo(Side.SERVER);
        assertThat(ctx.isDevelopment()).isFalse();
        assertThat(ctx.platform().minecraft()).isEqualTo(SemVer.parse("1.21.4"));
        assertThat(ctx.platform().javaMajor()).isEqualTo(21);
        assertThat(ctx.isModLoaded("fabric-api")).isTrue();
        assertThat(ctx.isModLoaded("nothing-like-this")).isFalse();
        assertThat(ctx.modVersionOf("fabric-api")).contains(SemVer.parse("0.114.0"));
        assertThat(ctx.modVersionOf("nothing-like-this")).isEmpty();
    }

    @Test
    @DisplayName("the mod's config directory is created on first access")
    void createsTheConfigDirectoryLazily() throws IOException {
        ModContext ctx = activeContext();
        Path expected = ctx.configDir().resolve("examplemod");
        assertThat(Files.exists(expected)).isFalse();

        Path created = ctx.modConfigDir();

        assertThat(created).isEqualTo(expected);
        assertThat(Files.isDirectory(created)).isTrue();
        assertThat(ctx.modConfigDir()).isSameAs(created);
    }

    @Test
    @DisplayName("the subsystems are the payload's")
    void delegatesSubsystemsToThePlatform() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);
        runtime.activation().preLaunch();
        ModContext ctx = runtime.activation().context();

        assertThat(ctx.registries())
                .isSameAs(runtime.activation().platform().registries());
        assertThat(ctx.commands()).isSameAs(runtime.activation().platform().commands());
        assertThat(ctx.events()).isSameAs(runtime.activation().platform().events());
        assertThat(ctx.networking()).isSameAs(runtime.activation().platform().networking());
    }

    @Test
    @DisplayName("a subsystem call before the platform exists reports OMNI-4002, not a null pointer")
    void refusesSubsystemCallsTooEarly() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);
        // Exactly the state PlatformFactory#create sees: the context exists, the platform does not.
        ModContextImpl ctx = new ModContextImpl(
                runtime.manifest().container(), runtime.environment(),
                new LifecycleFixture().loader(tempDir), runtime.lifecycle(),
                runtime.platformInfo(), new ServiceRegistryImpl("examplemod"),
                new CapabilityResolver("examplemod", runtime.activePayload(), runtime.log()),
                runtime.log());

        OmniApiMisuseException thrown =
                catchThrowableOfType(OmniApiMisuseException.class, ctx::registries);

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_4002);
        assertThat(thrown.getMessage())
                .contains("ModContext#registries()")
                .contains("PlatformFactory#create");
    }

    @Test
    @DisplayName("the phase advances with the lifecycle")
    void reportsThePhase() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().side(Side.SERVER).resolve(tempDir);
        ModContext ctx = runtime.activation().context();
        assertThat(ctx.phase()).isEqualTo(LifecyclePhase.RESOLVED);

        runtime.activation().preLaunch();
        assertThat(ctx.phase()).isEqualTo(LifecyclePhase.PLATFORM_READY);

        runtime.activation().initialise();
        assertThat(ctx.phase()).isEqualTo(LifecyclePhase.COMMON_INIT);

        runtime.activation().initialiseSide(Side.SERVER);
        assertThat(ctx.phase()).isEqualTo(LifecyclePhase.RUNNING);
    }
}
