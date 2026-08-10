# FabricMultiLoader — Technical Architecture and Implementation Document

**Status:** Final (ready to implement)
**Document version:** 1.0.0
**Target product:** FabricMultiLoader 1.0.0 (runtime library + Gradle toolchain + *Omni* container format)
**Date:** 2026-08-10

---

## Navigation index

| Chapters | File |
|---|---|
| 1–4 Executive summary, goals, non-goals, requirements | this document |
| 5 Fabric/JVM feasibility analysis | [part-01-feasibility.md](docs/design/part-01-feasibility.md) |
| 6–9 Architecture variants, final decision, runtime architecture, bootstrap sequence | [part-02-architecture.md](docs/design/part-02-architecture.md) |
| 10–12 Omni container format, metadata schema, version resolver | [part-03-container-format.md](docs/design/part-03-container-format.md) |
| 13–15 Classloading, Java compatibility, mapping strategy | [part-04-classloading.md](docs/design/part-04-classloading.md) |
| 16–17 Mixin architecture, access widener architecture | [part-05-mixins-aw.md](docs/design/part-05-mixins-aw.md) |
| 18–19, 26–28 Common API, version adapter API, client/server, networking, registries/events | [part-06-api.md](docs/design/part-06-api.md) |
| 20–25 Gradle plugin, DSL, repository structure, build pipeline, dependencies, resources | [part-07-gradle.md](docs/design/part-07-gradle.md) |
| 29–33 Error handling, diagnostics, validation, testing, CI/CD | [part-08-quality.md](docs/design/part-08-quality.md) |
| 34–38 Distribution, example mod, migration, adding Minecraft versions, documentation architecture | [part-09-project.md](docs/design/part-09-project.md) |
| 39–42 Security, performance, compatibility limits, versioning | [part-10-nfr.md](docs/design/part-10-nfr.md) |
| 43 Architecture Decision Records | [part-11-adrs.md](docs/design/part-11-adrs.md) |
| 44 Implementation plan | [part-12-implementation-plan.md](docs/design/part-12-implementation-plan.md) |
| Hard technical questions (25 answers) | [part-13-hard-questions.md](docs/design/part-13-hard-questions.md) |
| 45–46 Reality check, final architecture summary | [part-14-reality-check.md](docs/design/part-14-reality-check.md) |

---

## 1. Executive Summary

### 1.1 The result in one sentence

FabricMultiLoader turns a Gradle multi-project into a single, clearly identifiable mod file for players
(`examplemod-2.0.0-universal.jar`) that runs on multiple Minecraft versions, because it contains exactly one
**fully and separately built and remapped payload module** per supported Minecraft version — and because the
selection of the correct payload is made **by Fabric Loader itself**, not by hand-rolled classloading.

### 1.2 The central technical move

The universal JAR is **not an exotic container format with a custom ClassLoader**. It is an entirely ordinary
Fabric mod that contains several complete Fabric mods via **Jar-in-Jar (JiJ)**:

```
examplemod-2.0.0-universal.jar          ← container mod, mod id "examplemod"
├─ fabric.mod.json                      ← depends.minecraft = union of all payload ranges
├─ META-INF/omni-container.json         ← Omni manifest (source of truth for runtime + tooling)
├─ com/example/common/**.class          ← platform-neutral common code (Java 17, no MC references)
└─ META-INF/jars/
   ├─ fabricmultiloader-runtime-1.0.0.jar   ← the library itself, its own Fabric mod, Java 8
   ├─ examplemod-mc1201.jar             ← payload: depends { minecraft "1.20.1", java ">=17" }
   ├─ examplemod-mc1211.jar             ← payload: depends { minecraft ">=1.21 <1.21.2", java ">=21" }
   └─ examplemod-mc1214.jar             ← payload: depends { minecraft ">=1.21.4 <1.21.5", java ">=21" }
```

At startup Fabric Loader reads **only the JSON metadata** of all nested candidates, feeds them into its
SAT-based `ModSolver`, and then loads **exclusively** those candidates whose constraints are satisfiable.
Payloads for other Minecraft versions are **never extracted, never opened, never placed on the classpath and
never verified by the JVM**. This solves the four classic killer problems *without* FabricMultiLoader having to
solve them itself:

| Problem | Resolved by this architecture |
|---|---|
| Mixins of foreign versions must not be validated | The mixin config lives in the payload's own `fabric.mod.json`. Payload not loaded ⇒ config never registered ⇒ mixin class never read. |
| Access wideners are version-specific, and the loader knows only one file per mod | Every payload is *its own mod* and has *its own* `accessWidener` declaration. |
| Different Java class file versions in one JAR | Payloads for Java 21 are discarded by the solver on Java 17 (`depends.java`), so their class files are never read ⇒ no `UnsupportedClassVersionError`. |
| Different refmaps / mappings | Every payload is compiled and remapped separately by Loom against its own MC version and carries its own refmap. |

