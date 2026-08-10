# 16. Mixin Architecture

## 16.1 The principle

> **Mixins are not “filtered”, they are “not shipped”.**
> A mixin set belongs to exactly one payload. If the payload is not loaded, its mixin config does not exist as far
> as registration is concerned, and its mixin classes are not on the classpath.

This meets the hardest requirement of the brief (“a 1.20.1 mixin class must not be accidentally loaded or validated
under 1.21.4”) not through filtering logic but through non-existence — the only form of isolation that is immune to
Sponge Mixin's eager `ClassInfo` resolution (chapter 5.3.2).

## 16.2 Directory and naming convention

```
versions/mc-1.21.4/src/main/
├── java/com/example/mc1214/
│   ├── Platform1214.java
│   ├── Platform1214Factory.java
│   ├── mixin/                                  ← common-side mixins
│   │   ├── MinecraftServerMixin.java
│   │   └── ItemStackMixin.java
│   └── client/mixin/                           ← client-only mixins
│       ├── TitleScreenMixin.java
│       └── ItemRendererMixin.java
└── resources/
    ├── examplemod-mc1214.mixins.json           ← generated? NO: hand-written (see 16.3)
    ├── examplemod-mc1214.client.mixins.json
    └── examplemod-mc1214.accesswidener
```

| Element | Convention | Enforced by |
|---|---|---|
| Mixin package | `<basePackage>.<payloadId>.mixin` resp. `….<payloadId>.client.mixin` | validator `OMNI-1034` |
| Config file name | `<modId>-<payloadId>.mixins.json`, `<modId>-<payloadId>.client.mixins.json`, `<modId>-<payloadId>.server.mixins.json` | plugin default + validator `OMNI-1030` |
| Refmap name | `<modId>-<payloadId>-refmap.json` | Loom property, set by the plugin |
| Access widener name | `<modId>-<payloadId>.accesswidener` | plugin default |

The naming schemes always contain the `payloadId`. Config, refmap and AW names are therefore unique across the
entire universal JAR — not because it is technically required (never more than one payload loads), but so that
stack traces, slim JARs, crash reports and manual debugging stay unambiguous.

## 16.3 Mixin configs: hand-written, but validated

Mixin configs are **not** generated. Rationale: they contain substantive decisions (`compatibilityLevel`, the
`mixins` selection, `injectors.defaultRequire`, the plugin class) that the developer must make deliberately; a
generator would either guess everything by convention (fragile) or require a second DSL (redundant). Instead the
validator checks them strictly, and the `omniPayload` task automatically enters them into the generated
`fabric.mod.json` and into the manifest — so the developer maintains them in exactly one place.

`versions/mc-1.21.4/src/main/resources/examplemod-mc1214.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "injectors": { "defaultRequire": 1 },
  "mixins": [
    "MinecraftServerMixin",
    "ItemStackMixin"
  ]
}
```

`versions/mc-1.21.4/src/main/resources/examplemod-mc1214.client.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.client.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "injectors": { "defaultRequire": 1 },
  "client": [
    "TitleScreenMixin",
    "ItemRendererMixin"
  ]
}
```

`versions/mc-26.1/src/main/resources/examplemod-mc261.mixins.json` differs only in
`compatibilityLevel: "JAVA_25"` and the package — another reason to keep configs per payload: the compatibility
level is version-bound and must not exceed the target JVM.

**Validator rules for mixin configs:**

| Code | Check |
|---|---|
| `OMNI-1100` | `package` starts with the prefix declared for this payload. |
| `OMNI-1101` | Every class named in `mixins`/`client`/`server` exists in the payload under `package`. |
| `OMNI-1102` | Every mixin class in the payload is named in exactly one config (catches “forgotten” mixins that silently do nothing). |
| `OMNI-1103` | The `refmap` exists in the payload, is valid JSON and contains only classes of this payload. |
| `OMNI-1104` | `compatibilityLevel` ≤ `JAVA_<payload.requires.java minimum>`. |
| `OMNI-1105` | Client mixin classes (package `*.client.mixin`) are registered exclusively in a config with `environment: "client"`. |
| `OMNI-1106` | No mixin class references `net/minecraft/client/**` while sitting in a non-client config. |
| `OMNI-1107` | `required: true` is set (otherwise Mixin swallows errors silently). |
| `OMNI-1108` | No container entry declares mixins (the counterpart to `OMNI-1024`: the container is mixin-free). |

