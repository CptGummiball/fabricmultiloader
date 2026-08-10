# 5. Fabric/JVM Feasibility Analysis

This chapter lays out the technical constraints from which the architecture follows necessarily. All statements
refer to Fabric Loader 0.14.0 – 0.17.x (as of 2026-08) and Sponge Mixin 0.8.5 – 0.8.7 in its Fabric variant. Every
statement that is architecturally load-bearing is marked **[LB]** and guarded by a conformance test in
[chapter 32](part-08-quality.md).

---

## 5.1 Fabric Loader's start sequence — the decisive timeline

The order of load phases determines which degrees of freedom FabricMultiLoader has at all. It is:

```
1.  JVM starts net.fabricmc.loader.impl.launch.knot.KnotClient/KnotServer   (system ClassLoader)
2.  Knot#init
    2.1  GameProvider detection (locate the Minecraft JAR, determine the MC version)
    2.2  KnotClassLoader is created (+ KnotClassDelegate, transformer chain)
    2.3  FabricLoaderImpl#setup
         a) ModDiscoverer: scan the mods/ directory
            - open every *.jar, read ONLY fabric.mod.json  (no bytecode!)
            - evaluate the "jars" array -> nested candidates, recursively,
              again reading ONLY their fabric.mod.json
         b) register built-in candidates: minecraft, java, fabricloader
         c) ModResolver/ModSolver: SAT solution across all candidates
            - root candidates (files in mods/) = MANDATORY
            - nested candidates               = OPTIONAL
            - depends/breaks/conflicts/provides -> hard clauses
         d) selected nested candidates are extracted into
            <gameDir>/.fabric/processedMods/ (hash-named)
         e) (dev only) RuntimeModRemapper: intermediary -> named
         f) all selected mod JARs are added to the KnotClassLoader as
            classpath entries
         g) access wideners of all selected mods are read and merged into
            ONE AccessWidener -> AccessWidenerClassTransformer
    2.4  FabricMixinBootstrap#init
         - MixinBootstrap.init()
         - Mixins.addConfiguration(cfg) for EVERY "mixins" declaration
           of EVERY selected mod
         - MixinIntermediaryDevRemapper (dev only)
    2.5  EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint)
         -> THIS is where FabricMultiLoader runs its own code for the first time
3.  Knot loads the Minecraft main class through the KnotClassLoader
    -> from now on mixin transformation + access wideners apply on every class load
4.  Minecraft bootstrap; at some point:
    EntrypointUtils.invoke("main"/"client"/"server", ModInitializer...)
5.  Game start
```

**The four architecturally decisive observations:**

1. **[LB] In phase 2.3a only `fabric.mod.json` is read — no class file.** `ModDiscoverer` opens the JAR as a ZIP
   (via a `ZipFileSystem` in newer loaders), reads that single JSON entry, optionally hashes the file and closes it
   again. There is no bytecode inspection, no ASM parsing, no annotation scan and no class file version check.
   **Consequently: payloads with Java 21 bytecode can live inside a JAR that is being processed on a Java 17 JVM,
   as long as they are not selected.** That is the answer to the entire “class file version” problem family.
2. **[LB] Nested candidates are optional to the solver.** Root mods (files directly in `mods/`) must be loaded;
   if their resolution fails, the loader aborts with an error message. Nested mods, in contrast, are modelled as
   optional variables: the solver maximises the number of loaded mods, but hard clauses (`depends`, `breaks`,
   “at most one candidate per mod ID”) dominate. A nested mod with unsatisfiable `depends`, which no *loaded* mod
   hard-depends on, is simply not selected. This is exactly why JiJ libraries declaring
   `depends: { minecraft: "1.20.x" }` work throughout the ecosystem.
