# Answers to the 25 hard technical questions

Every answer is a commitment, not an option. References point at the normative place in the document.

---

### 1. How can a single JAR be accepted by Fabric Loader on several Minecraft versions?

By making the container itself Minecraft-independent and declaring the **union** of all supported MC ranges in its
`fabric.mod.json`:

```json
"depends": { "minecraft": [">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"] }
```

Fabric version predicates inside an array are OR-combined. The container contains no Minecraft-touching code, no
mixins and no access widener — it is therefore loadable on every one of those versions. The version-dependent part
sits in nested payload mods (`META-INF/jars/*.jar`), of which the loader's own SAT solver selects exactly one.
(Chapters 7.1, 11.8, 12.8)

---

### 2. Which class is the very first FabricMultiLoader class to be loaded?

`dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch`, loaded by `KnotClassLoader` when Fabric invokes the
`preLaunch` entrypoints. Its static initialiser is empty; `onPreLaunch()` first calls `RuntimeBootstrap.get()`.

The only exception: if a payload uses the optional `ConditionalMixinPlugin`, then
`dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin` is loaded during the Mixin `select()` phase, i.e.
before `preLaunch`. That class is therefore deliberately restricted to the JDK, `format` and `FabricLoader` APIs and
does not trigger `RuntimeBootstrap` (validated by `OMNI-1035`). (Chapter 9.2)

---

### 3. Against which Minecraft/Fabric version is this bootstrap class compiled?

Against **no** Minecraft version. Its compile dependencies are exclusively:

* the JDK 8 API (`--release 8`, class file major 52),
* `dev.fabricmultiloader.format` and `dev.fabricmultiloader.api`,
* `net.fabricmc:fabric-loader:0.14.0` as `compileOnly` — the **lowest** supported loader version, so that accidental
  use of newer loader API is caught at compile time.

The loader API used is limited to 12 stable methods (`FabricLoader.getInstance`, `getModContainer`, `getAllMods`,
`isModLoaded`, `getEnvironmentType`, `isDevelopmentEnvironment`, `getGameDir`, `getConfigDir`, `getObjectShare`,
`ModContainer#getMetadata/findPath`, `ModMetadata` getters). Forbidden are `net.fabricmc.loader.impl.**`,
`net.minecraft.**`, `com.mojang.**`, `net.fabricmc.fabric.api.**`, `org.spongepowered.**`. (Chapters 9.3, 14.2)

---

### 4. How does the system prevent incompatible version-specific classes from being loaded early?

Not by filtering, but by **non-existence**: the classes of inactive payloads sit as bytes in a ZIP entry inside the
container. The loader extracts and adds to the classpath **only** the payloads it selected. A JAR inside a JAR is
not a classpath entry; the JVM never sees those classes. In addition, the packages are disjoint per payload
(`OMNI-1044`), so not even an accidental FQCN collision is possible. (Chapters 5.1, 13.2, 14.2)

---

### 5. How are different Java class file versions handled?

Every payload is compiled to the Java level of its MC version (61 for 1.20.1, 65 for 1.21.x, **69 for 26.1**) and
declares `depends: {"java": ">=17"}` resp. `">=21"` resp. `">=25"`. The loader evaluates the synthetic mod candidate
`java` as a hard solver clause. The container itself is compiled at the **minimum** of the matrix (61 in the
example) and declares `depends.java >=17`.

The validator reads the class file header (bytes 4–7) of **every** class and checks: container ≤ baseline
(`OMNI-1040`), payload == the declared major (`OMNI-1041`), major ↔ `requires.java` consistent (`OMNI-1046`),
baseline == the minimum (`OMNI-1047`). Multi-release structures are forbidden (`OMNI-1049`). (Chapter 14)

---

### 6. How do mixins work, given that Fabric loads mixins very early?

Precisely **because** Fabric loads early, mixins belong in the payload mod: in phase 2.4 Fabric registers the
`mixins` configs of **all selected mods**. A payload that was not selected has no registered config; Sponge Mixin
therefore never reads its mixin classes, never resolves a `ClassInfo` and never validates a target. For the active
payload everything runs exactly as for a normal mod — including the correct `compatibilityLevel` per Java version.

