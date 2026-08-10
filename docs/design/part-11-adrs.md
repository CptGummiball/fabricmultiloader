# 43. Architecture Decision Records

Format: context / decision / alternatives / consequences. Status of all ADRs: **accepted** (as of 1.0.0).
Location in the repository: `docs/internals/adr/ADR-0xx-*.md`.

---

## ADR-001 — Universal container architecture

**Status:** accepted · **Date:** 2026-08-10 · **Affects:** chapters 7, 10

### Context

A mod should serve several Minecraft versions as a single file. Fabric processes mixin configs and access wideners
declaratively and eagerly, before mod code runs (chapter 5.1). A runtime dispatcher that selects classes cannot
influence that processing. At the same time, descriptor changes in the Minecraft API between versions are
unavoidable, so a single compilation cannot suffice in principle.

### Decision

The universal JAR is an ordinary Fabric mod (“container”) that contains, per supported Minecraft version range, a
complete, separately built and remapped Fabric mod (“payload”) via Jar-in-Jar. The selection is made by Fabric
Loader's own dependency solver based on generated `depends` constraints (`minecraft`, `java`, `fabricloader`,
foreign mods) and `provides`/`breaks` exclusivity.

### Alternatives

| Alternative | Reason for rejection |
|---|---|
| A bootstrap selects classes on the same classpath (approaches A/C) | The mixin configs and AW of the single `fabric.mod.json` are registered for **all** versions ⇒ a guaranteed startup crash. |
| Payloads as resources + reflection on `KnotClassLoader` (approach B) | Loader internals; the mixin bootstrap is already finished, the AW transformer already built. |
| A custom ClassLoader (approach D) | Bypasses the transformer chain; mixins and AW have no effect; class identity breaks. |
| A multi-release JAR | Selects by Java version, not Minecraft version; metadata is not MR-capable. |
| Two files (bootstrap + downloading) | Violates the core goal and platform rules. |

### Consequences

* **Positive:** mixin, AW, refmap and Java isolation fall out without a mechanism of our own. No ClassLoader, no
  runtime transformation, no loader internals. Debugging and stack traces stay normal.
* **Positive:** new Minecraft versions are additive; existing payloads are not touched.
* **Negative:** the JAR is as large as the sum of the payloads (deliberately accepted).
* **Negative/risk:** dependence on a loader property (optional nested candidates are discarded when `depends`
  cannot be satisfied). Guarded by nightly conformance tests across five loader versions with automatic issue
  creation, plus two documented fallback paths.
* **Consequence:** all the effort moves into the build toolchain — where mistakes surface at build time instead of
  on the player's machine.

---

## ADR-002 — Version payload isolation without a custom ClassLoader

**Status:** accepted · **Affects:** chapter 13, invariant I1

### Context

Isolating version-specific code is a core requirement. The obvious Java route is a separate ClassLoader per
payload.

### Decision

FabricMultiLoader **never** creates a ClassLoader. Isolation arises solely from the fact that inactive payloads
never become part of the classpath. All active classes (Minecraft, Fabric API, runtime, container common, active
payload) are defined by `KnotClassLoader`. The validator forbids references to ClassLoader constructors,
`URLClassLoader` and `net.fabricmc.loader.impl.**` in shipped artifacts (`OMNI-1036`).

### Alternatives

* A child ClassLoader with parent-first for `net.minecraft.**`: mixins and AW do not apply; Minecraft's own
  registry/codec reflection cannot find payload classes; `ClassCastException` at every boundary.
* Isolation via Java modules (JPMS): Minecraft and Fabric run in the unnamed module; Knot is not a module layer;
  Mixin is not module-compatible.
* Isolation via package conventions alone: does not prevent the eager loading of mixin configs.

### Consequences

* No class identity problems, no delegation rules, no memory leaks from unreleased loaders.
* Payload and common see each other without mediation — the common API can exchange objects directly.
* The only exception in the entire project: the conformance test harness loads Fabric Loader versions into their own
  `URLClassLoader` — test code, not shipped, explicitly documented.