Everything FabricMultiLoader adds on top is therefore **convenience, determinism, diagnostics and toolchain** —
not the risky core. That property is what makes the project maintainable long term.

### 1.3 What FabricMultiLoader itself contributes

1. **`fabricmultiloader-runtime`** — a standalone, Minecraft-free Fabric mod (Java 8, ~60 KB) that
   * reads and validates the Omni manifest,
   * detects the environment (Minecraft, loader, Fabric API, Java version, `EnvType`),
   * verifies that **exactly one** payload is active,
   * produces a complete, human-readable diagnostic report on failure instead of a `NoClassDefFoundError`,
   * executes the lifecycle chain container → payload → common code deterministically,
   * provides the common API (`ModContext`, `Platform`, `Registries`, `Networking`, `Commands`, `Events`,
     `Services`, `Capabilities`).
2. **`fabricmultiloader-gradle`** — four Gradle plugins that build version modules against Loom, **generate**
   `fabric.mod.json` and the Omni manifest, merge resources deterministically, **prove** version ranges to be
   disjoint, assemble the universal JAR reproducibly and validate it before release.
3. **Omni Container Format v1** — a fully specified, versioned file format including manifest schema, payload
   descriptors, checksums and compatibility rules.
4. **Test and CI infrastructure** that really boots the same universal JAR against every supported Minecraft
   version before it is published.

### 1.4 The one load-bearing assumption

The architecture stands or falls on a single property of Fabric Loader:

> **A nested mod candidate whose `depends` cannot be satisfied, and which no loaded mod depends on, is silently
> not selected by `ModSolver` — instead of triggering a hard resolution failure.**

This is the behaviour of loader lines 0.14.x through 0.17.x and the reason JiJ libraries with narrow version
ranges work throughout the ecosystem. Because the foundation rests on this property, it is derived technically in
[chapter 5](docs/design/part-01-feasibility.md), guarded in
[chapter 32](docs/design/part-08-quality.md) by a **loader conformance test across the entire loader matrix in
CI**, and paired in [chapter 41](docs/design/part-10-nfr.md) with a concrete fallback path (`buildSlimJars`).
There is no second unproven assumption of this weight anywhere in this document.

### 1.5 What the mod developer sees

```bash
git clone https://github.com/fabricmultiloader/fabricmultiloader-template my-mod
cd my-mod
./gradlew runClient1214            # ordinary Loom dev loop, one MC version
./gradlew test                     # unit tests (common code, JVM only, no Minecraft)
./gradlew buildUniversalJar        # -> build/libs/my-mod-1.0.0-universal.jar
./gradlew validateUniversalJar     # 34 checks including class file scan and disjointness proof
./gradlew integrationTest          # boots the same JAR on 1.20.1 / 1.21.1 / 1.21.4
./gradlew addMinecraftVersion --mc=1.22 --yarn=1.22+build.3 --java=21   # scaffold a new version
```

A new Minecraft version costs **one TOML block, one four-line `build.gradle.kts`, one directory** — and after
that, only the actual API adjustments in the version module.

---

## 2. Goals

Prioritised; on conflict, the lower number wins.

| # | Goal | Measurable criterion |
|---|---|---|
| G1 | **One file for players** | Exactly one download artifact per release that works in the `mods` folder on every declared MC version. No installer, no extra step, no companion JAR. |
| G2 | **Developer experience** | Migrating an existing single-version Fabric mod in ≤ 8 mechanical steps; adding an MC version without touching existing modules; full IntelliJ support including per-version run configurations. |
| G3 | **Technical stability** | No hand-rolled ClassLoaders, no runtime bytecode transformation, no reflection into loader internals on the critical path. Every mechanism used is a documented Fabric feature. |
| G4 | **Isolation of version-specific code** | Classes, mixins, refmaps, access wideners and resources of an inactive Minecraft version are **not on the classpath** at runtime and are not read by the JVM. Verified by test `PayloadIsolationTest`. |
| G5 | **Fabric compatibility** | Runs with Fabric Loader ≥ 0.14.0 without loader patches. Fabric API remains an ordinary per-payload `depends` relation. Other mods see an ordinary mod. |
| G6 | **Long-term maintainability** | Semantic versioning with binary-compatibility guarantees within major 1; format schema version decoupled from library version; forward-compat rules for unknown manifest fields. |
| G7 | **Reproducible builds** | Two builds of the same commit produce byte-identical universal JARs (identical SHA-256). Enforced via `preserveFileTimestamps=false`, `reproducibleFileOrder=true`, fixed entry order, fixed timestamps, sorted JSON output. |
| G8 | **Good error messages** | Every failure path has a stable error code (`OMNI-xxxx`), a cause description, the detected actual state, the expected state and an actionable instruction. Never a bare `NoClassDefFoundError`. |
| G9 | **Complete documentation** | 24 defined doc pages (chapter 38), API reference from Javadoc, DSL reference generated from the Gradle model. |