## 16.4 Environment assignment in `fabric.mod.json`

The `omniPayload` task assigns configs automatically, based on the file name:

| File name ends with | Generated entry |
|---|---|
| `.client.mixins.json` | `{ "config": "…", "environment": "client" }` |
| `.server.mixins.json` | `{ "config": "…", "environment": "server" }` |
| `.mixins.json` | `"…"` (i.e. `environment: "*"`) |

The `environment` filter in `fabric.mod.json` is the **more effective** of the two available mechanisms: it
prevents the config from being registered at all on a dedicated server, so Mixin never reads the classes. The
config-internal `"client": [...]` list is used additionally (belt and braces) but is not sufficient on its own,
because Mixin parses the config's classes regardless.

## 16.5 Version-specific mixins — a concrete example

The signature divergence of `ItemRenderer#renderItem` between 1.20.1 and 1.21.4 is a real case.

`versions/mc-1.20.1/src/main/java/com/example/mc1201/client/mixin/ItemRendererMixin.java`:

```java
package com.example.mc1201.client.mixin;

import com.example.common.hook.ItemRenderHooks;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    // 1.20.1: renderItem(LivingEntity, ItemStack, ModelTransformationMode, boolean,
    //                    MatrixStack, VertexConsumerProvider, World, int, int, int)
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;"
                   + "Lnet/minecraft/item/ItemStack;"
                   + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
                   + "ZLnet/minecraft/client/util/math/MatrixStack;"
                   + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                   + "Lnet/minecraft/world/World;III)V",
            at = @At("HEAD"))
    private void examplemod$beforeRenderItem(LivingEntity entity, ItemStack stack,
                                             ModelTransformationMode mode, boolean leftHanded,
                                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             World world, int light, int overlay, int seed,
                                             CallbackInfo ci) {
        ItemRenderHooks.beforeRender(new Mc1201ItemRenderContext(stack, mode.name(), leftHanded));
    }
}
```

`versions/mc-1.21.4/src/main/java/com/example/mc1214/client/mixin/ItemRendererMixin.java`:

```java
package com.example.mc1214.client.mixin;

import com.example.common.hook.ItemRenderHooks;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemDisplayContext;      // a different type in 1.21.4 than in 1.20.1
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void examplemod$beforeRenderItem(ItemStack stack, ItemDisplayContext context,
                                             boolean leftHanded, CallbackInfo ci) {
        ItemRenderHooks.beforeRender(new Mc1214ItemRenderContext(stack, context.name(), leftHanded));
    }
}
```

Both call the **same** common hook `ItemRenderHooks.beforeRender(ItemRenderContext)`. `ItemRenderContext` is a
common interface without Minecraft types:

```java
package com.example.common.hook;

public interface ItemRenderContext {
    String itemId();          // "minecraft:diamond_sword"
    int count();
    String displayMode();     // "GUI", "FIRST_PERSON_RIGHT_HAND", …
    boolean leftHanded();
}
```

This is the pattern for **every** version-specific mixin: *the mixin lives in the payload, the business logic lives
in the common code, and the boundary is a Minecraft-free interface.* That keeps the version-specific portion down
to a few lines of adapter logic.

Had `ItemRendererMixin` from the 1.20.1 payload been loaded under 1.21.4, the result would be a hard
`InvalidInjectionException` on the first render — the failure this architecture makes impossible: the 1.20.1 class
does not exist on the classpath under 1.21.4.

## 16.6 Conditional mixins *within* a payload

Within a payload there remains a legitimate need for conditions: integration mixins that should apply only when an
optional foreign mod is loaded, or that should be switchable via config. FabricMultiLoader ships a declarative
config plugin for that.

