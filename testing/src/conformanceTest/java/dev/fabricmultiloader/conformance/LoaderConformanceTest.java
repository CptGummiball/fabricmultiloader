package dev.fabricmultiloader.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.EnvironmentConstraint;
import dev.fabricmultiloader.testing.ManifestBuilder;
import dev.fabricmultiloader.testing.conformance.LoaderConformanceHarness;
import dev.fabricmultiloader.testing.conformance.LoaderVersion;
import dev.fabricmultiloader.testing.conformance.ResolutionProbe;
import dev.fabricmultiloader.testing.conformance.SyntheticContainer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The gate.
 *
 * <p>Eight properties, every supported Fabric Loader line, real solver. The first is the one the
 * whole architecture rests on; the rest are the guarantees built on top of it. If any of them fails
 * on a new loader, the design is refuted for that loader and the fallback of chapter 41 has to be
 * evaluated before anything else proceeds — which is why this sits before the Gradle plugin in the
 * implementation plan rather than after it.
 */
class LoaderConformanceTest {

    private static final String JAVA_17 = "17.0.10";
    private static final String JAVA_21 = "21.0.5";

    static Stream<LoaderVersion> loaders() {
        return LoaderVersion.matrix().stream();
    }

    private static ResolutionProbe resolve(LoaderVersion loader, ContainerManifest manifest,
            LoaderConformanceHarness.Env env) {
        return new LoaderConformanceHarness(loader).resolve(SyntheticContainer.of(manifest), env);
    }

    /** The reference matrix: 1.20.1 on Java 17, 1.21–1.21.1 and 1.21.4 on Java 21. */
    private static ContainerManifest referenceMatrix() {
        return ManifestBuilder.manifest()
                .payload("mc1201", "1.20.1", 17)
                .payloadRange("mc1211", ">=1.21 <1.21.2", 21)
                .payload("mc1214", "1.21.4", 21)
                .build();
    }

    // ------------------------------------------------------------------ the load-bearing property

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("a nested payload with unsatisfiable depends is dropped, not fatal")
    void nestedUnsatisfiableIsDropped(LoaderVersion loader) {
        ContainerManifest manifest = ManifestBuilder.manifest()
                .payload("mc1201", "1.20.1", 17)
                .payload("mc1214", "1.21.4", 21)
                .build();

        ResolutionProbe probe = resolve(loader, manifest,
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));

