package dev.fabricmultiloader.format.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SemVerCompareTest {

    @ParameterizedTest
    @CsvSource({
        "1.20.1,    1.20.2",
        "1.20.9,    1.21.0",
        "1.21.4,    1.22.0",
        "1.21.4,    26.1",
        "26.1,      26.2",
        "26.1,      26.10",
        "26.10,     27.0",
        "0.16.9,    0.16.10",
        "0.14.21,   0.15.11",
    })
    @DisplayName("ordering is numeric per component, not lexical")
    void ordersReleases(String lower, String higher) {
        assertThat(SemVer.parse(lower).isLowerThan(SemVer.parse(higher)))
                .as(lower + " < " + higher).isTrue();
        assertThat(SemVer.parse(higher).isHigherThan(SemVer.parse(lower))).isTrue();
    }

    @Test
    @DisplayName("the 1.21.x to 26.1 scheme change needs no special case")
    void handlesTheSchemeChange() {
        List<SemVer> versions = new ArrayList<SemVer>(Arrays.asList(
                SemVer.parse("26.1"),
                SemVer.parse("1.20.1"),
                SemVer.parse("1.21.4"),
                SemVer.parse("27.0"),
                SemVer.parse("1.16.5"),
                SemVer.parse("26.10")));
        Collections.sort(versions);

        assertThat(toStrings(versions)).containsExactly(
                "1.16.5", "1.20.1", "1.21.4", "26.1.0", "26.10.0", "27.0.0");
    }

    @Test
    @DisplayName("a prerelease sorts below its own release — this is why >=1.21.4 excludes snapshots")
    void prereleasesSortBelowReleases() {
        assertThat(SemVer.parse("1.21.4-rc.1").isLowerThan(SemVer.parse("1.21.4"))).isTrue();
        assertThat(SemVer.parse("1.21.5-alpha.24.45.a").isLowerThan(SemVer.parse("1.21.5"))).isTrue();
        assertThat(SemVer.parse("1.21.4-rc.1").isHigherThan(SemVer.parse("1.21.3"))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        // SemVer 2.0.0 section 11 examples
        "1.0.0-alpha,            1.0.0-alpha.1",
        "1.0.0-alpha.1,          1.0.0-alpha.beta",
        "1.0.0-alpha.beta,       1.0.0-beta",
        "1.0.0-beta,             1.0.0-beta.2",
        "1.0.0-beta.2,           1.0.0-beta.11",
        "1.0.0-beta.11,          1.0.0-rc.1",
        "1.0.0-rc.1,             1.0.0",
    })
    @DisplayName("prerelease ordering follows SemVer 2.0.0 section 11 exactly")
    void ordersPrereleaseIdentifiers(String lower, String higher) {
        assertThat(SemVer.parse(lower).isLowerThan(SemVer.parse(higher)))
                .as(lower + " < " + higher).isTrue();
    }

    @Test
    @DisplayName("numeric identifiers compare numerically, so beta.11 beats beta.2")
    void numericIdentifiersAreNotCompiledLexically() {
        assertThat(SemVer.parse("1.0.0-beta.2").isLowerThan(SemVer.parse("1.0.0-beta.11"))).isTrue();
        assertThat(SemVer.parse("1.21.5-alpha.24.9.a")
                .isLowerThan(SemVer.parse("1.21.5-alpha.24.45.a"))).isTrue();
    }

    @Test
    @DisplayName("numeric identifiers sort before alphanumeric ones")
    void numericSortsBeforeAlphanumeric() {
        assertThat(SemVer.parse("1.0.0-1").isLowerThan(SemVer.parse("1.0.0-alpha"))).isTrue();
    }

    @Test
    @DisplayName("a shorter prerelease set sorts below a longer one with the same prefix")
    void shorterPrereleaseSetSortsFirst() {
        assertThat(SemVer.parse("1.0.0-alpha").isLowerThan(SemVer.parse("1.0.0-alpha.1"))).isTrue();
    }

    @Test
    void equalityIgnoresBuildMetadataButNotPrerelease() {
        assertThat(SemVer.parse("1.21.4+a")).isEqualTo(SemVer.parse("1.21.4+b"));
        assertThat(SemVer.parse("1.21.4-rc.1")).isNotEqualTo(SemVer.parse("1.21.4"));
        assertThat(SemVer.parse("1.21")).isEqualTo(SemVer.parse("1.21.0"));
    }

    @Test
    void equalVersionsShareAHashCode() {
        assertThat(SemVer.parse("1.21.4+a").hashCode()).isEqualTo(SemVer.parse("1.21.4+b").hashCode());
        assertThat(SemVer.parse("1.21").hashCode()).isEqualTo(SemVer.of(1, 21, 0).hashCode());
    }

    @Test
    @DisplayName("the ordering is a total order: antisymmetric, transitive and consistent")
    void orderingIsTotalOverGeneratedVersions() {
        Random random = new Random(20260811L);
        List<SemVer> sample = new ArrayList<SemVer>();
        for (int i = 0; i < 200; i++) {
            sample.add(randomVersion(random));
        }

        for (SemVer a : sample) {
            for (SemVer b : sample) {
                int forward = a.compareTo(b);
                int backward = b.compareTo(a);
                assertThat(Integer.signum(forward))
                        .as(a + " vs " + b).isEqualTo(-Integer.signum(backward));
                assertThat(a.equals(b)).isEqualTo(forward == 0);
            }
        }

        List<SemVer> sorted = new ArrayList<SemVer>(sample);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i - 1).compareTo(sorted.get(i))).isLessThanOrEqualTo(0);
        }
    }

    private static SemVer randomVersion(Random random) {
        StringBuilder text = new StringBuilder();
        text.append(random.nextInt(28)).append('.')
                .append(random.nextInt(30)).append('.')
                .append(random.nextInt(12));
        int shape = random.nextInt(4);
        if (shape == 1) {
            text.append("-rc.").append(random.nextInt(5) + 1);
        } else if (shape == 2) {
            text.append("-alpha.").append(random.nextInt(30) + 1).append(".a");
        }
        if (random.nextBoolean()) {
            text.append("+build.").append(random.nextInt(50));
        }
        return SemVer.parse(text.toString());
    }

    private static List<String> toStrings(List<SemVer> versions) {
        List<String> out = new ArrayList<String>(versions.size());
        for (SemVer version : versions) {
            out.add(version.toString());
        }
        return out;
    }
}
