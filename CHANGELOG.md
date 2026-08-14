# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to the versioning rules in
[docs/design/part-10-nfr.md](docs/design/part-10-nfr.md) chapter 42 — one release train for all
framework modules, with the container format and manifest schema versioned independently.

## [Unreleased]

### Added

* **Implementation step 1 — repository scaffold.**
  Gradle 8.11.1 multi-project build with the module layout of chapter 22.1: `format`, `api`,
  `runtime`, `processor`, `gradle-plugin`, `testing`, `example`.
  * Convention plugins in `buildSrc`: `java-conventions` (toolchain, reproducible archives,
    `-Xlint:all -Werror`), `java8-conventions` (`--release 8` for the modules loaded on the
    oldest supported JVM), `java17-conventions` (build-time-only modules),
    `publishing-conventions` (POM metadata, artifact naming per chapter 22.2).
  * `VerifyClassfileVersionTask` — reads the class file header of every produced class and fails
    with `OMNI-1040` if a module exceeds its declared bytecode baseline. Wired into `check` for
    every module. This is the build-logic ancestor of the validator's `ClassfileScanner`
    (chapter 14.4).
  * First real constants, each carried forward by later steps: `OmniFormat` (format id, manifest
    paths, marker prefix), `Side` (physical side with environment-constraint parsing),
    `RuntimeInfo` (mod id, schema version support window), `ProcessorOptions`, `PluginIds`,
    `ClassFiles` (class file version arithmetic including Java 25 → major 69).
  * Project documents: `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`,
    `.editorconfig`.
  * CI: `.github/workflows/build.yml` — unit tests plus a configuration-cache reuse check on
    JDK 17, 21 and 25.

* **Implementation step 2 — JSON parser and error infrastructure.** (`format`)
  * `ErrorCode` — the normative registry of every `OMNI-xxxx` code, with the category derived from
    the numeric range (1xxx build, 2xxx runtime, 3xxx format, 4xxx API misuse). `Severity`,
    `OmniException`, `OmniApiMisuseException`, and `Messages` implementing the message format of
    chapter 29.1: detected state, explanation, at least one concrete fix, doc anchor.
  * A hand-written strict RFC 8259 parser — `Json`, `JsonReader`, `JsonValue` and its six subtypes,
    `JsonWriter`, `JsonPointer`, `JsonLimits`, `JsonLocation`, `JsonFormatException`. **Not Gson:**
    shading it would collide with Minecraft's own copy, and Minecraft's copy is not reliably
    initialised during `preLaunch` on older versions — which is exactly when the manifest is read.
  * Every parsed value carries its line, column and JSON pointer, so a malformed manifest produces
    a location rather than a type name. Numbers keep their original lexeme, which makes
    read-modify-write byte-exact. `JsonLimits` bounds document size, nesting depth, entry count and
    string length (`OMNI-3003`).
  * `Sha256` for the payload integrity model of chapter 10.7.

* **Implementation step 3 — version algebra.** (`format`)
  * `SemVer` with lenient parsing for Minecraft's and Java's real-world version strings, including
    the legacy Java form (`1.8.0_302` → `8.0.302`), `VersionPredicate`,
    `VersionPredicateParser`, `Interval`, `VersionRange` (kept in a sorted, disjoint, merged normal
    form), `JavaVersions` (`classFileMajor = featureVersion + 44`, continuous since Java 1.1) and
    `MinecraftVersions`.
  * `VersionPredicateEquivalenceTest` — a differential test that compares this implementation
    against the real `net.fabricmc.loader` classes over ~4,600 comparable predicate/version pairs.
    It passed unmodified, and it settled an open question in the design: Fabric's `^` uses the plain
    same-major rule, not npm's `0.x` special case.

* **Implementation steps 4 and 5 — manifest model, resolver, disjointness proof.** (`format`)
  * The complete `omni/1` model: `ContainerManifest`, `ContainerInfo`, `PayloadDescriptor`,
    `Requirements`, `EntrypointSet`, `MappingsInfo`, `MixinConfigRef`, `CommonPackaging`,
    `EnvironmentConstraint`, plus `ManifestReader` and `ManifestWriter`. `Identifiers` and
    `SafePaths` treat every string from a manifest as untrusted input (chapter 39.4).
  * `Environment`, `PayloadMatcher`, `MatchResult`, `Rejection`, `PayloadResolver` and
    `ResolutionReport` — the resolution that produces the `OMNI-2003` report of chapter 29.2, with a
    substantive reason per rejected payload rather than "mod not loaded".
  * `Domain` and `DomainDisjunctifier` — the build-time disjointness proof. Range subtraction by
    priority is what makes "catch-all plus special case" work with no runtime priority rule at all:
    the lower-priority payload's declared domain minus the higher-priority ones is computed exactly
    and becomes its generated `depends`. `Result.areEffectiveDomainsDisjoint()` re-checks the
    property independently. Diagnostics `OMNI-1010`, `OMNI-1015`, `OMNI-1016`.
  * `java-test-fixtures` with `ManifestFixtures`: the three-payload reference matrix of chapter
    11.2, shared by the `format` and `runtime` test suites.

