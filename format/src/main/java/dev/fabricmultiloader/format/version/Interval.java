package dev.fabricmultiloader.format.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A contiguous range of versions with optionally inclusive bounds and optional unboundedness.
 *
 * <p>This is the atom the whole disjointness proof rests on. Payload selection is only
 * deterministic because the build can show that no two payloads accept the same
 * (Minecraft × Java × environment) point, and that proof is exact interval arithmetic — not
 * sampling, not a heuristic. Consequently the bound comparisons below are written out in full
 * rather than approximated: an inclusive lower bound starts <em>before</em> an exclusive one at the
 * same version, an inclusive upper bound ends <em>after</em> an exclusive one, and an unbounded
 * side is lower resp. higher than every version.
 *
 * <p>Instances are immutable. {@code null} bounds mean unbounded.
 */
public final class Interval {

    /** Every version. */
    public static final Interval ALL = new Interval(null, false, null, false);

    /**
     * No version at all. Produced by contradictory constraints such as
     * {@code ">=1.22 <1.21"} — nonsense that a generator should never emit, but that a
     * hand-edited matrix can contain, so it has to be representable rather than crash.
     */
    public static final Interval EMPTY =
            new Interval(SemVer.of(0, 0, 0), false, SemVer.of(0, 0, 0), false);

    private final SemVer min;
    private final boolean minInclusive;
    private final SemVer max;
    private final boolean maxInclusive;

    private Interval(SemVer min, boolean minInclusive, SemVer max, boolean maxInclusive) {
        this.min = min;
        this.minInclusive = min != null && minInclusive;
        this.max = max;
        this.maxInclusive = max != null && maxInclusive;
    }

    /**
     * @param min lower bound, or {@code null} for unbounded
     * @param minInclusive whether {@code min} itself is included
     * @param max upper bound, or {@code null} for unbounded
     * @param maxInclusive whether {@code max} itself is included
     */
    public static Interval of(SemVer min, boolean minInclusive, SemVer max, boolean maxInclusive) {
        return new Interval(min, minInclusive, max, maxInclusive);
    }

    /** {@code [version, version]} — exactly one version. */
    public static Interval exactly(SemVer version) {
        return new Interval(version, true, version, true);
    }

    /** {@code [min, max)} — the shape almost every generated range has. */
    public static Interval closedOpen(SemVer min, SemVer max) {
        return new Interval(min, true, max, false);
    }

    /** {@code [min, ∞)}. */
    public static Interval atLeast(SemVer min) {
        return new Interval(min, true, null, false);
    }

    /** {@code (min, ∞)}. */
    public static Interval greaterThan(SemVer min) {
        return new Interval(min, false, null, false);
    }

    /** {@code (-∞, max]}. */
    public static Interval atMost(SemVer max) {
        return new Interval(null, false, max, true);
    }

    /** {@code (-∞, max)}. */
    public static Interval lessThan(SemVer max) {
        return new Interval(null, false, max, false);
    }

    // ------------------------------------------------------------------ accessors

    /** Lower bound, or {@code null} if unbounded. */
    public SemVer min() {
        return min;
    }

    /** Whether the lower bound itself is included. */
    public boolean minInclusive() {
        return minInclusive;
    }

    /** Upper bound, or {@code null} if unbounded. */
    public SemVer max() {
        return max;
    }

    /** Whether the upper bound itself is included. */
    public boolean maxInclusive() {
        return maxInclusive;
    }

    /** Whether the interval admits no version at all. */
    public boolean isEmpty() {
        if (min == null || max == null) {
            return false;
        }
        int comparison = min.compareTo(max);
        if (comparison > 0) {
            return true;
        }
        return comparison == 0 && !(minInclusive && maxInclusive);
    }

    /** Whether the interval admits exactly one version. */
    public boolean isExact() {
        return min != null && max != null && minInclusive && maxInclusive && min.compareTo(max) == 0;
    }

    /** Whether the interval admits every version. */
    public boolean isAll() {
        return min == null && max == null;
    }