* Should a future loader isolate mods by class, `commonPackaging = EMBEDDED` (chapter 41.3) applies; it is already
  implemented and tested.

---

## ADR-003 — Mixin strategy

**Status:** accepted · **Affects:** chapter 16

### Context

Sponge Mixin eagerly resolves `ClassInfo` and the targets of every mixin class of a registered config. A 1.20.1
mixin on a signature changed in 1.21.4 is therefore a hard startup failure as soon as its config is registered.
Fabric registers the configs of all **selected** mods before the `preLaunch` phase.

### Decision

Mixin sets are exclusively payload-bound: every mixin config lives in the `fabric.mod.json` of **its payload**.
There is no runtime mixin dispatcher. Within a payload, the declarative `ConditionalMixinPlugin` is available for
fine-grained control (optional foreign mods, config switches). The container never declares mixins. Naming scheme
`<modId>-<payloadId>[.client|.server].mixins.json` with `<modId>-<payloadId>-refmap.json`.

### Alternatives

| Alternative | Reason for rejection |
|---|---|
| `IMixinConfigPlugin#shouldApplyMixin` as the primary mechanism | Prevents application, not loading/validation; target resolution happens earlier. |
| A config plugin that reduces `mixins` dynamically | Fabric/Mixin parse the JSON's `mixins` list; a plugin cannot retract entries. |
| Calling `Mixins.addConfiguration` later from `preLaunch` | Unspecified, the environment is already in PREINIT, refmap/mod association is missing. |
| One shared mixin set using `targets` strings instead of class literals | Bypasses refmap checking, moves errors to runtime, version-fragile. |

### Consequences

* A 1.20.1 mixin cannot be loaded under 1.21.4, because its class is not on the classpath — the strongest guarantee
  achievable.
* Mixin configs stay hand-written (they encode substantive decisions) but are strictly validated (11 rules) and
  automatically carried into the generated metadata.
* `compatibilityLevel` can be set correctly per payload (`JAVA_17`/`JAVA_21`/`JAVA_25`) — impossible with a shared
  config.
* Duplication: a functionally identical mixin must exist per payload. Countermeasure: the mixin stays a three-liner
  calling a common hook.

---

## ADR-004 — Access widener strategy

**Status:** accepted · **Affects:** chapter 17

### Context

Fabric permits exactly **one** `accessWidener` file per mod. AW files are mapping-bound (the namespace header is
checked) and member names can differ between MC versions. Loom can remap an AW file against only **one** mappings
version.

### Decision

One access widener per payload, remapped by Loom against that payload's mappings. An optional shared source
`common/src/main/accesswidener/shared.accesswidener` is merged with the payload-specific file at build time
**before** the remap, in the `named` namespace (deduplicated, sorted, with a source comment). The container declares
no AW.

### Alternatives

* One shared, cross-version AW file: not producible in a mapping-correct way; it would have to be hand-written in
  intermediary.
* Loading AW entries at runtime: no public API; by the time `preLaunch` runs, the transformer is already built.
* Reflection instead of AW everywhere: does not solve `extendable` and is expensive in hot paths.
* `@Accessor`/`@Invoker` only: a **good** alternative, and the default recommendation in the docs, but not a
  replacement for `extendable`/`mutable` or for broad access from many classes.

### Consequences

* The AW problem disappears entirely, without custom transformers or reflection.
* Shared AW needs stay maintainable in one place; divergent ones stay local.
* An entry whose target is missing in a version has no effect; the validator warns (`OMNI-1121`) instead of failing,
  because optional targets are legitimate.
* Namespace errors (the only hard runtime failure of the AW system) are caught at build time (`OMNI-1082`).

---

## ADR-005 — Gradle/Loom integration

**Status:** accepted · **Affects:** chapters 20–23

### Context

