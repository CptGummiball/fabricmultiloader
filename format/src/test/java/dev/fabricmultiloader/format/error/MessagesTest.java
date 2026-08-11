package dev.fabricmultiloader.format.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A golden-file style check of the normative message layout (chapter 29.1). If somebody changes the
 * shape of a diagnostic, this test makes them do it deliberately.
 */
class MessagesTest {

    @Test
    @DisplayName("a full report renders code, detected state, explanation, fixes and a docs link")
    void rendersTheNormativeLayout() {
        String report = Messages.report(ErrorCode.OMNI_2003)
                .detected("Minecraft", "1.21.4")
                .detected("Fabric API", "0.110.0", "<- too old")
                .detected("Java", 21)
                .detail("None of the 3 bundled implementations accepts this environment.")
                .fix("update Fabric API to 0.114.0 or newer")
                .fix("or install a supported Minecraft version")
                .build();

        assertThat(report).isEqualTo(
                "OMNI-2003  no payload matches this environment\n"
                        + "\n"
                        + "  Minecraft   1.21.4\n"
                        + "  Fabric API  0.110.0   <- too old\n"
                        + "  Java        21\n"
                        + "\n"
                        + "  None of the 3 bundled implementations accepts this environment.\n"
                        + "\n"
                        + "  Fix:\n"
                        + "    · update Fabric API to 0.114.0 or newer\n"
                        + "    · or install a supported Minecraft version\n"
                        + "\n"
                        + "  Docs: " + Messages.DOCS_BASE + "omni-2003\n");
    }

    @Test
    @DisplayName("labels are padded to a common width so the detected block reads as a table")
    void padsLabels() {
        String report = Messages.report(ErrorCode.OMNI_1040)
                .detected("a", 1)
                .detected("longer label", 2)
                .fix("something")
                .build();

        assertThat(report)
                .contains("  a             1\n")
                .contains("  longer label  2\n");
    }

    @Test
    void omitsEmptySections() {
        String report = Messages.report(ErrorCode.OMNI_3011).build();

        assertThat(report).isEqualTo(
                "OMNI-3011  invalid version predicate\n"
                        + "\n"
                        + "  Docs: " + Messages.DOCS_BASE + "omni-3011\n");
    }

    @Test
    void simpleShorthandProducesTheSameShape() {
        String report = Messages.simple(ErrorCode.OMNI_3004, "the path escapes the jar", "use a relative path");

        assertThat(report)
                .startsWith("OMNI-3004  invalid identifier or unsafe path\n")
                .contains("  the path escapes the jar\n")
                .contains("    · use a relative path\n")
                .endsWith("omni-3004\n");
    }

    @Test
    void everyReportEndsWithADocumentationLink() {
        for (ErrorCode code : ErrorCode.values()) {
            String report = Messages.report(code).fix("do something").build();
            assertThat(report).as(code.id()).endsWith("omni-" + code.number() + "\n");
        }
    }

    @Test
    void rejectsAMissingCode() {
        assertThatThrownBy(() -> Messages.report(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankFixesAreIgnoredRatherThanRenderedAsEmptyBullets() {
        String report = Messages.report(ErrorCode.OMNI_1002).fix(null).fix("").build();
        assertThat(report).doesNotContain("·");
    }
}
