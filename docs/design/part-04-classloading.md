# 13. Classloading Strategy

## 13.1 Principle

> **FabricMultiLoader creates no ClassLoader, modifies no ClassLoader and accesses no ClassLoader internals. There
> is exactly one relevant ClassLoader: `KnotClassLoader`.**

That is a hard architectural boundary (invariant I1, ADR-002). Any pull request instantiating a `ClassLoader`,
using `URLClassLoader`, reflecting on `addURL` or modifying `Thread#setContextClassLoader` is rejected. The build
enforces it: the validator scans the runtime classes for references to `java/lang/ClassLoader` constructors,
`java/net/URLClassLoader` and `net/fabricmc/loader/impl/**` (rule `OMNI-1036`).

## 13.2 Who defines which class

| Classes | Defining loader | Transformed |
|---|---|---|
| `java.**`, `jdk.**`, `sun.**` | bootstrap/platform loader | no |
| `net.fabricmc.loader.**`, `org.spongepowered.asm.**`, Sat4j, tiny-remapper | system ClassLoader (app classpath) | no |
| `net.minecraft.**`, `com.mojang.**` | `KnotClassLoader` | yes (AW → Mixin) |
| `net.fabricmc.fabric.api.**` (Fabric API as a mod) | `KnotClassLoader` | yes |
| `dev.fabricmultiloader.**` (runtime mod) | `KnotClassLoader` | yes (technically; in practice no mixin targets it) |
| `com.example.common.**` (container) | `KnotClassLoader` | yes (technically) |
| `com.example.mc1214.**` (active payload) | `KnotClassLoader` | yes — this is where the payload mixins and the access widener apply |
| classes of non-selected payloads | **nobody** | — |

Consequence: there is exactly one `com.example.common.ExampleModApi`, exactly one `net.minecraft.item.Item`,
exactly one `dev.fabricmultiloader.api.Platform`. **Class identity problems are structurally impossible**, because
no type is defined twice.

## 13.3 Why payload classes see container classes (and vice versa)

Fabric Loader adds **all** selected mod JARs to the same `KnotClassLoader` as classpath entries (phase 2.3f). There
is no per-mod isolation and no module system between mods. It follows directly that:

* `com.example.mc1214.Platform1214` (payload) can reference, implement and instantiate
  `com.example.common.ExampleMod` (container) — it is the same namespace.
* `com.example.common.ExampleMod` (container) can reference `dev.fabricmultiloader.api.ModContext` (runtime mod).
* Payload classes can reference Minecraft and Fabric API classes — normally, as in any mod.
* Container and runtime classes must **not** reference Minecraft — not because it would technically fail, but
  because they must load on all supported versions, where Minecraft signatures differ (I3, validator
  `OMNI-1042`).

**Load order is irrelevant to visibility**, only to execution order. Classes are defined lazily on first active
use; whether the payload JAR precedes or follows the container JAR on the classpath changes nothing, because the
FQCN spaces are disjoint (validator `OMNI-1044`: payload packages and common packages must not overlap).

## 13.4 The one remaining collision case — and its resolution

Two different universal mods (`examplemod`, `othermod`) both contain FabricMultiLoader classes. With a classic
“fat JAR with an embedded library”, `KnotClassLoader` would let the first
`dev.fabricmultiloader.runtime.Bootstrap` it finds win (first-wins by classpath order) — the version would be
non-deterministic, and an older library might have to interpret a newer manifest.

**Resolution:** the library ships as its **own nested Fabric mod** (`fabricmultiloader`, chapter 8.1). The loader
deduplicates mods by ID and picks the highest version satisfying all constraints (5.2.1). Therefore:

* exactly **one** runtime exists process-wide, namely the newest across all installed universal mods;
* the selection is **deterministic** (highest SemVer) rather than classpath-dependent;
* every container enforces via `depends: {"fabricmultiloader": ">=1.0.0 <2.0.0"}` that the selected runtime is
  compatible. A too-new major version produces a clear loader error message instead of a `NoSuchMethodError`.

For the hypothetical major transition, the rule from chapter 42.3 applies: major 2 gets the mod ID
`fabricmultiloader2` and the package `dev.fabricmultiloader.v2`, so 1.x and 2.x can coexist and no mod is forced
into an update.

