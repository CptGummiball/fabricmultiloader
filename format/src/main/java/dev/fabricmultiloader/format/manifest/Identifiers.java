package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;

/**
 * Validates the identifier shapes the format uses.
 *
 * <p>Manifest content is untrusted (chapter 39.4), and two of these checks are load-bearing for
 * security rather than tidiness: a tampered manifest must not be able to name an arbitrary class
 * for the runtime to instantiate. Both the platform factory and the entrypoints are therefore
 * required to be syntactically valid class names <em>and</em> to sit under a package the payload
 * itself declares ({@code OMNI-2024}, {@code OMNI-2032}).
 */
public final class Identifiers {

    /** Maximum length Fabric accepts for a mod id. */
    private static final int MOD_ID_MAX = 64;
    private static final int MOD_ID_MIN = 2;
    private static final int PAYLOAD_ID_MAX = 32;
    private static final int FQCN_MAX = 512;

    /**
     * Validates a Fabric mod id: {@code ^[a-z][a-z0-9-_]{1,63}$}.
     *
     * @throws OmniException {@code OMNI-3004} if invalid
     */
    public static String requireModId(String value, String field) {
        if (value == null || value.length() < MOD_ID_MIN || value.length() > MOD_ID_MAX) {
            throw invalid(field, value, "a mod id must be " + MOD_ID_MIN + " to " + MOD_ID_MAX
                    + " characters long");
        }
        char first = value.charAt(0);
        if (first < 'a' || first > 'z') {
            throw invalid(field, value, "a mod id must start with a lower-case letter");
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                throw invalid(field, value,
                        "invalid character '" + c + "' — allowed: a-z 0-9 - _");
            }
        }
        return value;
    }

    /**
     * Validates a payload id: {@code ^[a-z][a-z0-9]{1,31}$}.
     *
     * <p>Stricter than a mod id on purpose — payload ids become Gradle task name fragments
     * ({@code runClient1214}, {@code integrationTestMc1214}) and directory names, where a hyphen
     * would either be dropped or produce an unusable identifier.
     */
    public static String requirePayloadId(String value, String field) {
        if (value == null || value.length() < 2 || value.length() > PAYLOAD_ID_MAX) {
            throw invalid(field, value, "a payload id must be 2 to " + PAYLOAD_ID_MAX
                    + " characters long");
        }
        char first = value.charAt(0);
        if (first < 'a' || first > 'z') {
            throw invalid(field, value, "a payload id must start with a lower-case letter");
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                throw invalid(field, value, "invalid character '" + c
                        + "' — payload ids are letters and digits only, because they become"
                        + " Gradle task names and directory names");
            }
        }
        return value;
    }

    /**
     * Validates a fully qualified Java class name.
     *
     * <p>Accepts nested classes written with {@code $}. Rejects anything that is not a sequence of
     * Java identifiers separated by dots, which is what keeps a tampered manifest from naming
     * something the runtime would try to load.
     */
    public static String requireClassName(String value, String field) {
        if (value == null || value.isEmpty() || value.length() > FQCN_MAX) {
            throw invalid(field, value, "a class name must be 1 to " + FQCN_MAX + " characters long");
        }
        boolean atSegmentStart = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                if (atSegmentStart) {
                    throw invalid(field, value, "empty package segment");
                }
                atSegmentStart = true;
                continue;
            }
            boolean valid = atSegmentStart
                    ? Character.isJavaIdentifierStart(c)
                    : Character.isJavaIdentifierPart(c);
            if (!valid) {
                throw invalid(field, value, "invalid character '" + c + "' in a class name");
            }
            atSegmentStart = false;
        }
        if (atSegmentStart) {
            throw invalid(field, value, "a class name must not end with '.'");
        }
        return value;
    }

    /** Validates a Java package prefix; the empty package is rejected. */
    public static String requirePackageName(String value, String field) {
        return requireClassName(value, field);
    }

    /**
     * Whether a class name sits inside one of the given package prefixes.
     *
     * @param className a validated fully qualified class name
     * @param packagePrefixes the permitted package prefixes
     * @return {@code true} if the class is inside any of them
     */
    public static boolean isInsideAnyPackage(String className, Iterable<String> packagePrefixes) {
        for (String prefix : packagePrefixes) {
            if (className.equals(prefix)) {
                return true;
            }
            if (className.startsWith(prefix) && className.length() > prefix.length()
                    && className.charAt(prefix.length()) == '.') {
                return true;
            }
        }
        return false;
    }

    private static OmniException invalid(String field, String value, String problem) {
        return new OmniException(ErrorCode.OMNI_3004, Messages.report(ErrorCode.OMNI_3004)
                .detected("field", field)
                .detected("value", value == null ? "(null)" : "\"" + value + "\"")
                .detected("problem", problem)
                .fix("correct the value in gradle/fabricmultiloader.toml and rebuild")
                .fix("if the file was not built by you, treat the jar as corrupted and re-download it")
                .build());
    }

    private Identifiers() {
        throw new AssertionError("no instances");
    }
}
