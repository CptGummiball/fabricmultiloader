package dev.fabricmultiloader.runtime.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.fixture.LifecycleFixture;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one reflective call on the critical path, and every way it can go wrong.
 *
 * <p>Each case asserts the error code <em>and</em> that the message names the payload and the class,
 * because the code alone is what a log filter sees and the message is what a player reads.
 */
class PlatformLoaderTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRecorder() {
        Recorder.clear();
    }

    @Test
    @DisplayName("a well-formed factory produces a platform")
    void createsThePlatform() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);

        assertThat(runtime.activation().preLaunch()).isTrue();

        assertThat(runtime.activation().platform()).isNotNull();
        assertThat(Recorder.events())
                .contains("factory:create:examplemod", "platform:constructed");
    }

    @Test
    @DisplayName("a factory outside the payload's packages is refused before it is loaded")
    void refusesAForeignClass() throws IOException {
        // The class exists and is a perfectly good factory. What disqualifies it is that the
        // payload does not claim its package — which is exactly the case a tampered manifest
        // produces, and the reason the check runs before Class.forName rather than after.
        ContainerRuntime runtime = new LifecycleFixture()
                .packages("com.example.somewhereelse")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2024);
        assertThat(thrown.getMessage())
                .contains("com.example.mc1214.Platform1214Factory")
                .contains("com.example.somewhereelse");
        assertThat(Recorder.events()).isEmpty();
    }

    @Test
    @DisplayName("a factory class that is not in the jar reports OMNI-2020")
    void reportsAMissingClass() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.NoSuchFactory")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2020);
        assertThat(thrown.getMessage()).contains("mc1214").contains("NoSuchFactory");
        assertThat(thrown.getCause()).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("a class that is not a PlatformFactory reports OMNI-2022")
    void reportsTheWrongType() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.BadFactories$NotAFactory")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2022);
        assertThat(thrown.getMessage())
                .contains("dev.fabricmultiloader.api.platform.PlatformFactory");
    }

    @Test
    @DisplayName("a factory that throws reports OMNI-2021 with the original cause attached")
    void reportsAThrowingFactory() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.BadFactories$Throwing")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2021);
        assertThat(thrown.getMessage()).contains("adapter could not start");
        // The exception the mod actually threw, not the reflective wrapper around it: the wrapper's
        // stack trace stops at the runtime and would send everyone to the wrong repository.
        assertThat(thrown.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter could not start");
    }

    @Test
    @DisplayName("a factory returning null reports OMNI-2023")
    void reportsANullPlatform() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.BadFactories$ReturningNull")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2023);
        assertThat(thrown.getMessage()).contains("ReturningNull");
    }

    @Test
    @DisplayName("a factory without a no-argument constructor reports OMNI-2021")
    void reportsAMissingConstructor() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.BadFactories$WithoutDefaultConstructor")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2021);
        assertThat(thrown.getMessage()).contains("no-argument constructor");
    }

    @Test
    @DisplayName("a failing static initialiser surfaces the real cause, not ExceptionInInitializerError")
    void unwrapsAStaticInitialiserFailure() throws IOException {
        ContainerRuntime runtime = new LifecycleFixture()
                .factory("com.example.mc1214.BadFactories$FailingStaticInitialiser")
                .resolve(tempDir);

        OmniException thrown = catchThrowableOfType(
                OmniException.class, () -> runtime.activation().preLaunch());

        assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2021);
        assertThat(thrown.getMessage()).contains("static initialiser exploded");
    }
}
