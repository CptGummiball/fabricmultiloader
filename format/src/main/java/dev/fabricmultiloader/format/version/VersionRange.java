package dev.fabricmultiloader.format.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A set of versions, held as a sorted list of disjoint, non-adjacent {@link Interval}s.
 *
 * <p>This is the type the payload resolver reasons in. Fabric models alternatives as a JSON array
 * of predicates — an OR — and a union of intervals is exactly that, normalised. Keeping the
 * normal form (sorted, disjoint, merged) as an invariant means {@link #isEmpty()},
 * {@link #intersects(VersionRange)} and equality are all trivially correct, which matters because
 * the build-time disjointness proof depends on them.
 *
 * <p>Union, intersection and difference are exact. Nothing here samples versions or approximates;
 * a proof that two payloads never both apply has to be a proof.
 */
public final class VersionRange {

    /** Matches nothing. */
    public static final VersionRange EMPTY = new VersionRange(Collections.<Interval>emptyList());

    /** Matches every version. */
    public static final VersionRange ALL = new VersionRange(Collections.singletonList(Interval.ALL));

    private static final Comparator<Interval> BY_LOWER_BOUND = new Comparator<Interval>() {
        @Override
        public int compare(Interval left, Interval right) {
            return Interval.compareByLowerBound(left, right);
        }
    };

    private final List<Interval> intervals;

    private VersionRange(List<Interval> normalised) {
        this.intervals = Collections.unmodifiableList(normalised);
    }

    // ------------------------------------------------------------------ factories

    /** Builds a range from intervals, normalising them. */
    public static VersionRange of(Interval... intervals) {
        return of(Arrays.asList(intervals));
    }

    /** Builds a range from intervals, normalising them. */
    public static VersionRange of(List<Interval> intervals) {
        return new VersionRange(normalise(intervals));
    }

    /** Exactly one version. */
    public static VersionRange exactly(SemVer version) {
        return of(Interval.exactly(version));
    }

    /**
     * Parses Fabric's OR array of predicates.
     *
     * @param predicates for example {@code ">=1.20.1 <1.20.2"}, {@code ">=1.21 <1.21.2"}
     * @return the union of the predicates
     */
    public static VersionRange parse(String... predicates) {
        if (predicates.length == 0) {
            return EMPTY;
        }
        List<Interval> parsed = new ArrayList<Interval>(predicates.length);
        for (String predicate : predicates) {
            parsed.add(VersionPredicateParser.parse(predicate).asInterval());
        }
        return of(parsed);
    }

    /** Builds a range from already-parsed predicates. */
    public static VersionRange ofPredicates(List<VersionPredicate> predicates) {
        List<Interval> parsed = new ArrayList<Interval>(predicates.size());
        for (VersionPredicate predicate : predicates) {
            parsed.add(predicate.asInterval());
        }
        return of(parsed);
    }

    /**
     * Sorts, drops empties, and merges everything that overlaps or touches. Runs in
     * O(n log n); n is the number of payloads, so in practice fewer than ten.
     */
    private static List<Interval> normalise(List<Interval> input) {
        List<Interval> candidates = new ArrayList<Interval>(input.size());
        for (Interval interval : input) {
            if (interval != null && !interval.isEmpty()) {
                candidates.add(interval);
            }
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(candidates, BY_LOWER_BOUND);

        List<Interval> merged = new ArrayList<Interval>(candidates.size());
        Interval current = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            Interval next = candidates.get(i);
            if (current.connectsWith(next)) {
                current = current.spanWith(next);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    // ------------------------------------------------------------------ queries

    /** Whether the version falls into this range. */
    public boolean test(SemVer version) {
        for (Interval interval : intervals) {
            if (interval.contains(version)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the range admits no version at all. */
    public boolean isEmpty() {
        return intervals.isEmpty();
    }

    /** Whether the range admits every version. */
    public boolean isAll() {
        return intervals.size() == 1 && intervals.get(0).isAll();
    }

    /** The normalised intervals, in ascending order. */
    public List<Interval> intervals() {
        return intervals;
    }

    /** Whether this range and the other share at least one version. */
    public boolean intersects(VersionRange other) {
        return !intersect(other).isEmpty();
    }

    /** Whether every version of the other range is also in this one. */
    public boolean containsAll(VersionRange other) {
        return other.subtract(this).isEmpty();
    }

    // ------------------------------------------------------------------ algebra

    /** Every version in either range. */
    public VersionRange union(VersionRange other) {
        List<Interval> combined = new ArrayList<Interval>(intervals.size() + other.intervals.size());
        combined.addAll(intervals);
        combined.addAll(other.intervals);
        return of(combined);
    }

    /** Every version in both ranges. */
    public VersionRange intersect(VersionRange other) {
        if (isEmpty() || other.isEmpty()) {
            return EMPTY;
        }
        List<Interval> overlaps = new ArrayList<Interval>();
        for (Interval mine : intervals) {
            for (Interval theirs : other.intervals) {
                Interval overlap = mine.intersect(theirs);
                if (overlap != null) {
                    overlaps.add(overlap);
                }
            }
        }
        return of(overlaps);
    }

    /**
     * Every version in this range but not in the other.
     *
     * <p>The operation that makes payload priorities work without a runtime rule: the effective
     * range of a lower-priority payload is its declared range minus everything already claimed by
     * higher-priority ones (chapter 12.7).
     */
    public VersionRange subtract(VersionRange other) {
        if (isEmpty() || other.isEmpty()) {
            return this;
        }
        List<Interval> remaining = new ArrayList<Interval>(intervals);
        for (Interval cut : other.intervals) {
            List<Interval> next = new ArrayList<Interval>(remaining.size() + 1);
            for (Interval piece : remaining) {
                next.addAll(piece.subtract(cut));
            }
            remaining = next;
            if (remaining.isEmpty()) {
                break;
            }
        }
        return of(remaining);
    }

    /** The complement of this range. */
    public VersionRange complement() {
        return ALL.subtract(this);
    }

    // ------------------------------------------------------------------ rendering

    /**
     * The canonical Fabric predicate list — exactly what goes into a generated
     * {@code depends} entry. An empty range yields an empty list, which callers must reject
     * rather than write out: {@code "depends": {"minecraft": []}} would match nothing.
     */
    public List<String> toPredicates() {
        List<String> predicates = new ArrayList<String>(intervals.size());
        for (Interval interval : intervals) {
            predicates.add(interval.toPredicate());
        }
        return predicates;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "(empty)";
        }
        StringBuilder out = new StringBuilder(32);
        for (String predicate : toPredicates()) {
            if (out.length() > 0) {
                out.append(" || ");
            }
            out.append(predicate);
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VersionRange && ((VersionRange) other).intervals.equals(intervals);
    }

    @Override
    public int hashCode() {
        return intervals.hashCode();
    }
}
