# 6. Evaluated Architecture Variants

Rating scale: **++** very good, **+** good, **o** neutral/laborious, **–** problematic, **– –** disqualifying.

## 6.1 Approach A — a bootstrap library loads version-specific classes from the same JAR

**Idea:** all version implementations live as classes inside the universal JAR (different packages); a bootstrap in
the `preLaunch` entrypoint detects the environment and instantiates the matching class via `Class.forName`. One
single `fabric.mod.json`.

| Criterion | Rating | Rationale |
|---|---|---|
| Fabric compatibility | + | An entirely ordinary mod with one entrypoint. |
| Mixin | **– –** | The `mixins` list of the single `fabric.mod.json` is registered in full. All mixin configs of all versions are parsed, all mixin classes read via ASM, all `targets` resolved. A 1.20.1 mixin on a method removed in 1.21.4 ⇒ hard startup crash. `IMixinConfigPlugin` cannot prevent it (5.3.2). |
| Access widener | **– –** | Only one `accessWidener` path per mod; it would have to be valid for all versions simultaneously. Not producible in a mapping-correct way. |
| Java | – | All classes live in the same classpath entry. As long as they are not defined, that is harmless — but the validator can no longer guarantee that nobody accidentally references a Java 21 class from common code. A single slip ⇒ `UnsupportedClassVersionError` on the player's machine. |
| Mappings | – | All version compilations would have to live in one artifact with **one** set of refmaps; refmaps are referenceable per config, so it is feasible, but Loom would have to compile N times into the same artifact — not supported, requires a custom Loom workaround. |
| Performance | ++ | No extraction. |
| Maintainability | o | One module, many packages; package discipline enforceable only by convention. |
| Effort | + | Low, as long as mixins/AW are ignored — which is not possible. |
| Error-proneness | – – | One wrong import in common code ⇒ crash on all other versions. |
| Debugging | + | One classpath, clean stack traces. |
| IDE | – | One module cannot compile against 1.20.1 and 1.21.4 simultaneously. Requires multi-module after all ⇒ the approach collapses. |
| Mod compatibility | + | One mod ID. |
| Future | – | Every new version enlarges the set of eagerly validated mixins. |

**Rejected** because of mixins and access wideners. The bootstrap idea itself is adopted, however — as a lifecycle
orchestrator, not as a classloading mechanism.

## 6.2 Approach B — remapped payload JARs as *resources* (not as Fabric nested JARs)

**Idea:** `payloads/1.20.1.jar` live as plain ZIP resources inside the universal JAR; the runtime extracts the
matching one and hooks it into `KnotClassLoader` via reflection (`addUrl`).

| Criterion | Rating | Rationale |
|---|---|---|
| Fabric compatibility | – – | `KnotClassLoader#addURL` is not public API; the signature changes between loader versions (`addUrlFwd`, `KnotClassDelegate#setAllowedPrefixes`). Reflection into loader internals violates G3 and breaks on every loader update. |
| Mixin | – – | By the time `preLaunch` runs, mixin configs are already registered (phase 2.4 < 2.5). A payload hooked in afterwards can no longer register its mixin configs. `Mixins.addConfiguration` *after* `MixinBootstrap` works only as long as the target classes are not yet loaded — coincidentally usually true for MC classes, but `MixinEnvironment` is already in phase `PREINIT`, and Fabric does not register the config with the required metadata (refmap remapper, mod association). Fragile and unspecified. |
| Access widener | – – | The `AccessWidenerClassTransformer` is already built at that point. Adding AW entries afterwards is not provided for. |
| Java | + | Non-selected payloads are not read. |
| Mappings | + | Payloads are pre-remapped. |
| Performance | o | Requires custom extraction plus a custom cache (violates NF-04). |
| Maintainability | – – | Coupled to loader internals. |
| Debugging | – | Classes from a retro-fitted path; IDE source association difficult. |
| Mod compatibility | o | Payload classes are visible to other mods, but the loader does not know the payload as a mod ⇒ no entrypoints, no `depends` checking, no ModMenu entry. |

