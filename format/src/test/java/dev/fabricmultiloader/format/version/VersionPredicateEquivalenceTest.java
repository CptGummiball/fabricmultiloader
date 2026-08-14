package dev.fabricmultiloader.format.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Differential test against the real Fabric Loader implementation.
 *
 * <p>The entire architecture rests on Fabric Loader and FabricMultiLoader reaching the same verdict
 * about which payload a constraint selects: the loader performs the actual selection, while the
 * validator proves at build time that the selection is unambiguous. If the two implementations
 * disagreed about a single predicate, a build could pass its disjointness proof and still load two
 * payloads — or none — on a player's machine.
 *
 * <p>So the agreement is verified rather than assumed, on thousands of generated pairs. Inputs one
 * side rejects are skipped: the point is not that the two parsers accept the same language, but
 * that where both understand an input, they answer identically.
 */
class VersionPredicateEquivalenceTest {

    private static final String[] PREDICATES = {
        "*",
        "=1.20.1", "1.20.1",
        ">=1.20.1", ">1.20.1", "<=1.20.1", "<1.20.1",
        ">=1.21", "<1.22", ">=1.21.4", "<1.21.5",
        ">=1.20.1 <1.20.2",
        ">=1.21 <1.21.2",
        ">=1.21.4 <1.21.5",
        ">=0.14.21", ">=0.15.11", ">=0.16.9", ">=0.17.0",
        ">=17", ">=21", ">=25",
        ">=0.92.2", ">=0.102.0", ">=0.114.0", ">=0.130.0",
        ">=26.1 <26.2",
        ">=1.21.4-rc.1",
        "~1.20.1", "^1.20.1", "^0.16.9",
    };

    private static final String[] VERSIONS = {
        "1.16.5", "1.18.2", "1.20.1", "1.20.2", "1.20.4", "1.20.6",
        "1.21.0", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.22.0",
        "26.1.0", "26.2.0", "27.0.0",
        "0.14.21", "0.15.11", "0.16.9", "0.16.14", "0.17.0",
        "17.0.0", "21.0.0", "25.0.0",
        "0.92.2", "0.102.0", "0.114.0", "0.130.0",
        "1.21.4-rc.1", "1.21.5-alpha.24.45.a", "2.0.0",
    };

    @Test
    @DisplayName("both implementations agree on every predicate/version pair they both understand")
    void agreesWithFabricLoaderOnCuratedPairs() {
        List<String> disagreements = new ArrayList<String>();
        int compared = 0;

        for (String predicateText : PREDICATES) {
            for (String versionText : VERSIONS) {
                Boolean ours = evaluateOurs(predicateText, versionText);
                Boolean theirs = evaluateFabric(predicateText, versionText);
                if (ours == null || theirs == null) {
                    continue;
                }
                compared++;
                if (!ours.equals(theirs)) {
                    disagreements.add(predicateText + " vs " + versionText
                            + " -> ours=" + ours + " fabric=" + theirs);
                }
            }
        }

        assertThat(compared).as("comparable pairs").isGreaterThan(500);
        assertThat(disagreements).as("predicate evaluation disagreements").isEmpty();
    }

    @Test
    @DisplayName("agreement holds for thousands of generated pairs, not just curated ones")
    void agreesWithFabricLoaderOnGeneratedPairs() {
        Random random = new Random(20260811L);
        List<String> disagreements = new ArrayList<String>();
        int compared = 0;

        for (int i = 0; i < 4096; i++) {
            String versionText = randomVersion(random);
            String predicateText = randomPredicate(random);

            Boolean ours = evaluateOurs(predicateText, versionText);
            Boolean theirs = evaluateFabric(predicateText, versionText);
            if (ours == null || theirs == null) {
                continue;
            }
            compared++;
            if (!ours.equals(theirs)) {
                disagreements.add(predicateText + " vs " + versionText
                        + " -> ours=" + ours + " fabric=" + theirs);
            }
        }

        assertThat(compared).as("comparable pairs").isGreaterThan(3000);
        assertThat(disagreements).as("predicate evaluation disagreements").isEmpty();
    }

