package dev.fabricmultiloader.format;

/**
 * Constants of the <em>Omni Container</em> format, version 1.
 *
 * <p>A universal JAR is an ordinary Fabric mod that carries one complete, separately built
 * Fabric mod ("payload") per supported Minecraft version range under {@link #NESTED_JAR_ROOT},
 * plus this format's manifest at {@link #CONTAINER_MANIFEST_PATH}. The format is a conformance
 * profile of the JAR format — there is no custom header, no custom compression and no custom
 * index, so that every existing tool keeps working.
 *
 * <p>The name deliberately avoids the abbreviation "FML", which is historically associated with
 * Forge Mod Loader.
 *
 * @see <a href="https://github.com/CptGummiball/fabricmultiloader/blob/main/docs/design/part-03-container-format.md">Chapter 10 — Universal Container Format</a>
 */
public final class OmniFormat {

    /** Format identifier written into the manifest and the JAR manifest attributes. */
    public static final String FORMAT_ID = "omni/1";

    /** Schema version of {@code omni-container.json} and {@code payload.json}. */
    public static final int SCHEMA_VERSION = 1;

    /** Container manifest — the source of truth for runtime, validator and tooling. */
    public static final String CONTAINER_MANIFEST_PATH = "META-INF/omni-container.json";

    /** Per-payload self-description, also used for the standalone dev fallback. */
    public static final String PAYLOAD_DESCRIPTOR_PATH = "omni/payload.json";

    /** Directory holding the nested runtime and payload mods. Fabric's standard JiJ location. */
    public static final String NESTED_JAR_ROOT = "META-INF/jars/";

    /**
     * Mod icon location. Deliberately outside {@code assets/}, so that the container does not
     * become a resource pack and cannot compete with its own payloads (chapter 25.1).
     */
    public static final String ICON_PATH = "omni/icon.png";

    /** Build-time input produced by the annotation processor; never read at runtime. */
    public static final String ENTRYPOINTS_PATH = "omni/entrypoints.json";

    /** JAR manifest attribute naming the format, e.g. {@code Omni-Container-Format: omni/1}. */
    public static final String MANIFEST_ATTRIBUTE_FORMAT = "Omni-Container-Format";

    /** JAR manifest attribute pointing at {@link #CONTAINER_MANIFEST_PATH}. */
    public static final String MANIFEST_ATTRIBUTE_MANIFEST = "Omni-Manifest";

    /**
     * Textual marker: a conforming container manifest starts with this byte sequence after
     * optional leading whitespace. Allows detection without a JSON parser.
     */
    public static final String FORMAT_MARKER_PREFIX = "{\"formatId\":\"omni/";

    /** Mod id of the runtime library, nested into every container and deduplicated by the loader. */
    public static final String RUNTIME_MOD_ID = "fabricmultiloader";

    /** Lowest Fabric Loader version the runtime is compiled and tested against. */
    public static final String MINIMUM_FABRIC_LOADER = "0.14.0";

    private OmniFormat() {
        throw new AssertionError("no instances");
    }
}