A runtime “mixin dispatcher” deliberately does not exist: after phase 2.4 no config can meaningfully be added, and
`IMixinConfigPlugin#shouldApplyMixin` prevents only the *application*, not the *loading and validation*.
(Chapters 5.3, 16.1, 16.8)

---

### 7. How do different refmaps work?

One refmap per payload, produced by the Mixin annotation processor during that payload's Loom compilation, with the
unique name `<modId>-<payloadId>-refmap.json` (set by the plugin via `loom.mixin.defaultRefmapName`), referenced
only by that payload's mixin configs. Refmaps are **never** merged — the same named symbol can map to different
intermediary names and descriptors in different versions. The validator checks existence, validity, name uniqueness
across all payloads, and that all refmap keys are classes of that payload (`OMNI-1030–1033`). In the dev runtime,
Fabric's `MixinIntermediaryDevRemapper` performs the inverse. (Chapters 5.3.3, 15.5)

---

### 8. How are access wideners handled?

One access widener **per payload**, because a payload is its own Fabric mod and Fabric's “one AW file per mod” rule
therefore applies per payload. Loom remaps the file into the `intermediary` namespace during `remapJar`. The loader
merges the AW files of all loaded mods — and since only one payload is loaded, exactly one mod-owned AW is active.

Shared entries may live in `common/src/main/accesswidener/shared.accesswidener` (namespace `named`) and are merged
with the payload-specific file at build time **before** the remap (deduplicated, sorted, with a source comment). The
container declares no AW (`OMNI-1024`). The recommended alternative for individual cases: Mixin `@Accessor`/
`@Invoker`. (Chapters 5.4, 17)

---

### 9. How are different Yarn/intermediary mappings handled?

Every payload is a standalone Loom build with its own MC version and its own mappings; payloads share no bytecode.
The mapping provider is freely selectable per payload (`yarn:<build>`, `mojang`, `layered:…`, `parchment:…`) — even
mixed. Every payload is published in the `intermediary` namespace. The container is namespace-neutral, because it
contains no Minecraft reference (`OMNI-1042`).

Important: intermediary guarantees **name** stability, not **signature** stability. That is exactly why “one
compilation for all versions” is impossible in principle and why payload separation is necessary. (Chapter 15)

---

### 10. Are payloads embedded into the universal JAR already remapped?

Yes. The order is: `compileJava` (named) → `remapJar` (named → intermediary, including the AW and the refmap
targets) → `omniPayload` (inserting metadata and merged resources, no bytecode change) → `assembleUniversalJar`
(embedded as a **STORED** ZIP entry). At runtime no further remap happens in production. Only in a Loom dev run does
Fabric's `RuntimeModRemapper` remap intermediary → named — standard behaviour for any external mod.
(Chapters 15.2, 23.1)

---

### 11. Do payload classes sit on the normal Knot classpath?

Yes — those of the **active** payload. The loader extracts the selected nested JAR into
`<gameDir>/.fabric/processedMods/` and adds it to `KnotClassLoader` as a classpath entry. Access wideners and mixin
transformation therefore apply normally, and payload classes see Minecraft, Fabric API, the runtime and the
container common code without mediation. Classes of inactive payloads sit on **no** classpath. (Chapters 13.2, 13.3)

---

### 12. Is a custom ClassLoader required?

**No.** FabricMultiLoader creates no ClassLoader anywhere — that is a hard invariant (I1, ADR-002), enforced by
validator rule 32 (`OMNI-1036`, forbidding references to ClassLoader constructors, `URLClassLoader` and
`net.fabricmc.loader.impl.**`). A custom ClassLoader would bypass Knot's transformer chain, rendering mixins and
access wideners ineffective, and would produce class identity breaks at every Minecraft type boundary.

The only exception in the entire project: the loader conformance test harness loads different Fabric Loader versions
into isolated `URLClassLoader`s — pure test code, not shipped. (Chapters 6.4, 13.1)

---

### 13. If so: how do those classes correctly access Minecraft and Fabric classes of the Knot ClassLoader?

The question does not arise, because there is no custom ClassLoader. Payload classes are defined by
`KnotClassLoader` itself and therefore access Minecraft and Fabric classes directly and without delegation rules —
identically to any normal Fabric mod. (Chapter 13.2)

---

