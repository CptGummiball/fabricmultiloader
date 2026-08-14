package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Validates every path that a manifest can name.
 *
 * <p>FabricMultiLoader never extracts anything itself — Fabric Loader does that, with its own
 * vetted code — and the runtime reads exclusively through {@code ModContainer#findPath}, which
 * hands back a path inside a loader-managed file system. A manifest path therefore never reaches
 * {@code new File(...)} or {@code Paths.get(...)}, which removes the classic Zip Slip vector by
 * construction.
 *
 * <p>These checks are the second line: a path from an untrusted manifest is rejected before it is
 * used for anything at all, including before it is printed into a diagnostic. The same code runs in
 * the validator at build time and in the runtime, so the two can never disagree about what is safe.
 *
 * @see <a href="https://github.com/CptGummiball/fabricmultiloader/blob/main/docs/design/part-10-nfr.md">Chapter 39.2</a>
 */
public final class SafePaths {

    private static final int MAX_LENGTH = 512;

    /** Roots a manifest is allowed to reference. Anything else is refused outright. */
    private static final List<String> ALLOWED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            OmniFormat.NESTED_JAR_ROOT,
            "omni/",
            "assets/",
            "data/"));

    /** Individual files a manifest may reference outside the allowed prefixes. */
    private static final List<String> ALLOWED_EXACT = Collections.unmodifiableList(Arrays.asList(
            "fabric.mod.json",
            OmniFormat.CONTAINER_MANIFEST_PATH,
            OmniFormat.PAYLOAD_DESCRIPTOR_PATH));

    /**
     * Validates a path that must sit inside a jar, and inside a root the format allows.
     *
     * @param raw the path as it appears in the manifest
     * @param field the manifest field, used in the diagnostic
     * @return the unchanged path
     * @throws OmniException {@code OMNI-3004} if the path is unsafe or outside the allowed roots
     */
    public static String requireJarPath(String raw, String field) {
        String checked = requireRelativePath(raw, field);
        for (String exact : ALLOWED_EXACT) {
            if (exact.equals(checked)) {
                return checked;
            }
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (checked.startsWith(prefix) && checked.length() > prefix.length()) {
                return checked;
            }
        }
        throw unsafe(field, raw, "path is outside the roots this format allows ("
                + String.join(", ", ALLOWED_PREFIXES) + ")");
    }

    /**
     * Validates a path as safe and relative, without restricting its root.
     *
     * <p>Used for entries whose location is fixed by the build rather than by the format — mixin
     * config file names and access wideners sit at a payload's root.
     */
    public static String requireRelativePath(String raw, String field) {
        if (raw == null || raw.isEmpty()) {
            throw unsafe(field, raw, "path must not be empty");
        }
        if (raw.length() > MAX_LENGTH) {
            throw unsafe(field, raw, "path must be at most " + MAX_LENGTH + " characters long");
        }
        if (raw.charAt(0) == '/' || raw.charAt(0) == '\\') {
            throw unsafe(field, raw, "path must be relative, not absolute");
        }
        if (raw.indexOf('\\') >= 0) {
            throw unsafe(field, raw, "path must use '/' as the separator, never '\\'");
        }
        if (raw.indexOf('\0') >= 0) {
            throw unsafe(field, raw, "path must not contain a NUL byte");
        }
        if (raw.contains("//")) {
            throw unsafe(field, raw, "path must not contain an empty segment");
        }
        if (raw.length() > 1 && raw.charAt(1) == ':') {
            throw unsafe(field, raw, "path must not be a drive-qualified Windows path");
        }
        if (raw.endsWith("/")) {
            throw unsafe(field, raw, "path must name a file, not a directory");
        }
        for (String segment : raw.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw unsafe(field, raw, "path must not contain '.' or '..' segments");
            }
        }
        return raw;
    }

    /** Whether the path passes {@link #requireJarPath} without throwing. */
    public static boolean isSafeJarPath(String raw) {
        try {
            requireJarPath(raw, "path");
            return true;
        } catch (OmniException e) {
            return false;
        }
    }

    private static OmniException unsafe(String field, String raw, String problem) {
        return new OmniException(ErrorCode.OMNI_3004, Messages.report(ErrorCode.OMNI_3004)
                .detected("field", field)
                .detected("path", raw == null ? "(null)" : "\"" + raw + "\"")
                .detected("problem", problem)
                .detail("Manifest paths are untrusted input. A path that could escape the jar, or")
                .detail("point somewhere the format does not use, is refused before it is read.")
                .fix("if you built this jar, report it as a generator bug")
                .fix("otherwise treat the file as tampered with and re-download the mod")
                .build());
    }

    private SafePaths() {
        throw new AssertionError("no instances");
    }
}
