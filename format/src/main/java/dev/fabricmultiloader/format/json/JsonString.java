package dev.fabricmultiloader.format.json;

/** A JSON string. */
public final class JsonString extends JsonValue {

    private final String value;

    JsonString(String value, JsonLocation location) {
        super(location);
        if (value == null) {
            throw new IllegalArgumentException("a JSON string value must not be null — use JsonNull");
        }
        this.value = value;
    }

    /** Creates a constructed string value. */
    public static JsonString of(String value) {
        return new JsonString(value, JsonLocation.UNKNOWN);
    }

    @Override
    public JsonType type() {
        return JsonType.STRING;
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonString && ((JsonString) other).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