`examplemod-mc1214.integration.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.integration.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "plugin": "dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin",
  "mixins": ["ClothConfigScreenMixin", "JeiPluginMixin"],
  "injectors": { "defaultRequire": 1 },
  "omni": {
    "conditions": {
      "ClothConfigScreenMixin": { "requireMod": "cloth-config", "version": ">=15.0.0" },
      "JeiPluginMixin":         { "requireMod": "jei" }
    },
    "defaultDecision": "apply"
  }
}
```

Implementation (complete, with no omissions of the relevant logic):

```java
package dev.fabricmultiloader.runtime.mixin;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative mixin config plugin. Reads the "omni" block of its own mixin config
 * and decides per mixin class whether it is applied.
 *
 * IMPORTANT: this class runs BEFORE the preLaunch phase (Mixin select()).
 * It may use only the JDK, format and FabricLoader APIs and must in particular
 * NOT trigger RuntimeBootstrap. Validator rule OMNI-1035.
 */
public final class ConditionalMixinPlugin implements IMixinConfigPlugin {

    private final Map<String, Condition> conditions = new HashMap<String, Condition>();
    private boolean defaultApply = true;
    private String configName = "<unknown>";

    @Override
    public void onLoad(String mixinPackage) {
        // The config name is not available via getRefMapperConfig(); Mixin passes only
        // the package to onLoad. We derive the config file from the package by scanning
        // all mixin configs on the classpath that declare this package.
        for (String candidate : ConfigLocator.configsForPackage(mixinPackage)) {
            configName = candidate;
            parse(candidate);
        }
    }

    private void parse(String resource) {
        try (InputStream in = ConditionalMixinPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return;
            JsonObject root = Json.parse(readAll(in)).asObject();
            JsonObject omni = root.optObject("omni");
            if (omni == null) return;
            defaultApply = !"skip".equals(omni.optString("defaultDecision", "apply"));
            JsonObject cond = omni.optObject("conditions");
            if (cond == null) return;
            for (String simpleName : cond.keys()) {
                conditions.put(simpleName, Condition.parse(cond.getObject(simpleName)));
            }
        } catch (Exception e) {
            // A broken plugin must not break startup: fail open with a warning.
            PluginLog.warn("OMNI-2200 could not read conditional mixin config '" + resource
                    + "': " + e + " — all mixins in this config will be applied.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        Condition c = conditions.get(simple);
        if (c == null) return defaultApply;
        boolean apply = c.evaluate();
        PluginLog.debug("OMNI-2201 " + configName + ": " + simple
                + (apply ? " applied" : " skipped (" + c.describe() + ")"));
        return apply;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return Collections.emptyList(); }
    @Override public void preApply (String t, ClassNode n, String m, IMixinInfo i) { }
    @Override public void postApply(String t, ClassNode n, String m, IMixinInfo i) { }

    /** requireMod + optional version, requireProperty, requireEnv. */
    static final class Condition {
        String requireMod; VersionRange version; String requireProperty; String requireEnv;

        boolean evaluate() {
            FabricLoader loader = FabricLoader.getInstance();
            if (requireMod != null) {
                if (!loader.isModLoaded(requireMod)) return false;
                if (version != null) {
                    SemVer v = SemVer.parseLenient(loader.getModContainer(requireMod).get()
                            .getMetadata().getVersion().getFriendlyString());
                    if (!version.test(v)) return false;
                }
            }
            if (requireProperty != null && !Boolean.parseBoolean(System.getProperty(requireProperty, "false")))
                return false;
            if (requireEnv != null && !requireEnv.equalsIgnoreCase(loader.getEnvironmentType().name()))
                return false;
            return true;
        }
        String describe() { … }
        static Condition parse(JsonObject o) { … }
    }
}
```

**Limits that must be documented** (`docs/mixins.md`):

* `shouldApplyMixin` prevents *application*, not the *loading and validation* of the mixin class. An integration
  mixin class may therefore reference only types that exist in this MC version; for foreign-mod types that means
  the mixin may use `cloth-config` classes in its body (resolved lazily) but **not** in `@Mixin(Target.class)` —
  there a `targets = "…"` string with the fully qualified name is required, because otherwise the target class is
  resolved eagerly and is missing without the foreign mod.