Loom must be configured once per Minecraft version (its own MC, mappings and Fabric API versions, its own
toolchain). Gradle projects must be declared in `settings.gradle.kts` and cannot come into existence dynamically.
Gradle is moving towards project isolation, which will eventually make cross-project configuration invalid.

### Decision

Four separate plugins (`settings`, `common`, `version`, `universal`), each applied by the module it is responsible
for. Shared configuration lives in the file `gradle/fabricmultiloader.toml` (“the matrix”), which every plugin reads
independently via a `ValueSource`. No root plugin configures subprojects. Payload artifacts are produced from
`remapJar` plus a dedicated `omniPayload` Zip task; the container is assembled in the root project without a module
of its own.

### Alternatives

* One root plugin with `subprojects { … }`: convenient, but hostile to isolation and caching; incomplete IDE models
  on the first sync.
* Everything in one project with several source sets: one project cannot compile against several MC versions (Loom
  is project-bound).
* Configuration exclusively in the Kotlin DSL: version modules would have to read root values ⇒ cross-project
  access.
* Loom's `include` for payloads: produces nested JARs but without control over generated metadata, ordering,
  compression and reproducibility.

### Consequences

* The build is configuration-cache-compatible and project-isolation-ready.
* A new MC version = a TOML block + a directory + a four-line build file; `addMinecraftVersion` creates all of it.
* The matrix is machine-readable and used alike by CI, validator, publishing and scaffolding — one source of truth
  instead of four.
* Downside: two configuration locations (TOML for version-dependent values, the Kotlin DSL for mod identity and
  assembly). Deliberately accepted; the dividing line is sharp and documented.

---

## ADR-006 — Java version compatibility

**Status:** accepted · **Affects:** chapter 14

### Context

Minecraft 1.16.5 requires Java 8, 1.18–1.20.4 requires Java 17, 1.20.5–1.21.x requires Java 21, and 26.1 onwards
requires Java 25 (class file major 69). A universal JAR must be opened and partly executed on the **oldest**
supported JVM while containing bytecode for newer JVMs.

### Decision

* `format`, `api`, `runtime` and `processor` are compiled to **class file 52 (Java 8)** (`--release 8`) so they load
  in every supported environment.
* The mod's container common code is compiled to `baselineJava` = the minimum of the matrix.
* Every payload is compiled to the Java level of its MC version and declares `depends.java`.
* The validator scans every class file header and checks container ≤ baseline, payload == declared major, and
  major ↔ `requires.java` consistency.
* Multi-release JARs are forbidden.

### Alternatives

* Everything at the highest Java level: breaks on older MC versions.
* Everything at Java 8: needlessly denies mod authors modern language features.
* MR JARs: the wrong selection axis (Java rather than Minecraft).
* Relying on lazy classloading without validation: a single accidental import in common leads to an
  `UnsupportedClassVersionError` on the player's machine.

### Consequences

* Three class file majors (61/65/69) in one file are unproblematic, because inactive payloads are never defined.
* The price is the Java 8 restriction on the framework modules: no records, no `var`, no `sealed`, no `List.of`.
  Enforced via `--release 8` rather than discipline; compensated by the builder pattern.
* `--release` (rather than `targetCompatibility`) catches accidental use of newer JDK API at compile time.
* The Java jump to 25 at 26.1 costs the mod author exactly one matrix entry.

---

## ADR-007 — Universal metadata format

**Status:** accepted · **Affects:** chapters 10, 11, 42

### Context

The runtime, the validator, the slim-JAR generator and the distribution publisher all need the same information
about payloads and their constraints. The loader, however, makes the selection based on the payload
`fabric.mod.json`. Two sources of information can diverge.

### Decision

* A dedicated manifest `META-INF/omni-container.json` (`formatId: "omni/1"`, `schemaVersion: 1`) describes the
  container, payloads, constraints, hashes, class file majors, entrypoints, capabilities and diagnostic URLs.
