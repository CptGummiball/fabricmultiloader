package dev.fabricmultiloader.format.version;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Arrays;

/**
 * A semantic version, tolerant enough for the four version namespaces FabricMultiLoader has to
 * compare: Minecraft, Fabric Loader, Fabric API and Java.
 *
 * <p>Those namespaces do not all follow SemVer 2.0.0. Minecraft ships {@code 1.21} (two
 * components) and, from 26.1 onwards, {@code 26.1} under an entirely new scheme; snapshots arrive
 * as {@code 1.21.5-alpha.24.45.a}; Java 8 reports itself as {@code 1.8.0_402}. Rather than
 * scattering special cases across the resolver, every quirk is normalised here, once, with a test
 * per rule.
 *
 * <p>Ordering follows SemVer 2.0.0 exactly: numeric identifiers compare numerically, alphanumeric
 * ones compare lexically, numeric sorts before alphanumeric, a shorter prerelease set sorts before
 * a longer one with the same prefix, and any prerelease sorts before its release. Build metadata is
 * ignored entirely — {@code 2.0.0+mc1.21.4} and {@code 2.0.0+mc1.20.1} are the <em>same</em>
 * version, which is exactly why payload versions can carry a readable {@code +mc…} suffix without
 * confusing the loader.
 */
public final class SemVer implements Comparable<SemVer> {

    /**
     * Sorts below every real version. Returned by {@link #parseLenient(String)} for input that
     * cannot be understood, so that an unreadable version degrades into "matches nothing that
     * requires a minimum" instead of aborting the bootstrap.
     */
    public static final SemVer UNKNOWN = new SemVer(0, 0, 0, new String[] {"unknown"}, "");

    private static final String[] NO_PRERELEASE = new String[0];

    private final int major;
    private final int minor;
    private final int patch;
    private final String[] prerelease;
    private final String build;

    private SemVer(int major, int minor, int patch, String[] prerelease, String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
        this.build = build;
    }

    // ------------------------------------------------------------------ factories

    /** A release version with all three components. */
    public static SemVer of(int major, int minor, int patch) {
        return new SemVer(major, minor, patch, NO_PRERELEASE, "");
    }

    /** {@code <major>.0.0} — used to compare a Java feature version against a predicate. */
    public static SemVer ofMajor(int major) {
        return of(major, 0, 0);
    }

    /**
     * Parses strictly.
     *
     * @param text a version string
     * @return the parsed version
     * @throws OmniException {@code OMNI-3010} if the text cannot be understood
     */
    public static SemVer parse(String text) {
        SemVer parsed = tryParse(text);
        if (parsed == null) {
            throw new OmniException(ErrorCode.OMNI_3010, Messages.report(ErrorCode.OMNI_3010)
                    .detected("input", text == null ? "(null)" : "\"" + text + "\"")
                    .detail("Expected a version such as 1.21.4, 1.21, 26.1, 0.16.9 or 1.21.5-alpha.24.45.a.")
                    .fix("correct the version string")
                    .fix("if it comes from a generated file, report it as a bug")
                    .build());
        }
        return parsed;
    }

    /**
     * Parses tolerantly, returning {@link #UNKNOWN} instead of throwing.
     *
     * <p>Used everywhere in the bootstrap: a mod with an exotic version string must not be able to
     * stop the game before the diagnostic layer is even running. Callers that care report the
     * failure as a warning ({@code OMNI-3010}); the resolver simply fails to match, and the
     * diagnostic report then shows the raw string.
     */
    public static SemVer parseLenient(String text) {
        SemVer parsed = tryParse(text);
        return parsed == null ? UNKNOWN : parsed;
    }

    /** Whether the text can be parsed at all. */
    public static boolean isParseable(String text) {
        return tryParse(text) != null;
    }

    private static SemVer tryParse(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        String text = rawInput.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == 'v' || text.charAt(0) == 'V') {
            text = text.substring(1);
        }
        // Legacy Java: 1.8.0_402 -> 8.0.402. Keyed on the underscore, never on the leading "1.",
        // because Minecraft 1.8.0 must stay 1.8.0 rather than becoming Java 8.
        int underscore = text.indexOf('_');
        if (underscore >= 0) {
            text = text.substring(0, underscore) + '.' + text.substring(underscore + 1);
            if (text.startsWith("1.")) {
                text = text.substring(2);
            }
        }

        String build = "";
        int plus = text.indexOf('+');
        if (plus >= 0) {
            build = text.substring(plus + 1);
            text = text.substring(0, plus);
            if (build.isEmpty() || !isValidIdentifierSequence(build)) {
                return null;
            }
        }

        String[] prerelease = NO_PRERELEASE;
        int hyphen = text.indexOf('-');
        if (hyphen >= 0) {
            String prereleaseText = text.substring(hyphen + 1);
            text = text.substring(0, hyphen);
            if (prereleaseText.isEmpty() || !isValidIdentifierSequence(prereleaseText)) {
                return null;
            }
            prerelease = prereleaseText.split("\\.", -1);
            for (String identifier : prerelease) {
                if (identifier.isEmpty() || hasLeadingZero(identifier)) {
                    return null;
                }
            }
        }