### 2.1 Explicit usage scenarios

* **S1** — A mod supports 1.20.1 (Java 17) and 1.21.4 (Java 21) with partly different Fabric API levels.
* **S2** — A mod has a version-specific mixin whose target method signature changed between 1.20.1 and 1.21.4.
* **S3** — A mod needs an access widener on 1.20.1 for a field that is already public on 1.21.4.
* **S4** — A mod has an optional Cloth Config integration that requires a different Cloth version per MC version.
* **S5** — A mod exposes an API for other mods; that API must be **binary-stable** across all MC versions.
* **S6** — The mod appears on Modrinth as **one** file tagged with game versions 1.20.1, 1.21, 1.21.1, 1.21.4.
* **S7** — A user launches the JAR on 1.19.2 → controlled, understandable error message.
* **S8** — New MC version 1.22 appears → the developer adds the version, existing payloads remain untouched.

---

## 3. Non-Goals

Deliberately excluded, with rationale. These points are revisited in the reality check (chapter 45).

| # | Non-goal | Rationale |
|---|---|---|
| N1 | **A complete, version-independent Minecraft API** | That would be a second Architectury *plus* a compatibility layer over five years of MC churn. FabricMultiLoader abstracts exactly those areas that can be abstracted stably (lifecycle, registration of simple content, commands, networking, events, config, resources) and provides a clean, type-safe **escape hatch** (`Services`, `Capabilities`) for everything else. |
| N2 | **A single compiled bytecode artifact for all MC versions** | Technically impossible as soon as descriptors change (`new Identifier(a,b)` → `Identifier.of(a,b)`; `PacketByteBuf` → `RegistryByteBuf`). Bytecode references methods by exact descriptor; a single artifact cannot resolve both. Hence: N compilations, one file. |
| N3 | **Cross-loader support (Forge/NeoForge/Quilt)** | The name says it: Fabric. Quilt loads Fabric mods but is neither tested nor guaranteed. The common API is deliberately cut so that a later `neoforge` payload backend would remain possible; that is not a goal for 1.x. |
| N4 | **Runtime bytecode patching to align APIs** | A custom transformer rewriting e.g. `Identifier.<init>` into `Identifier.of` would be a core transformer with conflict potential against every other mod and against Mixin. Violates G3. |
| N5 | **A custom ClassLoader for payloads** | Refuted in detail in chapter 6 (approach D) and chapter 13: it breaks Mixin, access wideners and `FabricLoader` entrypoints, and produces `ClassCastException` at every boundary with Minecraft types. |
| N6 | **Support for Minecraft < 1.16.5** | Before 1.16 there is no intermediary stability, no `getObjectShare`, no modern loader features. Lower bound of official support: **1.16.5**; tested reference matrix: 1.20.1 / 1.21.1 / 1.21.4. |
| N7 | **Automatic porting of mod code between MC versions** | No codemod tooling, no source preprocessor as a mandatory component. The developer writes version-specific code; the framework organises, isolates and packages it. |
| N8 | **Shrinking the universal JAR below the sum of its payloads** | Explicitly accepted by the project owner. Deduplicating classes between payloads is impossible (different descriptors); deduplicating resources is deliberately *not* done (chapter 25, determinism beats size). For size-critical cases `buildSlimJars` exists. |

---

## 4. Requirements

### 4.1 Functional requirements

| ID | Requirement |
|---|---|
| F-01 | A universal JAR contains 1..n payloads for different Minecraft version ranges. |
| F-02 | At runtime **exactly one** payload is active; the selection is deterministic and reproducible. |
| F-03 | The selection accounts for: Minecraft version, Fabric Loader version, Fabric API version, Java major version, physical environment (client/server), presence and version of arbitrary other mods. |
| F-04 | Payload-specific mixin configs, refmaps and access wideners are processed exclusively for the active payload. |
| F-05 | Common code exists exactly once in the JAR and is referenceable by all payloads. |
| F-06 | Resources (assets/data) are unambiguous per payload; there are never two simultaneously active resource packs for the same mod. |
| F-07 | To Fabric and to other mods the mod appears under **one** primary mod ID with **one** version. |
| F-08 | In an unsupported environment a structured error message with actual/expected state appears; either a hard abort (default) or a warning (`-Dfabricmultiloader.strict=false`). |
| F-09 | Payloads may carry version-specific libraries as their own nested JARs. |
| F-10 | The Gradle plugin generates all metadata (`fabric.mod.json`, Omni manifest, payload descriptors) — they are never hand-maintained. |
| F-11 | A validator checks the finished JAR against 34 defined rules before release. |
| F-12 | The developer can run `runClient`, `runServer` and `runDatagen` per MC version. |
| F-13 | The universal JAR can be booted automatically in a real server instance of every supported MC version. |
| F-14 | The mod's own public API (for third-party mods) is binary-compatible across all supported MC versions. |

