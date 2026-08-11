package dev.fabricmultiloader.format.json;

/**
 * Where a value came from: a line, a column and a JSON pointer.
 *
 * <p>Carried on every parsed value so that a diagnostic can quote the source line with a caret and
 * name the logical path at the same time. Values built programmatically (by the writer side) carry
 * {@link #UNKNOWN} and simply omit the position from messages.
 */
public final class JsonLocation implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Used for values that were constructed rather than parsed. */
    public static final JsonLocation UNKNOWN = new JsonLocation(-1, -1, JsonPointer.ROOT);

    private final int line;
    private final int column;
    private final String pointer;

    /**
     * @param line 1-based line, or {@code -1} if unknown
     * @param column 1-based column, or {@code -1} if unknown
     * @param pointer the RFC 6901 pointer to this value
     */
    public JsonLocation(int line, int column, String pointer) {
        this.line = line;
        this.column = column;
        this.pointer = pointer == null ? JsonPointer.ROOT : pointer;
    }

    /** 1-based line number, or {@code -1}. */
    public int line() {
        return line;
    }

    /** 1-based column number, or {@code -1}. */
    public int column() {
        return column;
    }

    /** The RFC 6901 pointer to this value; empty string for the document root. */
    public String pointer() {
        return pointer;
    }

    /** Whether a source position is available. */
    public boolean hasPosition() {
        return line > 0 && column > 0;
    }

    /**
     * A compact human-readable form, for example
     * {@code "/payloads/2/requires/minecraft (line 41, column 22)"}.
     */
    public String describe() {
        String path = JsonPointer.describe(pointer);
        if (!hasPosition()) {
            return path;
        }
        return path + " (line " + line + ", column " + column + ")";
    }

    @Override
    public String toString() {
        return describe();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JsonLocation)) {
            return false;
        }
        JsonLocation that = (JsonLocation) other;
        return line == that.line && column == that.column && pointer.equals(that.pointer);
    }

    @Override
    public int hashCode() {
        return (line * 31 + column) * 31 + pointer.hashCode();
    }
}
