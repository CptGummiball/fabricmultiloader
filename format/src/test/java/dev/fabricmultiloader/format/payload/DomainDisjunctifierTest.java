package dev.fabricmultiloader.format.payload;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.manifest.EnvironmentConstraint;
import dev.fabricmultiloader.format.manifest.ManifestFixtures;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.Requirements;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Determinism has to be established before the jar exists, because Fabric's solver has no specified
 * tie-break and payload selection happens before any mod code could arbitrate.
 */
class DomainDisjunctifierTest {

    private static PayloadDescriptor payload(String id, int priority, Requirements requires) {
        return PayloadDescriptor.builder()
                .id(id)
                .modId("mod-" + id)
                .modVersion("1.0.0")
                .file("META-INF/jars/" + id + ".jar")
                .classfileMajor(65)
                .priority(priority)
                .platformFactory("com.example." + id + ".Factory")
                .packages("com.example." + id)
                .requires(requires)
                .build();
    }

    private static Requirements mc(String... predicates) {
        return Requirements.builder().minecraft(predicates).build();
    }

    @Test
    @DisplayName("the reference matrix is already disjoint and needs no subtraction")
    void referenceMatrixIsUntouched() {
        DomainDisjunctifier.Result result =
                DomainDisjunctifier.disjunctify(ManifestFixtures.exampleManifest().payloads());

        assertThat(result.isValid()).isTrue();
        assertThat(result.areEffectiveDomainsDisjoint()).isTrue();
        assertThat(result.effectiveMinecraft("mc1201").toPredicates())
                .containsExactly(">=1.20.1 <1.20.2");
        assertThat(result.effectiveMinecraft("mc1211").toPredicates())
                .containsExactly(">=1.21.0 <1.21.2");
        assertThat(result.effectiveMinecraft("mc1214").toPredicates())
                .containsExactly(">=1.21.4 <1.21.5");
    }

