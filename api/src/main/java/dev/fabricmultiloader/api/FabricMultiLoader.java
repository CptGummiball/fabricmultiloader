package dev.fabricmultiloader.api;

import dev.fabricmultiloader.api.platform.PlatformInfo;
import java.util.List;
import java.util.Optional;

/**
 * Static entry point for code that has no {@link ModContext} — third-party mods, debug tooling and
 * crash reporters.
 *
 * <p>Mod code should take a context parameter instead; that is what keeps it testable without
 * Minecraft. This exists for the cases where there is genuinely nothing to pass one through, and
 * for one question a context cannot answer: whether a <em>different</em> universal mod is actually
 * active.
 *
 * <p>{@link #isActive(String)} is the precise form of {@code FabricLoader.isModLoaded}. The two
 * differ only in one situation, and it is worth understanding: the container loads even when no
 * payload matches, so that it can print a useful diagnostic instead of leaving Fabric to report a
 * missing internal alias. In the default strict mode the launch then aborts and nothing observes
 * the difference — but a server running with {@code -Dfabricmultiloader.strict=false} keeps going
 * with the mod inactive, and there {@code isModLoaded} says yes while {@code isActive} says no.
 * Integrators should use this one.
 */
public final class FabricMultiLoader {

    private static volatile Provider provider = Provider.UNAVAILABLE;

    /**
     * Whether a universal mod is loaded <em>and</em> has an active payload.
     *
     * @param containerModId the mod id, e.g. {@code examplemod}
     */
    public static boolean isActive(String containerModId) {
        return provider.isActive(containerModId);
    }

    /**
     * Which payload is active for a container, e.g. {@code mc1214}.
     *
     * <p>Payload ids are an implementation detail of the mod that owns them. Fine to log or show in
     * a diagnostic; never depend on a particular value.
     */
    public static Optional<String> activePayload(String containerModId) {
        return provider.activePayload(containerModId);
    }

    /** What a container is running on, if it is active. */
    public static Optional<PlatformInfo> platformInfo(String containerModId) {
        return provider.platformInfo(containerModId);
    }

    /** Every universal mod present, whether or not it resolved. */
    public static List<String> containers() {
        return provider.containers();
    }

    /**
     * The full diagnostic report for a container — the same text a failed launch prints.
     *
     * <p>Worth attaching to a bug report about a universal mod: it names the active payload, the
     * detected environment and every rejected payload with its reason.
     */
    public static String diagnosticReport(String containerModId) {
        return provider.diagnosticReport(containerModId);
    }

    /** Whether the runtime is present and initialised. */
    public static boolean isAvailable() {
        return provider != Provider.UNAVAILABLE;
    }

    /**
     * Installed by the runtime during bootstrap. Not part of the public API.
     *
     * @param newProvider the implementation
     */
    public static void install(Provider newProvider) {
        provider = newProvider == null ? Provider.UNAVAILABLE : newProvider;
    }

    /** The runtime's implementation of the static queries. Not implemented by mods. */
    @ImplementedByFramework
    public interface Provider {

        /** Answers everything negatively; installed until the runtime bootstraps. */
        Provider UNAVAILABLE = new Provider() {
            @Override
            public boolean isActive(String containerModId) {
                return false;
            }

            @Override
            public Optional<String> activePayload(String containerModId) {
                return Optional.empty();
            }

            @Override
            public Optional<PlatformInfo> platformInfo(String containerModId) {
                return Optional.empty();
            }

            @Override
            public List<String> containers() {
                return java.util.Collections.emptyList();
            }

            @Override
            public String diagnosticReport(String containerModId) {
                return "FabricMultiLoader runtime is not initialised.";
            }
        };

        /** See {@link FabricMultiLoader#isActive(String)}. */
        boolean isActive(String containerModId);

        /** See {@link FabricMultiLoader#activePayload(String)}. */
        Optional<String> activePayload(String containerModId);

        /** See {@link FabricMultiLoader#platformInfo(String)}. */
        Optional<PlatformInfo> platformInfo(String containerModId);

        /** See {@link FabricMultiLoader#containers()}. */
        List<String> containers();

        /** See {@link FabricMultiLoader#diagnosticReport(String)}. */
        String diagnosticReport(String containerModId);
    }

    private FabricMultiLoader() {
        throw new AssertionError("no instances");
    }
}
