package dev.fabricmultiloader.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compares generated text against a checked-in expected file.
 *
 * <p>The right tool for exactly one kind of output: things a human reads. A diagnostic report, a
 * generated {@code fabric.mod.json}, an error message — their value is in the wording and the
 * layout, and a test asserting {@code contains("OMNI-2003")} would keep passing while the report
 * degraded into something nobody could act on. A golden file makes every change to that text a
 * visible diff in review, which is the only place a wording regression can actually be caught.
 *
 * <p>Updating is a deliberate act: {@code -Dfabricmultiloader.golden=update} rewrites the files and
 * the test then fails anyway, so an accidental run cannot quietly bless a regression. The diff still
 * has to be read.
 */
public final class GoldenFiles {

    /** Set to {@code update} to rewrite the expected files instead of comparing. */
    public static final String UPDATE_PROPERTY = "fabricmultiloader.golden";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Path root;

    /**
     * @param root the directory holding the expected files, normally
     *     {@code src/test/resources/golden}
     */
    public GoldenFiles(Path root) {
        this.root = root;
    }

    /** A golden set rooted at {@code src/test/resources/golden} of the calling module. */
    public static GoldenFiles inTestResources() {
        return new GoldenFiles(Paths.get("src", "test", "resources", "golden"));
    }

    /** Whether the expected files are being rewritten. */
    public static boolean isUpdating() {
        return "update".equalsIgnoreCase(System.getProperty(UPDATE_PROPERTY));
    }

    /**
     * Compares actual output against the expected file.
     *
     * @param name the file name under the golden directory, e.g. {@code no-matching-payload.txt}
     * @param actual the generated text
     * @throws AssertionError if they differ, or if the file had to be created
     */
    public void assertMatches(String name, String actual) {
        Path expected = root.resolve(name);
        String normalisedActual = normalise(actual);

        if (isUpdating()) {
            write(expected, normalisedActual);
            throw new AssertionError("golden file " + name + " was rewritten. Read the diff, then "
                    + "run without -D" + UPDATE_PROPERTY + "=update.");
        }
        if (!Files.exists(expected)) {
            write(expected, normalisedActual);
            throw new AssertionError("golden file " + expected + " did not exist and was created "
                    + "from the current output. Review it and commit it — an expected file nobody "
                    + "read is not an expectation.");
        }

        String expectedText = normalise(read(expected));
        if (!expectedText.equals(normalisedActual)) {
            throw new AssertionError("output differs from " + expected + "\n"
                    + firstDifference(expectedText, normalisedActual)
                    + "\n\n--- expected ---\n" + expectedText
                    + "\n--- actual ---\n" + normalisedActual
                    + "\n\nIf the change is intended, rerun with -D" + UPDATE_PROPERTY + "=update.");
        }
    }

    /**
     * Line endings are normalised and trailing whitespace stripped.
     *
     * <p>Otherwise every golden test fails on Windows for a reason that has nothing to do with what
     * it is testing, and the usual response — deleting the test — costs more than the check was
     * worth.
     */
    private static String normalise(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(stripTrailing(lines[i]));
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static String firstDifference(String expected, String actual) {
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int limit = Math.min(expectedLines.length, actualLines.length);
        for (int i = 0; i < limit; i++) {
            if (!expectedLines[i].equals(actualLines[i])) {
                return "first difference at line " + (i + 1) + ":\n"
                        + "  expected: " + expectedLines[i] + "\n"
                        + "  actual:   " + actualLines[i];
            }
        }
        return "the files agree for " + limit + " lines, then differ in length ("
                + expectedLines.length + " expected, " + actualLines.length + " actual)";
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static void write(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, (content + "\n").getBytes(UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }
}
