package dev.fabricmultiloader.format.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RFC 6901 JSON pointers — the addressing scheme used in every format diagnostic.
 *
 * <p>A pointer such as {@code /payloads/2/requires/minecraft} tells a user exactly which part of a
 * manifest is wrong, which is far more useful than a line number alone when the file was generated
 * rather than hand-written.
 *
 * <p>Escaping follows the standard: {@code ~} becomes {@code ~0} and {@code /} becomes {@code ~1}.
 * The order matters in both directions — escaping {@code ~} first, unescaping {@code ~1} first —
 * otherwise a literal {@code ~1} would round-trip into a path separator.
 */
public final class JsonPointer {

    /** The pointer to the document root. */
    public static final String ROOT = "";

    /** Appends an object member to a pointer, escaping the key. */
    public static String child(String parent, String key) {
        return (parent == null ? ROOT : parent) + "/" + escape(key);
    }

    /** Appends an array index to a pointer. */
    public static String index(String parent, int index) {
        return (parent == null ? ROOT : parent) + "/" + index;
    }

    /** Escapes a single reference token. */
    public static String escape(String token) {
        if (token == null) {
            return "";
        }
        if (token.indexOf('~') < 0 && token.indexOf('/') < 0) {
            return token;
        }
        return token.replace("~", "~0").replace("/", "~1");
    }

    /** Reverses {@link #escape(String)}. */
    public static String unescape(String token) {
        if (token == null) {
            return "";
        }
        if (token.indexOf('~') < 0) {
            return token;
        }
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * Splits a pointer into its unescaped reference tokens.
     *
     * @param pointer a pointer, possibly {@link #ROOT}
     * @return the tokens, empty for the root
     * @throws IllegalArgumentException if the pointer does not start with {@code /}
     */
    public static List<String> split(String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return Collections.emptyList();
        }
        if (pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("a non-empty JSON pointer must start with '/': " + pointer);
        }
        List<String> tokens = new ArrayList<String>();
        int start = 1;
        for (int i = 1; i <= pointer.length(); i++) {
            if (i == pointer.length() || pointer.charAt(i) == '/') {
                tokens.add(unescape(pointer.substring(start, i)));
                start = i + 1;
            }
        }
        return tokens;
    }

    /** Renders a pointer for humans: the root becomes {@code "(root)"}. */
    public static String describe(String pointer) {
        return pointer == null || pointer.isEmpty() ? "(root)" : pointer;
    }

    private JsonPointer() {
        throw new AssertionError("no instances");
    }
}
