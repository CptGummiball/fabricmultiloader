package dev.fabricmultiloader.format.payload;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.ManifestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The report a user actually sees. Its wording is the reason the container deliberately does not
 * hard-depend on its payload alias — Fabric would then refuse to load it with a message naming an
 * internal alias, which tells a player nothing.
 */
class ResolutionReportTest {

    private static final ContainerManifest MANIFEST = ManifestFixtures.exampleManifest();

    @Test
    @DisplayName("an unsupported Minecraft version lists what is supported and where to look")
    void unsupportedVersionReport() {
        Environment env = Environment.builder()
                .minecraft("1.22.3")
                .fabricLoader("0.17.4")
                .javaMajor(25)
                .side(Side.CLIENT)
                .mod("fabric-api", "0.131.0")
                .build();

        String report = PayloadResolver.resolve(MANIFEST, env).render();

        assertThat(report)
                .startsWith("OMNI-2003  no payload matches this environment")
                .contains("Minecraft      1.22.3")
                .contains("Java           25")
                .contains("Side           client")
                .contains("Mod            examplemod 2.0.0")
                .contains("3 version-specific implementation(s)")
                .contains("payload  mc1201")
                .contains("Minecraft >=1.20.1 <1.20.2 — REJECTED: 1.22.3 found")
                .contains("Supported Minecraft versions:")
                .contains("https://modrinth.com/mod/examplemod")
                .contains("https://github.com/example/examplemod/issues")
                .endsWith("omni-2003\n");
    }

    @Test
    @DisplayName("a too-old Fabric API names the installed version and the required one")
    void outdatedDependencyReport() {
        Environment env = Environment.builder()
                .minecraft("1.21.4")
                .fabricLoader("0.16.9")
                .javaMajor(21)
                .side(Side.SERVER)
                .mod("fabric-api", "0.110.0")
                .build();

        String report = PayloadResolver.resolve(MANIFEST, env).render();

        assertThat(report)
                .contains("Fabric API     0.110.0")
                .contains("fabric-api >=0.114.0 — REJECTED: 0.110.0 found");
    }

    @Test
    @DisplayName("an absent Fabric API is shown as not installed, not as version 0")
    void missingDependencyReport() {
        Environment env = Environment.builder()
                .minecraft("1.21.4").fabricLoader("0.16.9").javaMajor(21).side(Side.SERVER).build();

        String report = PayloadResolver.resolve(MANIFEST, env).render();

        assertThat(report)
                .contains("Fabric API     not installed")
                .contains("fabric-api >=0.114.0 — REJECTED: not installed");
    }

    @Test
    @DisplayName("a resolved environment produces no report at all")
    void resolvedProducesNoReport() {
        Environment env = Environment.builder()
                .minecraft("1.21.4").fabricLoader("0.16.9").javaMajor(21).side(Side.CLIENT)
                .mod("fabric-api", "0.114.0").build();

        ResolutionReport report = PayloadResolver.resolve(MANIFEST, env);

        assertThat(report.isResolved()).isTrue();
        assertThat(report.errorCode()).isNull();
        assertThat(report.render()).isNull();
        assertThat(report.toString()).isEqualTo("resolved to mc1214");
    }

    @Test
    @DisplayName("several matching payloads point at a modified jar, and refuse to run")
    void ambiguousReport() {
        ContainerManifest broken = ContainerManifest.builder()
                .container(ManifestFixtures.exampleContainer())
                .payload(ManifestFixtures.payload("mcaa", "1.21.4", ">=1.21 <1.22", ">=21", 65, "*"))
                .payload(ManifestFixtures.payload("mcbb", "1.21.4", ">=1.21 <1.22", ">=21", 65, "*"))
                .build();

        Environment env = Environment.builder()
                .minecraft("1.21.4").fabricLoader("0.16.9").javaMajor(21).side(Side.CLIENT)
                .mod("fabric-api", "0.114.0").build();

        ResolutionReport report = PayloadResolver.resolve(broken, env);

        assertThat(report.isAmbiguous()).isTrue();
        assertThat(report.selected()).isNull();
        assertThat(report.errorCode()).isEqualTo(ErrorCode.OMNI_2004);
        assertThat(report.render())
                .startsWith("OMNI-2004  several payloads active simultaneously")
                .contains("A validated build cannot produce this")
                .contains("re-download the mod")
                .contains("validateUniversalJar");
    }
}
