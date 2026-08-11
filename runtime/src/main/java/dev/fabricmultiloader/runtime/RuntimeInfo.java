package dev.fabricmultiloader.runtime;

import dev.fabricmultiloader.format.OmniFormat;

/**
 * Identity and capability declaration of the FabricMultiLoader runtime.
 *
 * <p>The runtime ships as its own Fabric mod ({@link OmniFormat#RUNTIME_MOD_ID}) nested into
 * every universal JAR. Fabric Loader deduplicates mods by id and selects the highest compatible
 * version, so exactly one runtime exists per game — deterministically the newest, rather than
 * whichever copy happened to come first on the classpath (chapter 13.4, ADR-008).
 *
 * <p>A hypothetical major 2 will ship under mod id {@code fabricmultiloader2} and package
 * {@code dev.fabricmultiloader.v2}, so that 1.x and 2.x can coexist and no mod is forced into a
 * flag-day update (chapter 42.3).
 */
public final class RuntimeInfo {

    /** Mod id under which the runtime is registered with Fabric Loader. */
    public static final String MOD_ID = OmniFormat.RUNTIME_MOD_ID;

    /** Highest container manifest schema version this runtime can interpret. */
    public static final int MAX_SUPPORTED_SCHEMA_VERSION = OmniFormat.SCHEMA_VERSION;

    /** Lowest container manifest schema version this runtime accepts. */
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * Whether this runtime understands a container manifest of the given schema version.
     *
     * <p>A newer schema is refused deterministically with {@code OMNI-2002} ("update
     * FabricMultiLoader") rather than being half-interpreted. Unknown <em>fields</em> within a
     * supported schema version are ignored instead — that is what keeps the format additively
     * extensible (chapter 42.4).
     *
     * @param schemaVersion the {@code schemaVersion} field of a container manifest
     * @return {@code true} if the manifest can be read
     */
    public static boolean supportsSchemaVersion(int schemaVersion) {
        return schemaVersion >= MIN_SUPPORTED_SCHEMA_VERSION
                && schemaVersion <= MAX_SUPPORTED_SCHEMA_VERSION;
    }

    private RuntimeInfo() {
        throw new AssertionError("no instances");
    }
}
