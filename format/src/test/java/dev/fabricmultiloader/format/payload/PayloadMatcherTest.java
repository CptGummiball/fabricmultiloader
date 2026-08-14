package dev.fabricmultiloader.format.payload;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.ManifestFixtures;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayloadMatcherTest {

    private static final ContainerManifest MANIFEST = ManifestFixtures.exampleManifest();

    private static Environment.Builder environment(String minecraft, int java) {
        return Environment.builder()
                .minecraft(minecraft)
                .fabricLoader("0.16.9")
                .javaMajor(java)
                .side(Side.CLIENT)
                .mod("fabric-api", "0.114.0");
    }

    @Test
    @DisplayName("exactly one payload of the reference matrix matches each target version")
    void selectsOnePayloadPerTargetVersion() {
        assertSelects("1.20.1", 17, "0.92.2", "mc1201");
        assertSelects("1.21", 21, "0.102.0", "mc1211");
        assertSelects("1.21.1", 21, "0.102.0", "mc1211");
        assertSelects("1.21.4", 21, "0.114.0", "mc1214");
    }

    private void assertSelects(String minecraft, int java, String fabricApi, String expectedId) {
        Environment env = environment(minecraft, java).mod("fabric-api", fabricApi).build();
        ResolutionReport report = PayloadResolver.resolve(MANIFEST, env);

        assertThat(report.isResolved()).as(minecraft + " should resolve").isTrue();
        assertThat(report.selected().id()).isEqualTo(expectedId);
        assertThat(report.render()).isNull();
    }

    @Test
    @DisplayName("an unsupported Minecraft version rejects every payload on the Minecraft axis")
    void unsupportedMinecraftRejectsEverything() {
        ResolutionReport report = PayloadResolver.resolve(
                MANIFEST, environment("1.22.3", 21).build());

        assertThat(report.isUnmatched()).isTrue();
        assertThat(report.errorCode()).isEqualTo(
                dev.fabricmultiloader.format.error.ErrorCode.OMNI_2003);
        for (MatchResult result : report.results()) {
            assertThat(result.failedOnDomain()).as(result.payload().id()).isTrue();
            assertThat(result.primaryRejection().constraint())
                    .isEqualTo(Rejection.Constraint.MINECRAFT);
        }
    }

    @Test
    @DisplayName("the interesting case: Minecraft matches but Fabric API is too old")
    void reportsTheRealReasonWhenOnlyAFilterFails() {
        Environment env = environment("1.21.4", 21).mod("fabric-api", "0.110.0").build();
        ResolutionReport report = PayloadResolver.resolve(MANIFEST, env);

        assertThat(report.isUnmatched()).isTrue();

        MatchResult mc1214 = resultFor(report, "mc1214");
        assertThat(mc1214.failedOnDomain()).isFalse();
        assertThat(mc1214.rejections()).hasSize(1);

        Rejection rejection = mc1214.rejections().get(0);
        assertThat(rejection.constraint()).isEqualTo(Rejection.Constraint.MOD);
        assertThat(rejection.subject()).isEqualTo("fabric-api");
        assertThat(rejection.actual()).isEqualTo("0.110.0");
        assertThat(rejection.isMissing()).isFalse();
        assertThat(rejection.describe())
                .isEqualTo("fabric-api >=0.114.0 — REJECTED: 0.110.0 found");
    }

    @Test
    @DisplayName("a missing dependency reads differently from an outdated one")
    void distinguishesMissingFromOutdated() {
        Environment env = Environment.builder()
                .minecraft("1.21.4").fabricLoader("0.16.9").javaMajor(21).side(Side.SERVER).build();

        MatchResult result = PayloadMatcher.match(MANIFEST.payloadById("mc1214"), env);

        Rejection rejection = result.rejections().get(0);
        assertThat(rejection.isMissing()).isTrue();
        assertThat(rejection.describe()).endsWith("REJECTED: not installed");
    }

    @Test
    @DisplayName("the wrong Java version is reported even when Minecraft matches")
    void reportsJavaMismatch() {
        Environment env = environment("1.21.4", 17).build();
        MatchResult result = PayloadMatcher.match(MANIFEST.payloadById("mc1214"), env);

        assertThat(result.isMatch()).isFalse();
        Rejection java = result.rejections().get(0);
        assertThat(java.constraint()).isEqualTo(Rejection.Constraint.JAVA);
        assertThat(java.actual()).isEqualTo("17");
    }

    @Test
    @DisplayName("every failing requirement is collected, not just the first")
    void collectsAllRejections() {
        Environment env = Environment.builder()
                .minecraft("1.19.2").fabricLoader("0.13.0").javaMajor(8).side(Side.SERVER).build();

        MatchResult result = PayloadMatcher.match(MANIFEST.payloadById("mc1214"), env);

        assertThat(result.rejections()).hasSize(4);
        assertThat(result.rejections()).extracting(Rejection::constraint).containsExactly(
                Rejection.Constraint.MINECRAFT,
                Rejection.Constraint.JAVA,
                Rejection.Constraint.FABRIC_LOADER,
                Rejection.Constraint.MOD);
    }

    @Test
    @DisplayName("a client-only payload is rejected on a dedicated server")
    void respectsTheSideConstraint() {
        PayloadDescriptor clientOnly = PayloadDescriptor.builder()
                .id("mcclient")
                .modId("mod-mcclient")
                .modVersion("1.0.0")
                .file("META-INF/jars/client.jar")
                .classfileMajor(65)
                .platformFactory("com.example.mcclient.Factory")
                .packages("com.example.mcclient")
                .requires(dev.fabricmultiloader.format.manifest.Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5")
                        .environment(dev.fabricmultiloader.format.manifest.EnvironmentConstraint.CLIENT)
                        .build())
                .build();

        Environment server = Environment.builder()
                .minecraft("1.21.4").javaMajor(21).side(Side.SERVER).build();
        Environment client = Environment.builder()
                .minecraft("1.21.4").javaMajor(21).side(Side.CLIENT).build();

        assertThat(PayloadMatcher.match(clientOnly, server).isMatch()).isFalse();
        assertThat(PayloadMatcher.match(clientOnly, server).primaryRejection().constraint())
                .isEqualTo(Rejection.Constraint.ENVIRONMENT);
        assertThat(PayloadMatcher.match(clientOnly, client).isMatch()).isTrue();
    }

    @Test
    @DisplayName("optional dependencies never affect selection but are reported")
    void optionalModsAreReportedNotEnforced() {
        Environment env = environment("1.21.4", 21).build();
        PayloadDescriptor payload = MANIFEST.payloadById("mc1214");

        assertThat(PayloadMatcher.match(payload, env).isMatch()).isTrue();
        assertThat(PayloadMatcher.inactiveOptionalMods(payload, env))
                .extracting(Rejection::subject).containsExactly("modmenu");

        Environment withModMenu = environment("1.21.4", 21).mod("modmenu", "13.0.0").build();
        assertThat(PayloadMatcher.inactiveOptionalMods(payload, withModMenu)).isEmpty();
    }

    @Test
    @DisplayName("the loader's verdict is what the runtime acts on, not our own re-evaluation")
    void selectionFollowsTheLoader() {
        Environment env = environment("1.21.4", 21).build();

        assertThat(PayloadResolver.selectedByLoader(MANIFEST, env,
                java.util.Arrays.asList("minecraft", "fabric-api", "examplemod-mc1214")))
                .extracting(PayloadDescriptor::id).containsExactly("mc1214");

        assertThat(PayloadResolver.selectedByLoader(MANIFEST, env,
                java.util.Arrays.asList("minecraft"))).isEmpty();
    }

    private static MatchResult resultFor(ResolutionReport report, String payloadId) {
        for (MatchResult result : report.results()) {
            if (result.payload().id().equals(payloadId)) {
                return result;
            }
        }
        throw new AssertionError("no result for payload " + payloadId);
    }
}
