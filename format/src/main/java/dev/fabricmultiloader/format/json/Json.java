package dev.fabricmultiloader.format.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

/**
 * The entry point to the format layer's JSON support.
 *
 * <p>FabricMultiLoader ships its own parser instead of using Gson, and the reason is worth
 * recording: Gson inside the container would either be shaded — colliding by FQCN with Minecraft's
 * own copy, where classpath order decides the winner — or shipped as yet another nested mod.
 * Minecraft's Gson is present but its version drifts and it is not reliably initialised during
 * {@code preLaunch} on older versions. Roughly nine kilobytes of parser removes the question
 * entirely, and buys position tracking and hard input limits that a general-purpose library does
 * not offer.
 *
 * @see JsonLimits
 */
public final class Json {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int COPY_BUFFER = 8192;

    /** The UTF-8 byte order mark, decoded. Not valid JSON, but common enough to tolerate. */
    private static final char BOM = (char) 0xFEFF;

    /** Parses a document with the default limits. */
    public static JsonValue parse(String text) {
        return parse(text, JsonLimits.DEFAULT);
    }

    /** Parses a document with explicit limits. */
    public static JsonValue parse(String text, JsonLimits limits) {
        return new JsonReader(text, limits).parseDocument();
    }

    /**
     * Parses a document that must be an object.
     *
     * @throws JsonFormatException {@code OMNI-3002} if the top-level value is not an object
     */
    public static JsonObject parseObject(String text) {
        return parse(text).asObject();
    }

    /**
     * Reads and parses a UTF-8 document from a stream. The stream is fully consumed but not closed
     * — callers own it, which matters because manifests are read from loader-managed zip file
     * systems that must not be closed by us.
     */
    public static JsonValue parse(InputStream in, JsonLimits limits) {
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[COPY_BUFFER];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > limits.maxDocumentChars()) {
                    throw JsonMessages.limitExceeded(
                            "document size (bytes)", limits.maxDocumentChars(), buffer.size(),
                            1, 1, JsonPointer.ROOT);
                }
            }
            return parse(stripBom(new String(buffer.toByteArray(), UTF_8)), limits);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read JSON document", e);
        }
    }

    /** Serialises a value in the canonical form, without a trailing newline. */
    public static String write(JsonValue value) {
        return JsonWriter.write(value);
    }

    /** Serialises a value as file content: canonical form plus one trailing newline. */
    public static String writeDocument(JsonValue value) {
        return JsonWriter.writeDocument(value);
    }

    /** Serialises without whitespace, for hashing and log lines. */
    public static String writeCompact(JsonValue value) {
        return JsonWriter.writeCompact(value);
    }

    /**
     * Removes a UTF-8 byte order mark. Some editors add one; it is not valid JSON, and failing on
     * it would be a needlessly hostile way to greet somebody who opened a manifest in Notepad.
     */
    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }

    private Json() {
        throw new AssertionError("no instances");
    }
}
