package dev.fabricmultiloader.format.json;

/** JSON {@code null}. */
public final class JsonNull extends JsonValue {

    /** The canonical instance for constructed documents. */
    public static final JsonNull INSTANCE = new JsonNull(JsonLocation.UNKNOWN);

    JsonNull(JsonLocation location) {
        super(location);
    }

    @Override
    public JsonType type() {
        return JsonType.NULL;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonNull;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