**Rejected.** The approach loses precisely the loader services one needs.

## 6.3 Approach C — all version classes in different packages of the same JAR

Identical to A in every relevant respect (mixin/AW/Loom), plus the drawback that a single Gradle project cannot
compile against multiple MC versions. **Rejected.** Package separation itself is adopted as a *convention within*
the chosen architecture (chapter 22.4), because it makes stack traces and slim JARs unambiguous.

## 6.4 Approach D — a custom ClassLoader for payloads

| Criterion | Rating | Rationale |
|---|---|---|
| Mixin | – – | Mixin transforms exclusively classes that pass through Knot's transformer chain. A child loader bypasses it entirely ⇒ payload mixins never take effect. |
| Access widener | – – | Likewise: no AW transformer in the custom loader. |
| Class identity | – – | Minecraft types would have to be delegated to the parent (otherwise two `Item` classes ⇒ `ClassCastException` at every boundary). Delegation to Knot is possible (parent-first for `net.minecraft.**`), but then the payload classes live in a loader whose classes **cannot** be seen by mixin-transformed MC classes as soon as Minecraft resolves something via `Class.forName` in its own context. Registry callbacks, codecs with `Class` literals and datafixer reflection break unpredictably. |
| Fabric services | – – | `FabricLoader#getEntrypointContainers`, `getModContainer`, resource pack registration: all tied to mod candidates the loader knows about. |
| Debugging | – – | Duplicate class names in stack traces, unreliable IDE breakpoints. |

**Rejected as the primary mechanism.** No aspect of it is adopted. A custom ClassLoader appears **nowhere** in this
architecture — that is a deliberate, hard design boundary (ADR-002).

## 6.5 Approach E — Fabric's nested-JAR system (JiJ)

| Criterion | Rating | Rationale |
|---|---|---|
| Fabric compatibility | ++ | Exclusively documented features: `jars`, `depends`, `provides`, `breaks`, `environment`. |
| Mixin | ++ | Config per payload mod; not loaded ⇒ not registered ⇒ never validated. |
| Access widener | ++ | One AW per payload mod, correctly remapped by Loom. |
| Java | ++ | `depends.java` is evaluated by the solver; non-selected payloads are never defined. |
| Mappings | ++ | One Loom build per payload. |
| Performance | + | Loader-native extraction with hash cache; no custom mechanism. Cold start once ~20–60 ms per payload JAR, cache hit afterwards. |
| Maintainability | ++ | No loader internals, no bytecode engineering. |
| Effort | o | The effort sits entirely in the build toolchain — i.e. where mistakes surface at build time. |
| Error-proneness | + | The source of errors is the generator, not the player's machine. |
| Debugging | ++ | One ClassLoader, ordinary stack traces, `.fabric/processedMods` holds the real JARs for inspection. |
| IDE | ++ | One Gradle module per MC version = the standard Loom setup IntelliJ understands natively. |
| Mod compatibility | + | The container is an ordinary mod. Payloads appear as nested mods (ModMenu children). |
| Inter-mod communication | + | The common API in the container is binary-stable across all versions ⇒ third-party mods compile once against it. |
| Future | ++ | A new MC version = a new payload; nothing existing is touched. |
| Risk | o | One load-bearing assumption (5.1/2). Manageable with a conformance test and a fallback path. |

**Strongest approach.**

## 6.6 Approach F — build-time code generation + runtime dispatcher

**Idea:** a generator produces a dispatcher class from annotations (`switch` over the MC version) that instantiates
the correct adapter class.

This solves none of the hard problems (mixins, AW, descriptors, class file versions), but it is valuable as a
*convenience layer*: it eliminates boilerplate and makes payload metadata derivable from code.
**Adopted as a sub-component** (`fabricmultiloader-processor`, chapters 19.7, 23.5).

## 6.7 Approach G — combination