* **Implementation step 6 — the developer SPI.** (`api`)
  * ~45 types across `api`, `api.platform`, `api.registry`, `api.net`, `api.command`, `api.event`,
    `api.ref`, `api.text` and `api.capability` — `ModContext`, `Platform`/`PlatformFactory`/
    `AbstractPlatform`, `Registries` with specification-and-handle registration, `Networking`,
    `Commands`, `Events`, `ServiceRegistry`, `Capability`/`Capabilities`, the opaque reference types
    and a minimal version-neutral `Text` model.
  * `@ImplementedByMod` and `@ImplementedByFramework` carry `RUNTIME` retention and are enforced by
    `ApiSurfaceTest`, which also caps abstract-method counts on mod-implemented interfaces — with a
    documented `LARGE_BY_DESIGN` exemption list, so the rule stays meaningful rather than being
    weakened until everything passes.

* **Implementation step 7 — runtime bootstrap, environment detection, diagnostics.** (`runtime`)
  * `LoaderFacade` and `FabricLoaderFacade` — the twelve loader methods the design commits to,
    behind one interface. `FabricLoaderFacade` is the only class in the entire runtime that imports
    `net.fabricmc`, which makes the "stable since loader 0.14.0" claim checkable by reading one file
    and makes the whole bootstrap testable in milliseconds.
  * `Log` and `MessageFormatter` (SLF4J bound reflectively, standard error otherwise — a hard SLF4J
    dependency would be a `NoClassDefFoundError` on 1.16.5), `EnvironmentDetector`,
    `LifecycleStateMachine`, `IntegrityChecker`, `ContainerRuntime`, `RuntimeRegistry`,
    `RuntimeBootstrap`, `PlatformInfoImpl`, `ReportWriter`, `DiagnosticReport`,
    `ContainerPreLaunch`, and the runtime's own `fabric.mod.json` (mod id `fabricmultiloader`,
    deliberately with **no** `minecraft` dependency).
  * The exactly-one assertion acts on Fabric's verdict and evaluates the declared constraints
    alongside it purely to explain the outcome — so a disagreement between the two surfaces as a
    diagnostic instead of as silent misbehaviour.
  * `ForbiddenReferencesTest` — invariant I1 checked on the produced bytecode rather than on
    imports, because a reference introduced by a shaded dependency would pass a source check.

* **Implementation step 8 — payload activation, context, lifecycle entrypoints.** (`runtime`,
  `format`)
  * `PlatformLoader` — the one reflective call on the critical path. The package containment check
    runs *before* `Class.forName`, which is called with `initialize = false` so the type check
    happens before any static initialiser: a manifest edited after the fact cannot name an arbitrary
    class to construct (`OMNI-2020`–`OMNI-2024`).
  * `PayloadActivation` drives the four Fabric phases, each step idempotent. A Fabric entrypoint is
    never told which mod declared it — `EntrypointMetadata` lives under `net.fabricmc.loader.impl`,
    which this project does not touch — so every payload entrypoint acts on every container in the
    process and the first one to arrive does the work.
  * `ModContextImpl`, `ServiceRegistryImpl` (registration window opened and sealed around
    `Platform#onInitialize` rather than derived from the lifecycle phase), `CapabilityResolver`
    (manifest first, platform second), `PreLaunchContextImpl`, `CrashContextImpl`, `CommonBootstrap`
    (`OMNI-2030`–`OMNI-2033`), and the entrypoints `PayloadPreLaunch`, `PayloadMain`,
    `PayloadClient`, `PayloadServer`.
  * `DevFallback` plus `PayloadManifest` and `PayloadManifestReader` in `format` — a payload without
    its container synthesises a one-payload container manifest from `omni/payload.json` and runs
    down the identical code path. No second lifecycle for the development loop.

