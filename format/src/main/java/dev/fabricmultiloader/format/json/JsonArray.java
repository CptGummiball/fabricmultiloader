package dev.fabricmultiloader.format.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A JSON array. Mutable, so that the same type serves both parsing and document construction. */
public final class JsonArray extends JsonValue implements Iterable<JsonValue> {

    private final List<JsonValue> elements = new ArrayList<JsonValue>();

    /** Creates an empty, constructed array. */
    public JsonArray() {
        super(JsonLocation.UNKNOWN);
    }

    JsonArray(JsonLocation location) {
        super(location);
    }

    @Override
    public JsonType type() {
        return JsonType.ARRAY;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonArray asArray() {
        return this;
    }

    // ------------------------------------------------------------------ building

    /** Appends a value. */
    public JsonArray add(JsonValue value) {
        elements.add(value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    /** Appends a string. */
    public JsonArray add(String value) {
        return add(value == null ? (JsonValue) JsonNull.INSTANCE : JsonString.of(value));
    }

    /** Appends an integral number. */
    public JsonArray add(long value) {
        return add(JsonNumber.of(value));
    }

    /** Appends a boolean. */
    public JsonArray add(boolean value) {
        return add(JsonBool.of(value));
    }

    // ------------------------------------------------------------------ reading

    /** Number of elements. */
    public int size() {
        return elements.size();
    }

    /** Whether the array has no elements. */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * @param index zero-based element index
     * @return the element
     * @throws JsonFormatException {@code OMNI-3001} if the index is out of bounds
     */
    public JsonValue get(int index) {
        if (index < 0 || index >= elements.size()) {
            throw JsonMessages.missingElement(location(), index, elements.size());
        }
        return elements.get(index);
    }

    /** Element as a string. */
    public String getString(int index) {
        return get(index).asString();
    }

    /** Element as an object. */
    public JsonObject getObject(int index) {
        return get(index).asObject();
    }

    /** Element as an array. */
    public JsonArray getArray(int index) {
        return get(index).asArray();
    }

    /** Element as an {@code int}. */
    public int getInt(int index) {
        return get(index).asInt();
    }

    /** An unmodifiable view of the elements. */
    public List<JsonValue> values() {
        return Collections.unmodifiableList(elements);
    }

    /**
     * All elements as strings.
     *
     * @throws JsonFormatException {@code OMNI-3002} if any element is not a string
     */
    public List<String> asStringList() {
        List<String> out = new ArrayList<String>(elements.size());
        for (JsonValue element : elements) {
            out.add(element.asString());
        }
        return out;
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return Collections.unmodifiableList(elements).iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonArray && ((JsonArray) other).elements.equals(elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }
}
