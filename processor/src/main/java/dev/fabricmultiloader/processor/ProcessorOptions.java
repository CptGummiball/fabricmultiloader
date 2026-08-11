package dev.fabricmultiloader.processor;

/**
 * Annotation processor options accepted by the {@code @UniversalEntrypoint} processor.
 *
 * <p>Options are passed by the Gradle plugin as {@code -Aomni.modId=examplemod}. The processor
 * writes {@code omni/entrypoints.json} into the common module's resources; the
 * {@code generateOmniManifest} task merges it with the DSL declarations, and duplicates are a
 * build error (chapter 19.7).
 */
public final class ProcessorOptions {

    /** Mod id the generated entrypoint list belongs to. Required. */
    public static final String MOD_ID = "omni.modId";

    /** Comma-separated list of permitted common package prefixes. Optional but recommended. */
    public static final String COMMON_PACKAGES = "omni.commonPackages";

    /** Set to {@code true} to log every discovered entrypoint at NOTE level. Optional. */
    public static final String VERBOSE = "omni.verbose";

    /** All option keys, in declaration order. */
    public static String[] all() {
        return new String[] {MOD_ID, COMMON_PACKAGES, VERBOSE};
    }

    private ProcessorOptions() {
        throw new AssertionError("no instances");
    }
}