* Consequently, for optional foreign-mod integrations there is an additional constraint: registering a mixin config
  only when the mod is present is **not** possible declaratively (the `fabric.mod.json` is static). The robust
  route for hard foreign-mod dependencies is a **dedicated payload** with `requires.mods` — or dropping mixins and
  using the foreign mod's official API.

## 16.7 Error handling for mixin problems

| Situation | Who reports | Improvement by FabricMultiLoader |
|---|---|---|
| A mixin target does not exist (the wrong payload for this MC version, due to a matrix misconfiguration) | Mixin `InvalidInjectionException` at class load | In `PayloadPreLaunch` the runtime installs a **crash report attachment** via the payload adapter (`Platform#installCrashContext`), independent of any `Thread.UncaughtExceptionHandler`, adding the block “FabricMultiLoader: examplemod 2.0.0, payload mc1214, mc 1.21.4, java 21” to every crash report. The bug report then immediately shows which payload was active. |
| Mixin config in the payload but not in `fabric.mod.json` | nobody (the mixin silently does nothing) | Validator `OMNI-1109`: every `*.mixins.json` in the payload must be registered in the `fabric.mod.json`. |
| Mixin config in `fabric.mod.json`, file missing | loader: hard startup failure | Validator `OMNI-1110` catches it at build time. |
| Two payloads with identical config names | nobody (only one loads) | Validator `OMNI-1030` enforces uniqueness. |
| `compatibilityLevel` higher than the JVM | Mixin `IllegalArgumentException` at startup | Validator `OMNI-1104`. |

## 16.8 Why there is no “mixin dispatcher”

A central “mixin dispatcher” deciding at runtime which mixin sets are active was part of the original project idea.
In this architecture it is **absent and unnecessary** — and that is an improvement, not an omission:

* A dispatcher could only register mixins after `MixinBootstrap` (phase 2.4 is over by the time mod code runs).
  Retroactive `Mixins.addConfiguration` calls are unspecified and unsupported in Fabric.
* A dispatcher could not prevent the eager `ClassInfo` resolution.
* The loader already makes the selection — deterministically, before any class is touched, with full error
  diagnostics.

The role the dispatcher was meant to fill (ensuring only the right mixins apply) is therefore fully taken over by
the loader's payload selection plus build-time validation.

---

# 17. Access Widener Architecture

## 17.1 The starting problem

Fabric Loader accepts exactly **one** `accessWidener` path per mod, the file is mapping-dependent (the namespace
header is checked), and member names can differ between MC versions. A single cross-version AW file is therefore
not producible in a mapping-correct way (chapter 5.4.2).

## 17.2 Solution

**One access widener per payload.** Since a payload is its own Fabric mod, the “one file per mod” rule applies per
payload — not per universal JAR. The loader merges the AW files of all *loaded* mods; since only one payload is
loaded, exactly one mod-owned AW is active.

```
common/src/main/accesswidener/shared.accesswidener            (namespace named, meant to be version-neutral)
versions/mc-1.20.1/src/main/resources/examplemod-mc1201.accesswidener   (namespace named, version-specific)
versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener
versions/mc-26.1/src/main/resources/examplemod-mc261.accesswidener
        │
        │  mergeAccessWidener<Payload>   (build time, BEFORE the Loom remap, in the named namespace)
        ▼
versions/mc-X/build/omni/accesswidener/examplemod-mcX.accesswidener
        │  Loom remapJar  (named → intermediary)
        ▼
payload:  examplemod-mcX.accesswidener   (namespace intermediary)
```

## 17.3 Merge semantics

`MergeAccessWidenerTask` (Gradle, declared inputs/outputs, cacheable):

1. Reads the shared file `common/src/main/accesswidener/shared.accesswidener`, if present.
2. Reads the payload-specific file, if present.
3. Checks: both must have `accessWidener v2 named` as their header (`OMNI-1120`, otherwise an error with a line
   reference).
4. Writes a header `accessWidener v2 named` followed by the union of the entries:
   * Line-wise normalisation: comments (`#`) removed, whitespace collapsed, blank lines removed.
   * Deduplication over the normalised text.
   * Sorting by `(type, class, member)` lexicographically — for reproducibility.
   * Where entries for the same target conflict (`accessible` vs. `extendable` vs. `mutable`), **all** are kept
     (AW is additive; there is no conflict).
