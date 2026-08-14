package dev.fabricmultiloader.format.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A single Fabric version predicate, such as {@code "*"}, {@code ">=1.21.4"} or
 * {@code ">=1.21 <1.21.2"}.
 *
 * <p>Every predicate is also expressible as an {@link Interval}, which is what lets the resolver
 * reason about payload domains with exact set arithmetic instead of testing sample versions. A
 * predicate that cannot be expressed as one contiguous interval does not exist in this syntax:
 * space-separated terms are conjunctions, and a conjunction of intervals is an interval.
 * Disjunction happens one level up, in {@link VersionRange}, mirroring Fabric's own model where an
 * array of predicates is an OR.
 */
public interface VersionPredicate {

    /** Whether the given version satisfies this predicate. */
    boolean test(SemVer version);

    /** The set of versions this predicate accepts. */
    Interval asInterval();

    /** The canonical textual form, suitable for writing into a generated {@code fabric.mod.json}. */
    String canonical();

    /** The predicate that accepts everything. */
    static VersionPredicate any() {
        return Any.INSTANCE;
    }

    /** Parses one predicate string. Equivalent to {@link VersionPredicateParser#parse(String)}. */
    static VersionPredicate parse(String text) {
        return VersionPredicateParser.parse(text);
    }

    /** Builds a predicate from an interval. */
    static VersionPredicate of(Interval interval) {
        return VersionPredicateParser.parse(interval.toPredicate());
    }

    /** The comparison operators Fabric understands. */
    enum Operator {
        /** {@code =1.20.1} — exactly this version. */
        EQUAL("="),
        /** {@code >=1.20.1} */
        GREATER_EQUAL(">="),
        /** {@code >1.20.1} */
        GREATER(">"),
        /** {@code <=1.20.1} */
        LESS_EQUAL("<="),
        /** {@code <1.20.1} */
        LESS("<"),
        /** {@code ~1.20.1} — same major and minor: {@code >=1.20.1 <1.21.0}. */
        TILDE("~"),
        /** {@code ^1.20.1} — same major: {@code >=1.20.1 <2.0.0}. */
        CARET("^");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /** The textual operator. */
        public String symbol() {
            return symbol;
        }
    }

    /** {@code *} — accepts every version, including prereleases. */
    final class Any implements VersionPredicate {

        static final Any INSTANCE = new Any();

        private Any() {
        }

        @Override
        public boolean test(SemVer version) {
            return version != null;
        }

        @Override
        public Interval asInterval() {
            return Interval.ALL;
        }

        @Override
        public String canonical() {
            return "*";
        }

        @Override
        public String toString() {
            return canonical();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Any;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

    /** A single operator applied to a single version. */
    final class Comparison implements VersionPredicate {

        private final Operator operator;
        private final SemVer version;
        private final Interval interval;

        Comparison(Operator operator, SemVer version) {
            this.operator = operator;
            this.version = version;
            this.interval = toInterval(operator, version);
        }

        private static Interval toInterval(Operator operator, SemVer version) {
            switch (operator) {
                case EQUAL:
                    return Interval.exactly(version);
                case GREATER_EQUAL:
                    return Interval.atLeast(version);
                case GREATER:
                    return Interval.greaterThan(version);
                case LESS_EQUAL:
                    return Interval.atMost(version);
                case LESS:
                    return Interval.lessThan(version);
                case TILDE:
                    return Interval.closedOpen(version, version.nextMinor());
                case CARET:
                default:
                    return Interval.closedOpen(version, version.nextMajor());
            }
        }

        /** The operator. */
        public Operator operator() {
            return operator;
        }

        /** The version being compared against. */
        public SemVer version() {
            return version;
        }

        @Override
        public boolean test(SemVer candidate) {
            return interval.contains(candidate);
        }

        @Override
        public Interval asInterval() {
            return interval;
        }

        @Override
        public String canonical() {
            return operator.symbol() + version;
        }

        @Override
        public String toString() {
            return canonical();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Comparison)) {
                return false;
            }
            Comparison that = (Comparison) other;
            return operator == that.operator && version.equals(that.version);
        }

        @Override
        public int hashCode() {
            return operator.hashCode() * 31 + version.hashCode();
        }
    }

    /** Several comparisons that must all hold — Fabric writes these space-separated. */
    final class All implements VersionPredicate {

        private final List<VersionPredicate> terms;
        private final Interval interval;

        All(List<VersionPredicate> terms) {
            this.terms = Collections.unmodifiableList(new ArrayList<VersionPredicate>(terms));
            Interval combined = Interval.ALL;
            for (VersionPredicate term : this.terms) {
                Interval next = combined.intersect(term.asInterval());
                if (next == null) {
                    combined = Interval.EMPTY;
                    break;
                }
                combined = next;
            }
            this.interval = combined;
        }

        /** The conjoined terms, in source order. */
        public List<VersionPredicate> terms() {
            return terms;
        }

        @Override
        public boolean test(SemVer version) {
            for (VersionPredicate term : terms) {
                if (!term.test(version)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Interval asInterval() {
            return interval;
        }

        @Override
        public String canonical() {
            StringBuilder out = new StringBuilder(32);
            for (VersionPredicate term : terms) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(term.canonical());
            }
            return out.toString();
        }

        @Override
        public String toString() {
            return canonical();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof All && ((All) other).terms.equals(terms);
        }

        @Override
        public int hashCode() {
            return terms.hashCode();
        }
    }

    /** Convenience for building a conjunction. */
    static VersionPredicate all(VersionPredicate... terms) {
        if (terms.length == 1) {
            return terms[0];
        }
        return new All(Arrays.asList(terms));
    }
}
