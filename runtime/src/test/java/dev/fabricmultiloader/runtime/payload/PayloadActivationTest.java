package dev.fabricmultiloader.runtime.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.fixture.LifecycleFixture;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PayloadActivationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRecorder() {
        Recorder.clear();
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("the full lifecycle runs platform, mod, then flush — in that order")
        void runsTheWholeLifecycleInOrder() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().side(Side.SERVER).resolve(tempDir);

            runtime.activation().preLaunch();
            runtime.activation().initialise();
            runtime.activation().initialiseSide(Side.SERVER);

            assertThat(Recorder.events()).containsExactly(
                    "factory:create:examplemod",
                    "platform:constructed",
                    "platform:onPreLaunch:examplemod",
                    "platform:installCrashContext",
                    "mod:onPreLaunch:examplemod:phase=mc1214",
                    "platform:onInitialize",
                    "mod:onInitialize:phase=COMMON_INIT",
                    "mod:sawPreLaunchState=true",
                    "mod:greeting=hello from 1.21.4",
                    "mod:hasComponents=true",
                    "mod:hasTags=false",
                    // After the mod code, never before: this is the moment everything the mod
                    // declares has actually been declared.
                    "registries:flush",
                    "platform:onInitializeServer");
            assertThat(runtime.lifecycle().phase()).isEqualTo(LifecyclePhase.RUNNING);
        }

        @Test
        @DisplayName("one entrypoint class serving two phases keeps its state")
        void reusesEntrypointInstances() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);

            runtime.activation().preLaunch();
            runtime.activation().initialise();

            // The pre-launch hook set a field the main hook read back. A fresh instance per phase
            // would report false here and would silently break every mod written this way.
            assertThat(Recorder.events()).contains("mod:sawPreLaunchState=true");
            assertThat(runtime.activation().commonBootstrap().instances()).hasSize(1);
        }

        @Test
        @DisplayName("a side phase reached first still runs the common phase before it")
        void recoversFromAnUnexpectedEntrypointOrder() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().side(Side.CLIENT).resolve(tempDir);

            // Neither preLaunch nor initialise has been called. Fabric's dependency graph makes
            // this order unlikely, but "unlikely" is not something a mod should have to rely on.
            runtime.activation().initialiseSide(Side.CLIENT);

            assertThat(Recorder.events())
                    .containsSubsequence(
                            "platform:constructed",
                            "platform:onInitialize",
                            "mod:onInitialize:phase=COMMON_INIT",
                            "registries:flush",
                            "platform:onInitializeClient",
                            "mod:onInitializeClient:side=client:phase=SIDE_INIT");
            assertThat(runtime.lifecycle().phase()).isEqualTo(LifecyclePhase.RUNNING);
        }

        @Test
        @DisplayName("the client entrypoint does nothing on a dedicated server")
        void ignoresTheWrongSide() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().side(Side.SERVER).resolve(tempDir);
            runtime.activation().preLaunch();
            runtime.activation().initialise();
            Recorder.clear();

            assertThat(runtime.activation().initialiseSide(Side.CLIENT)).isFalse();

            assertThat(Recorder.events()).isEmpty();
            assertThat(runtime.lifecycle().phase()).isEqualTo(LifecyclePhase.COMMON_INIT);
        }
    }

    @Nested
    @DisplayName("idempotence")
    class Idempotence {

        @Test
        @DisplayName("every step reports whether it did the work, and repeats do nothing")
        void repeatedCallsAreNoOps() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().side(Side.SERVER).resolve(tempDir);
            PayloadActivation activation = runtime.activation();

            assertThat(activation.preLaunch()).isTrue();
            assertThat(activation.initialise()).isTrue();
            assertThat(activation.initialiseSide(Side.SERVER)).isTrue();
            int afterFirstRun = Recorder.events().size();

            assertThat(activation.preLaunch()).isFalse();
            assertThat(activation.initialise()).isFalse();
            assertThat(activation.initialiseSide(Side.SERVER)).isFalse();

            assertThat(Recorder.events()).hasSize(afterFirstRun);
        }

        @Test
        @DisplayName("the container hands back the same activation every time")
        void activationIsShared() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture().resolve(tempDir);

            assertThat(runtime.activation()).isSameAs(runtime.activation());
        }
    }

    @Nested
    @DisplayName("entrypoint failures")
    class EntrypointFailures {

        @Test
        @DisplayName("an entrypoint class outside commonPackages reports OMNI-2032")
        void refusesAForeignEntrypoint() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture()
                    .entrypoints(EntrypointSet.builder()
                            .add(EntrypointSet.Phase.COMMON, "com.example.mc1214.Platform1214Factory")
                            .build())
                    .resolve(tempDir);
            runtime.activation().preLaunch();

            OmniException thrown = catchThrowableOfType(
                    OmniException.class, () -> runtime.activation().initialise());

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2032);
            assertThat(thrown.getMessage()).contains("com.example.common");
        }

        @Test
        @DisplayName("a missing entrypoint class reports OMNI-2030")
        void reportsAMissingEntrypoint() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture()
                    .entrypoints(EntrypointSet.builder()
                            .add(EntrypointSet.Phase.COMMON, "com.example.common.Nonexistent")
                            .build())
                    .resolve(tempDir);
            runtime.activation().preLaunch();

            OmniException thrown = catchThrowableOfType(
                    OmniException.class, () -> runtime.activation().initialise());

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2030);
            assertThat(thrown.getMessage()).contains("Nonexistent");
        }

        @Test
        @DisplayName("an entrypoint not implementing its phase interface reports OMNI-2033")
        void reportsTheWrongInterface() throws IOException {
            // ExampleModClient implements UniversalClientMod, not UniversalMod. Declared for the
            // common phase it would simply never be called — which is the kind of silence that
            // costs an evening, so it is an error instead.
            ContainerRuntime runtime = new LifecycleFixture()
                    .entrypoints(EntrypointSet.builder()
                            .add(EntrypointSet.Phase.COMMON, "com.example.common.ExampleModClient")
                            .build())
                    .resolve(tempDir);
            runtime.activation().preLaunch();

            OmniException thrown = catchThrowableOfType(
                    OmniException.class, () -> runtime.activation().initialise());

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2033);
            assertThat(thrown.getMessage())
                    .contains("dev.fabricmultiloader.api.UniversalMod");
        }

        @Test
        @DisplayName("a platform hook that throws reports OMNI-2040 naming the hook")
        void reportsAFailingPlatformHook() throws IOException {
            ContainerRuntime runtime = new LifecycleFixture()
                    .factory("com.example.mc1214.FailingHookFactory")
                    .resolve(tempDir);
            runtime.activation().preLaunch();

            OmniException thrown = catchThrowableOfType(
                    OmniException.class, () -> runtime.activation().initialise());

            assertThat(thrown.code()).isEqualTo(ErrorCode.OMNI_2040);
            assertThat(thrown.getMessage())
                    .contains("Platform#onInitialize")
                    .contains("mc1214");
        }
    }

    @Nested
    @DisplayName("preconditions")
    class Preconditions {

        @Test
        @DisplayName("an unresolved container has no activation to hand out")
        void refusesToActivateAnInactiveContainer() throws IOException {
            System.setProperty("fabricmultiloader.strict", "false");
            try {
                ContainerRuntime runtime = new LifecycleFixture()
                        .withoutPayload()
                        .resolve(tempDir);

                assertThat(runtime.isActive()).isFalse();
                assertThatThrownBy(runtime::activation)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("no active payload");
            } finally {
                System.clearProperty("fabricmultiloader.strict");
            }
        }
    }
}
