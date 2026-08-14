package dev.fabricmultiloader.runtime.mixin;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.json.JsonValue;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Finds the mixin config a plugin instance belongs to.
 *
 * <p>Mixin hands {@code onLoad} the mixin <em>package</em> and nothing else — not the config file
 * the plugin was declared in, which is what the conditions live in. The obvious workaround,
 * {@code getResourceAsStream} on a guessed file name, is wrong in exactly the situation this
 * framework exists for: with several universal mods installed, the classpath holds several mixin
 * configs, and the first one found is not necessarily this one.
 *
 * <p>So the search goes the other way round: ask the loader which mods are present, read each one's
 * declared mixin configs, and keep those whose {@code package} matches. That is mod-scoped, exact,
 * and needs nothing beyond the loader facade and the JSON parser — which is also what keeps this
 * class inside the isolation rule the validator enforces ({@code OMNI-1035}).
 *
 * <p>Cost is one small read per mod, once, and only when a payload actually uses the plugin.
 */
final class ConfigLocator {

    /** A mixin config, together with the mod carrying it. */
    static final class Located {

        private final String modId;
        private final String config;

        Located(String modId, String config) {
            this.modId = modId;
            this.config = config;
        }

        /** The mod carrying the config. */
        String modId() {
            return modId;
        }

        /** The config file name, as declared in {@code fabric.mod.json}. */
        String config() {
            return config;
        }

        @Override
        public String toString() {
            return modId + ":" + config;
        }
    }

    /**
     * Every mixin config declaring the given package.
     *
     * @param loader the loader facade
     * @param mixinPackage the package Mixin passed to {@code onLoad}
     * @return the matching configs; normally exactly one
     */
    static List<Located> configsForPackage(LoaderFacade loader, String mixinPackage) {
        List<Located> matches = new ArrayList<Located>();
        if (mixinPackage == null || mixinPackage.isEmpty()) {
            return matches;
        }

        for (String modId : loader.loadedModIds()) {
            for (String config : declaredConfigs(loader, modId)) {
                JsonObject parsed = readJson(loader, modId, config);
                if (parsed != null && mixinPackage.equals(parsed.optString("package", null))) {
                    matches.add(new Located(modId, config));
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /** The {@code mixins} entries of a mod's {@code fabric.mod.json}. */
    private static List<String> declaredConfigs(LoaderFacade loader, String modId) {
        JsonObject metadata = readJson(loader, modId, "fabric.mod.json");
        if (metadata == null) {
            return Collections.emptyList();
        }
        JsonArray mixins = metadata.optArray("mixins");
        if (mixins == null) {
            return Collections.emptyList();
        }

        List<String> configs = new ArrayList<String>();
        for (JsonValue entry : mixins) {
            try {
                if (entry.isString()) {
                    configs.add(entry.asString());
                } else if (entry.isObject()) {
                    // Fabric's object form, { "config": "...", "environment": "client" }. The
                    // environment is Fabric's business, not ours: a config it did not register
                    // never reaches this plugin in the first place.
                    String config = entry.asObject().optString("config", null);
                    if (config != null) {
                        configs.add(config);
                    }
                }
            } catch (RuntimeException e) {
                PluginLog.warn("could not read a mixins entry of " + modId + ": " + e);
            }
        }
        return configs;
    }

    /**
     * Reads a JSON file out of a mod, or returns {@code null}.
     *
     * <p>Everything is tolerated: a mod with no such file, an unreadable file, a file that is not
     * JSON. This runs during mixin selection on behalf of a mod that has nothing to do with the one
     * being read, so a foreign mod's malformed metadata must not be able to abort the launch.
     */
    private static JsonObject readJson(LoaderFacade loader, String modId, String path) {
        Optional<Path> located;
        try {
            located = loader.findPath(modId, path);
        } catch (RuntimeException e) {
            return null;
        }
        if (!located.isPresent()) {
            return null;
        }
        InputStream in = null;
        try {
            in = Files.newInputStream(located.get());
            JsonValue parsed = Json.parse(in, dev.fabricmultiloader.format.json.JsonLimits.DEFAULT);
            return parsed.isObject() ? parsed.asObject() : null;
        } catch (IOException | RuntimeException e) {
            PluginLog.warn("could not read " + path + " of " + modId + ": " + e);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Read-only stream from the loader's file system.
                }
            }
        }
    }

    private ConfigLocator() {
        throw new AssertionError("no instances");
    }
}
