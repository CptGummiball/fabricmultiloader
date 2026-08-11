package dev.fabricmultiloader.gradle;

/**
 * The four plugin ids published by {@code fabricmultiloader-gradle}.
 *
 * <p>They are deliberately separate rather than one root plugin configuring subprojects: Gradle
 * project isolation forbids a project from reading or mutating another project's model, and
 * cross-project configuration also breaks the configuration cache and produces incomplete IDE
 * models on the first sync. Each module applies its own plugin and reads the shared matrix file
 * independently (ADR-005).
 */
public final class PluginIds {

    /** Applied in {@code settings.gradle.kts}: repositories and auto-inclusion of modules. */
    public static final String SETTINGS = "dev.fabricmultiloader.settings";

    /** Applied to {@code :common}: baseline toolchain, API dependency, annotation processor. */
    public static final String COMMON = "dev.fabricmultiloader.common";

    /** Applied to {@code :versions:mc-*}: Loom setup, metadata generation, payload assembly. */
    public static final String VERSION = "dev.fabricmultiloader.version";

    /** Applied to the root project: DSL, manifest, assembler, validator, runs, publishing. */
    public static final String UNIVERSAL = "dev.fabricmultiloader.universal";

    /** File name of the version matrix, relative to the project root. */
    public static final String MATRIX_FILE = "gradle/fabricmultiloader.toml";

    /** All plugin ids, in the order they appear in a project. */
    public static String[] all() {
        return new String[] {SETTINGS, COMMON, VERSION, UNIVERSAL};
    }

    private PluginIds() {
        throw new AssertionError("no instances");
    }
}
