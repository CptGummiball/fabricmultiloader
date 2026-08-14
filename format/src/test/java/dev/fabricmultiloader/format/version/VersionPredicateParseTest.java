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

class VersionPredicateParseTest {

    @Test
    void parsesTheWildcard() {
        VersionPredicate any = VersionPredicateParser.parse("*");
        assertThat(any.test(SemVer.parse("1.20.1"))).isTrue();
        assertThat(any.test(SemVer.parse("26.1"))).isTrue();
        assertThat(any.test(SemVer.parse("1.21.4-rc.1"))).isTrue();
        assertThat(any.asInterval().isAll()).isTrue();
        assertThat(any.canonical()).isEqualTo("*");
    }

    @ParameterizedTest
    @CsvSource({
        "=1.20.1,     1.20.1,   true",
        "=1.20.1,     1.20.2,   false",
        "1.20.1,      1.20.1,   true",
        ">=1.20.1,    1.20.1,   true",
        ">=1.20.1,    1.21.0,   true",
        ">=1.20.1,    1.20.0,   false",
        ">1.20.1,     1.20.1,   false",
        ">1.20.1,     1.20.2,   true",
        "<1.22,       1.21.4,   true",
        "<1.22,       1.22.0,   false",
        "<=1.21.4,    1.21.4,   true",
        "<=1.21.4,    1.21.5,   false",
    })
    @DisplayName("every operator the generator emits")
    void evaluatesComparisons(String predicate, String version, boolean expected) {
        assertThat(VersionPredicateParser.parse(predicate).test(SemVer.parse(version)))
                .as(predicate + " vs " + version)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("space-separated terms are a conjunction — the generated range shape")
    void parsesConjunctions() {
        VersionPredicate range = VersionPredicateParser.parse(">=1.21 <1.21.2");

        assertThat(range.test(SemVer.parse("1.21"))).isTrue();
        assertThat(range.test(SemVer.parse("1.21.1"))).isTrue();
        assertThat(range.test(SemVer.parse("1.21.2"))).isFalse();
        assertThat(range.test(SemVer.parse("1.20.9"))).isFalse();
        assertThat(range.canonical()).isEqualTo(">=1.21.0 <1.21.2");
    }

    @Test
    @DisplayName("tilde and caret are understood for reading foreign metadata")
    void parsesTildeAndCaret() {
        VersionPredicate tilde = VersionPredicateParser.parse("~1.20.1");
        assertThat(tilde.asInterval()).isEqualTo(
                Interval.closedOpen(SemVer.of(1, 20, 1), SemVer.of(1, 21, 0)));
        assertThat(tilde.test(SemVer.parse("1.20.9"))).isTrue();
        assertThat(tilde.test(SemVer.parse("1.21.0"))).isFalse();

        VersionPredicate caret = VersionPredicateParser.parse("^1.20.1");
        assertThat(caret.asInterval()).isEqualTo(
                Interval.closedOpen(SemVer.of(1, 20, 1), SemVer.of(2, 0, 0)));
        assertThat(caret.test(SemVer.parse("1.99.0"))).isTrue();
        assertThat(caret.test(SemVer.parse("2.0.0"))).isFalse();
    }

    @Test
    @DisplayName("caret uses the plain same-major rule, including for 0.x")
    void caretHasNoZeroMajorSpecialCase() {
        VersionPredicate caret = VersionPredicateParser.parse("^0.16.9");
        assertThat(caret.test(SemVer.parse("0.17.0"))).isTrue();
        assertThat(caret.test(SemVer.parse("1.0.0"))).isFalse();
    }

    @Test
    @DisplayName("every predicate is expressible as exactly one interval")
    void predicatesMapToIntervals() {
        assertThat(VersionPredicateParser.parse(">=1.20.1").asInterval())
                .isEqualTo(Interval.atLeast(SemVer.of(1, 20, 1)));
        assertThat(VersionPredicateParser.parse("=1.20.1").asInterval().isExact()).isTrue();
        assertThat(VersionPredicateParser.parse(">=1.21 <1.21.2").asInterval())
                .isEqualTo(Interval.closedOpen(SemVer.of(1, 21, 0), SemVer.of(1, 21, 2)));
    }

    @Test
    @DisplayName("a contradictory conjunction yields an empty interval rather than crashing")
    void contradictionsAreRepresentable() {
        VersionPredicate impossible = VersionPredicateParser.parse(">=1.22 <1.21");
        assertThat(impossible.asInterval().isEmpty()).isTrue();
        assertThat(impossible.test(SemVer.parse("1.21.4"))).isFalse();
        assertThat(impossible.test(SemVer.parse("1.22.0"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        ">=",
        ">",
        "=",
        ">=abc",
        ">= 1.20.1",
        "> = 1.20.1",
        ">=1.20.1 *",
        "* >=1.20.1",
        "!=1.20.1",
        ">>1.20.1",
        "1.20.x",
    })
    void rejectsInvalidPredicates(String predicate) {
        assertThat(VersionPredicateParser.isValid(predicate)).as(predicate).isFalse();
        assertThatThrownBy(() -> VersionPredicateParser.parse(predicate))
                .isInstanceOf(OmniException.class)
                .satisfies(thrown ->
                        assertThat(((OmniException) thrown).code()).isEqualTo(ErrorCode.OMNI_3011));
    }

    @Test
    @DisplayName("an invalid predicate explains the supported syntax and where to fix it")
    void invalidPredicateDiagnosticIsActionable() {
        assertThatThrownBy(() -> VersionPredicateParser.parse(">=1.20.x"))
                .hasMessageContaining("OMNI-3011")
                .hasMessageContaining("Supported forms")
                .hasMessageContaining("gradle/fabricmultiloader.toml")
                .hasMessageContaining("omni-3011");
    }

    @Test
    void parsesFabricOrArrays() {
        assertThat(VersionPredicateParser.parseAll(">=1.20.1 <1.20.2", ">=1.21 <1.21.2"))
                .hasSize(2);
    }

    @Test
    void canonicalFormRoundTrips() {
        String[] predicates = {"*", "=1.20.1", ">=1.20.1", ">1.20.1", "<1.22.0", "<=1.21.4",
            ">=1.21.0 <1.21.2"};
        for (String predicate : predicates) {
            VersionPredicate parsed = VersionPredicateParser.parse(predicate);
            assertThat(parsed.canonical()).as(predicate).isEqualTo(predicate);
            assertThat(VersionPredicateParser.parse(parsed.canonical())).isEqualTo(parsed);
        }
    }
}