        String[] numbers = text.split("\\.", -1);
        if (numbers.length == 0 || numbers.length > 3) {
            return null;
        }
        int[] components = new int[] {0, 0, 0};
        for (int i = 0; i < numbers.length; i++) {
            String component = numbers[i];
            if (component.isEmpty() || hasLeadingZero(component)) {
                return null;
            }
            for (int c = 0; c < component.length(); c++) {
                if (component.charAt(c) < '0' || component.charAt(c) > '9') {
                    return null;
                }
            }
            try {
                components[i] = Integer.parseInt(component);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new SemVer(components[0], components[1], components[2], prerelease, build);
    }

    private static boolean hasLeadingZero(String identifier) {
        return identifier.length() > 1 && identifier.charAt(0) == '0' && isNumeric(identifier);
    }

    private static boolean isValidIdentifierSequence(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == '-' || c == '.';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumeric(String identifier) {
        for (int i = 0; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !identifier.isEmpty();
    }

    // ------------------------------------------------------------------ accessors

    /** The major component. */
    public int major() {
        return major;
    }

    /** The minor component; {@code 0} when the source had only a major. */
    public int minor() {
        return minor;
    }

    /** The patch component; {@code 0} when the source had fewer than three components. */
    public int patch() {
        return patch;
    }

    /** The prerelease identifiers, empty for a release. */
    public String[] prerelease() {
        return prerelease.length == 0 ? NO_PRERELEASE : Arrays.copyOf(prerelease, prerelease.length);
    }

    /** Whether this is a prerelease — a snapshot, alpha, beta or release candidate. */
    public boolean isPrerelease() {
        return prerelease.length > 0;
    }

    /** Build metadata after {@code +}, or the empty string. Never affects ordering. */
    public String build() {
        return build;
    }

    /** Whether this is {@link #UNKNOWN}. */
    public boolean isUnknown() {
        return this == UNKNOWN || (major == 0 && minor == 0 && patch == 0
                && prerelease.length == 1 && "unknown".equals(prerelease[0]));
    }

    /** The same version without prerelease or build metadata. */
    public SemVer toRelease() {
        return prerelease.length == 0 && build.isEmpty() ? this : of(major, minor, patch);
    }

    /**
     * The lowest possible prerelease of this version, {@code X.Y.Z-0}.
     *
     * <p>This is how "include snapshots" is expressed as a real bound: {@code >=1.21.4} excludes
     * {@code 1.21.4-alpha.24.45.a} because a prerelease sorts below its release, whereas
     * {@code >=1.21.4-0} includes every prerelease of 1.21.4. The identifier {@code 0} is the
     * smallest numeric identifier, and numeric sorts before alphanumeric.
     */
    public SemVer withLowestPrerelease() {
        return new SemVer(major, minor, patch, new String[] {"0"}, "");
    }

    /** The next major version, {@code (major+1).0.0} — the exclusive upper bound of {@code ^}. */
    public SemVer nextMajor() {
        return of(major + 1, 0, 0);
    }

    /** The next minor version, {@code major.(minor+1).0} — the exclusive upper bound of {@code ~}. */
    public SemVer nextMinor() {
        return of(major, minor + 1, 0);
    }

    /** The next patch version, {@code major.minor.(patch+1)}. */
    public SemVer nextPatch() {
        return of(major, minor, patch + 1);
    }

    // ------------------------------------------------------------------ ordering

    @Override
    public int compareTo(SemVer other) {
        if (major != other.major) {
            return major < other.major ? -1 : 1;
        }
        if (minor != other.minor) {
            return minor < other.minor ? -1 : 1;
        }
        if (patch != other.patch) {
            return patch < other.patch ? -1 : 1;
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    private static int comparePrerelease(String[] left, String[] right) {
        if (left.length == 0 && right.length == 0) {
            return 0;
        }
        // A prerelease always sorts below its release.
        if (left.length == 0) {
            return 1;
        }
        if (right.length == 0) {
            return -1;
        }
        int shared = Math.min(left.length, right.length);
        for (int i = 0; i < shared; i++) {
            int result = compareIdentifier(left[i], right[i]);
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            // Compare by length first: identifiers have no leading zeroes, so a longer one is larger.
            if (left.length() != right.length()) {
                return left.length() < right.length() ? -1 : 1;
            }
            return left.compareTo(right);
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        return left.compareTo(right);
    }

    /** Whether this version is strictly lower than the other. */
    public boolean isLowerThan(SemVer other) {
        return compareTo(other) < 0;
    }

    /** Whether this version is strictly higher than the other. */
    public boolean isHigherThan(SemVer other) {
        return compareTo(other) > 0;
    }

    // ------------------------------------------------------------------ identity

    @Override
    public boolean equals(Object other) {
        return other instanceof SemVer && compareTo((SemVer) other) == 0;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        result = 31 * result + Arrays.hashCode(prerelease);
        return result;
    }

    /** The canonical string form, including prerelease and build metadata. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(16);
        out.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease.length > 0) {
            out.append('-');
            for (int i = 0; i < prerelease.length; i++) {
                if (i > 0) {
                    out.append('.');
                }
                out.append(prerelease[i]);
            }
        }
        if (!build.isEmpty()) {
            out.append('+').append(build);
        }
        return out.toString();
    }
}
