package dev.fabricmultiloader.format.version;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Fabric's version predicate syntax.
 *
 * <p>The generator only ever emits {@code *}, {@code =}, {@code >=}, {@code >}, {@code <=} and
 * {@code <} — the subset whose meaning is identical across every supported loader version. The
 * parser nevertheless understands {@code ~} and {@code ^} as well, because it also has to
 * <em>read</em> predicates it did not write: the validator compares the Omni manifest against the
 * generated {@code fabric.mod.json} ({@code OMNI-1011}), and a mod project's matrix is hand-edited.
 *
 * <p>{@code ~1.20.1} means {@code >=1.20.1 <1.21.0} and {@code ^1.20.1} means
 * {@code >=1.20.1 <2.0.0}, including for {@code 0.x} versions — Fabric applies the plain
 * same-major rule rather than npm's special case for leading zeroes. That agreement is not assumed:
 * {@code VersionPredicateEquivalenceTest} checks this parser against the real loader
 * implementation for thousands of generated cases.
 */
public final class VersionPredicateParser {

    /**
     * Parses one predicate string, which may be a space-separated conjunction.
     *
     * @param text for example {@code ">=1.21 <1.21.2"}
     * @return the parsed predicate
     * @throws OmniException {@code OMNI-3011} if the syntax is invalid
     */
    public static VersionPredicate parse(String text) {
        if (text == null) {
            throw invalid(null, "a version predicate must not be null");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw invalid(text, "a version predicate must not be empty");
        }
        if ("*".equals(trimmed)) {
            return VersionPredicate.any();
        }

        String[] tokens = trimmed.split("\\s+");
        List<VersionPredicate> terms = new ArrayList<VersionPredicate>(tokens.length);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if ("*".equals(token)) {
                throw invalid(text, "'*' cannot be combined with other terms");
            }
            terms.add(parseTerm(text, token));
        }
        if (terms.isEmpty()) {
            throw invalid(text, "a version predicate must not be empty");
        }
        return terms.size() == 1 ? terms.get(0) : new VersionPredicate.All(terms);
    }

    /**
     * Parses several predicates, as Fabric's OR array.
     *
     * @param texts the predicate strings
     * @return one predicate per input, in order
     */
    public static List<VersionPredicate> parseAll(String... texts) {
        List<VersionPredicate> parsed = new ArrayList<VersionPredicate>(texts.length);
        for (String text : texts) {
            parsed.add(parse(text));
        }
        return parsed;
    }

    /** Whether the text is a valid predicate. */
    public static boolean isValid(String text) {
        try {
            parse(text);
            return true;
        } catch (OmniException e) {
            return false;
        }
    }

    private static VersionPredicate parseTerm(String whole, String token) {
        VersionPredicate.Operator operator = VersionPredicate.Operator.EQUAL;
        String remainder = token;

        // Two-character operators first: ">=" must win over ">".
        if (token.startsWith(">=")) {
            operator = VersionPredicate.Operator.GREATER_EQUAL;
            remainder = token.substring(2);
        } else if (token.startsWith("<=")) {
            operator = VersionPredicate.Operator.LESS_EQUAL;
            remainder = token.substring(2);
        } else if (token.startsWith(">")) {
            operator = VersionPredicate.Operator.GREATER;
            remainder = token.substring(1);
        } else if (token.startsWith("<")) {
            operator = VersionPredicate.Operator.LESS;
            remainder = token.substring(1);
        } else if (token.startsWith("=")) {
            operator = VersionPredicate.Operator.EQUAL;
            remainder = token.substring(1);
        } else if (token.startsWith("~")) {
            operator = VersionPredicate.Operator.TILDE;
            remainder = token.substring(1);
        } else if (token.startsWith("^")) {
            operator = VersionPredicate.Operator.CARET;
            remainder = token.substring(1);
        }

        if (remainder.isEmpty()) {
            throw invalid(whole, "the operator '" + operator.symbol() + "' is not followed by a version");
        }
        if (!SemVer.isParseable(remainder)) {
            throw invalid(whole, "'" + remainder + "' is not a valid version");
        }
        return new VersionPredicate.Comparison(operator, SemVer.parse(remainder));
    }

    private static OmniException invalid(String text, String problem) {
        return new OmniException(ErrorCode.OMNI_3011, Messages.report(ErrorCode.OMNI_3011)
                .detected("predicate", text == null ? "(null)" : "\"" + text + "\"")
                .detected("problem", problem)
                .detail("Supported forms: * · =1.20.1 · >=1.20.1 · >1.20 · <=1.21.4 · <1.22")
                .detail("Several terms separated by spaces are combined with AND: \">=1.21 <1.21.2\".")
                .detail("Alternatives are expressed as a JSON array, which means OR.")
                .fix("correct the predicate in gradle/fabricmultiloader.toml")
                .fix("see docs/design/part-03-container-format.md, chapter 12.3")
                .build());
    }

    private VersionPredicateParser() {
        throw new AssertionError("no instances");
    }
}