**Deliberately rejected alternative:** relocation (jarjar/shadow) per mod. It would also solve the collision, but
(a) it would enlarge every universal JAR with its own copy, (b) it would make the mod's *public* API unusable
(`com.example.common.api.Handle` would reference
`dev.example.shadow.fabricmultiloader.api.ModContext` — third-party mods could not compile against it), and
(c) it would clutter debugging and stack traces.

## 13.5 Resource lookup

* Mod resources are read via `ModContainer#findPath(String)`, never via `Class#getResourceAsStream` or
  `ClassLoader#getResource`. Rationale: with several universal mods present,
  `getResource("META-INF/omni-container.json")` would return an arbitrary first manifest. `findPath` is mod-bound
  and therefore unambiguous.
* `findPath` returns a `Path` inside a loader-managed `ZipFileSystem` (production) or a directory (dev). The path
  is used read-only and exclusively with `Files.readAllBytes`/`Files.newInputStream`; a `FileSystem` is never
  opened or closed by us (that would destroy the loader's own).
* Minecraft resources (`assets/`, `data/`) are **not** read by FabricMultiLoader; they are registered by the Fabric
  Resource Loader as the payload's resource pack (chapter 25.2).

## 13.6 Instantiating the payload class

```java
package dev.fabricmultiloader.runtime.payload;

final class PlatformLoader {

    static Platform create(PayloadDescriptor payload, ModContext ctx) {
        String fqcn = payload.platformFactory();
        Class<?> raw;
        try {
            // Deliberately THIS class's ClassLoader: it is the KnotClassLoader,
            // which also defines all payload classes. No TCCL, no custom lookup.
            raw = Class.forName(fqcn, false, PlatformLoader.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new OmniException(ErrorCode.OMNI_2020, Messages.platformFactoryMissing(payload, fqcn), e);
        }
        if (!PlatformFactory.class.isAssignableFrom(raw)) {
            throw new OmniException(ErrorCode.OMNI_2022, Messages.platformFactoryWrongType(payload, raw));
        }
        try {
            PlatformFactory factory = (PlatformFactory) raw.getDeclaredConstructor().newInstance();
            Platform platform = factory.create(ctx);
            if (platform == null) {
                throw new OmniException(ErrorCode.OMNI_2023, Messages.platformFactoryReturnedNull(payload));
            }
            return platform;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new OmniException(ErrorCode.OMNI_2021, Messages.platformFactoryFailed(payload, fqcn), e);
        }
    }
}
```

`Class.forName(fqcn, false, …)` with `initialize = false` is deliberate: the factory's static initialiser runs only
at instantiation time, so a type error (`OMNI-2022`) is reported **before** foreign code executes.

## 13.7 What happens to client classes on a dedicated server

* A payload with `environment: "client"` is **not loaded at all** on a dedicated server (the loader evaluates
  `environment` before classloading). If *all* payloads are client-only, the server ends up loading the container
  but no payload — the runtime detects this and reports `OMNI-2003` with the specific text “this mod is a client
  mod” instead of a generic message.
* Within a universal payload, client classes live in their own package (`com.example.mc1214.client.**`) and are
  referenced exclusively from the `client` entrypoint path. Class-level separation is mandatory: a class using
  `MinecraftClient` in a field type must never be loaded on the server. The validator checks this statically
  (rule `OMNI-1045`): every class referencing `net/minecraft/client/**` must reside under a package declared as
  `clientOnly`, and no non-client class may reference them.

---

# 14. Java Compatibility

## 14.1 The problem in one sentence

A universal JAR must contain bytecode for Java 17 (MC 1.18–1.20.4), Java 21 (MC 1.20.5–1.21.x) and Java 25
(MC 26.1+) simultaneously, while it is being opened and partly executed on the *oldest* of those JVMs.

## 14.2 The solution

| Layer | Target class file | Loaded on | Mechanism |
|---|---|---|---|
| `fabricmultiloader-format/api/runtime/processor` | **52** (Java 8) | every supported JVM | `--release 8` |
| the mod's container common code | `baselineJavaMajor` = minimum of the matrix (example: **61**/Java 17) | every JVM on which the mod starts | `--release <baseline>` |
| payload 1.20.1 | 61 (Java 17) | only Java ≥ 17 | `depends.java >=17` |
| payload 1.21.1 | 65 (Java 21) | only Java ≥ 21 | `depends.java >=21` |
| payload 26.1 | **69 (Java 25)** | only Java ≥ 25 | `depends.java >=25` |

The JVM checks the class file version in `defineClass`. A non-selected payload is never extracted, never added to
the classpath and never defined — its bytecode is pure ZIP content to the JVM. Therefore:

> **A Java 25 payload inside a JAR running on a Java 17 JVM cannot cause an `UnsupportedClassVersionError`,
> because none of its classes is ever defined.**

That is the complete answer to questions 5, 21, 22 and 23.

## 14.3 Java version detection and `depends.java`

Fabric Loader provides a synthetic mod candidate `java` whose version is the JVM version (major from
`Runtime.version().feature()` resp. the `java.specification.version` property). `depends: {"java": ">=25"}` is
therefore a hard solver clause evaluated by the loader — exactly like `minecraft`.

The runtime's own detection (for diagnostics) is Java-8-compatible and reflection-free:

```java
package dev.fabricmultiloader.format.version;

public final class JavaVersions {
    public static int currentMajor() {
        String v = System.getProperty("java.specification.version", "");
        if (v.startsWith("1.")) {                     // 1.8 → 8
            return parseIntSafe(v.substring(2), 8);
        }
        int dot = v.indexOf('.');                     // "25" or "25.0.1"
        return parseIntSafe(dot < 0 ? v : v.substring(0, dot), 8);
    }

    /** Class file major for a Java major version: 8→52, 17→61, 21→65, 25→69. */
    public static int classfileMajor(int javaMajor) { return javaMajor + 44; }

    /** Inverse; throws for values < 45. */
    public static int javaMajorOf(int classfileMajor) { … }
}
```

The formula `classfileMajor = javaMajor + 44` holds continuously from Java 1.1 (45) onwards and needs no table; it
is covered by tests for 8, 11, 17, 21, 25 and 30 and makes future Java versions configuration-free.

## 14.4 Class file scan in the validator

`ValidateUniversalJarTask` reads bytes 4–7 (minor/major of the class file header) of **every** `.class` file in the
container and in the payloads — no ASM, no class definition, ~200 MB/s.

| Rule | Check | Reaction |
|---|---|---|
| `OMNI-1040` | Every container class has `major ≤ container.baselineJavaMajor` | error, lists the first 20 violations with path and major |
| `OMNI-1041` | Every payload class has `major == payload.classfileMajor` | error |
| `OMNI-1046` | `payload.classfileMajor` matches the lower bound of `requires.java` (`javaMajorOf(major) ≤ min(requires.java)`) | error — prevents exactly the case “Java 25 bytecode with `depends.java >=21`” |
| `OMNI-1047` | `container.baselineJavaMajor` == minimum of all `min(requires.java)` | error |
| `OMNI-1048` | Nested libraries of a payload have `major ≤ payload.classfileMajor` | warning (libraries are often more conservative; the other way round would be an error) |

The most likely mistake a mod developer can make — “I accidentally left `--release 21` in the common module, and
now my mod does not start on 1.20.1” — is therefore a build error with an exact file reference instead of a player
crash.

## 14.5 Toolchains in the build

```kotlin
// gradle/fabricmultiloader.toml drives the values; here is the resulting configuration
// :common
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }   // compiler JDK
tasks.withType<JavaCompile> { options.release = 17 }                  // target bytecode = baseline

// :versions:mc-1.20.1
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile> { options.release = 17 }

// :versions:mc-1.21.1
tasks.withType<JavaCompile> { options.release = 21 }

// :versions:mc-26.1
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType<JavaCompile> { options.release = 25 }
```

Rules the plugin enforces:

1. **`options.release` instead of `sourceCompatibility`/`targetCompatibility`.** `--release` additionally checks
   the API used against the target JDK level and thereby prevents common code from accidentally using
   `List.of(...)` (Java 9) or `String.formatted` (Java 15) while `baselineJavaMajor = 8`.
2. **A toolchain JDK ≥ the highest target** is provisioned automatically via the `foojay-resolver`; for
   `release = 25` at least JDK 25 is required, because older compilers do not support `--release 25`. If it is
   missing, the plugin emits `OMNI-1090` with the concrete `gradle/fabricmultiloader.toml` entry and a download
   hint.
3. **Loom run tasks** receive an explicit `javaLauncher` from the appropriate toolchain per version, so that
   `runClient1201` starts with Java 17 and `runClient261` with Java 25 — even when Gradle itself runs on a
   different JDK.

## 14.6 Multi-release JARs

Rejected (rationale in chapter 5.5.3). The assembler writes **no** `Multi-Release: true` and creates **no**
`META-INF/versions/` entries; the validator rejects both (`OMNI-1049`), because they would duplicate the selection
semantics and could conflict with payload selection.

## 14.7 Language features and library API

| Module | Permitted language level | Rationale |
|---|---|---|
| `format`, `api`, `runtime`, `processor` | Java 8: no `var`, no records, no switch expressions, no sealed classes, no `List.of` | they must run on 1.16.5-era JVMs; records would additionally bind the API's binary compatibility to Java 16+ |
| the mod's container common code | `baselineJavaMajor` of the matrix — Java 17 in the example: records, `var`, switch expressions, text blocks and `sealed` permitted | freely chosen by the mod developer; `--release` enforces correctness |
| payload `mc-26.1` | Java 25 | full freedom |

The Java 8 restriction on the framework modules is the price of keeping FabricMultiLoader usable for 1.16.5 mods.
It is enforced by code style conventions (chapter 40 of the contributor guide) and `--release 8`, not by discipline
alone. In the API design it is compensated by the builder pattern instead of records and `Optional` instead of
`sealed` hierarchies.

## 14.8 Behaviour with a too-old JVM

| Scenario | Outcome |
|---|---|
| The JVM is too old for **Minecraft** | Minecraft/the loader aborts on its own before mods are loaded. Not our concern. |
| JVM ≥ the MC requirement but too old for all payloads (impossible with a correct matrix, possible with manual matrix maintenance) | The container loads (`depends.java` = minimum), no payload is selectable ⇒ `OMNI-2003` with the line “Java: 17 detected — payload 'mc261' requires ≥ 25”. |
| The JVM is too old for the **container** | The container's `depends.java` fails ⇒ loader error GUI with “requires Java 17 or later”. |
| An unknown newer JVM (e.g. Java 30) | All `>=` constraints are satisfied; the newest payload is chosen. Open upper bounds on `java` are intentional: a newer JVM is virtually always backwards-compatible. |

---

# 15. Mapping Strategy

## 15.1 Principle

Every payload is a **standalone Loom build** with its own Minecraft version, its own mappings, its own refmap and
its own access widener remap. Payloads share **no bytecode**. There is therefore no cross-version mapping
problem — there are N independent, individually correct mapping contexts.

## 15.2 Namespace states in a payload's lifecycle

```
source code (versions/mc-1.21.4/src/main/java)
   namespace: named (Yarn 1.21.4+build.8)
        │  javac + Mixin annotation processor
        ▼
build/classes  +  examplemod-mc1214-refmap.json (named → intermediary)
   namespace: named
        │  Loom remapJar (tiny-remapper)
        ▼
build/libs/examplemod-mc1214.jar
   namespace: intermediary   ← classes, refmap targets and the AW file are remapped
        │  omniPayload task (metadata + resource merge, no remap)
        ▼
build/omni/payloads/examplemod-mc1214.jar
   namespace: intermediary
        │  assembleUniversalJar (STORED embedding)
        ▼
examplemod-2.0.0-universal.jar → META-INF/jars/examplemod-mc1214.jar
   namespace: intermediary
        │  production start: the loader extracts, no remap
        ▼  dev start with the universal JAR: RuntimeModRemapper intermediary → named
runtime
```

The container passes through **no** remap step: it contains no Minecraft references, which makes `remapJar` not
merely unnecessary but forbidden for it (the assembler is a pure `Zip` task, not a Loom task).

## 15.3 Mapping provider freely selectable per version

```toml
[versions.mc1201]
minecraft   = "1.20.1"
mappings    = "yarn:1.20.1+build.10"

[versions.mc1214]
minecraft   = "1.21.4"
mappings    = "yarn:1.21.4+build.8"

[versions.mc261]
minecraft   = "26.1"
mappings    = "mojang"            # Mojang official mappings, e.g. because Yarn is not ready yet
```

Permitted values: `yarn:<build>`, `mojang`, `layered:<spec>` (passed through to Loom's `loom.layered { … }`),
`parchment:<version>` (a layer on top of Mojmap). Since payloads share no bytecode, a mixed matrix is technically
unproblematic. The validator checks only the *consistency within* a payload (AW namespace, refmap presence) —
`OMNI-1080`.

Practical consequence for the mod developer: in `versions/mc-26.1/src/main/java`, classes are then called
`net.minecraft.world.item.Item` (Mojmap) instead of `net.minecraft.item.Item` (Yarn). That is permitted, because
every version module has its own source code. For the shared `shared` source set (chapter 24.8), by contrast, the
mapping provider must be identical across all participating versions; the validator enforces that
(`OMNI-1081`).

## 15.4 Intermediary stability — what is guaranteed and what is not

| Guarantee | Holds | Consequence |
|---|---|---|
| A class's intermediary name stays stable across versions as long as the class is “the same” | yes | A mixin target name rarely breaks from the version alone. |
| A member's intermediary name stays stable | mostly | Newly introduced members receive new numbers; relocated members may be renumbered. |
| **Descriptors stay stable** | **no** | The main reason for payload separation. A changed parameter ⇒ a different descriptor ⇒ bytecode unresolvable ⇒ `NoSuchMethodError`. |
| The class exists in every version | no | New/removed classes are normal. |

The obvious idea “I write my mod code directly against intermediary, then one artifact runs everywhere” is
therefore not viable either: it solves the naming problem, not the signature problem. FabricMultiLoader uses
intermediary only as the **publication namespace** — exactly like every normal Fabric mod.

## 15.5 Refmap strategy

| Rule | Implementation |
|---|---|
| One refmap per payload | The result of the separate Loom compilation; no merging. |
| Unique refmap name across all payloads | Loom property `loom.mixin.defaultRefmapName = "<modid>-<payloadId>-refmap.json"`, set by the plugin. Validator `OMNI-1030`. |
| A refmap must exist when mixins are present | Validator `OMNI-1031`: for every mixin config with a `refmap` field, the file must be present in the payload and be valid JSON. |
| Refmap entries must belong to the payload's mixin classes | Validator `OMNI-1032`: every top-level key of the refmap must be a class present in the payload. Catches foreign refmaps accidentally packaged. |
| No `refmap` with an empty mixin list | Validator `OMNI-1033`: warning when a refmap exists without a corresponding config (clean-up hint). |
| Dev runtime | The loader's `MixinIntermediaryDevRemapper` handles named↔intermediary; no logic of our own. |

## 15.6 Access widener remap

* Source: `versions/mc-X/src/main/resources/<modid>-<payloadId>.accesswidener`, namespace header `named`.
* Loom configuration: `loom.accessWidenerPath = file("src/main/resources/<modid>-<payloadId>.accesswidener")`
  (set by the plugin). `remapJar` writes the file into the payload with the `intermediary` header.
* The shared AW portion from `common/src/main/accesswidener/shared.accesswidener` is concatenated **before** the
  remap (chapter 17.4), so it is likewise expressed in `named` and is remapped correctly along with the rest.
* The validator reads the AW header in the finished payload and compares it with `payload.mappings.namespace`
  (`OMNI-1082`). A `named` header in the release artifact would be a Loom configuration mistake and would cause a
  hard loader abort at runtime — which is why the check is an error, not a warning.

## 15.7 Handling Yarn renames in shared source code

When `shared` source code is compiled across several versions and Yarn renames a class (e.g. `ItemStack#getName`
stays but `PlayerEntity` → `Player` in a future Yarn generation), there are exactly three permitted reactions — and
the preprocessor route is deliberately not among them:

1. **Type alias through the common API**: the affected usage is pulled behind a common interface (e.g.
   `PlayerRef`) and implemented separately in each payload. The preferred route.
2. **Move the class from `shared` into the version modules** (duplicate with its own imports). Pragmatic for small
   classes.
3. **Pin the mapping layer**: `mappings = "layered:yarn:<older build>+patch"` — artificially keeps old names
   stable. Only as a transitional measure, with warning `OMNI-1083`, because it blocks Yarn updates.

The documentation page `docs/mappings.md` describes all three routes with examples and a decision matrix.

---

Continue with [chapters 16–17 — mixin architecture and access wideners](part-05-mixins-aw.md).