3. **Mixin configs are registered per *selected* mod (2.4), i.e. after resolution.** A mixin config belonging to
   a non-selected payload is never passed to `Mixins.addConfiguration`. Sponge Mixin reads the mixin classes of a
   config only on the first `select`/`prepare` pass; configs that were never registered do not exist for Mixin.
4. **Access wideners are read per selected mod and *merged* (2.3g).** A mod declares exactly *one* AW path
   (`"accessWidener": "…"`). Several mods contribute several AW files; all of them are merged into one shared
   `AccessWidener`. Meaning: **one file per mod — but our payload *is* its own mod.** The AW problem is therefore
   structurally solved without custom transformers.

---

## 5.2 Fabric Loader in detail

### 5.2.1 Mod discovery

* Scanned: `<gameDir>/mods/*.jar`, `<gameDir>/mods/<mcVersion>/*.jar` (versioned subfolders, loader ≥ 0.15),
  classpath entries containing a `fabric.mod.json` (dev), plus recursively every path declared in `jars[]` inside
  already-found JARs.
* A candidate without a parseable `fabric.mod.json` causes a hard failure naming the file (good for us: corrupted
  containers are reported early and with a file reference).
* For deduplication of nested libraries: among several candidates of the same mod ID, the highest version that
  satisfies all constraints wins. **This is the mechanism that reduces `fabricmultiloader-runtime` from many
  universal JARs down to exactly one instance.**
* Extraction of selected nested JARs goes to `<gameDir>/.fabric/processedMods/`, with a hash/name-based cache. On
  the second launch, extraction is skipped when hash and size match. FabricMultiLoader implements **no cache of
  its own** and no extraction (NF-04).

### 5.2.2 `fabric.mod.json` — relevant fields and their semantics

| Field | Relevance to FabricMultiLoader |
|---|---|
| `schemaVersion` | Always `1`. Written by the generator. |
| `id` | `^[a-z][a-z0-9-_]{1,63}$`. Container = primary mod ID; payloads = `<id>-mc<compact>`. |
| `version` | Container = mod version. Payload = `<modVersion>+mc<mcVersion>` (build metadata after `+` is irrelevant to SemVer comparison but visible in logs/ModMenu). |
| `provides` | Alias IDs. Two loaded mods must **not** provide the same ID ⇒ usable as an “at most one payload” guarantee. |
| `environment` | `*`/`client`/`server`. Evaluated by the loader **before** classloading: a `client` mod is not loaded at all on a dedicated server. Usable for client-only payloads. |
| `entrypoints` | Map of phase → list of class names (optionally with `adapter`). Classes may live **in another mod** — all mod classes share one ClassLoader. |
| `jars` | `[{"file": "META-INF/jars/x.jar"}]`. Recursive. The core mechanism of this architecture. |
| `mixins` | List of config file names or objects `{config, environment}`. Per mod. Registered in 2.4. |
| `accessWidener` | Exactly one path per mod. |
| `depends` | Map ID → version predicate **or array of predicates (OR semantics)**. Built-in IDs: `minecraft`, `java` (version = Java major, e.g. `17.0.0`), `fabricloader`. |
| `breaks` / `conflicts` | Hard resp. soft negative relation. `breaks` = SAT clause “not both”. |
| `recommends` / `suggests` | Log hints only, no clauses. |
| `custom` | Arbitrary objects; `custom.modmenu.parent`, `custom.modmenu.badges` are evaluated by ModMenu. |

**Version predicate syntax** (loader `VersionPredicateParser`): `*`, `1.20.1`, `=1.20.1`, `>=1.20.1`, `>1.20`,
`<=1.21.4`, `<1.22`, `~1.20.1` (≥1.20.1 <1.21.0), `^1.20.1` (≥1.20.1 <2.0.0), several conditions combined by
spaces (AND: `">=1.21 <1.21.2"`), alternatives via an array (OR).
FabricMultiLoader uses exclusively `>=`/`<`/`=` and arrays — the subset whose semantics are identical across all
loader versions 0.14+, and which is reproduced bit-exactly by its own implementation in
`fabricmultiloader-format` (chapter 12).