The real solution is a combination of **E** (core: isolation and selection), **F** (convenience: metadata and
entrypoint generation) and the *bootstrap idea* from **A** (convenience: lifecycle, diagnostics, determinism).
Approaches B, C and D are not used.

## 6.8 Approach H — further alternatives examined and likewise rejected

| Alternative | Rejected because |
|---|---|
| **Two-file solution** (a bootstrap mod downloads version-specific mods from the internet) | Violates G1 (one file) and platform policies (Modrinth/CurseForge forbid downloading code). Additionally a security disaster. |
| **Loader plugin / custom `GameProvider`** | Fabric has no public plugin interface ahead of `ModDiscoverer`. A custom `GameProvider` would replace the start sequence and be incompatible with every other tool (Prism, server packs, loader updates). |
| **Source preprocessor as a mandatory component** (Stonecutter/ReplayMod style, `//#if MC>=12100`) | Solves source duplication elegantly, but changes nothing about packaging, mixin isolation or AW. Compatible as an *optional* addition (chapter 24.8), not as a mandatory component: a preprocessor makes source code worse for IDEs and contributors and is therefore a G2 risk. |
| **One payload per MC version as a separate root JAR in `mods/<mcversion>/`** | Works (loader ≥ 0.15 supports versioned mod folders) but requires the player to place several files into the right subfolders ⇒ violates G1. |
| **Runtime bytecode rewriting to align APIs** | Non-goal N4. |

---

# 7. Final Architecture Decision

## 7.1 The decision

> **FabricMultiLoader implements approach G with approach E at its core:**
> The universal JAR is an ordinary Fabric mod (“**container**”) that contains, per supported Minecraft version
> range, one complete, separately built and remapped Fabric mod (“**payload**”) via Jar-in-Jar. The payload
> selection is made by the **Fabric-Loader-owned dependency solver** based on generated `depends` constraints that
> are proven disjoint at build time. FabricMultiLoader itself provides the runtime library (lifecycle,
> diagnostics, common API), the container format and the build toolchain — but **no** custom ClassLoader, **no**
> runtime bytecode transformation and **no** reflection into loader internals.

## 7.2 The five invariants

These invariants are normative. Every implementation decision must preserve them; the validator checks them.

* **I1 — One ClassLoader.** All classes of the container, of all payloads, of the runtime and of Minecraft are
  defined by `KnotClassLoader`. FabricMultiLoader never creates a ClassLoader.
* **I2 — Exactly one active payload.** At runtime exactly one payload is loaded per container. Guaranteed by: the
  build-time disjointness proof, `provides` alias exclusivity, mutual `breaks` declarations, and a runtime
  assertion with error code `OMNI-2003`.
* **I3 — The container does not touch Minecraft.** No references to `net/minecraft/**`, `com/mojang/**`,
  `net/fabricmc/fabric/api/**` in container classes. No mixins, no access widener, no `assets/` or `data/` entries
  in the container.
* **I4 — Payloads are complete and self-sufficient.** Every payload contains all classes, mixin configs, refmaps,
  access wideners, resources and version-specific libraries needed for its MC version. A payload is functional in
  a dev run without a container (dev fallback, 9.7) and publishable as a slim JAR.
* **I5 — All metadata is generated.** `fabric.mod.json` (container and payloads),
  `META-INF/omni-container.json` and `omni/payload.json` are produced exclusively by the Gradle plugin.
  Hand-written variants are a build error (`OMNI-1021`).

## 7.3 Why this decision satisfies the objective

| Original objective | Fulfilment |
|---|---|
| “One JAR, many MC versions” | Yes, literally one file. |
| “Detects the environment at startup” | Yes — detection happens in loader phase 2.3c (the solver) and is verified and reported by the runtime in `preLaunch`. Detection is therefore *earlier* than in the original idea, which is what makes it correct in the first place. |
| “Uses exclusively the matching implementation” | Yes, more strongly than required: the other implementations are not even on the classpath. |
| “Mixins, resources, integrations version-specific” | Yes, each natively via the payload mod metadata. |
| “A larger JAR is acceptable” | Used: no deduplication, full isolation in return. |

