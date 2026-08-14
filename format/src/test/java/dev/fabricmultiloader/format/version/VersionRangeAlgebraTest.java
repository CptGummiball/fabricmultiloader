package dev.fabricmultiloader.format.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The disjointness proof that makes payload selection deterministic is exact set arithmetic, so it
 * is tested as such: worked examples for the cases the resolver actually produces, plus algebraic
 * properties over hundreds of generated ranges.
 */
class VersionRangeAlgebraTest {

    private static final long SEED = 20260811L;

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        @DisplayName("overlapping intervals merge into one")
        void mergesOverlapping() {
            VersionRange range = VersionRange.parse(">=1.20 <1.22", ">=1.21 <1.23");
            assertThat(range.toPredicates()).containsExactly(">=1.20.0 <1.23.0");
        }

        @Test
        @DisplayName("touching intervals merge: [1.20,1.21) and [1.21,1.22) leave no gap")
        void mergesTouching() {
            VersionRange range = VersionRange.parse(">=1.20 <1.21", ">=1.21 <1.22");
            assertThat(range.toPredicates()).containsExactly(">=1.20.0 <1.22.0");
        }

        @Test
        @DisplayName("intervals that only look adjacent are kept apart: 1.21.0 belongs to neither")
        void doesNotMergeAcrossAnExcludedPoint() {
            VersionRange range = VersionRange.of(
                    Interval.lessThan(SemVer.of(1, 21, 0)),
                    Interval.greaterThan(SemVer.of(1, 21, 0)));
            assertThat(range.intervals()).hasSize(2);
            assertThat(range.test(SemVer.of(1, 21, 0))).isFalse();
        }

        @Test
        @DisplayName("the real matrix normalises to the container's depends.minecraft")
        void producesTheContainerUnion() {
            VersionRange union = VersionRange.parse(
                    ">=1.21.4 <1.21.5",
                    ">=1.20.1 <1.20.2",
                    ">=1.21 <1.21.2");

            assertThat(union.toPredicates()).containsExactly(
                    ">=1.20.1 <1.20.2",
                    ">=1.21.0 <1.21.2",
                    ">=1.21.4 <1.21.5");
        }

        @Test
        void dropsEmptyIntervals() {
            VersionRange range = VersionRange.of(
                    Interval.closedOpen(SemVer.of(1, 21, 0), SemVer.of(1, 21, 0)),
                    Interval.closedOpen(SemVer.of(1, 20, 1), SemVer.of(1, 20, 2)));
            assertThat(range.intervals()).hasSize(1);
        }

