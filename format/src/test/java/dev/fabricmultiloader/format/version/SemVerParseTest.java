package dev.fabricmultiloader.format.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Covers every normalisation rule of chapter 12.2, one case per row. */
class SemVerParseTest {

    @ParameterizedTest
    @CsvSource({
        // input                    major minor patch
        "1.20.1,                    1,    20,   1",
        "1.21,                      1,    21,   0",
        "1.21.4,                    1,    21,   4",
        "26.2,                      26,   2,    0",
        "26.1,                      26,   1,    0",
        "0.16.9,                    0,    16,   9",
        "21.0.4,                    21,   0,    4",
        "21,                        21,   0,    0",
        "8,                         8,    0,    0",
        "1.20.1+build.10,           1,    20,   1",
        "1.21.5-alpha.24.45.a,      1,    21,   5",
        "1.21.4-rc.1,               1,    21,   4",
        "1.21.4-pre1,               1,    21,   4",
        "v1.21.4,                   1,    21,   4",
        "'  1.21.4  ',              1,    21,   4",
    })
    @DisplayName("the documented normalisation table, verbatim")
    void normalisesComponents(String input, int major, int minor, int patch) {
        SemVer parsed = SemVer.parse(input);
        assertThat(parsed.major()).as("major of " + input).isEqualTo(major);
        assertThat(parsed.minor()).as("minor of " + input).isEqualTo(minor);
        assertThat(parsed.patch()).as("patch of " + input).isEqualTo(patch);
    }

    @Test
    @DisplayName("Fabric's snapshot normal form keeps every prerelease identifier")
    void parsesSnapshotPrereleases() {
        SemVer snapshot = SemVer.parse("1.21.5-alpha.24.45.a");
        assertThat(snapshot.isPrerelease()).isTrue();
        assertThat(snapshot.prerelease()).containsExactly("alpha", "24", "45", "a");

        assertThat(SemVer.parse("1.21.4-rc.1").prerelease()).containsExactly("rc", "1");
        assertThat(SemVer.parse("1.21.4-pre1").prerelease()).containsExactly("pre1");
    }

    @Test
    @DisplayName("build metadata is kept for display but never affects identity")
    void buildMetadataIsComparisonNeutral() {
        SemVer withMc = SemVer.parse("2.0.0+mc1.21.4");
        SemVer plain = SemVer.parse("2.0.0");

        assertThat(withMc.build()).isEqualTo("mc1.21.4");
        assertThat(withMc).isEqualTo(plain);
        assertThat(withMc.compareTo(plain)).isZero();
        assertThat(withMc.toString()).isEqualTo("2.0.0+mc1.21.4");
    }

    @Test
    @DisplayName("payload versions differing only in +mc metadata are the same version")
    void payloadVersionsAreEqualAcrossMetadata() {
        assertThat(SemVer.parse("2.0.0+mc1.20.1")).isEqualTo(SemVer.parse("2.0.0+mc1.21.4"));
    }

    @Test
    @DisplayName("legacy Java 1.8.0_402 becomes 8.0.402 — keyed on the underscore")
    void normalisesLegacyJavaVersions() {
        SemVer java8 = SemVer.parse("1.8.0_402");
        assertThat(java8.major()).isEqualTo(8);
        assertThat(java8.minor()).isEqualTo(0);
        assertThat(java8.patch()).isEqualTo(402);
    }

    @Test
    @DisplayName("Minecraft 1.8.0 must NOT be mistaken for Java 8")
    void doesNotMisreadMinecraftOneDotEight() {
        assertThat(SemVer.parse("1.8.0").major()).isEqualTo(1);
        assertThat(SemVer.parse("1.8").major()).isEqualTo(1);
        assertThat(SemVer.parse("1.8.9").major()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "abc",
        "1.2.3.4",
        "1..2",
        "1.-2.3",
        "01.2.3",
        "1.02.3",
        "-1.2.3",
        "1.2.3-",
        "1.2.3+",
        "1.2.3-01",
        "1.2.3-alpha..1",
        "1.2.3-alpha!",
        "1.2.x",
    })
    void rejectsMalformedVersions(String input) {
        assertThat(SemVer.isParseable(input)).as(input).isFalse();
        assertThatThrownBy(() -> SemVer.parse(input))
                .isInstanceOf(OmniException.class)
                .satisfies(thrown ->
                        assertThat(((OmniException) thrown).code()).isEqualTo(ErrorCode.OMNI_3010));
    }

    @Test
    void rejectsNull() {
        assertThat(SemVer.isParseable(null)).isFalse();
        assertThatThrownBy(() -> SemVer.parse(null)).isInstanceOf(OmniException.class);
    }

    @Test
    @DisplayName("lenient parsing degrades to UNKNOWN rather than stopping the bootstrap")
    void lenientParsingNeverThrows() {
        assertThat(SemVer.parseLenient("not a version")).isEqualTo(SemVer.UNKNOWN);
        assertThat(SemVer.parseLenient(null)).isEqualTo(SemVer.UNKNOWN);
        assertThat(SemVer.parseLenient("")).isEqualTo(SemVer.UNKNOWN);
        assertThat(SemVer.parseLenient("1.21.4")).isEqualTo(SemVer.of(1, 21, 4));
        assertThat(SemVer.UNKNOWN.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("UNKNOWN sorts below everything, so it satisfies no minimum requirement")
    void unknownSortsLowest() {
        assertThat(SemVer.UNKNOWN.isLowerThan(SemVer.of(0, 0, 0))).isTrue();
        assertThat(SemVer.UNKNOWN.isLowerThan(SemVer.parse("1.20.1"))).isTrue();
        assertThat(VersionRange.parse(">=0.0.1").test(SemVer.UNKNOWN)).isFalse();
    }

    @Test
    void toStringRoundTrips() {
        String[] inputs = {"1.21.4", "1.21.5-alpha.24.45.a", "2.0.0+mc1.21.4", "26.1", "0.16.9"};
        for (String input : inputs) {
            SemVer parsed = SemVer.parse(input);
            assertThat(SemVer.parse(parsed.toString())).as(input).isEqualTo(parsed);
        }
        assertThat(SemVer.parse("1.21").toString()).isEqualTo("1.21.0");
    }

    @Test
    void derivedVersionsAreCorrect() {
        SemVer version = SemVer.parse("1.20.1");
        assertThat(version.nextPatch()).isEqualTo(SemVer.of(1, 20, 2));
        assertThat(version.nextMinor()).isEqualTo(SemVer.of(1, 21, 0));
        assertThat(version.nextMajor()).isEqualTo(SemVer.of(2, 0, 0));
        assertThat(SemVer.parse("1.21.4-rc.1").toRelease()).isEqualTo(SemVer.of(1, 21, 4));
        assertThat(SemVer.ofMajor(21)).isEqualTo(SemVer.of(21, 0, 0));
    }

    @Test
    @DisplayName("prerelease() returns a copy — callers cannot corrupt an interned version")
    void prereleaseArrayIsDefensivelyCopied() {
        SemVer version = SemVer.parse("1.21.4-rc.1");
        version.prerelease()[0] = "tampered";
        assertThat(version.prerelease()).containsExactly("rc", "1");
    }
}