### 14. How are class identity problems prevented?

Structurally: there is exactly one ClassLoader defining Minecraft, Fabric API, the runtime, the container common
code and the active payload. Every type therefore exists exactly once; a `ClassCastException` between “two versions
of the same class” is impossible.

The only collision case that really remains is “two mods ship classes with the same FQCN” (classpath first-wins).
It is eliminated by shipping the library as its own nested mod `fabricmultiloader` and letting the loader
deduplicate by mod ID to the highest compatible version — deterministic rather than classpath-dependent. A major
transition receives a new mod ID and package (`fabricmultiloader2` / `dev.fabricmultiloader.v2`) so 1.x and 2.x
coexist. Additionally the runtime warns (`OMNI-2050`) when its own classes come from an unexpected JAR.
(Chapters 13.4, 39.7, 42.3, ADR-008)

---

### 15. How does inter-mod communication work?

In three ways, all via the **container** mod ID (payload IDs are an implementation detail and must not be referenced
by third parties):

1. **`FabricLoader.isModLoaded("examplemod")`** — works unchanged, because the container carries the primary mod ID.
2. **The public mod API in the container**: `com.example.common.api.*` lives in the container and is therefore
   **the same compilation** across all MC versions. Third-party mods compile once against
   `com.example:examplemod-api:2.0.0` (`compileOnly`) and work on every MC version. That is an advantage a classic
   one-JAR-per-version release does not offer.
3. **`FabricLoader.getObjectShare()`**: the container publishes `"examplemod:api"` (the implementation) and
   `"examplemod:omni"` (a `ContainerHandle` for diagnostics) — usable without a compile dependency.

Fabric API events, registries, networking and commands remain available unchanged; to other mods a universal mod is
indistinguishable from a normal one. (Chapters 19.9, 24.7, 30.5)

---

### 16. What does `FabricLoader.isModLoaded()` look like for other mods?

| Query | Result |
|---|---|
| `isModLoaded("examplemod")` | `true` as soon as the container is loaded — i.e. on every MC version inside the declared union |
| `isModLoaded("examplemod-mc1214")` | `true` only on 1.21.4; **not** to be used by third parties |
| `isModLoaded("examplemod-impl")` (the alias) | `true` when any payload is active; internal |
| `isModLoaded("fabricmultiloader")` | `true` as soon as at least one universal mod is installed |

An honest caveat: when the MC version lies inside the union but a side condition fails (e.g. Fabric API too old),
the container loads and `isModLoaded("examplemod")` is `true` even though no functionality is active. In the default
mode (`strict = true`) this state is unobservable, because `preLaunch` aborts the game with `OMNI-2003`. It persists
only in the explicitly chosen `strict = false` mode; for that, `FabricMultiLoader.isActive("examplemod")` exists as
the precise query, and the documentation recommends it to integrators. (Chapters 18.1, 29.5, 30.5)

---

### 17. How are different Fabric API versions handled?

Per payload. Every payload declares its own minimum version in its `fabric.mod.json`
(`"fabric-api": ">=0.114.0"`) and in the `requires.mods` of its descriptor. The container declares Fabric API only
as `recommends: "*"` — not as `depends`, because the concrete requirement is payload-dependent.

If Fabric API is too old, the payload is discarded by the solver, the container loads, and our `preLaunch`
diagnostics state exactly “fabric-api >=0.114.0 — REJECTED: 0.110.0 installed” together with a download link. For
mods that need only individual Fabric API modules there is `fabricApiMode = MODULES`: module IDs
(`fabric-networking-api-v1`, …) are then declared instead of the aggregate ID. On the compile side, each version
module uses the Fabric API version recorded in the matrix. (Chapters 12.4, 20.3, 24.2, 29.2)

---

### 18. How do client-/server-specific classes work?

On three combined levels:

1. **Payload level**: `"environment": "client"` resp. `"server"` in the payload `fabric.mod.json`. A client payload
   is not loaded at all on a dedicated server — including its mixins, AW and resources.
2. **Mixin config level**: `{"config": "…client.mixins.json", "environment": "client"}` prevents registration on the
   server, so Mixin never reads the classes.
