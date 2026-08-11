package dev.fabricmultiloader.format.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The error code registry is the one place where a stale entry silently costs a user an hour of
 * confusion, so its invariants are checked mechanically rather than by review.
 */
class ErrorCodeUniquenessTest {

    @Test
    void idsAreUnique() {
        Set<String> seen = new HashSet<String>();
        List<String> duplicates = new ArrayList<String>();
        for (ErrorCode code : ErrorCode.values()) {
            if (!seen.add(code.id())) {
                duplicates.add(code.id());
            }
        }
        assertThat(duplicates).as("duplicate error code ids").isEmpty();
    }

    @Test
    @DisplayName("every code falls inside a defined range, so category() cannot throw")
    void everyCodeHasACategory() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.category()).as(code.id()).isNotNull();
            assertThat(code.number()).isBetween(1000, 4999);
        }
    }

    @Test
    void rangesMapToTheDocumentedCategories() {
        assertThat(ErrorCode.OMNI_1040.category()).isEqualTo(ErrorCode.Category.BUILD);
        assertThat(ErrorCode.OMNI_2003.category()).isEqualTo(ErrorCode.Category.RUNTIME);
        assertThat(ErrorCode.OMNI_3001.category()).isEqualTo(ErrorCode.Category.FORMAT);
        assertThat(ErrorCode.OMNI_4010.category()).isEqualTo(ErrorCode.Category.API);
    }

    @Test
    @DisplayName("the enum constant name and the printable id cannot drift apart")
    void namesMatchIds() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.id()).isEqualTo("OMNI-" + code.name().substring("OMNI_".length()));
            assertThat(code.docAnchor()).isEqualTo(code.id().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Test
    @DisplayName("titles are short prose, not restatements of the code")
    void everyCodeHasATitle() {
        for (ErrorCode code : ErrorCode.values()) {
            String title = code.title();
            assertThat(title).as(code.id()).isNotEmpty();
            // Not a restatement of the id — the id already leads every message.
            assertThat(title).as(code.id()).doesNotStartWith("OMNI");
            // A title is a headline, not a sentence: no terminal punctuation, no stray padding.
            assertThat(title).as(code.id()).doesNotEndWith(".").isEqualTo(title.trim());
            // Long enough to mean something, short enough to fit on the first line of a report.
            assertThat(title.length()).as(code.id()).isBetween(10, 72);
        }
    }

    @Test
    void lookupByIdWorksAndFailsSoftly() {
        assertThat(ErrorCode.byId("OMNI-1040")).isEqualTo(ErrorCode.OMNI_1040);
        assertThat(ErrorCode.byId("OMNI-9999")).isNull();
        assertThat(ErrorCode.byId(null)).isNull();
        assertThat(ErrorCode.all()).hasSize(ErrorCode.values().length);
    }

    @Test
    @DisplayName("severities are assigned deliberately, not left at a default")
    void warningsAndInfosAreDeliberate() {
        assertThat(ErrorCode.OMNI_1013.severity()).isEqualTo(Severity.INFO);
        assertThat(ErrorCode.OMNI_1050.severity()).isEqualTo(Severity.WARNING);
        assertThat(ErrorCode.OMNI_1121.severity()).isEqualTo(Severity.WARNING);
        assertThat(ErrorCode.OMNI_2003.severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void apiMisuseExceptionRefusesNonApiCodes() {
        assertThatThrownBy(() -> new OmniApiMisuseException(ErrorCode.OMNI_2003, "report"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a 4xxx code");

        OmniApiMisuseException valid = new OmniApiMisuseException(ErrorCode.OMNI_4010, "report");
        assertThat(valid.code()).isEqualTo(ErrorCode.OMNI_4010);
        assertThat(valid.is(ErrorCode.OMNI_4010)).isTrue();
    }

    @Test
    void exceptionsExposeCodeAndReport() {
        OmniException withReport = new OmniException(ErrorCode.OMNI_2001, "the report body");
        assertThat(withReport.code()).isEqualTo(ErrorCode.OMNI_2001);
        assertThat(withReport.report()).isEqualTo("the report body");
        assertThat(withReport.getMessage()).isEqualTo("the report body");

        OmniException withoutReport = new OmniException(ErrorCode.OMNI_2001, null);
        assertThat(withoutReport.getMessage())
                .isEqualTo("OMNI-2001  " + ErrorCode.OMNI_2001.title());

        Throwable cause = new IllegalStateException("root cause");
        assertThat(new OmniException(ErrorCode.OMNI_2001, "r", cause)).hasCause(cause);
    }
}