The only deviation from the original vision: **the dispatcher does not select classes itself; the loader selects
mods.** That is not a limitation but the precondition for mixins and access wideners to work at all
(chapters 5.3.2, 5.4.2).

---

# 8. Runtime Architecture

## 8.1 Components at runtime

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  JVM  ·  system ClassLoader                                                   │
│  ├── net.fabricmc.loader.**            (Fabric Loader, Knot, ModSolver)       │
│  └── org.spongepowered.asm.**          (Mixin)                                │
└───────────────────────────────────────────────────────────────────────────────┘
                                    │  creates
                                    ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│  KnotClassLoader   (transformers: AccessWidener → Mixin)                      │
│                                                                               │
│  ┌─ mod: minecraft ───────────────┐  ┌─ mod: fabric-api (+ modules) ────────┐ │
│  │  net.minecraft.**              │  │  net.fabricmc.fabric.api.**          │ │
│  └────────────────────────────────┘  └──────────────────────────────────────┘ │
│                                                                               │
│  ┌─ mod: fabricmultiloader  (nested, deduplicated, Java 8) ─────────────────┐ │
│  │  dev.fabricmultiloader.format.**     manifest, SemVer, predicates        │ │
│  │  dev.fabricmultiloader.api.**        common API (SPI for mod authors)    │ │
│  │  dev.fabricmultiloader.runtime.**    bootstrap, resolver, diagnostics    │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ mod: examplemod  (container = the universal JAR) ───────────────────────┐ │
│  │  com.example.common.**   platform-neutral mod code + public mod API      │ │
│  │  META-INF/omni-container.json                                            │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ mod: examplemod-mc1214  (nested, EXACTLY ONE active) ───────────────────┐ │
│  │  com.example.mc1214.**            adapter, mixins, registration         │ │
│  │  examplemod-mc1214.mixins.json / .client.mixins.json                    │ │
│  │  examplemod-mc1214-refmap.json                                          │ │
│  │  examplemod-mc1214.accesswidener                                        │ │
│  │  assets/examplemod/**  data/examplemod/**   (common ⊕ version, merged)   │ │
│  │  omni/payload.json                                                      │ │
│  │  META-INF/jars/cloth-config-15.0.140.jar   (version-specific library)   │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ NOT loaded: examplemod-mc1201.jar, examplemod-mc1211.jar ──────────────┐ │
│  │  sit untouched as ZIP entries inside the container. Never extracted,    │ │
│  │  never opened, never verified, not on the classpath.                    │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────┘
```

## 8.2 Dependency directions (compile and runtime)

```
fabricmultiloader-format   ──────────────┐            (Java 8, no dependencies)
        ▲                                │
        │                                ▼
fabricmultiloader-api ◄────── fabricmultiloader-runtime ──compileOnly──► fabric-loader
        ▲                                ▲
        │                                │ reads
   mod:common                     META-INF/omni-container.json
        ▲                                │
        │ compile + dev runtime          │
   mod:versions/mc-X ──────► Loom(MC, yarn, fabric-api) │
        │                                              │
        └────────── produces payload ──────────────────►┘
```

* `format` has **no** dependencies (not even Gson) — it ships its own minimal JSON parser (chapter 11.7) so it can
  be used both in the Gradle build and in the runtime without shading.
* `api` depends only on `format` (for `MinecraftVersion`, `VersionRange`).
* `runtime` depends on `format` + `api` and declares `fabric-loader` as `compileOnly`.
* On the mod side: `common` sees only `api` (+ `format` transitively). `versions/mc-X` sees `api`, `common`, and
  Minecraft plus Fabric API through Loom.

## 8.3 Runtime object model

```
RuntimeRegistry (singleton, inside fabricmultiloader)
 ├─ Map<String /*containerModId*/, ContainerRuntime>
 │
 └─ ContainerRuntime
     ├─ ContainerManifest      (from META-INF/omni-container.json, immutable)
     ├─ Environment            (MC, loader, API, Java version, EnvType, dev?)
     ├─ ResolutionReport       (per payload: matched / rejected + reason)
     ├─ PayloadDescriptor      (the active payload entry)
     ├─ Platform               (instance from the payload's platformFactory)
     ├─ ModContextImpl         (handed to the mod code)
     └─ LifecycleState         (DISCOVERED → RESOLVED → PLATFORM_READY →
                                COMMON_INIT → SIDE_INIT → RUNNING | FAILED)
```

`RuntimeRegistry` supports several containers at once: two different universal mods in the same game are
independent `ContainerRuntime` instances. Thanks to loader deduplication (5.2.1), the runtime mod itself exists
only once.

## 8.4 Thread and state model

* All bootstrap steps run on the loader thread (main), synchronously, inside `preLaunch` and the initialiser
  phases. No custom thread, no executor.
* `RuntimeRegistry` uses `ConcurrentHashMap` and `computeIfAbsent` to be robust against unusual entrypoint
  orderings; every state transition method is idempotent and logs double invocations at `DEBUG`.
* State transitions are forward-only; a step backwards is a programming error (`OMNI-4001`).

---

# 9. Bootstrap Sequence

## 9.1 Overall flow

```
Fabric Loader ModDiscoverer
  reads fabric.mod.json of: container, runtime, all payloads              [JSON only]
        │
        ▼
Fabric Loader ModSolver
  selects: container (mandatory) + runtime + EXACTLY ONE payload          [SAT]
        │
        ▼
Loader: extract selected JiJ mods, extend classpath,
        merge access wideners, register mixin configs
        │
        ▼
preLaunch phase  (topological: fabricmultiloader → examplemod → examplemod-mc1214)
        │
        ├─► [1] RuntimeBootstrap (from mod fabricmultiloader, no entrypoint —
        │        initialised statically on first access)
        │        · determine the environment
        │        · discover all containers (scan loaded mods for a manifest)
        │
        ├─► [2] ContainerPreLaunch  (the container's entrypoint)
        │        · load manifest, validate, check schema version
        │        · compute the ResolutionReport (self-check against the environment)
        │        · exactly-one-payload assertion  → otherwise OMNI-2003/2004/2005 + abort
        │        · integrity check: SHA-256 of the active payload (optional, on by default)
        │        · start banner + diagnostic report
        │
        └─► [3] PayloadPreLaunch  (the payload's entrypoint)
                 · instantiate platformFactory  → Platform
                 · Platform#onPreLaunch(PreLaunchContext)
                 · LifecycleState = PLATFORM_READY
        │
        ▼
Minecraft classes are loaded  → the active payload's mixins apply
        │
        ▼
main phase  →  PayloadMain
                 · CommonBootstrap: UniversalMod#onInitialize(ModContext)  [common]
                 · Platform#onInitialize(ModContext)                       [version]
                 · LifecycleState = COMMON_INIT
        │
        ▼
client phase → PayloadClient          server phase → PayloadServer
   · UniversalClientMod#onInitializeClient   · UniversalServerMod#onInitializeServer
   · Platform#onInitializeClient             · Platform#onInitializeServer
        │
        ▼
LifecycleState = RUNNING;  Events#gameStarted fires on the first server/client tick
```

## 9.2 Exact entry point and first loaded class

* **The very first FabricMultiLoader class:**
  `dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch`, loaded by `KnotClassLoader` when
  `EntrypointUtils.invoke("preLaunch", …)` resolves the container entrypoint.
  Its static initialiser is empty; `onPreLaunch()` first calls `RuntimeBootstrap.get()`, which kicks off the actual
  initialisation.
* Before that point **no** FabricMultiLoader code executes. Everything earlier is declarative (JSON).
* One exception exists only if a payload uses the optional `ConditionalMixinPlugin` (chapter 16.6): then
  `dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin` is loaded already in phase 2.4/`select()` — i.e.
  **before** `preLaunch`. That class is therefore deliberately written to use only `format` classes and the
  `FabricLoader` API and to **never** trigger `RuntimeBootstrap`. The validator checks this isolation
  (`OMNI-1035`).

## 9.3 The bootstrap's compilation target

| Property | Value |
|---|---|
| Bytecode target | `--release 8` (class file major 52) |
| Permitted dependencies | JDK 8 API, `dev.fabricmultiloader.format.**`, `dev.fabricmultiloader.api.**`, `net.fabricmc.loader.api.**`, `net.fabricmc.api.EnvType` |
| Forbidden dependencies | anything under `net.minecraft`, `com.mojang`, `net.fabricmc.fabric.api`, `org.spongepowered`, `net.fabricmc.loader.impl` |
| Fabric Loader compile version | `net.fabricmc:fabric-loader:0.14.0` (`compileOnly`) — the lowest supported, which rules out accidental use of newer API |
| Loader API used | `FabricLoader.getInstance()`, `getModContainer(String)`, `getAllMods()`, `isModLoaded(String)`, `getEnvironmentType()`, `isDevelopmentEnvironment()`, `getGameDir()`, `getConfigDir()`, `getObjectShare()`, `ModContainer#getMetadata()`, `ModContainer#findPath(String)`, `ModMetadata#getId()/getVersion()/getName()`, `Version#getFriendlyString()` |
| Logging | `java.util.logging` is forbidden; output via `System.out`/`System.err`? **No** — see 9.8: SLF4J via reflection with a fallback |

## 9.4 Environment detection

```java
package dev.fabricmultiloader.runtime.env;

public final class EnvironmentDetector {

    public static Environment detect() {
        FabricLoader loader = FabricLoader.getInstance();

        SemVer minecraft = loader.getModContainer("minecraft")
                .map(c -> SemVer.parseLenient(c.getMetadata().getVersion().getFriendlyString()))
                .orElseThrow(() -> new OmniException(ErrorCode.OMNI_2010,
                        "Minecraft mod container not present — unsupported launch setup."));

        SemVer fabricLoader = loader.getModContainer("fabricloader")
                .map(c -> SemVer.parseLenient(c.getMetadata().getVersion().getFriendlyString()))
                .orElse(SemVer.UNKNOWN);

        SemVer fabricApi = firstPresent(loader, "fabric-api", "fabric");   // 'fabric' = alias of fabric-api

        int javaMajor = JavaVersions.currentMajor();                       // 8, 17, 21, 25, …

        Side side = loader.getEnvironmentType() == EnvType.CLIENT ? Side.CLIENT : Side.SERVER;

        return new Environment(minecraft, fabricLoader, fabricApi, javaMajor, side,
                loader.isDevelopmentEnvironment(), loadedModVersions(loader));
    }
}
```

**Detection details:**

| Quantity | Source | Notes |
|---|---|---|
| Minecraft version | mod container `minecraft` | Fabric already normalises to SemVer form: `1.20.1`, `1.21.4`, snapshots as `1.21.5-alpha.24.45.a`, pre-releases as `1.21.4-rc.1`. `parseLenient` additionally accepts two-part schemes such as `26.1` (→ `26.1.0`) for future Mojang versioning. |
| Fabric Loader | mod container `fabricloader` | Always present. |
| Fabric API | mod container `fabric-api`, alternatively the alias `fabric` | Legitimately absent when the mod does not need Fabric API ⇒ `Optional`. Note: single-module installations (only `fabric-networking-api-v1`) provide no `fabric-api`; the resolver therefore additionally checks per-payload declared module IDs (chapter 12.4). |
| Java | `Runtime.version()` from 9 onwards, otherwise `System.getProperty("java.specification.version")` with a `1.8` special case | Compiled to Java 8 bytecode, hence reflection-free via the property. |
| Side | `loader.getEnvironmentType()` | The **physical** side (client JAR vs. server JAR), not the logical one. |
| Dev | `loader.isDevelopmentEnvironment()` | Controls the dev fallback (9.7) and namespace expectations. |
| Loaded mods | `loader.getAllMods()` | For `requires.mods` checks and the diagnostic report. |

## 9.5 Container discovery

```java
for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
    Path manifest = mod.findPath("META-INF/omni-container.json").orElse(null);
    if (manifest == null) continue;
    ContainerManifest m = ManifestReader.read(manifest, mod.getMetadata().getId());
    registry.register(new ContainerRuntime(m, environment, mod));
}
```

* `findPath` is loader ≥ 0.12 API and returns a `Path` inside a `ZipFileSystem` or a directory (dev). No manual
  ZIP handling, no Zip Slip risk.
* The scan is O(number of mods) with one file lookup per mod; measured at < 3 ms with 300 mods (NF-01).
* The mod ID from the manifest must match the ID of the carrying `ModContainer`, otherwise `OMNI-2012`
  (“manifest belongs to a different mod — the JAR was tampered with or assembled incorrectly”).

## 9.6 Self-check: exactly one payload

```java
List<PayloadDescriptor> loaded  = new ArrayList<>();
List<Rejection>        rejected = new ArrayList<>();

for (PayloadDescriptor p : manifest.payloads()) {
    if (FabricLoader.getInstance().isModLoaded(p.modId())) {
        loaded.add(p);
    } else {
        rejected.add(PayloadMatcher.explain(p, environment));   // yields a concrete reason
    }
}

switch (loaded.size()) {
    case 1  -> activate(loaded.get(0));
    case 0  -> fail(ErrorCode.OMNI_2003, DiagnosticReport.noMatchingPayload(manifest, environment, rejected));
    default -> fail(ErrorCode.OMNI_2004, DiagnosticReport.ambiguousPayloads(manifest, loaded));
}
```

`PayloadMatcher.explain` re-evaluates the payload's **own** declared constraints against the detected environment
and thereby yields the *substantive* reason (“Fabric API 0.110.0 < 0.114.0 required”) rather than merely “mod not
loaded”. That is the difference between a loader message and a usable error message (chapter 29).

The `default` case (several payloads loaded) is ruled out by build-time disjointness and `provides` exclusivity; it
is checked nonetheless, because a tampered or hand-merged JAR can produce it.

## 9.7 Dev fallback (payload runs without a container)

In a Loom dev run of a version module (`./gradlew :versions:mc-1.21.4:runClient`) the container does not exist. So
that the dev loop works, every payload is self-sufficient (invariant I4):

```
PayloadPreLaunch
  ├─ is the container mod loaded (from omni/payload.json: containerModId)?
  │    yes → normal path, the container manifest is authoritative
  │    no  → DEV FALLBACK:
  │           · permitted only when FabricLoader#isDevelopmentEnvironment()
  │             OR the system property fabricmultiloader.slim=true (slim JAR mode)
  │           · omni/payload.json contains an embedded copy of
  │             container.modId/modVersion/entrypoints  → a synthetic
  │             ContainerManifest is built from it (1 payload: this one)
  │           · warning OMNI-2100 at INFO level: "running standalone payload"
  └─ continue as normal
```

Result: the same code, the same lifecycle, the same API — in a dev run, in the universal JAR, and in a slim JAR.
No second code path in the mod code.

## 9.8 Logging

* The runtime uses **SLF4J** if available, otherwise `System.err`. Determined once, reflectively,
  Java-8-compatible:

```java
final class Log {
    private static final Object SLF4J = tryCreate("dev.fabricmultiloader");   // null if unavailable
    static void info (String msg) { emit("INFO",  msg, null); }
    static void warn (String msg, Throwable t) { emit("WARN", msg, t); }
    static void error(String msg, Throwable t) { emit("ERROR", msg, t); }
    // emit(): reflective call of org.slf4j.Logger#info/warn/error,
    //         fallback: System.err.println("[FabricMultiLoader/LEVEL] " + msg)
}
```

Rationale: SLF4J is guaranteed present on MC ≥ 1.17, but not on 1.16.5. A hard SLF4J compile dependency would be a
`NoClassDefFoundError` in the bootstrap on 1.16.5 — precisely the failure mode we want to avoid. The reflective
access costs ~0.3 ms once.

* **Default startup output** (level INFO, one line per container):

```
[FabricMultiLoader] examplemod 2.0.0 → payload 'mc1214' (examplemod-mc1214 2.0.0+mc1.21.4)
                    mc=1.21.4 loader=0.16.9 fabric-api=0.114.0 java=21 side=CLIENT
```

* **Debug mode** `-Dfabricmultiloader.debug=true`: the full `ResolutionReport` (all payloads with match/reject
  reasons), a manifest dump, timings per bootstrap phase, and the path of the extracted payload.

## 9.9 Bootstrap error handling — complete case matrix

| Case | Detection | Code | Behaviour |
|---|---|---|---|
| Minecraft version outside the union of payload ranges | Fabric solver (container `depends.minecraft`) | — | The loader shows its own error GUI with the permitted ranges. Container code does not run. |
| MC supported, but no payload selectable (Fabric API too old, Java too old, foreign mod missing, client-only payload on a server) | `ContainerPreLaunch` | `OMNI-2003` | Diagnostic report (chapter 29.2), abort (`strict=true`) or warning + deactivation (`strict=false`). |
| Several payloads loaded | `ContainerPreLaunch` | `OMNI-2004` | Abort; the report lists the collision. |
| Manifest missing/unparseable | `ManifestReader` | `OMNI-2001` | Abort: “container corrupted — please re-download”, including the JAR's SHA-256. |
| Manifest schema version > supported | `ManifestReader` | `OMNI-2002` | Abort: “FabricMultiLoader ≥ X required” + download link. |
| Manifest requires a newer runtime (`minRuntime`) | `ContainerPreLaunch` | `OMNI-2002` | As above. |
| Manifest mod ID ≠ carrying mod ID | `ContainerPreLaunch` | `OMNI-2012` | Abort: JAR tampered with / built incorrectly. |
| SHA-256 of the active payload differs | `IntegrityChecker` | `OMNI-2013` | Abort (disableable via `-Dfabricmultiloader.verify=false`); the message states expected/actual. |
| `platformFactory` class missing | `PlatformLoader` | `OMNI-2020` | Abort with FQCN, payload ID and a hint about a corrupted payload. |
| `platformFactory` throws | `PlatformLoader` | `OMNI-2021` | Abort, cause passed through as `cause`, report attached. |
| Common entrypoint class missing/throws | `CommonBootstrap` | `OMNI-2030/2031` | Abort with class name and phase. |
| Java version too old for the container itself | container `depends.java` | — | Loader error GUI. |
| Java version too old for all payloads but sufficient for the container | `ContainerPreLaunch` | `OMNI-2003` | The diagnostic report names the exact required Java version per payload. |
| Duplicate container of the same mod ID (two universal JARs in the folder) | loader (mandatory, at-most-one) | — | Loader error “duplicate mod”. Standard behaviour, well understood. |
| Runtime mod missing (JiJ removed) | container `depends.fabricmultiloader` | — | Loader error “missing dependency fabricmultiloader”. |

## 9.10 Behaviour with unknown and future versions

* An MC version outside all ranges ⇒ loader message (controlled, see above).
* An MC version *inside* an open range (`>=1.21.4`) ⇒ the payload is loaded. Open upper bounds are permitted, but
  the validator warns (`OMNI-1050`) because they will inevitably break one day; the template uses closed ranges up
  to the next minor version by default.
* On a successful start the container writes
  `<gameDir>/.fabricmultiloader/<modid>-last-launch.json` with the environment and the chosen payload. That file is
  the first place to look in a support case (chapter 30.4) and is overwritten atomically on every start (temp file
  + `ATOMIC_MOVE`).

---

Continue with [chapters 10–12 — Omni container format, metadata schema, version resolver](part-03-container-format.md).