    @Test
    @DisplayName("version ordering matches Fabric's, including prereleases")
    void agreesWithFabricLoaderOnOrdering() {
        List<String> disagreements = new ArrayList<String>();
        int compared = 0;

        for (String leftText : VERSIONS) {
            for (String rightText : VERSIONS) {
                SemanticVersion left = parseFabric(leftText);
                SemanticVersion right = parseFabric(rightText);
                if (left == null || right == null) {
                    continue;
                }
                if (!SemVer.isParseable(leftText) || !SemVer.isParseable(rightText)) {
                    continue;
                }
                compared++;
                int ours = Integer.signum(SemVer.parse(leftText).compareTo(SemVer.parse(rightText)));
                int theirs = Integer.signum(left.compareTo((net.fabricmc.loader.api.Version) right));
                if (ours != theirs) {
                    disagreements.add(leftText + " vs " + rightText
                            + " -> ours=" + ours + " fabric=" + theirs);
                }
            }
        }

        assertThat(compared).as("comparable pairs").isGreaterThan(800);
        assertThat(disagreements).as("ordering disagreements").isEmpty();
    }

    @Test
    @DisplayName("everything the generator emits is understood by the real loader")
    void generatedPredicatesAreAcceptedByFabric() {
        List<String> rejected = new ArrayList<String>();

        List<VersionRange> ranges = Arrays.asList(
                VersionRange.parse(">=1.20.1 <1.20.2"),
                VersionRange.parse(">=1.21 <1.21.2"),
                VersionRange.parse(">=1.21.4 <1.21.5"),
                VersionRange.parse(">=26.1 <26.2"),
                VersionRange.parse(">=1.21").subtract(VersionRange.parse(">=1.21.4 <1.21.5")),
                MinecraftVersions.between(SemVer.of(1, 21, 4), SemVer.of(1, 21, 5), true),
                VersionRange.exactly(SemVer.of(1, 20, 1)),
                VersionRange.ALL);

        for (VersionRange range : ranges) {
            for (String predicate : range.toPredicates()) {
                if (!isAcceptedByFabric(predicate)) {
                    rejected.add(predicate);
                }
            }
        }

        assertThat(rejected).as("predicates the loader would refuse to parse").isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    /** @return our verdict, or {@code null} if we cannot parse the inputs */
    private static Boolean evaluateOurs(String predicateText, String versionText) {
        if (!VersionPredicateParser.isValid(predicateText) || !SemVer.isParseable(versionText)) {
            return null;
        }
        return Boolean.valueOf(
                VersionPredicateParser.parse(predicateText).test(SemVer.parse(versionText)));
    }

    /** @return Fabric's verdict, or {@code null} if it cannot parse the inputs */
    private static Boolean evaluateFabric(String predicateText, String versionText) {
        SemanticVersion version = parseFabric(versionText);
        if (version == null) {
            return null;
        }
        try {
            return Boolean.valueOf(
                    net.fabricmc.loader.api.metadata.version.VersionPredicate
                            .parse(predicateText)
                            .test(version));
        } catch (VersionParsingException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isAcceptedByFabric(String predicateText) {
        try {
            net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(predicateText);
            return true;
        } catch (VersionParsingException | RuntimeException e) {
            return false;
        }
    }

    private static SemanticVersion parseFabric(String versionText) {
        try {
            return SemanticVersion.parse(versionText);
        } catch (VersionParsingException | RuntimeException e) {
            return null;
        }
    }

    private static String randomVersion(Random random) {
        StringBuilder text = new StringBuilder();
        text.append(random.nextInt(28)).append('.')
                .append(random.nextInt(25)).append('.')
                .append(random.nextInt(10));
        int shape = random.nextInt(6);
        if (shape == 1) {
            text.append("-rc.").append(random.nextInt(4) + 1);
        } else if (shape == 2) {
            text.append("-alpha.").append(random.nextInt(30) + 1).append(".a");
        } else if (shape == 3) {
            text.append("-beta.").append(random.nextInt(12) + 1);
        }
        return text.toString();
    }

    private static String randomPredicate(Random random) {
        String[] operators = {"=", ">=", ">", "<=", "<", "~", "^"};
        String first = operators[random.nextInt(operators.length)] + randomBound(random);
        if (random.nextInt(3) == 0) {
            return first + " " + operators[random.nextInt(5)] + randomBound(random);
        }
        return first;
    }

    private static String randomBound(Random random) {
        StringBuilder text = new StringBuilder();
        text.append(random.nextInt(28)).append('.').append(random.nextInt(25));
        if (random.nextBoolean()) {
            text.append('.').append(random.nextInt(10));
        }
        if (random.nextInt(8) == 0) {
            text.append("-rc.").append(random.nextInt(4) + 1);
        }
        return text.toString();
    }
}