        @Test
        void emptyAndAllBehaveAsIdentities() {
            VersionRange range = VersionRange.parse(">=1.20.1 <1.20.2");
            assertThat(range.union(VersionRange.EMPTY)).isEqualTo(range);
            assertThat(range.intersect(VersionRange.ALL)).isEqualTo(range);
            assertThat(range.subtract(VersionRange.EMPTY)).isEqualTo(range);
            assertThat(range.subtract(VersionRange.ALL)).isEqualTo(VersionRange.EMPTY);
            assertThat(VersionRange.ALL.isAll()).isTrue();
            assertThat(VersionRange.EMPTY.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("subtraction — the mechanism behind payload priorities")
    class Subtraction {

        @Test
        @DisplayName("a specialised payload carves a hole out of a catch-all")
        void catchAllMinusSpecialised() {
            VersionRange catchAll = VersionRange.parse(">=1.21");
            VersionRange specialised = VersionRange.parse(">=1.21.4 <1.21.5");

            VersionRange effective = catchAll.subtract(specialised);

            assertThat(effective.toPredicates())
                    .containsExactly(">=1.21.0 <1.21.4", ">=1.21.5");
            assertThat(effective.test(SemVer.parse("1.21.4"))).isFalse();
            assertThat(effective.test(SemVer.parse("1.21.3"))).isTrue();
            assertThat(effective.test(SemVer.parse("1.21.5"))).isTrue();
            assertThat(effective.intersects(specialised)).isFalse();
            assertThat(effective.union(specialised)).isEqualTo(catchAll);
        }

        @Test
        @DisplayName("subtracting a prefix leaves only the tail")
        void subtractPrefix() {
            assertThat(VersionRange.parse(">=1.20 <1.22").subtract(VersionRange.parse("<1.21"))
                    .toPredicates()).containsExactly(">=1.21.0 <1.22.0");
        }

        @Test
        @DisplayName("subtracting a superset leaves nothing")
        void subtractSuperset() {
            assertThat(VersionRange.parse(">=1.21 <1.21.2").subtract(VersionRange.parse(">=1.20")))
                    .isEqualTo(VersionRange.EMPTY);
        }

        @Test
        @DisplayName("subtracting a single version splits a range at that point")
        void subtractExactVersion() {
            VersionRange result = VersionRange.parse(">=1.21 <1.22")
                    .subtract(VersionRange.exactly(SemVer.of(1, 21, 4)));

            assertThat(result.test(SemVer.of(1, 21, 4))).isFalse();
            assertThat(result.test(SemVer.of(1, 21, 3))).isTrue();
            assertThat(result.test(SemVer.of(1, 21, 5))).isTrue();
            assertThat(result.intervals()).hasSize(2);
        }

        @Test
        void subtractingItselfYieldsNothing() {
            VersionRange range = VersionRange.parse(">=1.20.1 <1.20.2", ">=1.21 <1.21.2");
            assertThat(range.subtract(range)).isEqualTo(VersionRange.EMPTY);
        }

        @Test
        void complementIsTheInverse() {
            VersionRange range = VersionRange.parse(">=1.21 <1.22");
            assertThat(range.complement().intersects(range)).isFalse();
            assertThat(range.complement().union(range)).isEqualTo(VersionRange.ALL);
        }
    }

    @Nested
    @DisplayName("prerelease boundaries")
    class Prereleases {

        @Test
        @DisplayName(">=1.21.4 excludes snapshots of 1.21.4 — a prerelease sorts below its release")
        void releaseBoundExcludesSnapshots() {
            VersionRange range = VersionRange.parse(">=1.21.4 <1.21.5");
            assertThat(range.test(SemVer.parse("1.21.4"))).isTrue();
            assertThat(range.test(SemVer.parse("1.21.4-rc.1"))).isFalse();
            assertThat(range.test(SemVer.parse("1.21.5-alpha.24.45.a"))).isTrue();
        }

        @Test
        @DisplayName("withLowestPrerelease() is how a range opts into snapshots")
        void lowestPrereleaseBoundIncludesSnapshots() {
            VersionRange withSnapshots = MinecraftVersions.between(
                    SemVer.of(1, 21, 4), SemVer.of(1, 21, 5), true);

            assertThat(withSnapshots.test(SemVer.parse("1.21.4-rc.1"))).isTrue();
            assertThat(withSnapshots.test(SemVer.parse("1.21.4-alpha.24.45.a"))).isTrue();
            assertThat(withSnapshots.test(SemVer.parse("1.21.4"))).isTrue();
            assertThat(withSnapshots.test(SemVer.parse("1.21.3"))).isFalse();
            assertThat(withSnapshots.toPredicates()).containsExactly(">=1.21.4-0 <1.21.5");
        }

        @Test
        void snapshotDetection() {
            assertThat(MinecraftVersions.isSnapshot(SemVer.parse("1.21.5-alpha.24.45.a"))).isTrue();
            assertThat(MinecraftVersions.isSnapshot(SemVer.parse("1.21.4-rc.1"))).isTrue();
            assertThat(MinecraftVersions.isSnapshot(SemVer.parse("1.21.4"))).isFalse();
            assertThat(MinecraftVersions.isSnapshot(SemVer.UNKNOWN)).isFalse();
        }
    }

    @Nested
    @DisplayName("algebraic properties over generated ranges")
    class Properties {

        @Test
        @DisplayName("union and intersection are commutative; a \\ a is empty")
        void basicIdentities() {
            Random random = new Random(SEED);
            for (int i = 0; i < 500; i++) {
                VersionRange a = randomRange(random);
                VersionRange b = randomRange(random);

                assertThat(a.union(b)).as("union commutative").isEqualTo(b.union(a));
                assertThat(a.intersect(b)).as("intersect commutative").isEqualTo(b.intersect(a));
                assertThat(a.subtract(a)).as("a \\ a").isEqualTo(VersionRange.EMPTY);
                assertThat(a.union(a)).as("a u a").isEqualTo(a);
                assertThat(a.intersect(a)).as("a n a").isEqualTo(a);
            }
        }

        @Test
        @DisplayName("(a \\ b) and b never overlap, and their union restores a")
        void subtractionPartitionsCorrectly() {
            Random random = new Random(SEED + 1);
            for (int i = 0; i < 500; i++) {
                VersionRange a = randomRange(random);
                VersionRange b = randomRange(random);

                VersionRange difference = a.subtract(b);
                assertThat(difference.intersects(b)).as(a + " \\ " + b + " must not touch b").isFalse();
                assertThat(difference.union(a.intersect(b)))
                        .as("(a \\ b) u (a n b) = a").isEqualTo(a);
                assertThat(a.containsAll(difference)).as("a \\ b is a subset of a").isTrue();
            }
        }

        @Test
        @DisplayName("membership agrees with the set operations for every sampled version")
        void membershipMatchesSetOperations() {
            Random random = new Random(SEED + 2);
            List<SemVer> probes = probeVersions();

            for (int i = 0; i < 300; i++) {
                VersionRange a = randomRange(random);
                VersionRange b = randomRange(random);
                VersionRange union = a.union(b);
                VersionRange intersection = a.intersect(b);
                VersionRange difference = a.subtract(b);

                for (SemVer probe : probes) {
                    boolean inA = a.test(probe);
                    boolean inB = b.test(probe);
                    assertThat(union.test(probe)).as(probe + " in " + a + " u " + b)
                            .isEqualTo(inA || inB);
                    assertThat(intersection.test(probe)).as(probe + " in " + a + " n " + b)
                            .isEqualTo(inA && inB);
                    assertThat(difference.test(probe)).as(probe + " in " + a + " \\ " + b)
                            .isEqualTo(inA && !inB);
                }
            }
        }

        @Test
        @DisplayName("the normal form holds: sorted, disjoint, and never adjacent")
        void normalFormIsMaintained() {
            Random random = new Random(SEED + 3);
            for (int i = 0; i < 500; i++) {
                VersionRange a = randomRange(random);
                VersionRange b = randomRange(random);
                assertNormalised(a.union(b));
                assertNormalised(a.intersect(b));
                assertNormalised(a.subtract(b));
                assertNormalised(a.complement());
            }
        }

        @Test
        @DisplayName("toPredicates() round-trips through the parser")
        void predicateRoundTrip() {
            Random random = new Random(SEED + 4);
            for (int i = 0; i < 500; i++) {
                VersionRange original = randomRange(random);
                if (original.isEmpty()) {
                    continue;
                }
                List<String> predicates = original.toPredicates();
                VersionRange reparsed =
                        VersionRange.parse(predicates.toArray(new String[0]));
                assertThat(reparsed).as(original + " -> " + predicates).isEqualTo(original);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNormalised(VersionRange range) {
        List<Interval> intervals = range.intervals();
        for (Interval interval : intervals) {
            assertThat(interval.isEmpty()).as("empty interval in " + range).isFalse();
        }
        for (int i = 1; i < intervals.size(); i++) {
            Interval previous = intervals.get(i - 1);
            Interval current = intervals.get(i);
            assertThat(Interval.compareByLowerBound(previous, current))
                    .as("sorted: " + range).isLessThan(0);
            assertThat(previous.connectsWith(current))
                    .as("must not be adjacent or overlapping: " + range).isFalse();
        }
    }

    private static VersionRange randomRange(Random random) {
        int parts = random.nextInt(3) + 1;
        List<Interval> intervals = new ArrayList<Interval>(parts);
        for (int i = 0; i < parts; i++) {
            intervals.add(randomInterval(random));
        }
        return VersionRange.of(intervals);
    }

    private static Interval randomInterval(Random random) {
        SemVer low = randomVersion(random);
        SemVer high = randomVersion(random);
        if (low.isHigherThan(high)) {
            SemVer swap = low;
            low = high;
            high = swap;
        }
        switch (random.nextInt(6)) {
            case 0:
                return Interval.atLeast(low);
            case 1:
                return Interval.lessThan(high);
            case 2:
                return Interval.exactly(low);
            case 3:
                return Interval.of(low, false, high, true);
            case 4:
                return Interval.ALL;
            default:
                return Interval.closedOpen(low, high);
        }
    }

    private static SemVer randomVersion(Random random) {
        int major = random.nextInt(3) + 1;
        int minor = random.nextInt(4) + 20;
        int patch = random.nextInt(4);
        if (random.nextInt(5) == 0) {
            return SemVer.parse(major + "." + minor + "." + patch + "-rc." + (random.nextInt(3) + 1));
        }
        return SemVer.of(major, minor, patch);
    }

    private static List<SemVer> probeVersions() {
        List<SemVer> probes = new ArrayList<SemVer>();
        for (String text : Arrays.asList(
                "1.19.4", "1.20.0", "1.20.1", "1.20.2", "1.21.0", "1.21.1", "1.21.2",
                "1.21.3", "1.21.4", "1.21.4-rc.1", "1.21.5", "1.22.0", "2.20.0", "3.23.3",
                "1.20.1-rc.2", "2.21.1")) {
            probes.add(SemVer.parse(text));
        }
        return probes;
    }
}
