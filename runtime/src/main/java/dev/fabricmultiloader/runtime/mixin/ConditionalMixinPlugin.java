package dev.fabricmultiloader.runtime.mixin;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonLimits;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.json.JsonValue;
import dev.fabricmultiloader.runtime.loader.FabricLoaderFacade;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies a payload's mixins conditionally, declaratively.
 *
 * <p>Choosing between payloads is the loader's job and needs no plugin. What remains is the choice
 * <em>within</em> one payload: an integration mixin that only makes sense when an optional mod is
 * installed, a feature behind a launch flag, a mixin belonging to one physical side. Doing that in
 * Java would mean a class per payload; doing it here means a JSON block in the mixin config the
 * mixins already live in.
 *
 * <pre>
 * "plugin": "dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin",
 * "omni": {
 *   "conditions": {
 *     "ClothConfigScreenMixin": { "requireMod": "cloth-config", "version": "&gt;=15.0.0" },
 *     "JeiPluginMixin":         { "requireMod": "jei" }
 *   },
 *   "defaultDecision": "apply"
 * }
 * </pre>
 *
 * <h2>Why this class is written differently from the rest of the runtime</h2>
 *
 * <p>It is the only FabricMultiLoader class that can run <em>before</em> {@code preLaunch}. Mixin
 * instantiates it during {@code select()}, which is earlier than the container's entrypoint, earlier
 * than {@code RuntimeBootstrap}, and earlier than anything that could turn a failure into a
 * readable message. Two consequences follow, and both are enforced rather than merely intended:
 *
 * <ul>
 *   <li>It touches nothing from {@code runtime.boot}, {@code runtime.payload},
 *       {@code runtime.context} or {@code runtime.entrypoint} — only the JDK, {@code format} and the
 *       loader facade. Triggering the bootstrap from here would initialise the framework at a point
 *       where its own diagnostics do not yet work. Checked by {@code MixinPluginIsolationTest} and
 *       by the validator as {@code OMNI-1035}.
 *   <li>It fails open. Every error path — a missing config, malformed JSON, an unreadable file, an
 *       exception from anywhere — logs and then applies the mixin. A mixin applied when it should
 *       not have been fails loudly and specifically at the injection point; a mixin silently skipped
 *       produces a mod that is installed, starts cleanly and does nothing, which is far harder to
 *       diagnose.
 * </ul>
 */
public final class ConditionalMixinPlugin implements IMixinConfigPlugin {

    private final LoaderFacade loader;
    private final Map<String, Condition> conditions = new LinkedHashMap<String, Condition>();

    private boolean defaultApply = true;
    private String configName = "<unknown>";

    /** Mixin instantiates the plugin through this constructor. */
    public ConditionalMixinPlugin() {
        this(new FabricLoaderFacade());
    }

    /**
     * @param loader the loader facade; tests supply a fake one
     */
    ConditionalMixinPlugin(LoaderFacade loader) {
        this.loader = loader;
    }

    @Override
    public void onLoad(String mixinPackage) {
        try {
            List<ConfigLocator.Located> configs =
                    ConfigLocator.configsForPackage(loader, mixinPackage);
            if (configs.isEmpty()) {
                PluginLog.warn("OMNI-2200 no mixin config declares package '" + mixinPackage
                        + "' — every mixin in it will be applied unconditionally");
                return;
            }
            for (ConfigLocator.Located config : configs) {
                configName = config.config();
                parse(config);
            }
        } catch (Throwable thrown) {
            // Including Error: this runs before there is any way to report a failure to the user.
            PluginLog.warn("OMNI-2200 conditional mixin setup failed for package '" + mixinPackage
                    + "' (" + thrown + ") — all mixins in it will be applied");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            String simpleName = simpleName(mixinClassName);
            Condition condition = conditions.get(simpleName);
            if (condition == null) {
                return defaultApply;
            }
            boolean apply = condition.evaluate(loader);
            PluginLog.debug("OMNI-2201 " + configName + ": " + simpleName
                    + (apply ? " applied" : " skipped (" + condition.describe() + ")"));
            return apply;
        } catch (Throwable thrown) {
            PluginLog.warn("OMNI-2200 could not evaluate the condition for " + mixinClassName
                    + " (" + thrown + ") — applying it");
            return true;
        }
    }

    @Override
    public String getRefMapperConfig() {
        // The payload's own refmap, declared in the config's "refmap" field, is the right one and
        // Mixin uses it already. Returning a name here would override it with one that does not
        // exist.
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Nothing to coordinate: decisions here depend on the environment, not on which classes
        // other configs happen to target.
    }

    @Override
    public List<String> getMixins() {
        // The config's own "mixins" list is authoritative. Adding to it here would mean a mixin
        // that the validator, the refmap and a reader of the config all know nothing about.
        return Collections.emptyList();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // Bytecode transformation is the payload's business. This plugin decides whether a mixin
        // applies, never what it does.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        // See preApply.
    }

    /** The conditions read from the config, for tests and diagnostics. */
    Map<String, Condition> conditions() {
        return Collections.unmodifiableMap(conditions);
    }

    /** What an unlisted mixin does. */
    boolean defaultDecision() {
        return defaultApply;
    }

    /** The config this plugin instance belongs to. */
    String configName() {
        return configName;
    }

    private void parse(ConfigLocator.Located located) {
        JsonObject root = read(located);
        if (root == null) {
            return;
        }
        JsonObject omni = root.optObject("omni");
        if (omni == null) {
            // A config naming this plugin but declaring no conditions is legitimate — it is how a
            // developer disables the whole mechanism without editing two files.
            return;
        }
        defaultApply = !"skip".equals(omni.optString("defaultDecision", "apply"));

        JsonObject declared = omni.optObject("conditions");
        if (declared == null) {
            return;
        }
        for (String simpleName : declared.keys()) {
            try {
                conditions.put(simpleName, Condition.parse(declared.getObject(simpleName)));
            } catch (RuntimeException e) {
                PluginLog.warn("OMNI-2200 " + located.config() + ": the condition for "
                        + simpleName + " is invalid (" + e + ") — the mixin will be applied");
            }
        }
    }

    private JsonObject read(ConfigLocator.Located located) {
        InputStream in = null;
        try {
            Optional<Path> path = loader.findPath(located.modId(), located.config());
            if (!path.isPresent()) {
                return null;
            }
            in = Files.newInputStream(path.get());
            JsonValue parsed = Json.parse(in, JsonLimits.DEFAULT);
            return parsed.isObject() ? parsed.asObject() : null;
        } catch (Throwable thrown) {
            PluginLog.warn("OMNI-2200 could not read conditional mixin config '"
                    + located.config() + "' (" + thrown
                    + ") — all mixins in it will be applied");
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {
                    // Read-only stream from the loader's file system.
                }
            }
        }
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