* Every payload additionally carries `omni/payload.json` with a self-description **and** a copy of the container
  identity and entrypoints (enabling the dev fallback and slim JARs).
* **All** metadata including both `fabric.mod.json` files is generated; hand-written ones are a build error.
* The validator checks the equivalence of manifest and `fabric.mod.json` (`OMNI-1011`) — divergence is a build
  error, not a runtime problem.
* The container declares **no** hard `depends` on the payload alias but the union of the MC ranges; “no payload
  selectable” is reported by our `preLaunch` diagnostics.
* A custom JSON parser in `format` (not Gson), with position information and input limits.
* Format, schema and library versions are independent axes; `minRuntime` allows additive extension with a
  deterministic failure on runtimes that are too old.

### Alternatives

| Alternative | Reason for rejection |
|---|---|
| Only `fabric.mod.json` files, no dedicated manifest | No place for hashes, capabilities, class file majors or diagnostic URLs; the runtime would have to open every nested JAR. |
| The manifest as `.properties` or a custom binary format | Less readable, less diffable, no benefit. |
| Using Gson | FQCN collision with Minecraft's Gson (classpath first-wins) or shading; not guaranteed available during `preLaunch` on older versions. |
| A hard `depends` on the payload alias | Produces the unreadable loader message “requires examplemod-impl which is missing” instead of an explanation. |
| Maintaining the manifest by hand | Error-prone, diverges from reality, prevents hash/class file information. |

### Consequences

* One change takes effect in one place (matrix/DSL) and propagates consistently into both metadata worlds.
* The error message in an unsupported environment is the best possible, because it can evaluate the constraints
  itself.
* 9 KiB of custom JSON parser as the price of independence — acceptable and fully testable.
* The forward-compat rule (readers ignore the unknown, the validator rejects it) allows the format to evolve without
  schema version jumps.

---

## ADR-008 — The runtime as its own nested mod instead of shading

**Status:** accepted · **Affects:** chapters 13.4, 42.3

### Context

Several universal mods in the same game each bring FabricMultiLoader classes along. With embedded (shaded or
unpacked) classes, classpath order decides which version wins — non-deterministic, and an older runtime might
interpret a newer manifest.

### Decision

`fabricmultiloader-runtime` (including `format` and `api`) is a standalone Fabric mod with the ID
`fabricmultiloader` that every container brings along via Jar-in-Jar. The loader deduplicates by mod ID and picks
the highest compatible version. Every container declares
`depends: {"fabricmultiloader": ">=X <nextMajor>"}`. A major transition gets a new mod ID (`fabricmultiloader2`)
and a new root package (`dev.fabricmultiloader.v2`).

### Alternatives

* Relocation per mod (Shadow/jarjar): removes the collision but makes the public mod API unusable (signatures would
  point at relocated types), clutters stack traces and enlarges every JAR.
* Expecting the user to install the library separately: violates the core goal “one file”.
* Placing the classes unpacked into every container: exactly the first-wins problem.

### Consequences

* Process-wide exactly one runtime, deterministically the newest; a clear loader message on major incompatibility.
* A mod's public API can use `dev.fabricmultiloader.api` types in its signatures because they are process-wide
  unambiguous — the precondition for guarantee C7.
* One additional nested JAR entry (62 KiB) per container.
* A major transition is possible without forcing the ecosystem into a flag-day update.

---

## ADR-009 — Resources are merged into the payloads

**Status:** accepted · **Affects:** chapter 25

### Context

Fabric registers every mod with `assets/` or `data/` as its own resource pack. If shared resources lived in the
container and version-specific ones in the payload, two packs for the same mod would be active simultaneously; their
precedence depends on mod load order and is not reliably defined.

### Decision

The container contains **no** `assets/` and `data/` entries (validator `OMNI-1023`). All resources are merged into
**every** payload at build time, in the precedence order common → shared → version → datagen. Differing files with
the same path require an explicit `allowOverride` declaration; language files are optionally merged key by key. The
mod icon lives under `omni/icon.png`, i.e. outside `assets/`, so the container does not become a resource pack.

