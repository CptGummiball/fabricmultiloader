package dev.fabricmultiloader.format.error;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds diagnostics in the normative message layout.
 *
 * <p>Every FabricMultiLoader message — build time and runtime alike — has the same shape, so that
 * a user who has read one has effectively read them all:
 *
 * <pre>
 * OMNI-2003  no payload matches this environment
 *
 *   Minecraft      1.21.4
 *   Fabric API     0.110.0        &lt;- too old
 *
 *   None of the 3 bundled implementations accepts this environment.
 *
 *   Fix:
 *     · update Fabric API to 0.114.0 or newer
 *
 *   Docs: https://github.com/.../docs/errors.md#omni-2003
 * </pre>
 *
 * <p>Three rules are enforced by construction rather than by review: the code always leads, the
 * detected state is always printed even when it is not causal (support cases become solvable in
 * one round trip), and there is always at least one concrete suggested fix. A message without a
 * fix is a complaint, not a diagnostic.
 */
public final class Messages {

    /** Base URL of the error documentation; the anchor is appended per code. */
    public static final String DOCS_BASE =
            "https://github.com/CptGummiball/fabricmultiloader/blob/main/docs/errors.md#";

    private static final String INDENT = "  ";
    private static final String BULLET = "    · ";
    private static final int MAX_LABEL_WIDTH = 24;

    /** Starts a report for the given code. */
    public static Builder report(ErrorCode code) {
        if (code == null) {
            throw new IllegalArgumentException("error code must not be null");
        }
        return new Builder(code);
    }

    /** Convenience for a one-line diagnostic with a single fix. */
    public static String simple(ErrorCode code, String detail, String fix) {
        return report(code).detail(detail).fix(fix).build();
    }

    private Messages() {
        throw new AssertionError("no instances");
    }

    /** Accumulates the sections of a report. Not thread-safe; used and discarded locally. */
    public static final class Builder {

        private final ErrorCode code;
        private final List<String[]> detected = new ArrayList<String[]>();
        private final List<String> details = new ArrayList<String>();
        private final List<String> fixes = new ArrayList<String>();
        private String docsAnchor;

        Builder(ErrorCode code) {
            this.code = code;
            this.docsAnchor = code.docAnchor();
        }

        /**
         * Adds a label/value line to the "detected" block. Labels are padded to a common width so
         * the block reads as a table.
         */
        public Builder detected(String label, Object value) {
            detected.add(new String[] {String.valueOf(label), String.valueOf(value)});
            return this;
        }

        /** Adds a label/value line with a trailing marker, e.g. {@code "<- too old"}. */
        public Builder detected(String label, Object value, String marker) {
            detected.add(new String[] {String.valueOf(label), String.valueOf(value) + "   " + marker});
            return this;
        }

        /** Adds a free-form explanation line. Several calls produce several lines. */
        public Builder detail(String line) {
            details.add(line == null ? "" : line);
            return this;
        }

        /** Adds a concrete, actionable fix. At least one is required. */
        public Builder fix(String line) {
            if (line != null && !line.isEmpty()) {
                fixes.add(line);
            }
            return this;
        }

        /** Overrides the documentation anchor; defaults to the code's own. */
        public Builder docsAnchor(String anchor) {
            this.docsAnchor = anchor;
            return this;
        }

        /** Renders the report. */
        public String build() {
            StringBuilder out = new StringBuilder(256);
            out.append(code.id()).append("  ").append(code.title()).append('\n');

            if (!detected.isEmpty()) {
                out.append('\n');
                int width = 0;
                for (String[] row : detected) {
                    width = Math.max(width, row[0].length());
                }
                width = Math.min(width, MAX_LABEL_WIDTH);
                for (String[] row : detected) {
                    out.append(INDENT).append(pad(row[0], width)).append("  ").append(row[1]).append('\n');
                }
            }

            if (!details.isEmpty()) {
                out.append('\n');
                for (String line : details) {
                    out.append(line.isEmpty() ? "" : INDENT).append(line).append('\n');
                }
            }

            if (!fixes.isEmpty()) {
                out.append('\n').append(INDENT).append("Fix:").append('\n');
                for (String fix : fixes) {
                    out.append(BULLET).append(fix).append('\n');
                }
            }

            out.append('\n').append(INDENT).append("Docs: ").append(DOCS_BASE).append(docsAnchor).append('\n');
            return out.toString();
        }

        private static String pad(String value, int width) {
            if (value.length() >= width) {
                return value;
            }
            StringBuilder padded = new StringBuilder(width);
            padded.append(value);
            while (padded.length() < width) {
                padded.append(' ');
            }
            return padded.toString();
        }
    }
}