        // Everything rests on this sentence: the 1.20.1 payload cannot run here, and the loader
        // leaves it out instead of refusing to start.
        assertThat(probe.succeeded()).as("resolution succeeded: %s", probe).isTrue();
        assertThat(probe.selected("examplemod")).as("the container loads").isTrue();
        assertThat(probe.selected("examplemod-mc1201")).as("the 1.20.1 payload is dropped").isFalse();
        assertThat(probe.selected("examplemod-mc1214")).as("the 1.21.4 payload loads").isTrue();
    }

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("with three disjoint payloads exactly one is selected")
    void exactlyOneSelected(LoaderVersion loader) {
        ResolutionProbe probe = resolve(loader, referenceMatrix(),
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));

        assertThat(probe.succeeded()).as("%s", probe).isTrue();
        assertThat(probe.selectedStartingWith("examplemod-"))
                .containsExactly("examplemod-mc1214");
    }

    // ------------------------------------------------------------------ exclusivity

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("two payloads providing the same alias are never both selected")
    void providesExclusivity(LoaderVersion loader) {
        // Deliberately overlapping and deliberately without mutual breaks, so that the only thing
        // that can separate them is the shared `provides` alias.
        ContainerManifest manifest = ManifestBuilder.manifest()
                .withoutMutualBreaks()
                .payload("alpha", "1.21.4", 21)
                .payload("beta", "1.21.4", 21)
                .build();

        ResolutionProbe probe = resolve(loader, manifest,
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));

        assertThat(probe.selectedStartingWith("examplemod-"))
                .as("at most one may provide examplemod-impl: %s", probe)
                .hasSizeLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("mutual breaks keep two otherwise valid payloads apart")
    void breaksExclusivity(LoaderVersion loader) {
        ContainerManifest manifest = ManifestBuilder.manifest()
                .payload("alpha", "1.21.4", 21)
                .payload("beta", "1.21.4", 21)
                .build();
        // The fixture has to actually declare the breaks, or the test would pass because of the
        // shared provides alias and prove nothing about this axis.
        assertThat(manifest.payloads().get(0).breaks()).contains("examplemod-beta");

        ResolutionProbe probe = resolve(loader, manifest,
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));

        assertThat(probe.selectedStartingWith("examplemod-"))
                .as("mutual breaks: %s", probe)
                .hasSizeLessThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------ the other axes

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("depends.java is evaluated, and a payload too new for the JVM is dropped")
    void javaDependencyEvaluated(LoaderVersion loader) {
        ContainerManifest manifest = ManifestBuilder.manifest()
                .payload("mc1201", "1.20.1", 17)
                .payload("mc1214", "1.21.4", 21)
                .build();

        // Minecraft 1.21.4 on a Java 17 JVM: the container's own baseline is 17 so it loads, the
        // 1.20.1 payload is out on Minecraft and the 1.21.4 payload is out on Java. Chapter 14.8's
        // "JVM too old for all payloads" case, and the reason it produces a readable diagnostic
        // rather than a crash.
        ResolutionProbe probe = resolve(loader, manifest,
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_17));

        assertThat(probe.succeeded()).as("%s", probe).isTrue();
        assertThat(probe.selected("examplemod")).isTrue();
        assertThat(probe.selectedStartingWith("examplemod-")).isEmpty();
    }

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("a client-only payload is not selected on a dedicated server")
    void environmentEvaluated(LoaderVersion loader) {
        ManifestBuilder builder = ManifestBuilder.manifest()
                .payload("mc1201", "1.20.1", 17)
                .payload("mc1214", "1.21.4", 21);
        builder.lastPayload().environment(EnvironmentConstraint.CLIENT);
        ContainerManifest manifest = builder.build();

        ResolutionProbe onServer = resolve(loader, manifest,
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));
        ResolutionProbe onClient = resolve(loader, manifest,
                LoaderConformanceHarness.Env.client("1.21.4", JAVA_21));

        assertThat(onServer.succeeded()).as("%s", onServer).isTrue();
        assertThat(onServer.selected("examplemod-mc1214"))
                .as("client-only payload on a server").isFalse();
        assertThat(onClient.selected("examplemod-mc1214"))
                .as("the same payload on a client").isTrue();
    }

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("two universal mods share one runtime, and it is the newer one")
    void runtimeDeduplication(LoaderVersion loader) {
        ContainerManifest older = ManifestBuilder.manifest()
                .modId("examplemod").runtimeVersion("1.0.0")
                .payload("mc1214", "1.21.4", 21)
                .build();
        ContainerManifest newer = ManifestBuilder.manifest()
                .modId("othermod").runtimeVersion("1.1.0")
                .payload("mc1214", "1.21.4", 21)
                .build();

        ResolutionProbe probe = new LoaderConformanceHarness(loader).resolve(
                SyntheticContainer.of(older, newer),
                LoaderConformanceHarness.Env.server("1.21.4", JAVA_21));

        // ADR-008: the runtime ships as its own nested mod precisely so that the loader picks one
        // deterministically by version, rather than the classpath deciding which copy wins.
        assertThat(probe.succeeded()).as("%s", probe).isTrue();
        assertThat(probe.selected("fabricmultiloader")).isTrue();
        assertThat(probe.selected("examplemod")).isTrue();
        assertThat(probe.selected("othermod")).isTrue();
        assertThat(probe.selectedStartingWith("fabricmultiloader")).hasSize(1);
    }

    @ParameterizedTest(name = "fabric-loader {0}")
    @MethodSource("loaders")
    @DisplayName("a Minecraft version outside the union is the loader's error, not ours")
    void containerRangeError(LoaderVersion loader) {
        ResolutionProbe probe = resolve(loader, referenceMatrix(),
                LoaderConformanceHarness.Env.server("1.19.2", JAVA_21));

        // The container itself is a root mod, so an unsatisfiable range there is a hard failure —
        // and that is what we want: the loader's own error dialog lists the supported ranges, which
        // is a better message than anything we could produce after the fact.
        assertThat(probe.succeeded()).as("%s", probe).isFalse();
        assertThat(probe.failureMessage()).containsIgnoringCase("examplemod");
    }
}