    @Test
    @DisplayName("a specialised payload carves its range out of a catch-all")
    void prioritySubtractionSplitsTheCatchAll() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mcmodern", 0, mc(">=1.21")),
                payload("mc1214", 10, mc(">=1.21.4 <1.21.5")));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isTrue();
        assertThat(result.effectiveMinecraft("mc1214").toPredicates())
                .containsExactly(">=1.21.4 <1.21.5");
        assertThat(result.effectiveMinecraft("mcmodern").toPredicates())
                .containsExactly(">=1.21.0 <1.21.4", ">=1.21.5");
        assertThat(result.areEffectiveDomainsDisjoint()).isTrue();
    }

    @Test
    @DisplayName("equal priority plus overlap is a build failure, not a coin flip")
    void equalPriorityOverlapIsRejected() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mc1211", 0, mc(">=1.21 <1.21.2")),
                payload("mc1214", 0, mc(">=1.21 <1.21.5")));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isFalse();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0).code()).isEqualTo(ErrorCode.OMNI_1010);
        assertThat(result.problems().get(0).report())
                .contains("OMNI-1010")
                .contains("does not define which one wins")
                .contains("gradle/fabricmultiloader.toml");
    }

    @Test
    @DisplayName("a payload that could never be selected is reported rather than shipped")
    void fullyShadowedPayloadIsRejected() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mcwide", 10, mc(">=1.20")),
                payload("mcnarrow", 0, mc(">=1.21.4 <1.21.5")));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isFalse();
        assertThat(result.problems().get(0).code()).isEqualTo(ErrorCode.OMNI_1015);
        assertThat(result.problems().get(0).payloadId()).isEqualTo("mcnarrow");
        assertThat(result.effectiveDomain("mcnarrow").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("payloads may share a Minecraft range when they differ by Java version")
    void javaAxisSeparatesPayloads() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mcnew", 10, Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5").java(">=21").build()),
                payload("mcold", 0, Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5").java(">=17").build()));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isTrue();
        assertThat(result.areEffectiveDomainsDisjoint()).isTrue();

        Domain old = result.effectiveDomain("mcold");
        assertThat(old.cells()).hasSize(1);
        assertThat(old.cells().get(0).java().toPredicates()).containsExactly(">=17.0.0 <21.0.0");
    }

    @Test
    @DisplayName("payloads may share everything when they differ by side")
    void sideAxisSeparatesPayloads() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mcclient", 10, Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5")
                        .environment(EnvironmentConstraint.CLIENT).build()),
                payload("mcboth", 0, Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5")
                        .environment(EnvironmentConstraint.BOTH).build()));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isTrue();
        assertThat(result.areEffectiveDomainsDisjoint()).isTrue();
        assertThat(result.effectiveDomain("mcboth").cells().get(0).side())
                .isEqualTo(EnvironmentConstraint.SERVER);
    }

    @Test
    @DisplayName("a remainder needing different Java ranges per Minecraft range is refused")
    void inexpressibleRemainderIsRejected() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mccut", 10, Requirements.builder()
                        .minecraft(">=1.21.2 <1.21.4").java(">=21").build()),
                payload("mcwide", 0, Requirements.builder()
                        .minecraft(">=1.21 <1.22").java(">=17").build()));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isFalse();
        assertThat(result.problems().get(0).code()).isEqualTo(ErrorCode.OMNI_1016);
        assertThat(result.problems().get(0).report())
                .contains("cannot express that")
                .contains("split this payload");
    }

    @Test
    @DisplayName("the order payloads are declared in does not change the outcome")
    void resultIsIndependentOfInputOrder() {
        PayloadDescriptor catchAll = payload("mcmodern", 0, mc(">=1.21"));
        PayloadDescriptor specific = payload("mc1214", 10, mc(">=1.21.4 <1.21.5"));

        DomainDisjunctifier.Result forward =
                DomainDisjunctifier.disjunctify(Arrays.asList(catchAll, specific));
        DomainDisjunctifier.Result backward =
                DomainDisjunctifier.disjunctify(Arrays.asList(specific, catchAll));

        assertThat(forward.effectiveMinecraft("mcmodern"))
                .isEqualTo(backward.effectiveMinecraft("mcmodern"));
        assertThat(forward.effectiveMinecraft("mc1214"))
                .isEqualTo(backward.effectiveMinecraft("mc1214"));
    }

    @Test
    @DisplayName("chained priorities subtract cumulatively")
    void threeLevelPriorityChain() {
        List<PayloadDescriptor> payloads = Arrays.asList(
                payload("mcall", 0, mc(">=1.20")),
                payload("mc121x", 5, mc(">=1.21 <1.22")),
                payload("mc1214", 10, mc(">=1.21.4 <1.21.5")));

        DomainDisjunctifier.Result result = DomainDisjunctifier.disjunctify(payloads);

        assertThat(result.isValid()).isTrue();
        assertThat(result.areEffectiveDomainsDisjoint()).isTrue();
        assertThat(result.effectiveMinecraft("mc1214").toPredicates())
                .containsExactly(">=1.21.4 <1.21.5");
        assertThat(result.effectiveMinecraft("mc121x").toPredicates())
                .containsExactly(">=1.21.0 <1.21.4", ">=1.21.5 <1.22.0");
        assertThat(result.effectiveMinecraft("mcall").toPredicates())
                .containsExactly(">=1.20.0 <1.21.0", ">=1.22.0");
    }

    @Test
    @DisplayName("cell subtraction yields at most three pieces and never overlaps")
    void cellSubtractionIsExact() {
        Domain.Cell outer = new Domain.Cell(
                dev.fabricmultiloader.format.version.VersionRange.parse(">=1.20 <1.23"),
                dev.fabricmultiloader.format.version.VersionRange.parse(">=17"),
                EnvironmentConstraint.BOTH);
        Domain.Cell inner = new Domain.Cell(
                dev.fabricmultiloader.format.version.VersionRange.parse(">=1.21 <1.22"),
                dev.fabricmultiloader.format.version.VersionRange.parse(">=21"),
                EnvironmentConstraint.CLIENT);

        List<Domain.Cell> remainder = outer.subtract(inner);

        assertThat(remainder).hasSizeLessThanOrEqualTo(3);
        for (int i = 0; i < remainder.size(); i++) {
            assertThat(remainder.get(i).intersects(inner)).as("piece " + i + " must not overlap")
                    .isFalse();
            for (int j = i + 1; j < remainder.size(); j++) {
                assertThat(remainder.get(i).intersects(remainder.get(j)))
                        .as("pieces must not overlap each other").isFalse();
            }
        }
        Domain restored = Domain.of(remainder).union(Domain.of(Arrays.asList(outer.intersect(inner))));
        assertThat(restored.subtract(Domain.of(Arrays.asList(outer))).isEmpty()).isTrue();
    }
}