5. Prepends a generated comment block naming the source of each line — for debugging:

```
accessWidener v2 named
# generated by fabricmultiloader-gradle 1.0.0 for payload mc1214 — do not edit
# sources: common/src/main/accesswidener/shared.accesswidener (3 entries)
#          versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener (2 entries)
accessible	field	net/minecraft/client/MinecraftClient	itemUseCooldown	I
accessible	method	net/minecraft/entity/player/PlayerEntity	getAttackCooldownProgress	(F)F
extendable	class	net/minecraft/block/AbstractBlock
mutable	field	net/minecraft/entity/LivingEntity	activeItemStack	Lnet/minecraft/item/ItemStack;
```

`shared.accesswidener` is **not** a promise that its entries exist in every version — it is a convenience for the
common case. If an entry does not exist in a version it has no effect (chapter 5.4.1); the validator warns
(`OMNI-1121`) when an entry in a payload has no matching class in that payload's intermediary mappings, for which
it reads the tiny mappings file Loom provides for the version module. That check is a **warning**, not an error,
because AW entries may legitimately point at optional targets.

## 17.4 Access wideners for foreign-mod classes

Access wideners can affect only classes that pass through the Knot transformer — which means Minecraft **and all
mods**. An AW entry for a foreign-mod class is therefore technically possible, but:

* The namespace header is `named`/`intermediary` and refers to Minecraft mappings; foreign-mod classes are not
  mapped and must be entered with their real FQCN. Loom does not remap them (no mapping entry ⇒ passed through
  unchanged), so it works.
* The validator warns (`OMNI-1122`), because it is a fragile coupling to foreign-mod internals, and it requires an
  explicit opt-in: `omni { allowForeignAccessWidener("cloth-config") }` in the version module's DSL.

## 17.5 Why no custom transformer, no reflection, no build-time bytecode patch

Alternatives examined and rejected:

| Alternative | Rejected because |
|---|---|
| Load an additional `AccessWidener` at runtime | The `AccessWidenerClassTransformer` is built in phase 2.3g; there is no public API to add to it. Reflection on `FabricLoaderImpl#getAccessWidener` violates G3. |
| Reflection instead of access widening in the mod code | Works for field access and method invocation, but not for `extendable` (inheriting from final classes) and is not performant in hot paths. Additionally `setAccessible` breaks on JDK 17+ for JPMS-protected packages — not for Minecraft (unnamed module), but the failure class is unpleasant. Documented as a *supplement* for individual cases, not as a replacement. |
| Build-time bytecode patch (making classes public in the payload) | Would modify Minecraft bytecode, which is not part of our artifact — impossible, since Minecraft is not shipped. |
| Mixin `@Accessor`/`@Invoker` instead of AW | A legitimate and often **better** alternative: version-specific (it lives in the payload), refmap-backed, without a global visibility change. The documentation recommends `@Accessor`/`@Invoker` as the default and AW only for `extendable`/`mutable` and for access from many classes. |

## 17.6 Validator rules (summary)

| Code | Rule | Severity |
|---|---|---|
| `OMNI-1024` | The container declares **no** `accessWidener` | error |
| `OMNI-1082` | The AW namespace in the finished payload == `payload.mappings.namespace` (`intermediary`) | error |
| `OMNI-1120` | Source files have the header `accessWidener v2 named` | error |
| `OMNI-1121` | Every AW entry has a resolvable target in the payload's mappings | warning |
| `OMNI-1122` | AW entries for non-Minecraft classes are explicitly opted in | warning/error when the opt-in is missing |
| `OMNI-1123` | `payload.accessWidener` in the manifest == `accessWidener` in the payload `fabric.mod.json` == an existing file | error |
| `OMNI-1124` | The AW file is deterministically sorted (detects hand-editing of the generated artifact) | warning |

---

Continue with [chapters 18–19, 26–28 — common API, version adapter API, client/server, networking, registries](part-06-api.md).