    /** Whether the given version falls inside. */
    public boolean contains(SemVer version) {
        if (version == null) {
            return false;
        }
        if (min != null) {
            int comparison = version.compareTo(min);
            if (comparison < 0 || (comparison == 0 && !minInclusive)) {
                return false;
            }
        }
        if (max != null) {
            int comparison = version.compareTo(max);
            if (comparison > 0 || (comparison == 0 && !maxInclusive)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ algebra

    /** Orders by lower bound, then by upper bound. */
    static int compareByLowerBound(Interval left, Interval right) {
        int comparison = compareLower(left, right);
        return comparison != 0 ? comparison : compareUpper(left, right);
    }

    /** Compares lower bounds; unbounded sorts lowest, inclusive before exclusive. */
    static int compareLower(Interval left, Interval right) {
        if (left.min == null && right.min == null) {
            return 0;
        }
        if (left.min == null) {
            return -1;
        }
        if (right.min == null) {
            return 1;
        }
        int comparison = left.min.compareTo(right.min);
        if (comparison != 0) {
            return comparison;
        }
        if (left.minInclusive == right.minInclusive) {
            return 0;
        }
        return left.minInclusive ? -1 : 1;
    }

    /** Compares upper bounds; unbounded sorts highest, inclusive after exclusive. */
    static int compareUpper(Interval left, Interval right) {
        if (left.max == null && right.max == null) {
            return 0;
        }
        if (left.max == null) {
            return 1;
        }
        if (right.max == null) {
            return -1;
        }
        int comparison = left.max.compareTo(right.max);
        if (comparison != 0) {
            return comparison;
        }
        if (left.maxInclusive == right.maxInclusive) {
            return 0;
        }
        return left.maxInclusive ? 1 : -1;
    }

    /**
     * Whether the union of two intervals is again a single interval — that is, they overlap or
     * touch without a gap. {@code [1,2)} and {@code [2,3)} connect; {@code [1,2)} and
     * {@code (2,3]} do not, because version 2 belongs to neither.
     */
    public boolean connectsWith(Interval other) {
        return !hasGapBefore(this, other) && !hasGapBefore(other, this);
    }

    private static boolean hasGapBefore(Interval lower, Interval upper) {
        if (lower.max == null || upper.min == null) {
            return false;
        }
        int comparison = lower.max.compareTo(upper.min);
        if (comparison < 0) {
            return true;
        }
        return comparison == 0 && !lower.maxInclusive && !upper.minInclusive;
    }

    /**
     * The smallest interval containing both. Only meaningful when {@link #connectsWith} holds;
     * otherwise the result also covers the gap between them.
     */
    public Interval spanWith(Interval other) {
        boolean takeLeftLower = compareLower(this, other) <= 0;
        boolean takeLeftUpper = compareUpper(this, other) >= 0;
        return new Interval(
                takeLeftLower ? min : other.min,
                takeLeftLower ? minInclusive : other.minInclusive,
                takeLeftUpper ? max : other.max,
                takeLeftUpper ? maxInclusive : other.maxInclusive);
    }

    /** The overlap of two intervals, or {@code null} if they are disjoint. */
    public Interval intersect(Interval other) {
        boolean takeLeftLower = compareLower(this, other) >= 0;
        boolean takeLeftUpper = compareUpper(this, other) <= 0;
        Interval result = new Interval(
                takeLeftLower ? min : other.min,
                takeLeftLower ? minInclusive : other.minInclusive,
                takeLeftUpper ? max : other.max,
                takeLeftUpper ? maxInclusive : other.maxInclusive);
        return result.isEmpty() ? null : result;
    }

    /**
     * This interval minus the other, yielding zero, one or two intervals.
     *
     * <p>Two is the interesting case: removing {@code [1.21.4, 1.21.5)} from {@code [1.21, 1.22)}
     * leaves a piece on each side. That is precisely what makes a "catch-all payload plus a
     * specialised payload" configuration expressible without a runtime priority rule.
     */
    public List<Interval> subtract(Interval other) {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        Interval overlap = intersect(other);
        if (overlap == null) {
            return Collections.singletonList(this);
        }
        List<Interval> remainder = new ArrayList<Interval>(2);

        Interval leftPart = new Interval(min, minInclusive, overlap.min, !overlap.minInclusive);
        if (overlap.min != null && !leftPart.isEmpty()) {
            remainder.add(leftPart);
        }
        Interval rightPart = new Interval(overlap.max, !overlap.maxInclusive, max, maxInclusive);
        if (overlap.max != null && !rightPart.isEmpty()) {
            remainder.add(rightPart);
        }
        return remainder;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The canonical Fabric version predicate for this interval, for example
     * {@code ">=1.21.4 <1.21.5"}, {@code "=1.20.1"} or {@code "*"}.
     */
    public String toPredicate() {
        if (isAll()) {
            return "*";
        }
        if (isExact()) {
            return "=" + min;
        }
        StringBuilder out = new StringBuilder(24);
        if (min != null) {
            out.append(minInclusive ? ">=" : ">").append(min);
        }
        if (max != null) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(maxInclusive ? "<=" : "<").append(max);
        }
        return out.toString();
    }

    /** Mathematical notation, for diagnostics and test failure output. */
    @Override
    public String toString() {
        if (isEmpty()) {
            return "(empty)";
        }
        return (minInclusive ? "[" : "(")
                + (min == null ? "-inf" : min.toString())
                + ", "
                + (max == null ? "+inf" : max.toString())
                + (maxInclusive ? "]" : ")");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Interval)) {
            return false;
        }
        Interval that = (Interval) other;
        if (isEmpty() && that.isEmpty()) {
            return true;
        }
        return minInclusive == that.minInclusive
                && maxInclusive == that.maxInclusive
                && (min == null ? that.min == null : min.equals(that.min))
                && (max == null ? that.max == null : max.equals(that.max));
    }

    @Override
    public int hashCode() {
        if (isEmpty()) {
            return 0;
        }
        int result = min == null ? 0 : min.hashCode();
        result = 31 * result + (minInclusive ? 1 : 0);
        result = 31 * result + (max == null ? 0 : max.hashCode());
        result = 31 * result + (maxInclusive ? 1 : 0);
        return result;
    }
}