3. **Code level**: separate entrypoints (`UniversalClientMod`/`UniversalServerMod`) and separate packages
   (`…<payloadId>.client.**`, declared in `clientOnlyPackages`). The validator checks statically that no class
   reachable from the `common` entrypoint references a client package (`OMNI-1150`) and that
   `net/minecraft/client/**` references occur only in client packages (`OMNI-1045`).

The physical side is queryable at runtime via `ctx.side()`. (Chapter 26)

---

### 19. What happens on an unsupported Minecraft version?

Two clearly separated cases:

1. **MC outside the union**: Fabric's own resolver rejects the container and shows its localised error GUI (client)
   resp. a formatted console message (server) listing the permitted ranges. No FabricMultiLoader code runs; by
   construction there can be no mixin or JVM error.
2. **MC inside the union but no payload selectable** (Fabric API too old, Java too old, a foreign mod missing, a
   client payload on a server): the container loads and `ContainerPreLaunch` produces the report `OMNI-2003` with
   the detected environment, every payload, every constraint and its evaluation, the list of supported MC versions
   and concrete instructions with links (the example in chapter 29.2). In the default mode the start aborts; with
   `-Dfabricmultiloader.strict=false` the game continues and the mod stays deactivated (`OMNI-2101`).

In neither case does a bare `NoClassDefFoundError` or a mixin stack trace appear. An integration test
(`itest unsupported`) checks this automatically and fails the build if a `NoClassDefFoundError` appears in the log.
(Chapters 9.9, 29.2, 32.5)

---

### 20. What is the minimum technical size of a universal JAR?

| Component | Size |
|---|---|
| `fabric.mod.json` (generated) | ~1.2 KiB |
| `META-INF/omni-container.json` (1 payload) | ~1.8 KiB |
| `META-INF/MANIFEST.MF` | ~0.3 KiB |
| `fabricmultiloader-runtime-1.0.0.jar` (contains `format` + `api` + `runtime`) | ~62 KiB |
| **Overhead without mod content** | **≈ 66 KiB** (NF-03: < 80 KiB satisfied) |
| plus the payload minimum (one adapter class, one factory, generated metadata) | ~6 KiB |
| **The smallest possible functioning universal JAR** | **≈ 72 KiB** |

Thanks to loader deduplication, the runtime portion is loaded only **once per game**, even with 40 universal mods
installed. (Chapter 40.4)

---

### 21. Can the universal JAR contain Minecraft versions with different required Java major versions?

Yes, without restriction. The reference matrix deliberately contains three: Java 17 (1.20.1), Java 21 (1.21.x) and
**Java 25 (26.1)** — class file majors 61, 65 and 69 in one file. The loader makes the selection via `depends.java`;
the container is compiled at the minimum and declares `depends.java >=17` so it loads in the oldest environment. The
validator enforces consistency between the bytecode level, the `classfileMajor` declaration and `requires.java`
(`OMNI-1041/1046/1047`). A new Java jump costs the mod author exactly one matrix entry. (Chapters 14, 37.4)

---

### 22. If an old JVM opens the JAR: how is an `UnsupportedClassVersionError` from payloads for newer Java versions prevented?

The error arises exclusively in `ClassLoader#defineClass`, i.e. when **defining** a class — not when opening a JAR
and not when reading a ZIP entry. The chain is airtight:

1. Fabric's `ModDiscoverer` reads **only** `fabric.mod.json` from nested JARs — a text file. No bytecode inspection,
   no ASM, no class file version check.
2. The solver discards the Java 25 payload on a Java 17 JVM, because `depends: {"java": ">=25"}` is unsatisfiable.
3. Discarded payloads are not extracted and not added to the classpath.
4. Consequently none of their classes is ever defined.

Additional build-time safeguard: the validator checks that no payload has a class file major higher than its
`requires.java` lower bound permits (`OMNI-1046`) — the only way this error could arise is therefore ruled out before
the file exists. And the container, whose classes really are defined on the oldest JVM, is fully scanned at the class
file level (`OMNI-1040`). (Chapters 5.5.1, 14.2, 14.4)

---

### 23. Which parts must mandatorily be compiled at the lowest common Java level?

