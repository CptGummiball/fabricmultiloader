package dev.fabricmultiloader.format.json;

import java.util.Map;

/**
 * Serialises JSON deterministically.
 *
 * <p>The canonical form is fixed by chapter 10.5 and is a build reproducibility requirement, not a
 * matter of taste: two indent spaces, {@code \n} line endings, UTF-8 without a BOM, no trailing
 * whitespace, and member order exactly as inserted. Numbers are emitted from their original
 * lexeme, so read → write → read is byte-identical.
 *
 * <p>Only the characters RFC 8259 requires are escaped. In particular {@code /} is <em>not</em>
 * escaped — escaping it is legal but would make every path in a manifest unreadable in a diff.
 */
public final class JsonWriter {

    private static final String INDENT = "  ";

    /** Pretty-prints a value in the canonical form, without a trailing newline. */
    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(value, out, 0, true);
        return out.toString();
    }

    /**
     * Pretty-prints a value as file content: canonical form plus a single trailing newline.
     * This is what the manifest generator writes to disk.
     */
    public static String writeDocument(JsonValue value) {
        return write(value) + "\n";
    }

    /** Serialises without whitespace — for hashing and for log lines. */
    public static String writeCompact(JsonValue value) {
        StringBuilder out = new StringBuilder(128);
        writeValue(value, out, 0, false);
        return out.toString();
    }

    private static void writeValue(JsonValue value, StringBuilder out, int depth, boolean pretty) {
        if (value == null || value.isNull()) {
            out.append("null");
            return;
        }
        switch (value.type()) {
            case OBJECT:
                writeObject(value.asObject(), out, depth, pretty);
                return;
            case ARRAY:
                writeArray(value.asArray(), out, depth, pretty);
                return;
            case STRING:
                writeString(value.asString(), out);
                return;
            case NUMBER:
                out.append(((JsonNumber) value).raw());
                return;
            case BOOLEAN:
                out.append(value.asBoolean() ? "true" : "false");
                return;
            case NULL:
            default:
                out.append("null");
        }
    }

    private static void writeObject(JsonObject object, StringBuilder out, int depth, boolean pretty) {
        if (object.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1, pretty);
            writeString(member.getKey(), out);
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            writeValue(member.getValue(), out, depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append('}');
    }

    private static void writeArray(JsonArray array, StringBuilder out, int depth, boolean pretty) {
        if (array.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (JsonValue element : array) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newline(out, depth + 1, pretty);
            writeValue(element, out, depth + 1, pretty);
        }
        newline(out, depth, pretty);
        out.append(']');
    }

    private static void newline(StringBuilder out, int depth, boolean pretty) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
    }

    private JsonWriter() {
        throw new AssertionError("no instances");
    }
}
