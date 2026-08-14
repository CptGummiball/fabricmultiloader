package dev.fabricmultiloader.runtime.mixin;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.util.Optional;

/**
 * When one mixin inside a payload should apply.
 *
 * <p>Three predicates, combined with AND, covering the cases that actually arise: an integration
 * mixin that only makes sense when an optional mod is present, a feature behind a launch flag, and
 * a mixin that belongs to one physical side. Anything more expressive would mean evaluating
 * arbitrary logic before {@code preLaunch}, which is the one place in the whole framework where
 * there is no diagnostic infrastructure to report a mistake with.
 *
 * <p>Declared in the {@code omni.conditions} block of the mixin config the plugin belongs to:
 *
 * <pre>
 * "ClothConfigScreenMixin": { "requireMod": "cloth-config", "version": "&gt;=15.0.0" },
 * "DebugOverlayMixin":      { "requireProperty": "examplemod.debugOverlay" },
 * "HudMixin":               { "requireEnv": "client" }
 * </pre>
 */
final class Condition {

    private final String requireMod;
    private final VersionRange version;
    private final String requireProperty;
    private final Side requireEnv;

    private Condition(String requireMod, VersionRange version, String requireProperty,
            Side requireEnv) {
        this.requireMod = requireMod;
        this.version = version;
        this.requireProperty = requireProperty;
        this.requireEnv = requireEnv;
    }

    /**
     * Reads a condition.
     *
     * @param json the condition object
     * @return the condition; one with no predicates always evaluates to {@code true}
     */
    static Condition parse(JsonObject json) {
        String mod = emptyToNull(json.optString("requireMod", ""));
        String range = emptyToNull(json.optString("version", ""));
        String property = emptyToNull(json.optString("requireProperty", ""));
        String environment = emptyToNull(json.optString("requireEnv", ""));

        return new Condition(
                mod,
                range == null ? null : VersionRange.parse(range),
                property,
                environment == null ? null : side(environment));
    }

    /**
     * An unrecognised side is dropped rather than thrown, in keeping with the plugin's fail-open
     * policy: a typo here should cost the condition, not the launch.
     */
    private static Side side(String value) {
        Side parsed = Side.parseOrNull(value);
        if (parsed == null) {
            PluginLog.warn("unknown requireEnv '" + value
                    + "' — expected \"client\" or \"server\"; the condition is ignored");
        }
        return parsed;
    }

    /**
     * Evaluates the condition.
     *
     * @param loader the loader facade
     * @return whether the mixin should apply
     */
    boolean evaluate(LoaderFacade loader) {
        if (requireMod != null) {
            if (!loader.isModLoaded(requireMod)) {
                return false;
            }
            if (version != null) {
                Optional<String> installed = loader.modVersion(requireMod);
                if (!installed.isPresent()
                        || !version.test(SemVer.parseLenient(installed.get()))) {
                    return false;
                }
            }
        }
        if (requireProperty != null && !readFlag(requireProperty)) {
            return false;
        }
        return requireEnv == null || requireEnv == loader.side();
    }

    /** Why the condition failed, for the debug trace. */
    String describe() {
        StringBuilder out = new StringBuilder();
        if (requireMod != null) {
            out.append("requires ").append(requireMod);
            if (version != null) {
                out.append(' ').append(version);
            }
        }
        if (requireProperty != null) {
            append(out, "-D" + requireProperty + "=true");
        }
        if (requireEnv != null) {
            append(out, "on the " + requireEnv.id());
        }
        return out.length() == 0 ? "no conditions" : out.toString();
    }

    @Override
    public String toString() {
        return describe();
    }

    private static void append(StringBuilder out, String text) {
        if (out.length() > 0) {
            out.append(" and ");
        }
        out.append(text);
    }

    /**
     * A system property is read defensively: a security manager can refuse the read, and refusing
     * to apply a mixin is the safe answer to "I could not find out".
     */
    private static boolean readFlag(String name) {
        try {
            return Boolean.parseBoolean(System.getProperty(name, "false"));
        } catch (SecurityException e) {
            PluginLog.warn("could not read system property '" + name + "' (" + e
                    + ") — treating the condition as unmet");
            return false;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
