package dev.fabricmultiloader.format.version;

/**
 * Java feature versions and their class file counterparts.
 *
 * <p>The arithmetic here is what makes a single universal JAR able to carry Java 17, 21 and 25
 * payloads at once. Fabric Loader evaluates {@code depends: {"java": ">=25"}} as a hard solver
 * clause, so a payload whose bytecode the running JVM could not verify is never selected — and
 * therefore never defined, which is why its class file version is irrelevant. The validator uses
 * the same conversion to prove, at build time, that a payload's declared {@code requires.java} and
 * its actual bytecode agree ({@code OMNI-1046}).
 *
 * <p>The relation {@code classFileMajor = javaFeature + 44} has held continuously since Java 1.1
 * (major 45), so no lookup table is needed and future Java versions require no configuration.
 */
public final class JavaVersions {

    /** Java 8 -> 52, 17 -> 61, 21 -> 65, 25 -> 69. */
    private static final int CLASS_FILE_MAJOR_OFFSET = 44;

    /** The lowest class file version any JVM accepts. */
    private static final int LOWEST_CLASS_FILE_MAJOR = 45;

    /** The Java baseline of the framework modules loaded inside Minecraft. */
    public static final int BASELINE_FEATURE_VERSION = 8;

    /** The class file version of {@link #BASELINE_FEATURE_VERSION}. */
    public static final int BASELINE_CLASS_FILE_MAJOR = 52;

    /**
     * The feature version of the running JVM.
     *
     * <p>Reads {@code java.specification.version} rather than {@code Runtime.version()}, because
     * this class is compiled to Java 8 bytecode and {@code Runtime.version()} only exists from
     * Java 9. Handles the legacy {@code 1.8} form.
     *
     * @return 8, 17, 21, 25, … or {@link #BASELINE_FEATURE_VERSION} if the property is unusable
     */
    public static int currentMajor() {
        return parseFeatureVersion(System.getProperty("java.specification.version", ""));
    }

    /**
     * Extracts a feature version from a Java version string.
     *
     * @param text {@code "1.8"}, {@code "1.8.0_402"}, {@code "17"}, {@code "21.0.7"}, {@code "25"}
     * @return the feature version, or {@link #BASELINE_FEATURE_VERSION} if it cannot be read
     */
    public static int parseFeatureVersion(String text) {
        if (text == null) {
            return BASELINE_FEATURE_VERSION;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return BASELINE_FEATURE_VERSION;
        }
        // Java 8 and earlier report "1.8"; the feature version is the second component.
        if (trimmed.startsWith("1.")) {
            trimmed = trimmed.substring(2);
        }
        int end = 0;
        while (end < trimmed.length() && trimmed.charAt(end) >= '0' && trimmed.charAt(end) <= '9') {
            end++;
        }
        if (end == 0) {
            return BASELINE_FEATURE_VERSION;
        }
        try {
            return Integer.parseInt(trimmed.substring(0, end));
        } catch (NumberFormatException e) {
            return BASELINE_FEATURE_VERSION;
        }
    }

    /**
     * The class file major version emitted by a given Java feature version.
     *
     * @param featureVersion 8, 17, 21, 25, …
     * @return 52, 61, 65, 69, …
     * @throws IllegalArgumentException for a version below 1
     */
    public static int classFileMajor(int featureVersion) {
        if (featureVersion < 1) {
            throw new IllegalArgumentException("invalid Java feature version: " + featureVersion);
        }
        return featureVersion + CLASS_FILE_MAJOR_OFFSET;
    }

    /**
     * The Java feature version that emits a given class file major version.
     *
     * @param classFileMajor 52, 61, 65, 69, …
     * @return 8, 17, 21, 25, …
     * @throws IllegalArgumentException for a major below 45
     */
    public static int featureVersionOf(int classFileMajor) {
        if (classFileMajor < LOWEST_CLASS_FILE_MAJOR) {
            throw new IllegalArgumentException("invalid class file major version: " + classFileMajor);
        }
        return classFileMajor - CLASS_FILE_MAJOR_OFFSET;
    }

    /**
     * A feature version as a comparable version, so it can be tested against a predicate such as
     * {@code ">=21"}. Fabric models its synthetic {@code java} mod the same way.
     */
    public static SemVer asVersion(int featureVersion) {
        return SemVer.ofMajor(featureVersion);
    }

    private JavaVersions() {
        throw new AssertionError("no instances");
    }
}