### 5.2.3 The solver

`ModSolver` builds a boolean satisfiability problem (Sat4j) with one variable per candidate:

* **Mandatory clause** per root mod ID: at least one candidate of that ID must be `true`.
* **At-most-one clause** per mod ID *and* per provided alias ID (`provides`).
* **Depends clause** per candidate: `candidate → OR(matching candidates of the target ID)`.
* **Breaks/conflicts clause**: `¬(a ∧ b)`.
* **Optimisation objective**: load as many and as new mods as possible.

Consequences for us:

1. Two payloads sharing the same `provides` alias ID can **never** be loaded simultaneously — structural
   exclusivity without any logic of our own.
2. The optimisation objective (“as many/as new as possible”) is **not a deterministic priority mechanism** on
   which payload selection may be based: with two simultaneously satisfiable payloads it is unspecified which one
   wins. **Therefore the disjointness of payload constraints must be proven at *build time*** (chapter 12.6,
   validator rules `OMNI-1010`/`OMNI-1012`). The framework's `priority` mechanism is consequently not evaluated at
   runtime but converted into *disjoint* ranges at build time (range subtraction, chapter 12.7).
3. Unsatisfiability of a *root* mod produces a detailed, localised loader error message with a Fabric GUI dialog.
   We use that for the case “Minecraft version not supported at all” by having the container itself declare
   `depends.minecraft` = union of all payload ranges.

### 5.2.4 Classpath behaviour and Knot

