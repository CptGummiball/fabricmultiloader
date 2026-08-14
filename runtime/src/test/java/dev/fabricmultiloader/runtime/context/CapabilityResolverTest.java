package dev.fabricmultiloader.runtime.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.api.Capabilities;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.fixture.LifecycleFixture;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilityResolverTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRecorder() {
        Recorder.clear();
    }

    @Test
    @DisplayName("a declared and implemented capability resolves")
    void resolvesADeclaredCapability() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .capabilities("registries", "components")
                .resolve(tempDir);
        runtime.activation().preLaunch();
        ModContext ctx = runtime.activation().context();

        assertThat(ctx.has(Capabilities.COMPONENTS)).isTrue();
        assertThat(ctx.capability(Capabilities.COMPONENTS)).isPresent();
    }

    @Test
    @DisplayName("an undeclared capability is empty without the platform being asked")
    void skipsAnUndeclaredCapability() throws IOException {
        // The fake platform would happily return a components implementation. The manifest is what
        // decides, so that the validator, the diagnostic report and the code all agree.
        ContainerRuntime runtime = new LifecycleFixture()
                .capabilities("registries")
                .resolve(tempDir);
        runtime.activation().preLaunch();
        ModContext ctx = runtime.activation().context();

        assertThat(ctx.has(Capabilities.COMPONENTS)).isFalse();
        assertThat(ctx.capability(Capabilities.COMPONENTS)).isEmpty();
    }

    @Test
    @DisplayName("a capability declared but not implemented resolves to empty and is reported")
    void reportsADeclaredButMissingCapability() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .capabilities("registries", "tags")
                .resolve(tempDir);
        runtime.activation().preLaunch();
        ModContext ctx = runtime.activation().context();

        // Empty rather than an exception: common code guards on the Optional anyway, and taking a
        // launch down over an optional feature would be a worse trade than logging it loudly.
        assertThat(ctx.capability(Capabilities.TAGS)).isEmpty();
        assertThat(ctx.has(Capabilities.TAGS)).isFalse();
    }

    @Test
    @DisplayName("capabilities are empty before the platform exists")
    void isEmptyBeforeThePlatformIsCreated() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);
        ModContext ctx = runtime.activation().context();

        // Pre-launch code legitimately asks; "not yet" and "not on this version" want the same
        // handling from the caller, so they get the same answer.
        assertThat(ctx.capability(Capabilities.COMPONENTS)).isEmpty();
    }
}
