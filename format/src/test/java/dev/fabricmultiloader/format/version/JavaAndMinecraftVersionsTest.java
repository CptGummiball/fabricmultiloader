package dev.fabricmultiloader.format.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaAndMinecraftVersionsTest {

    @Nested
    @DisplayName("Java feature versions and class file majors")
    class Java {

        @ParameterizedTest
        @CsvSource({"8, 52", "11, 55", "17, 61", "21, 65", "25, 69", "30, 74"})
        @DisplayName("major = feature + 44, in both directions, including Java 25 -> 69")
        void conversionRoundTrips(int feature, int classFileMajor) {
            assertThat(JavaVersions.classFileMajor(feature)).isEqualTo(classFileMajor);
            assertThat(JavaVersions.featureVersionOf(classFileMajor)).isEqualTo(feature);
        }

        @Test
        @DisplayName("the three Java levels of the reference matrix")
        void coversTheReferenceMatrix() {
            assertThat(JavaVersions.classFileMajor(17)).isEqualTo(61);  // 1.18 - 1.20.4
            assertThat(JavaVersions.classFileMajor(21)).isEqualTo(65);  // 1.20.5 - 1.21.x
            assertThat(JavaVersions.classFileMajor(25)).isEqualTo(69);  // 26.1+
            assertThat(JavaVersions.BASELINE_CLASS_FILE_MAJOR)
                    .isEqualTo(JavaVersions.classFileMajor(JavaVersions.BASELINE_FEATURE_VERSION));
        }

        @ParameterizedTest
        @CsvSource({
            "1.8,        8",
            "1.8.0_402,  8",
            "8,          8",
            "11,         11",
            "17,         17",
            "21,         21",
            "21.0.7,     21",
            "25,         25",
            "25.0.1,     25",
        })
        void parsesJavaVersionStrings(String text, int expected) {
            assertThat(JavaVersions.parseFeatureVersion(text)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an unreadable property degrades to the baseline instead of throwing")
        void unreadableVersionsFallBack() {
            assertThat(JavaVersions.parseFeatureVersion(null))
                    .isEqualTo(JavaVersions.BASELINE_FEATURE_VERSION);
            assertThat(JavaVersions.parseFeatureVersion(""))
                    .isEqualTo(JavaVersions.BASELINE_FEATURE_VERSION);
            assertThat(JavaVersions.parseFeatureVersion("unknown"))
                    .isEqualTo(JavaVersions.BASELINE_FEATURE_VERSION);
        }

        @Test
        void rejectsImpossibleVersions() {
            assertThatThrownBy(() -> JavaVersions.classFileMajor(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JavaVersions.featureVersionOf(44))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a feature version is testable against a depends.java predicate")
        void featureVersionsWorkWithPredicates() {
            assertThat(VersionRange.parse(">=21").test(JavaVersions.asVersion(21))).isTrue();
            assertThat(VersionRange.parse(">=21").test(JavaVersions.asVersion(25))).isTrue();
            assertThat(VersionRange.parse(">=21").test(JavaVersions.asVersion(17))).isFalse();
            assertThat(VersionRange.parse(">=25").test(JavaVersions.asVersion(21))).isFalse();
        }

        @Test
        @DisplayName("the JVM running this test reports a plausible feature version")
        void currentMajorIsPlausible() {
            assertThat(JavaVersions.currentMajor()).isGreaterThanOrEqualTo(8).isLessThan(100);
        }
    }

    @Nested
    @DisplayName("Minecraft versions and ordinals")
    class Minecraft {

        @ParameterizedTest
        @CsvSource({
            "1.16.5,  11605",
            "1.20.1,  12001",
            "1.21,    12100",
            "1.21.4,  12104",
            "1.21.5,  12105",
            "26.1,    260100",
            "26.2,    260200",
            "26.10,   261000",
            "27.0,    270000",
        })
        @DisplayName("the compact ordinal encoding, including the 26.1 scheme change")
        void encodesOrdinals(String version, int ordinal) {
            assertThat(MinecraftVersions.ordinal(version)).as(version).isEqualTo(ordinal);
        }

        @Test
        @DisplayName("ordinals are strictly monotonic across the scheme change")
        void ordinalsAreMonotonic() {
            List<String> ascending = Arrays.asList(
                    "1.16.5", "1.18.2", "1.20.1", "1.20.4", "1.21", "1.21.1", "1.21.4",
                    "1.21.5", "1.22", "26.1", "26.2", "26.10", "27.0");

            List<Integer> ordinals = new ArrayList<Integer>();
            for (String version : ascending) {
                ordinals.add(MinecraftVersions.ordinal(version));
            }
            for (int i = 1; i < ordinals.size(); i++) {
                assertThat(ordinals.get(i))
                        .as(ascending.get(i - 1) + " -> " + ascending.get(i))
                        .isGreaterThan(ordinals.get(i - 1));
            }
        }

        @Test
        @DisplayName("a prerelease collapses onto its release ordinal")
        void prereleasesShareTheReleaseOrdinal() {
            assertThat(MinecraftVersions.ordinal("1.21.5-alpha.24.45.a"))
                    .isEqualTo(MinecraftVersions.ordinal("1.21.5"));
        }

        @Test
        @DisplayName("the encoding refuses input it cannot represent monotonically")
        void rejectsComponentsBeyondTheEncoding() {
            assertThatThrownBy(() -> MinecraftVersions.ordinal(SemVer.of(1, 100, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("monotonic");
            assertThatThrownBy(() -> MinecraftVersions.ordinal(SemVer.of(1, 21, 100)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parsingIsLenient() {
            assertThat(MinecraftVersions.parse("1.21.4")).isEqualTo(SemVer.of(1, 21, 4));
            assertThat(MinecraftVersions.parse("garbage")).isEqualTo(SemVer.UNKNOWN);
            assertThat(MinecraftVersions.ordinal((SemVer) null)).isZero();
        }

        @Test
        @DisplayName("release-line ranges match the matrix entries they represent")
        void buildsReleaseLineRanges() {
            VersionRange oneTwentyOne = MinecraftVersions.between(
                    SemVer.of(1, 21, 0), SemVer.of(1, 21, 2), false);

            assertThat(oneTwentyOne.toPredicates()).containsExactly(">=1.21.0 <1.21.2");
            assertThat(oneTwentyOne.test(SemVer.parse("1.21"))).isTrue();
            assertThat(oneTwentyOne.test(SemVer.parse("1.21.1"))).isTrue();
            assertThat(oneTwentyOne.test(SemVer.parse("1.21.2"))).isFalse();
        }
    }
}
