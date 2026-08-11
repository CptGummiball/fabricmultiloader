package dev.fabricmultiloader.format.json;

/** The six JSON value types of RFC 8259. */
public enum JsonType {

    /** {@code { … }} */
    OBJECT("object"),

    /** {@code [ … ]} */
    ARRAY("array"),

    /** {@code "…"} */
    STRING("string"),

    /** Any JSON number; FabricMultiLoader keeps the literal text for exact round-tripping. */
    NUMBER("number"),

    /** {@code true} or {@code false} */
    BOOLEAN("boolean"),

    /** {@code null} */
    NULL("null");

    private final String displayName;

    JsonType(String displayName) {
        this.displayName = displayName;
    }

    /** The name used in error messages, e.g. {@code "expected string, found number"}. */
    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
