package dev.fabricmultiloader.format.hash;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Streaming SHA-256, in the lower-case hex form the manifest uses.
 *
 * <p>Used for the payload integrity check at startup ({@code OMNI-2013}) and for the resource
 * digests the validator compares across payloads. The check runs against the <em>zip entry inside
 * the container</em> rather than the extracted copy, because a tampered download is the case it
 * exists to catch, and the extracted copy is derived from it.
 *
 * <p>Streams in fixed-size chunks so that hashing a 2 MiB payload never allocates a 2 MiB array —
 * this runs during {@code preLaunch}, on the game thread, before anything else has warmed up.
 */
public final class Sha256 {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Hashes a stream fully. The stream is consumed but not closed — the caller owns it. */
    public static String of(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read input while hashing", e);
        }
        return toHex(digest.digest());
    }

    /** Hashes a byte array. */
    public static String of(byte[] bytes) {
        return toHex(newDigest().digest(bytes));
    }

    /** Hashes the UTF-8 encoding of a string. */
    public static String ofUtf8(String text) {
        return of(text.getBytes(UTF_8));
    }

    /**
     * Compares two hashes without leaking timing information.
     *
     * <p>Overkill for a mod jar and cheap enough to be worth doing anyway: comparison routines have
     * a habit of being copied into places where it does matter.
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= lower(expected.charAt(i)) ^ lower(actual.charAt(i));
        }
        return difference == 0;
    }

    /** Whether the text looks like a SHA-256 hex digest. */
    public static boolean isValidDigest(String text) {
        if (text == null || text.length() != 64) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = lower(text.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static char lower(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Required of every conforming JRE since Java 7.
            throw new IllegalStateException("this JVM does not provide SHA-256", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hex[i * 2] = HEX[value >>> 4];
            hex[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(hex);
    }

    private Sha256() {
        throw new AssertionError("no instances");
    }
}
