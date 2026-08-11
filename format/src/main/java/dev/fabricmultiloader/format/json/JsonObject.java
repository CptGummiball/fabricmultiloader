package dev.fabricmultiloader.format.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object with insertion-ordered members.
 *
 * <p>Order is preserved rather than sorted, because the container manifest has a <em>normative</em>
 * key order that is neither alphabetical nor accidental (chapter 11.5): it groups related fields so
 * that a diff of two release artifacts stays readable. Sorting here would destroy that.
 *
 * <p>Two families of accessors: {@code getX} require the member and throw {@code OMNI-3001} when it
 * is absent, {@code optX} return a caller-supplied default. Required-by-default is deliberate —
 * a silently defaulted field in a manifest is how a build produces an artifact nobody notices is
 * wrong until a player launches it.
 */
public final class JsonObject extends JsonValue {

    private final Map<String, JsonValue> members = new LinkedHashMap<String, JsonValue>();

    /** Creates an empty, constructed object. */
    public JsonObject() {
        super(JsonLocation.UNKNOWN);
    }

    JsonObject(JsonLocation location) {
        super(location);
    }

    @Override
    public JsonType type() {
        return JsonType.OBJECT;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    // ------------------------------------------------------------------ building

    /** Sets a member, replacing any previous value under the same key. */
    public JsonObject set(String key, JsonValue value) {
        requireKey(key);
        members.put(key, value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    /** Sets a string member; a {@code null} value becomes JSON {@code null}. */
    public JsonObject set(String key, String value) {
        return set(key, value == null ? (JsonValue) JsonNull.INSTANCE : JsonString.of(value));
    }

    /** Sets an integral number member. */
    public JsonObject set(String key, long value) {
        return set(key, JsonNumber.of(value));
    }

    /** Sets a boolean member. */
    public JsonObject set(String key, boolean value) {
        return set(key, JsonBool.of(value));
    }

    // ------------------------------------------------------------------ reading

    /** Whether a member with this key exists (even if its value is JSON {@code null}). */
    public boolean has(String key) {
        return members.containsKey(key);
    }

    /** The member, or {@code null} if absent. */
    public JsonValue get(String key) {
        return members.get(key);
    }

    /**
     * The member, required.
     *
     * @throws JsonFormatException {@code OMNI-3001} if absent
     */
    public JsonValue require(String key) {
        JsonValue value = members.get(key);
        if (value == null) {
            throw JsonMessages.missingMember(location(), key);
        }
        return value;
    }

    /** Required object member. */
    public JsonObject getObject(String key) {
        return require(key).asObject();
    }

    /** Required array member. */
    public JsonArray getArray(String key) {
        return require(key).asArray();
    }

    /** Required string member. */
    public String getString(String key) {
        return require(key).asString();
    }

    /** Required {@code int} member. */
    public int getInt(String key) {
        return require(key).asInt();
    }

    /** Required {@code long} member. */
    public long getLong(String key) {
        return require(key).asLong();
    }

    /** Required boolean member. */
    public boolean getBoolean(String key) {
        return require(key).asBoolean();
    }

    /** Optional object member, or {@code null}. JSON {@code null} also yields {@code null}. */
    public JsonObject optObject(String key) {
        JsonValue value = members.get(key);
        return value == null || value.isNull() ? null : value.asObject();
    }

    /** Optional array member, or {@code null}. */
    public JsonArray optArray(String key) {
        JsonValue value = members.get(key);
        return value == null || value.isNull() ? null : value.asArray();
    }

    /** Optional string member, or the fallback. */
    public String optString(String key, String fallback) {
        JsonValue value = members.get(key);
        return value == null || value.isNull() ? fallback : value.asString();
    }

    /** Optional {@code int} member, or the fallback. */
    public int optInt(String key, int fallback) {
        JsonValue value = members.get(key);
        return value == null || value.isNull() ? fallback : value.asInt();
    }

    /** Optional boolean member, or the fallback. */
    public boolean optBoolean(String key, boolean fallback) {
        JsonValue value = members.get(key);
        return value == null || value.isNull() ? fallback : value.asBoolean();
    }

    /** The member keys, in insertion order. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(members.keySet());
    }

    /** An unmodifiable view of the members, in insertion order. */
    public Map<String, JsonValue> members() {
        return Collections.unmodifiableMap(members);
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }

    /** Whether the object has no members. */
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * The keys present here that are not in the given set — the basis of the "unknown field" check
     * (`OMNI-1002`), which the validator applies to a project's own build output. Readers ignore
     * unknown fields instead, which is what keeps the format additively extensible (chapter 42.4).
     *
     * @param known the field names the caller understands
     * @return the unexpected keys, in insertion order
     */
    public Set<String> unknownKeys(Set<String> known) {
        Set<String> unknown = new java.util.LinkedHashSet<String>(members.keySet());
        unknown.removeAll(known);
        return unknown;
    }

    private static void requireKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("a JSON member key must not be null");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonObject && ((JsonObject) other).members.equals(members);
    }

    @Override
    public int hashCode() {
        return members.hashCode();
    }
}
