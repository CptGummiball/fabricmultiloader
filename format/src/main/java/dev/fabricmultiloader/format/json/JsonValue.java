package dev.fabricmultiloader.format.json;

/**
 * A parsed or constructed JSON value.
 *
 * <p>The hierarchy is closed: the constructor is package-private, so the only subtypes are
 * {@link JsonObject}, {@link JsonArray}, {@link JsonString}, {@link JsonNumber}, {@link JsonBool}
 * and {@link JsonNull}. Java 8 has no {@code sealed}, and the framework modules are bound to Java 8
 * so they can load on every supported Minecraft version — this is the idiomatic substitute.
 *
 * <p>The {@code as…} accessors throw {@link JsonFormatException} with {@code OMNI-3002} rather than
 * returning {@code null} or a default. A manifest with a wrongly typed field is a broken artifact,
 * and failing loudly at the point of access produces a far better message than a
 * {@link NullPointerException} three frames later.
 */
public abstract class JsonValue {

    private final JsonLocation location;

    JsonValue(JsonLocation location) {
        this.location = location == null ? JsonLocation.UNKNOWN : location;
    }

    /** Where this value came from; {@link JsonLocation#UNKNOWN} for constructed values. */
    public final JsonLocation location() {
        return location;
    }

    /** This value's JSON type. */
    public abstract JsonType type();

    /** Whether this is a JSON object. */
    public boolean isObject() {
        return false;
    }

    /** Whether this is a JSON array. */
    public boolean isArray() {
        return false;
    }

    /** Whether this is a JSON string. */
    public boolean isString() {
        return false;
    }

    /** Whether this is a JSON number. */
    public boolean isNumber() {
        return false;
    }

    /** Whether this is a JSON boolean. */
    public boolean isBoolean() {
        return false;
    }

    /** Whether this is JSON {@code null}. */
    public boolean isNull() {
        return false;
    }

    /**
     * @return this value as an object
     * @throws JsonFormatException {@code OMNI-3002} if it is not one
     */
    public JsonObject asObject() {
        throw JsonMessages.typeMismatch(this, JsonType.OBJECT);
    }

    /**
     * @return this value as an array
     * @throws JsonFormatException {@code OMNI-3002} if it is not one
     */
    public JsonArray asArray() {
        throw JsonMessages.typeMismatch(this, JsonType.ARRAY);
    }

    /**
     * @return this value as a string
     * @throws JsonFormatException {@code OMNI-3002} if it is not one
     */
    public String asString() {
        throw JsonMessages.typeMismatch(this, JsonType.STRING);
    }

    /**
     * @return this value as a boolean
     * @throws JsonFormatException {@code OMNI-3002} if it is not one
     */
    public boolean asBoolean() {
        throw JsonMessages.typeMismatch(this, JsonType.BOOLEAN);
    }

    /**
     * @return this value as an {@code int}
     * @throws JsonFormatException {@code OMNI-3002} if it is not an integral number in range
     */
    public int asInt() {
        throw JsonMessages.typeMismatch(this, JsonType.NUMBER);
    }

    /**
     * @return this value as a {@code long}
     * @throws JsonFormatException {@code OMNI-3002} if it is not an integral number in range
     */
    public long asLong() {
        throw JsonMessages.typeMismatch(this, JsonType.NUMBER);
    }

    /**
     * @return this value as a {@code double}
     * @throws JsonFormatException {@code OMNI-3002} if it is not a number
     */
    public double asDouble() {
        throw JsonMessages.typeMismatch(this, JsonType.NUMBER);
    }

    /** The literal number text as it appeared in the source; only meaningful for numbers. */
    String asRawNumber() {
        return type().displayName();
    }

    /** Writes this value using the canonical writer settings. */
    @Override
    public final String toString() {
        return JsonWriter.write(this);
    }
}