* **Implementation step 9 — version-stable adapters and the conditional mixin plugin.** (`runtime`)
  * `runtime.adapter`: `CommandRegistry` (collection, side filtering, permission accumulation down
    the command tree, path-conflict detection, argument folding), `CommandInvocationImpl`,
    `EventBus` (subscriptions, dispatch order, self-unsubscription during dispatch, per-handler
    failure containment), `TextConverter`, `Feedback`.
  * `runtime.mixin`: `ConditionalMixinPlugin`, `Condition`, `ConfigLocator`, `PluginLog` — the
    declarative `omni.conditions` block of chapter 16.6. This is the only framework class that can
    run before `preLaunch`, so it fails open on every path and touches nothing from `runtime.boot`,
    `runtime.payload`, `runtime.context` or `runtime.diag`; `MixinPluginIsolationTest` is validator
    rule `OMNI-1035` applied to the framework itself.
  * `net.fabricmc:sponge-mixin` as a new `compileOnly` dependency. Mixin sits on the system class
    loader in every Fabric environment because the loader itself depends on it, so exactly one class
    may reference it and nothing is shipped.

* **Implementation step 10 — the test harness.** (`testing`, `format`)
  * `FakeModContext` — a `ModContext` with no Minecraft behind it that records every item, block,
    sound, item group, channel and sent payload the mod declares, in declaration order. Commands and
    events are deliberately *not* faked: they delegate to the real `CommandRegistry` and `EventBus`
    from step 9, so a mod author's test exercises the actual side filtering, permission
    accumulation, conflict detection and dispatch ordering rather than a lookalike that could
    disagree with them. `fireServerStarted`, `firePlayerJoin` and `deliver` then drive the mod's own
    handlers.
  * `RecordedRegistrations`, `FakeHandles` (handles that admit they are unbound, and fail `unwrap`
    with a sentence), `FakeComponents` (in-memory data components, keyed by stack identity),
    `FakePlatform`, `ManifestBuilder`, `JarWriter`, `PayloadJarBuilder`, `ContainerJarBuilder`,
    `JarFixtures`, `GoldenFiles`.
  * `format.manifest.FabricModJsonWriter` and `PayloadManifestWriter` — the derivation of the
    loader's view from the Omni manifest. In `format` rather than in the Gradle plugin because three
    consumers need exactly one of it: the plugin that produces real jars, the conformance harness
    that produces synthetic ones, and the validator that reads them back. The jar fixtures therefore
    carry generated metadata rather than hand-written metadata, which is the only way a conformance
    test proves anything about what the build actually emits.
  * `FakeLoader` moved out of the runtime's tests and became `FakeFabricLoader` in `testing`, as
    step 7 said it would. The runtime's own tests now consume the published harness, so a gap in it
    shows up here before it shows up in a mod project.

### Changed

* `Registries` gained a `default flush()`. The lifecycle requires deferred registrations to run
  *after* the mod's `onInitialize` — the first moment everything it declares has been declared — and
  the runtime had no method to call. A `default` rather than an abstract method keeps the addition
  compatible with design principle P6 and costs an eagerly-registering adapter nothing.
* `omni/payload.json` gained a mandatory `packages` field. Without a container it is the only thing
  bounding which class `platformFactory` may name, and that bound is what makes `OMNI-2024`
  meaningful in a development run.
* `ForbiddenReferencesTest` changed shape while keeping its teeth. The blunt "never mention
  `java/lang/ClassLoader`" rule would have forced the *wrong* class loader lookup, so the two
  manifest-driven `Class.forName` call sites are exempted by name; the half of invariant I1 a byte
  scan cannot see — subclassing a `ClassLoader` — is now checked against the real class hierarchy.
  `ConditionalMixinPlugin` is exempted for Mixin on the same narrow basis.
* New error codes: `OMNI-3000` (malformed JSON), `OMNI-1016` (inexpressible remainder domain),
  `OMNI-2033` (entrypoint does not implement its phase interface).

### Corrections to the design document

Recorded here because each was found by implementing the design, not by re-reading it.