### Alternatives

* Two packs with a defined priority: Fabric offers no stable, cross-mod priority declaration for mod packs.
* Merging resources at runtime (a custom pack provider): requires a version-specific resource API, moves complexity
  into runtime, complicates debugging.
* Shared resources in the container, version-specific in the payload, conflicts forbidden: too restrictive (models
  and shaders really do change between versions).

### Consequences

* At runtime there is exactly one resource pack per mod — behaviour is deterministic and identical to a normal mod.
* Resources are stored N times in the JAR (the dominant contribution to its size).
* Every deviation between versions is visible in the merge report and in code review — a common source of error in
  multi-version projects becomes explicit.

---

## ADR-010 — No source preprocessor as a mandatory component

**Status:** accepted · **Affects:** chapter 24.8

### Context

Established multi-version projects use comment preprocessors (`//#if MC>=12100`) to compile the same source for
several MC versions. That reduces duplication considerably.

### Decision

FabricMultiLoader contains no preprocessor and requires none. Source sharing happens through (a) `:common` for
Minecraft-free code, (b) the optional `shared` source set for MC-touching code that compiles **unchanged** in
several version modules (shadowing forbidden), and (c) adapters/services for divergence. An externally applied
preprocessor is not blocked, and the combination is documented.

### Alternatives

* A custom preprocessor: a second, untyped language in the project; worse IDE support, refactoring and review; and
  it solves neither mixin, AW nor packaging questions.
* Stonecutter as a hard dependency: binds the framework to a third-party project and its lifecycle.
* Generating Java sources: produces “generated sources you must not edit” — a known DX trap, especially in IntelliJ.

### Consequences

* All source code in the repository is real, compilable, refactorable Java; every file means what the compiler sees.
* More duplication in the adapter layer than with a preprocessor — bounded, because the handle/spec design keeps
  adapters small (example mod: 18–22 classes per payload versus 142 common classes).
* `shared` covers the common case “identical code for two adjacent versions” without a preprocessor.

---

## ADR-011 — Determinism through build-time disjointness instead of runtime priority

**Status:** accepted · **Affects:** chapters 12.5–12.8

### Context

Payload selection is performed by Fabric's SAT solver. Its optimisation objective (“as many and as new mods as
possible”) is not a specified priority mechanism. If two payloads were satisfiable at once, it would be undefined
which one wins — and a runtime tie-break by FabricMultiLoader is impossible, because selection happens before any
mod code runs.

### Decision

The constraint domains of all payloads (`minecraft × java × environment`) must be pairwise **disjoint** at build
time; that is proven, not assumed (`OMNI-1010`). The `priority` mechanism acts exclusively at build time: a
`DomainDisjunctifier` subtracts the domains of higher-priority payloads from lower ones (exact interval/set
algebra) and writes the resulting disjoint ranges into the generated `depends`. Constraints that can only filter
(foreign mods, Fabric API) do not count towards the domain; two payloads differing only in those are a build error
(`OMNI-1012`). Additionally, the `provides` alias and mutual `breaks` enforce exclusivity, and the runtime verifies
“exactly one” (`OMNI-2003/2004`).

### Alternatives

* Runtime priority: impossible, because selection precedes mod code.
* Allowing overlaps and hoping for solver behaviour: non-deterministic, hence not reproducible and not supportable.
* Forbidding overlaps without subtraction: would make the legitimate “catch-all + special case” pattern impossible.

### Consequences

* Selection is deterministic and **provable** — a build that passes the validator cannot have a selection problem on
  the player's machine.
* `priority` survives as a convenient means of expression (catch-all + special case) while remaining runtime-free.
* The set algebra code is the most complex part of `format` (interval subtraction with prerelease boundaries) —
  guarded by 30 test cases and differential tests against the loader's predicate implementation.

---

Continue with [chapter 44 — implementation plan](part-12-implementation-plan.md).
