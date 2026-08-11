package dev.fabricmultiloader.format.json;

/** A JSON {@code true} or {@code false}. */
public final class JsonBool extends JsonValue {

    /** The canonical {@code true}. */
    public static final JsonBool TRUE = new JsonBool(true, JsonLocation.UNKNOWN);

    /** The canonical {@code false}. */
    public static final JsonBool FALSE = new JsonBool(false, JsonLocation.UNKNOWN);

    private final boolean value;

    JsonBool(boolean value, JsonLocation location) {
        super(location);
        this.value = value;
    }

    /** Returns the canonical instance for a boolean. */
    public static JsonBool of(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public JsonType type() {
        return JsonType.BOOLEAN;
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean asBoolean() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonBool && ((JsonBool) other).value == value;
    }

    @Override
    public int hashCode() {
        return value ? 1231 : 1237;
    }
}