* **Chapter 28.2 — `CommandsImpl` in the runtime is not buildable.** It was specified to register
  Brigadier commands through Fabric API's `CommandRegistrationCallback`, whose functional method is
  `register(CommandDispatcher<ServerCommandSource>, CommandRegistryAccess, RegistrationEnvironment)`.
  Two of the three parameters are Minecraft types and therefore land in the descriptor of any class
  implementing it. The runtime is not a Loom build, is never remapped, and loads unchanged on every
  supported version — so such a reference resolves in at most one namespace (`net.minecraft.…` in
  development or `net.minecraft.class_…` in production), never both; remapping the runtime instead
  would bind it to one Minecraft version, which invariant I3 exists to prevent. The adapter is now
  split at the **Minecraft boundary** rather than at the subsystem boundary, and the "compile
  against Fabric API 0.92.2 and 0.114.0" definition of done is replaced by a stronger check that
  already existed: the runtime references no Fabric API, Minecraft or Mojang class at all, enforced
  on the bytecode of every class in the module.
* **Chapter 16.6** — the mixin plugin reaches the loader through `LoaderFacade` instead of
  `FabricLoader.getInstance()`, and `ConfigLocator` finds its config by asking the loader which mods
  declare which mixin packages rather than by guessing a classpath resource name. With several
  universal mods installed, the first `getResourceAsStream` hit is not necessarily the right one.
* **Chapter 12.7** — the range subtraction produces at most **3** remainder cells per subtraction,
  not 27. The decomposition proceeds one axis at a time (Minecraft, then Java within the shared
  Minecraft range, then side), which is both correct and far cheaper than the product.
* **Chapter 9.5** — containers are identified by the presence of a manifest, not by entrypoint
  metadata. `EntrypointMetadata` is not public loader API. This turned out to be the better answer
  anyway: it finds every universal mod in the process regardless of how its entrypoints happen to be
  declared.
* **Chapter 19.2** — `Registries#flush()` is called by the runtime after the mod's `onInitialize`,
  not by the adapter inside its own. The example in the chapter contradicted the lifecycle table two
  sections later.
* `Side` lives in `format`, not `api`: the payload matcher needs it and the matcher is shared
  between runtime and validator. `ModLoggerImpl` was never written — `runtime.log.Log` already
  *is* the `ModLogger` implementation. `CrashContextImpl` sits in `runtime.diag`, matching the
  module tree of chapter 22.1. `FeedbackAdapter` is called `Feedback` and is a plain interface the
  payload supplies per invocation rather than a capability, because a capability is a feature gate
  and every payload must be able to send command output.

### Notes

* The build runs on **JDK 21**; Gradle 8.11.1 does not support running on JDK 24+. Compilation
  targets are set per module via `--release`.
* Gradle 8.11.1 rather than the 8.12 named in the design document: 8.11.1 was already present in
  the local wrapper cache, the difference is immaterial, and staying on the 8.x line preserves
  Fabric Loom compatibility for implementation step 17.
* Binary compatibility checking (`japicmp` in the design) is deferred to step 6, where `api` gains
  its first published surface and a baseline can exist. Until then `verifyBytecodeBaseline` covers
  the property that actually matters at this stage.
* The `gradle-plugin` module is plain Java for now; `java-gradle-plugin` and Kotlin arrive with
  the first real plugin implementation in step 12.
* Test count after step 10: **602** across `format` (397), `runtime` (131), `api` (36), `testing` (32),
  `processor` (3) and `gradle-plugin` (3). The distribution is deliberate — the version algebra, the
  resolver and the disjointness proof are where a defect would be silent, so that is where the tests
  are.
* The `runtime` module's **test** source set compiles at Java 17 while its main source set stays at
  Java 8. The baseline is about the bytecode that ships inside every universal jar; the tests run on
  the build's own JDK and consume `testing`, which is Java 17. `verifyBytecodeBaseline` scans
  `compileJava`'s output only, so the guarantee that matters is unchanged.
* No release has been cut. Nothing here has a version number yet; `[Unreleased]` covers implementation
  steps 1–10 and will be split into a real release entry when 1.0.0 ships (step 21).

## [0.0.0] — design phase

### Added

* Complete technical architecture and implementation document, 46 chapters
  ([DESIGN.md](DESIGN.md)): feasibility analysis, the Omni container format, version resolver,
  classloading, mixin and access widener architecture, the common and adapter APIs, the Gradle
  toolchain, error handling, testing, CI/CD, distribution, security, performance, compatibility
  limits, versioning, 11 ADRs, a 21-step implementation plan, answers to 25 hard technical
  questions, and a reality check.
* Interim proprietary licence; Apache-2.0 intended before any third-party productive use.

[Unreleased]: https://github.com/CptGummiball/fabricmultiloader/commits/main