| Artifact | Target | Reason |
|---|---|---|
| `fabricmultiloader-format` | class file 52 (Java 8) | used by both the runtime **and** the Gradle plugin and must load on every supported JVM |
| `fabricmultiloader-api` | 52 | referenced by common code and all payloads |
| `fabricmultiloader-runtime` | 52 | the bootstrap runs on the oldest JVM |
| `fabricmultiloader-processor` | 52 | annotation processor |
| the mod's container common code | `baselineJava` = the minimum of the matrix (61/Java 17 in the example) | loaded on the oldest supported MC version |
| payloads | their own level each (61/65/69) | loaded only on matching JVMs |

Enforced with `--release` (not `targetCompatibility`), so the **API** used is also checked against the target JDK,
and additionally by the validator's class file scan. Practical consequence: records, `var`, `sealed`, switch
expressions and `List.of` are forbidden in `format`/`api`/`runtime`; compensated by the builder pattern.
(Chapters 5.5.2, 14.2, 14.7)

---

### 24. How is a new Minecraft version added?

One command, then the real adaptation work:

```bash
./gradlew addMinecraftVersion --id=mc261 --mc=26.1 --range=">=26.1 <26.2" \
    --yarn=26.1+build.1 --loader=0.17.0 --fabric-api=0.130.0+26.1 --java=25 --copy-from=mc1214
```

The task produces: the matrix entry in `gradle/fabricmultiloader.toml`, `versions/mc-26.1/build.gradle.kts`, the
source directories, the copied and **renamed** adapter code (`com.example.mc1214` → `com.example.mc261`,
`Platform1214` → `Platform261`), mixin configs with adjusted `package`/`refmap`/`compatibilityLevel` (`JAVA_25`), an
AW stub and the CI matrix entry. It checks the disjointness of the new range against all existing ones and otherwise
aborts with `OMNI-1010`.

Then: `:versions:mc-26.1:build` (every compiler error is a real API change), `runClient261`, `runDatagen261`,
`buildUniversalJar`, `validateUniversalJar`, `integrationTestMc261`, and updating `capabilities`.
**Existing payloads are not touched**, and the container baseline stays at the minimum (17), so the mod keeps
working unchanged on 1.20.1/Java 17. (Chapter 37)

---

### 25. Which areas cannot be fully abstracted?

An honest list. For these areas there is **no** version-neutral common API; they live in the payload and are wired
up via `Services`, `Capabilities` and common hooks:

| Area | Reason |
|---|---|
| **Rendering** (`DrawContext`, `MatrixStack`, `RenderLayer`, the shader pipeline, `HudRenderCallback`) | Signatures and concepts change almost every version; rebuilt several times from 1.21.x onwards |
| **Mixins** | By definition bound to concrete targets and descriptors |
| **World generation** (`ConfiguredFeature`, `PlacedFeature`, biome modification, `Codec` registrations) | Registry and codec overhauls per version |
| **NBT/data components** | 1.20.5 replaced NBT item data with components — no common model is possible; encapsulated via `Capabilities.COMPONENTS` |
| **Codecs/serialisation** (`Codec`, `PacketCodec`, `StreamCodec`) | Type system and registry binding are version-dependent |
| **DataFixerUpper / schema migration** | Tied to Mojang's version schemas |
| **Packet formats and registry sync** | The protocol changes per version; only the *payload data path* (`ByteSink`/`ByteSource`) is abstracted, not the protocol |
| **GUI layout and screen classes** | Class hierarchy and layout model change |
| **Entity/block attributes and behaviour in depth** | Only simple cases are declaratively expressible via `ItemSpec`/`BlockSpec` |
| **Datagen providers** | The `FabricDataGenerator` API is version-dependent; only the *input data* (`RecipeSpec`, `LootSpec`) is abstracted |
| **Core transformations beyond Mixin** | Fabric offers no public transformer API — not even without FabricMultiLoader |

Abstracted and stable, by contrast: lifecycle, logging, config, paths, registration of simple content (items,
blocks, sounds, item groups), commands, networking payload data, the stable Fabric API events, resources and
diagnostics. Measured on the example mod: 142 common classes versus 18–22 classes per payload — i.e. 85–89 % of the
code is version-neutral. (Chapters 18.1, 19.7, 28.4, 41.2)

---

Continue with [chapters 45–46 — reality check and the final architecture summary](part-14-reality-check.md).