* `KnotClassLoader` (or `KnotCompatibilityClassLoader` with
  `-Dfabric.loader.useCompatibilityClassLoader=true`) is the ClassLoader for **Minecraft and all mods**. It
  delegates to an internal `URLClassLoader`-like delegate for resources and applies the transformer chain to every
  loaded class (access widener → Mixin → Fabric's own transformers).
* **There is no isolation between mods.** All mod classes live in the same namespace of the same ClassLoader. It
  follows directly that (a) payload classes can see the container's common classes and vice versa; (b) there are
  no `ClassIdentity` problems; (c) identical FQCNs from two mods collide (first wins) — which is why the runtime
  is shipped as a **nested mod with loader deduplication** rather than as shaded fat-JAR content (ADR-008).
* The parent of `KnotClassLoader` is the system ClassLoader, holding JVM classes and the loader itself. Classes
  under `net.fabricmc.loader.` are loaded by the parent (loader internals are visible to mods but not
  transformable).
* Mixin transformation applies only to classes loaded **through the KnotClassLoader**. A custom child ClassLoader
  bypasses the transformer chain entirely — classes landing there receive neither mixins nor access widening, and
  their Minecraft types, if loaded there again, would be incompatible with those in the Knot loader. That is the
  hard argument against approach D.

### 5.2.5 Entrypoints

* Phases: `preLaunch` (`PreLaunchEntrypoint`), `main` (`ModInitializer`), `client` (`ClientModInitializer`),
  `server` (`DedicatedServerModInitializer`), plus mod-defined phases of arbitrary types via
  `FabricLoader#getEntrypointContainers`.
* Invocation order: mods are sorted topologically by `depends`; within equal ordering, by ID. **Since every
  payload declares `depends` on the container, container entrypoints are guaranteed to run before payload
  entrypoints.** The runtime does not rely on that alone (idempotent, explicitly sequenced initialisation,
  chapter 9.6) but uses it as the standard path.
* An exception thrown from an entrypoint is wrapped by `EntrypointUtils` into a
  `net.fabricmc.loader.impl.FormattedException` and rendered by Knot through the Fabric error GUI (client) or
  written formatted to stderr (server). The **message of the thrown exception appears in full** — that is our
  channel for diagnostic reports without touching loader internals (chapter 29.4).
* `preLaunch` runs **before** the first Minecraft class load. Aborting there is clean: no half-initialised
  registry, no already-applied mixins.

### 5.2.6 Object exchange between mods

`FabricLoader.getInstance().getObjectShare()` (loader ≥ 0.12) is a process-wide `Map<String,Object>` with
`put`/`get`/`whenAvailable`. FabricMultiLoader uses it to publish a handle per container
(`"<modid>:omni"` → `ContainerHandle`) so third-party mods and debug tools can read the active payload without
importing runtime classes (chapter 19.9).

---

## 5.3 Mixin

### 5.3.1 Timing

| Point in time | What happens |
|---|---|
| Knot 2.4 | `Mixins.addConfiguration(name)` per config entry of selected mods. Only the **file name is registered**; the JSON file is not necessarily parsed yet. |
| First transformation | `MixinProcessor#select` → all registered configs are parsed, `IMixinConfigPlugin#onLoad` is called, mixin classes are resolved (`ClassInfo`), `targets` are validated. |
| Per target class load | `shouldApplyMixin` (plugin) → `preApply` → injection → `postApply`. Missing targets/injection points raise `InvalidInjectionException`/`MixinApplyError`. |

Important: **a mixin class is validated only once its config is registered.** A config that was never registered
costs exactly nothing — no file read, no ASM, no error. That is the foundation of G4/F-04.

### 5.3.2 Version-specific targets

The real breaking points between MC versions:

* **Renamed classes**: intermediary keeps classes stable, but newly introduced/split classes get new numbers.
  Example: the networking payload types of 1.20.5+ do not exist at all in 1.20.1.
* **Changed method signatures**: `ItemRenderer#renderItem` gained additional parameters between 1.20.1 and
  1.21.x. An `@Inject` with `method = "renderItem(...)V"` is therefore version-bound.
* **Removed methods**: an `@Inject` on a removed method is a hard startup failure.
* **Changed injection points**: `@At(value="INVOKE", target="…")` references exact descriptors.

Consequence: **a mixin set valid across versions is generally impossible.** Any solution must separate mixin sets
per version. There are exactly three mechanisms for that:

| Mechanism | Assessment |
|---|---|
| `IMixinConfigPlugin#shouldApplyMixin` | Prevents *application*, but **not** the loading and validation of the mixin class via `ClassInfo`. `targets` resolution happens earlier. A mixin class referencing a target class that does not exist in this version already fails in `select()`. **Insufficient as the primary mechanism.** Usable for fine-grained control *within* a version (e.g. “only if mod X is loaded”). |
| Separate mixin configs per version, all declared in one mod, with a config plugin whose `getMixins()` returns empty | `getMixins()` can add mixins; to *remove* them one would have to omit them from the JSON. Fabric parses the config fully; `mixins` entries in the JSON are always resolved. A plugin cannot retract them. **Not viable.** |
| Separate mixin configs in separate mods, of which only one is loaded | The non-loaded config is never registered; its classes are not even on the classpath. **Fully safe.** |

The third mechanism is precisely what JiJ payloads provide. Payload separation is therefore not merely a packaging
decision but the **only sound** mixin isolation strategy.

### 5.3.3 Refmaps

* When compiling, Loom produces a refmap via the Mixin annotation processor
  (`<archivesBaseName>-refmap.json`) mapping named (Yarn) → intermediary, and references it from the mixin config
  via `"refmap": "…"`.
* The refmap is **strictly bound to the MC version and the mappings** it was compiled against. Merging refmaps of
  several MC versions is semantically wrong: the same named symbol can point to different intermediary names in
  different versions (for newly introduced members), and the same method can have different descriptors.
* **Solution:** one refmap per payload, with a unique name (`examplemod-mc1201-refmap.json`), never merged. That
  is automatic, because every payload is its own Loom compilation.
* In the dev runtime, `MixinIntermediaryDevRemapper` shifts refmap resolution to named; that is loader-internal
  and works unchanged, because only one payload exists per run.

### 5.3.4 Client/server mixins

Two levels:

* `fabric.mod.json`: `"mixins": [{"config": "x.client.mixins.json", "environment": "client"}]` — the config is
  not registered on dedicated servers. **Preferred mechanism.**
* Inside the config: `"client": [...]`, `"server": [...]`, `"mixins": [...]` — Mixin's own split by
  `MixinEnvironment.Side`.

FabricMultiLoader generates up to three configs per payload (`common`, `client`, `server`) and declares them with
the correct `environment`. Client mixins reference classes that do not exist on a dedicated server
(`net.minecraft.client.**`) — the split is therefore not optional but mandatory.

### 5.3.5 Limits

* Mixins cannot be applied retroactively to already-loaded classes. Since our payload selection happens in the
  solver (before 2.4), that is not a problem.
* Two payloads could in theory contain identical mixin class names. Since two payloads are never loaded, that is
  harmless; the validator nevertheless enforces unique **config file names and refmap names** across all payloads
  (rule `OMNI-1030`) so that slim JARs and manual debugging stay unambiguous.
* `@Mixin(targets = "…")` with string class names bypasses refmap checking and is version-fragile; the
  documentation recommends class literals.

---

## 5.4 Access wideners

### 5.4.1 Processing

* Format: text file, header `accessWidener v2 <namespace>` (namespace `named` in sources, `intermediary` in the
  published artifact — Loom remaps during `remapJar`).
* The loader reads the AW files of **all selected mods** in phase 2.3g into a shared `AccessWidener` and installs
  an `AccessWidenerClassTransformer` in Knot's transformer chain. Widening happens at class load, before Mixin.
* Namespace check: the loader requires the header namespace to match the runtime namespace (`intermediary` in
  production, `named` in a dev run). Wrong namespace ⇒ hard failure.
* Entries whose class is never loaded have no effect. Entries targeting a **non-existent member of an existing
  class** are simply not found during transformation and are silently ignored — that is not an error, but also not
  a reliable foundation: a cross-version AW file would be “mostly harmless”, yet it cannot express that a member
  is named differently in one version.

### 5.4.2 Why a single shared AW file is not enough

1. Intermediary names for **newly introduced** members differ between versions; a line
   `accessible field net/minecraft/class_310 field_1724 …` may denote a different field in 1.20.1 than in 1.21.4
   if fields were renumbered.
2. Classes that do not exist in a version cause no errors — but Loom can remap an AW file against only **one**
   mappings version. A hand-written cross-version AW file would have to be written in intermediary already, giving
   up Yarn readability.
3. The loader accepts exactly **one** `accessWidener` path per mod. Multiple files per mod cannot be declared.

### 5.4.3 Consequence

**One payload = one mod = its own access widener, remapped by Loom against the correct mappings version.** That is
the complete solution; it needs no runtime transformation, no reflection and no custom transformer. The container
itself declares **no** access widener (validator rule `OMNI-1024`), because it touches no Minecraft classes.

For the special case “the same AW need in every version”, the Gradle plugin generates the payload AW file from a
shared source: `common/src/main/accesswidener/shared.accesswidener` is laid underneath every version module and
merged with `versions/mc-X/src/main/resources/<modid>.accesswidener` (chapter 17.4) — the merge happens in the
named namespace *before* Loom's remap and is therefore mapping-correct.

---

## 5.5 Java and the JVM

### 5.5.1 Java requirements per Minecraft version

| Minecraft | required Java major | class file major of MC classes |
|---|---|---|
| 1.16.5 | 8 | 52 |
| 1.17 – 1.17.1 | 16 | 60 |
| 1.18 – 1.20.4 | 17 | 61 |
| 1.20.5 – 1.21.x | 21 | 65 |
| **26.1 and newer** | **25** | **69** |
| future | ≥ 25 | ≥ 69 |

The jump from 1.21.x to 26.1 raises the required Java major from 21 to **25** (class file major 69). A mod
supporting 1.20.1, 1.21.1 and 26.1 must therefore ship payloads with class file majors 61, 65 and 69 in **one**
file and still be startable on a Java 17 JVM (1.20.1). That is exactly what `depends.java` achieves, in
combination with the fact that non-selected payloads are never defined.

The JVM checks the class file version **when defining a class** (`ClassLoader#defineClass` →
`UnsupportedClassVersionError`), not when reading the JAR. As long as a class file is not defined, its version is
irrelevant. The loader defines only classes of selected mods (5.1, observation 1) ⇒ **a universal JAR may contain
payloads with class file majors 61, 65 and 69 simultaneously** (answer to questions 21/22).

### 5.5.2 What must mandatorily be compiled at baseline level

Everything that is loaded in the **oldest** supported environment:

| Artifact | Target bytecode | Rationale |
|---|---|---|
| `fabricmultiloader-format` | 52 (Java 8) | also used by the Gradle plugin; must run on every supported JVM |
| `fabricmultiloader-api` | 52 | referenced by common code and all payloads |
| `fabricmultiloader-runtime` | 52 | the bootstrap runs on the oldest JVM |
| `fabricmultiloader-processor` | 52 | annotation processor, runs in the build |
| the mod's container common code | `baselineJava` from the matrix (example: 17) | loaded on the oldest supported MC version |
| payload `mc-1.20.1` | 61 | MC 1.20.1 → Java 17 |
| payload `mc-1.21.4` | 65 | MC 1.21.4 → Java 21 |

The validator scans every class file of the container and of every payload and compares the major value against
the declared expectation (rules `OMNI-1040`/`OMNI-1041`). An accidental `--release 21` in the common module is
thereby caught at build time, not on the player's machine.

### 5.5.3 Multi-release JARs — why they are unsuitable here

An MR JAR (`Multi-Release: true`, `META-INF/versions/<n>/…`) selects by **Java version**, not by Minecraft
version. Therefore:

* it could separate Java 17 from Java 21 bytecode — but not 1.21.1 from 1.21.4 (both Java 21). The actual problem
  is not addressed.
* selection is performed by the ClassLoader. `KnotClassLoader` does not guarantee MR semantics (it reads resources
  through its own delegate); behaviour would depend on the loader version.
* mixin configs, refmaps and `fabric.mod.json` could not be selected along with the classes — they live at the
  root and are not MR-capable in the loader's sense.

**Rejected.** MR JARs solve a different problem.

### 5.5.4 Lazy classloading as an isolation mechanism — and its limit

A frequently proposed approach is “put everything in one JAR, classes are loaded lazily anyway”. That only carries
part of the way:

* Classes are indeed defined only on first active use. An `if (mc >= 1.21) new Foo1214()` loads `Foo1214` only in
  the `true` branch — **however** the verifying method referencing `Foo1214` must be resolvable; resolution in
  HotSpot is lazy per bytecode instruction, hence practically tolerant.
* **But**: mixin configs and access wideners are *not* lazily loaded classes but declarative metadata that the
  loader processes eagerly. That is exactly where the naive approach breaks.
* **And**: Sponge Mixin builds a `ClassInfo` for every mixin class of a registered config — that is an eager ASM
  read of the mixin class *and* its targets.

Lazy classloading is therefore a **necessary but not sufficient** property. It is the reason common code and the
runtime can live in one JAR alongside everything else; it is not the reason payloads are isolated — that is
achieved by JiJ selection.

### 5.5.5 Reflection, MethodHandles, ServiceLoader

* **Reflection** is used at exactly one place on the critical path:
  `Class.forName(platformFactory).getDeclaredConstructor().newInstance()`. The class name comes from the
  hashed manifest, not from a scan. Cost: one class.
* **MethodHandles** are not used. They would bring no benefit, since the calls are one-off, and they would raise
  the baseline requirement (`MethodHandles.privateLookupIn` only from Java 9).
* **ServiceLoader** is deliberately **not** used for payload discovery: `ServiceLoader` scans
  `META-INF/services/**` across the entire classpath, would therefore be non-deterministic with several universal
  mods and yields no usable error messages. Instead: an explicit FQCN in the manifest.
  (`ServiceLoader` *works* in the Knot loader — the decision is about determinism, not technical necessity.)
* **`ClassCastException`/class identity**: ruled out, because no second ClassLoader exists. Every class is defined
  by exactly one loader (Knot).

---

## 5.6 Mappings

### 5.6.1 Namespaces

| Namespace | Properties |
|---|---|
| `official` | Mojang's obfuscated names; change completely per version. |
| `intermediary` | Managed by Fabric, **stable across versions as long as the element stays “the same”**. New elements receive new numbers; removed numbers are not reused. Runtime namespace in production. |
| `named` (Yarn) | Human-readable, its own build per version, renameable between versions. Dev runtime namespace. |
| Mojang official mappings | Usable as an alternative to Yarn (Loom `layered { officialMojangMappings() }`); changes nothing about the architecture, since it is freely selectable per payload. |

### 5.6.2 Why intermediary stability is not enough

Intermediary guarantees name stability, **not signature stability**. When `method_1234(PacketByteBuf)` becomes
`method_1234(RegistryByteBuf)`, the name stays the same but the descriptor changes — and bytecode references name
**and** descriptor. A single compilation therefore cannot serve both versions. This is precisely the hard limit at
which “one artifact for all versions” fails (non-goal N2) and why compilation and remapping must happen per
version.

### 5.6.3 Consequences for the architecture

1. **One complete Loom build per payload**, with its own MC version, its own mappings, its own refmap, its own AW
   remap. Payloads may use different mapping *providers* (Yarn here, Mojmap there), because they share no
   bytecode.
2. **The container is namespace-neutral**, because it contains no Minecraft reference. Loom never remaps it
   (validator rule `OMNI-1042`: no references to `net/minecraft/`, `com/mojang/blaze3d/`,
   `net/fabricmc/fabric/api/` in container classes).
3. **The dev runtime** remaps selected mods intermediary→named. That affects only the payload; the container stays
   untouched. Testing the finished universal JAR in a Loom dev run therefore works as well.

---

## 5.7 Summary: where the real difficulties lie

| Difficulty | Severity | Resolution in this architecture |
|---|---|---|
| Mixin configs are processed eagerly | **hard** | The config lives in the payload mod; mod not loaded ⇒ no config. |
| Access wideners: exactly one file per mod, mapping-dependent | **hard** | Payload = its own mod ⇒ its own AW. A shared AW source is merged pre-remap. |
| Different descriptors for the same MC method | **hard, unsolvable in one artifact** | N compilations, one container. Common code must not touch Minecraft. |
| Different Java major versions | medium | `depends.java` + solver + validator class file scan. |
| Deterministic payload selection | medium | Build-time disjointness proof + `provides` exclusivity + runtime assertion. |
| One mod identity to the outside | medium | The container carries the primary mod ID; payloads are ModMenu children with a library badge. |
| Version-dependent Fabric API / mod dependencies | easy | Own `depends` per payload; optionally nested libraries per payload. |
| Resource conflicts between common and payload | easy | Resources are merged into every payload at build time; the container carries **no** `assets/` or `data/` entries. |
| Loader behaviour for unsatisfiable nested mods | **load-bearing assumption** | Conformance test across the loader matrix in CI; fallback path `buildSlimJars`. |
| Size of the JAR | accepted | N payloads, no deduplication. |

---

Continue with [chapters 6–9 — architecture variants and the final decision](part-02-architecture.md).