### 4.2 Non-functional requirements

| ID | Requirement | Target |
|---|---|---|
| NF-01 | Startup overhead of the framework | < 15 ms (manifest parse + resolve + verification), measured in `BootstrapBenchmark` |
| NF-02 | Additional heap usage of the runtime | < 512 KB after initialisation |
| NF-03 | Size overhead of the container without payloads | < 80 KB |
| NF-04 | No extraction beyond the loader's own JiJ extraction | 0 custom extraction steps |
| NF-05 | Reproducibility | SHA-256 of two builds of the same commit identical |
| NF-06 | Minimum Java version of the framework artifacts | Class file major 52 (Java 8) for `format`, `api`, `runtime`, `processor` |
| NF-07 | Minimum Fabric Loader version | 0.14.0 |
| NF-08 | Test coverage of `format` + `runtime` (lines) | ≥ 90 % |
| NF-09 | Every failure path with a stable code and doc anchor | 100 % |
| NF-10 | Gradle configuration cache | fully compatible; all tasks with declared inputs/outputs |

### 4.3 Reference compatibility matrix (version 1.0.0)

| Minecraft | Java (min) | Fabric Loader (min) | Fabric API (min) | Mappings | Status |
|---|---|---|---|---|---|
| 1.16.5 | 8 | 0.14.0 | 0.42.0 | Yarn `1.16.5+build.10` | supported, not in CI |
| 1.18.2 | 17 | 0.14.0 | 0.76.0 | Yarn `1.18.2+build.4` | supported, not in CI |
| 1.20.1 | 17 | 0.14.21 | 0.92.2 | Yarn `1.20.1+build.10` | **CI reference** |
| 1.20.4 | 17 | 0.15.0 | 0.97.2 | Yarn `1.20.4+build.3` | supported, not in CI |
| 1.21 / 1.21.1 | 21 | 0.15.11 | 0.102.0 | Yarn `1.21.1+build.3` | **CI reference** |
| 1.21.4 | 21 | 0.16.9 | 0.114.0 | Yarn `1.21.4+build.8` | **CI reference** |
| 1.21.5 – 1.21.x | 21 | 0.16.10 | 0.119.2 | Yarn `1.21.5+build.1` | supported, not in CI |
| **26.1 and newer** (new Mojang version scheme) | **25** | ≥ 0.17.0 | ≥ 0.130.0 | Yarn `26.1+build.1` | **CI reference once released** |
| future (e.g. `26.2`, `27.x`) | ≥ 25 | ≥ 0.17 | — | any | covered by the version model, see chapter 12 |

“Supported, not in CI” means: the framework has no known blockers, the matrix is preconfigured in the template,
but the reference CI boots only three versions to keep pipeline runtime under 25 minutes. Mod developers extend
the matrix in their own project.

**Java jumps in the matrix.** The reference matrix therefore deliberately contains **three** Java major versions:
Java 17 (1.18–1.20.4), Java 21 (1.20.5–1.21.x) and Java 25 (26.1+, class file major 69). The case “one universal
JAR contains payloads with three different class file versions” is therefore not a theoretical corner case but
the normal case for any mod that follows the jump from 1.21.x to 26.1. The architecture handles it via
`depends.java` in the payload (chapter 12.4) and the validator's class file scan (chapter 14.4); the container
itself stays mandatorily on the **lowest** Java level of the matrix (17 in the example, class file major 61).

### 4.4 Glossary

| Term | Meaning in this document |
|---|---|
| **Container** | The universal JAR itself; a Fabric mod carrying the developer's primary mod ID. |
| **Payload** | A complete Fabric mod inside the container, built and remapped for exactly one MC version range. |
| **Runtime** | The mod `fabricmultiloader` (library), nested in every container. |
| **Common** | Platform-neutral mod code without Minecraft references, living in the container. |
| **Shared** | Optional source layer compiled *into each version module*, allowed to touch Minecraft. |
| **Omni** | Name of the container format (`omni/1`). Deliberately not “FMLU”, to rule out any confusion with Forge Mod Loader. |
| **Adapter / Platform** | The version-specific implementation of the common SPI inside a payload. |
| **Matrix** | The file `gradle/fabricmultiloader.toml`, single source of truth about supported versions. |

---

Continue with [chapter 5 — Fabric/JVM feasibility analysis](docs/design/part-01-feasibility.md).
