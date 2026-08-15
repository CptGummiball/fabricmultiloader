# 44. Implementation Plan

The order is binding: every step builds only on steps already completed. “Definition of Done” (DoD) is always
machine-checkable. Effort figures are experience values for an experienced Java/Gradle developer.

## Milestone M0 — repository scaffold (step 1)

### Step 1 — repository, build, CI skeleton

| | |
|---|---|
| **Files** | `settings.gradle.kts` (includes: `format`, `api`, `runtime`, `processor`, `gradle-plugin`, `testing`, `example`), the root `build.gradle.kts` (convention plugins: `java-library`, `--release` settings, `maven-publish`, `japicmp`), `gradle/libs.versions.toml`, `gradle.properties` (`org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`), the wrapper (Gradle 8.12), `LICENSE` (currently proprietary, “All Rights Reserved”; the move to Apache-2.0 must happen before third parties use it productively — mandatory, because the runtime is embedded into foreign mod JARs via Jar-in-Jar and that is redistribution), `NOTICE`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`, `.editorconfig`, `.gitattributes` (`* text=auto eol=lf`), `.github/workflows/build.yml` (compile + tests only) |
| **Conventions** | `format`/`api`/`runtime`/`processor`: `options.release = 8`, a Checkstyle rule “no `var`, no records, no `sealed`”; `gradle-plugin`/`testing`: `release = 17` |
| **Tests** | one smoke test per module, so `check` has something to do everywhere |
| **DoD** | `./gradlew build` green; `./gradlew build --configuration-cache` reports “reused” on the second run; CI green; `japicmp` active (no baseline yet) |
| **Depends on** | — |
| **Effort** | 1 day |

---

## Milestone M1 — `format` (steps 2–5)

Everything here is pure, Minecraft-free logic and fully unit-testable. M1 is the foundation for both the runtime
**and** the Gradle plugin; a defect here propagates everywhere, which is why it comes first and carries the highest
test coverage (target: 95 % of lines).

### Step 2 — JSON parser and error infrastructure

| | |
|---|---|
| **Classes** | `format.json`: `Json`, `JsonValue`, `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull`, `JsonReader`, `JsonWriter`, `JsonPointer`, `JsonLimits`, `JsonFormatException`<br>`format.error`: `ErrorCode` (an enum with `id()`, `title()`, `severity()`), `OmniException`, `OmniApiMisuseException`, `Messages` (text building blocks, one method per message) |
| **Tests** | `JsonParserTest` (the RFC 8259 suite + 40 failure cases with expected line/column), `JsonWriterRoundTripTest`, `JsonLimitsTest` (size/depth/count/string length ⇒ `OMNI-3003`), `JsonPointerTest`, `ErrorCodeUniquenessTest` (no duplicate IDs, IDs in the right range) |
| **DoD** | The parser passes the suite; every error message contains line, column and the source line with a caret; writer output is byte-stable |
| **Effort** | 2 days |

### Step 3 — version algebra

| | |
|---|---|
| **Classes** | `format.version`: `SemVer`, `SemVerParser`, `VersionPredicate` (+ `Comparison`, `AnyPredicate`, `AndPredicate`), `VersionPredicateParser`, `Interval`, `VersionRange`, `JavaVersions`, `MinecraftVersions` (`ordinal()`, `normalize()`) |
| **Tests** | `SemVerParseTest` (parameterised over the complete table from chapter 12.2), `SemVerCompareTest` (including prerelease ordering, build neutrality, `UNKNOWN`), `VersionPredicateParseTest`, `VersionRangeAlgebraTest` (union/intersect/subtract; property tests: idempotence, commutativity of union/intersect, `a \ a = ∅`, `(a ∪ b) \ b ⊆ a`), `UnionNormalizationTest`, `JavaVersionsTest`, `MinecraftOrdinalTest` (1.16.5, 1.20.1, 1.21.4, 26.1, 26.10, 27.0 — monotonicity) |
| **DoD** | 500 generated random intervals satisfy all algebraic properties; `toPredicates()` is round-trip-stable |
| **Depends on** | step 2 |
| **Effort** | 3 days |

### Step 4 — manifest model and parser

| | |
|---|---|
| **Classes** | `format.manifest`: `ContainerManifest`, `ContainerInfo`, `RuntimeRef`, `EntrypointSet`, `PayloadDescriptor`, `Requirements`, `EnvironmentConstraint`, `MappingsInfo`, `MixinConfigRef`, `DiagnosticsInfo`, `ManifestReader`, `ManifestWriter`, `PayloadDescriptorReader/Writer`, `SafePaths` (the path validation from chapter 39.2), `Identifiers` (mod ID/FQCN validation) |
| **Tests** | `ManifestReaderTest` (required fields, type errors with pointers, unknown fields, `minRuntime`, `schemaVersion`), `ManifestRoundTripTest` (byte-identical, canonical key order), `SafePathsTest` (25 attack patterns: absolute, `..`, backslash, NUL, double slash, outside the permitted roots), `IdentifiersTest` |
| **DoD** | The golden file of the example manifest from chapter 11.2 is read, written back and byte-identical; all attack patterns rejected |
| **Depends on** | steps 2–3 |
| **Effort** | 2 days |

### Step 5 — resolver and disjointness

| | |
|---|---|
| **Classes** | `format.payload`: `PayloadResolver`, `PayloadMatcher`, `MatchResult`, `Rejection`, `Constraint` (enum), `Domain`, `DomainCell`, `DomainDisjunctifier`, `ResolutionReport`<br>`format.hash`: `Sha256` |
| **Tests** | `PayloadMatcherTest` (each constraint kind individually and combined; completeness of the rejection list), `DomainDisjunctifierTest` (30 scenarios: catch-all + specific, Java variants, client/server, complete shadowing ⇒ `OMNI-1015`, an empty remainder), `ResolutionReportTest` (golden files of both reports from chapter 29.2), `Sha256Test` |
| **DoD** | For 200 generated payload sets, after disjunctification: pairwise intersections empty **and** the union unchanged; reports match the golden file character for character |
| **Depends on** | steps 2–4 |
| **Effort** | 4 days |

---

## Milestone M2 — `api` (step 6)

### Step 6 — the complete developer SPI

| | |
|---|---|
| **Classes** | `api`: `UniversalMod`, `UniversalClientMod`, `UniversalServerMod`, `UniversalPreLaunch`, `UniversalEntrypoint` (annotation), `ModContext`, `Side`, `Id`, `ModLogger`, `LifecyclePhase`, `Capability`, `Capabilities`, `ServiceRegistry`, `FabricMultiLoader`, `ImplementedByMod`/`ImplementedByFramework` (annotations)<br>`api.platform`: `Platform`, `PlatformFactory`, `PlatformInfo`, `AbstractPlatform`, `PreLaunchContext`, `CrashContext`<br>`api.registry`: `Registries`, `ItemSpec`, `BlockSpec`, `SoundSpec`, `ItemGroupSpec`, `ItemHandle`, `BlockHandle`, `SoundHandle`, `ItemGroupHandle`, `Rarity`, `ItemBehavior`, `UseContext`, `BlockUseContext`, `UseResult`, `Hand`<br>`api.net`: `Networking`, `ChannelSpec`, `ChannelHandle`, `PayloadCodec`, `ByteSink`, `ByteSource`, `C2SReceiver`, `S2CReceiver`, `TypedPayloadApi`<br>`api.command`: `Commands`, `CommandSpec`, `Arg`, `CommandInvocation`, `CommandSender`, `Permission`<br>`api.event`: `Events`, `Subscription`, `EventKey`, `ServerRef`, `BlockBreakHandler`, `ItemUseHandler`<br>`api.ref`: `Unwrappable`, `PlayerRef`, `WorldRef`, `ItemStackRef`, `BlockPosRef`<br>`api.text`: `Text`, `TextColor`, `TextStyle`, `ClickAction`, `HoverAction`<br>`api.config`: `ConfigHandle`, `ConfigCodec`<br>`api.resource`: `ResourceReloadListener`, `PackHandle` |
| **Tests** | `ApiSurfaceTest` (every public class carries `@ImplementedByMod` or `@ImplementedByFramework`; all `@ImplementedByMod` interfaces contain only the abstract methods intended for 1.0), `Java8ComplianceTest` (class file major of all `api` classes == 52), `SpecBuilderTest` (builder validation, immutability, `equals`/`hashCode`), `TextModelTest` |
| **DoD** | `api` compiles with `--release 8`; the `japicmp` baseline is set; Javadoc without doclint errors; every example signature from chapters 18/19/27/28 compiles against this state |
| **Depends on** | M1 |
| **Effort** | 4 days |

---

## Milestone M3 — `runtime` (steps 7–9)

### Step 7 — bootstrap, environment, container discovery

| | |
|---|---|
| **Classes** | `runtime.log`: `Log`, `Slf4jBridge`, `Formatter`<br>`runtime.env`: `EnvironmentDetector`, `Environment`<br>`runtime.boot`: `RuntimeBootstrap`, `RuntimeRegistry`, `ContainerRuntime`, `LifecycleStateMachine`, `IntegrityChecker`<br>`runtime.diag`: `DiagnosticReport`, `ReportWriter`, `DebugDump`<br>`runtime.entrypoint`: `ContainerPreLaunch`<br>resource: `src/main/resources/fabric.mod.json` (mod `fabricmultiloader`, `depends` fabricloader ≥0.14.0, java ≥8) |
| **Tests** | `EnvironmentDetectorTest`, `ContainerDiscoveryTest`, `PayloadActivationTest` (1/0/n payloads), `LifecycleStateMachineTest`, `IntegrityCheckerTest`, `DiagnosticReportTest` (golden file), `LogBridgeTest` (two classpath variants: with/without SLF4J), `AtomicReportWriteTest` (temp+move; an unwritable directory does not abort) |
| **DoD** | All tests green against `FakeFabricLoader`; class file major of all runtime classes == 52; no import from `net.fabricmc.loader.impl` (an ArchUnit-style bytecode test `ForbiddenReferencesTest`) |
| **Depends on** | M1, M2 |
| **Effort** | 4 days |

### Step 8 — payload activation, context, lifecycle entrypoints

| | |
|---|---|
| **Classes** | `format.manifest`: `PayloadManifest`, `PayloadManifestReader` (`omni/payload.json`, needed by the dev fallback)<br>`runtime.payload`: `PlatformLoader`, `PayloadActivation`<br>`runtime.context`: `ModContextImpl`, `ServiceRegistryImpl`, `CapabilityResolver`, `PreLaunchContextImpl`<br>`runtime.diag`: `CrashContextImpl`<br>`runtime.entrypoint`: `PayloadPreLaunch`, `PayloadMain`, `PayloadClient`, `PayloadServer`, `PayloadEntrypoints` (their shared body)<br>`runtime.boot`: `CommonBootstrap` (instantiates the mod code's entrypoint classes), `DevFallback` |
| **Tests** | `PlatformLoaderTest` (missing class, wrong type, `null`, a throwing factory ⇒ `OMNI-2020…2023`; an FQCN outside the payload packages ⇒ `OMNI-2024`), `PayloadActivationTest` (ordering, idempotence, entrypoint failures `OMNI-2030…2033`, `OMNI-2040`), `ModContextImplTest`, `ServiceRegistryImplTest` (registration only in the permitted window), `CapabilityResolverTest`, `DevFallbackTest` |
| **DoD** | The full lifecycle runs through with a fake platform; all 4xxx failure paths tested |
| **Depends on** | step 7 |
| **Effort** | 4 days |

> Two deviations recorded during implementation. `ModLoggerImpl` does not exist as a separate class: `runtime.log.Log`
> from step 7 already *is* the `ModLogger` implementation, and a second class delegating to it would be pure
> indirection. `CrashContextImpl` lives in `runtime.diag` rather than `runtime.context`, matching the module tree in
> chapter 22.1 — collecting crash report lines is a diagnostics concern.
>
> One API addition: `Registries#flush()` as a `default` method. The lifecycle requires the deferred registrations to
> run *after* the mod's `onInitialize`, and the runtime cannot enforce that ordering through an interface that has no
> method for it. A `default` (rather than abstract) method keeps the addition compatible with P6 and costs an adapter
> that registers eagerly nothing.

### Step 9 — version-stable adapters and the mixin plugin

| | |
|---|---|
| **Classes** | `runtime.adapter`: `CommandRegistry` (collection, side filtering, permission accumulation, flattening), `CommandInvocationImpl`, `EventBus` (subscriptions, dispatch, failure containment), `TextConverter`, `Feedback`<br>`runtime.mixin`: `ConditionalMixinPlugin`, `ConfigLocator`, `PluginLog`, `Condition` |
| **Tests** | `ConditionalMixinPluginTest` (the condition matrix, fail-open, config location), `MixinPluginIsolationTest` (a bytecode test: the mixin package references only the JDK/`format`/the loader facade/Mixin ⇒ `OMNI-1035`), `CommandRegistryTest`, `EventBusTest`, `TextConverterTest` |
| **DoD** | The runtime compiles against **no** Fabric API at all, verified for every class in the module by `ForbiddenReferencesTest`; the mixin plugin's isolation verified at the bytecode level |
| **Depends on** | step 8 |
| **Effort** | 4 days |

> **Correction to chapter 28.2, found during implementation.** The plan called for a `CommandsImpl` in the runtime
> registering Brigadier commands through Fabric API's `CommandRegistrationCallback`, verified by compiling the
> module against Fabric API 0.92.2 and 0.114.0. That is not buildable. `CommandRegistrationCallback`'s functional
> method is `register(CommandDispatcher<ServerCommandSource>, CommandRegistryAccess, RegistrationEnvironment)`; two
> of the three parameters are Minecraft types and therefore appear in the descriptor of any class implementing it.
> The runtime is not a Loom build, is never remapped, and is loaded unchanged on every supported version, so such a
> reference would resolve in at most one namespace — `net.minecraft.…` in development or `net.minecraft.class_…` in
> production, never both. Remapping the runtime instead would bind it to one Minecraft version, which is exactly
> what invariant I3 and validator rule `OMNI-1042` forbid.
>
> The adapter is therefore split at the **Minecraft boundary** rather than at the subsystem boundary. Command
> collection, side filtering, permission accumulation, path-conflict detection, argument folding, subscription
> management, dispatch ordering, per-handler failure containment and the text tree walk all stay in the runtime —
> one implementation for every version, which is what chapter 28.4 actually claims. What moves into the payload is
> the wiring: a `CommandRegistrationCallback` listener that walks `CommandRegistry#nodes()`, and Fabric API event
> listeners that call `EventBus#fire…`. That is under forty lines per payload.
>
> The two-Fabric-API DoD is replaced by a stronger check that already existed: the runtime references no Fabric API,
> no Minecraft and no Mojang class at all, enforced on the bytecode of every class in the module.
>
> Two smaller deviations. `FeedbackAdapter` is called `Feedback` and is a plain interface the payload supplies per
> invocation, rather than a capability — a capability is a feature gate, and every payload has to be able to send
> command output. And `ConditionalMixinPlugin` reaches the loader through `LoaderFacade` instead of calling
> `FabricLoader.getInstance()` as sketched in 16.6, which keeps the loader API countable in one file and is what
> makes the plugin testable against a fake loader at all.

---

## Milestone M4 — `testing` and loader conformance (steps 10–11)

### Step 10 — the test harness

| | |
|---|---|
| **Classes** | `testing`: `FakeFabricLoader`, `FakeModContext` (+ `RecordedRegistrations`), `FakePlatform`, `FakeComponents`, `ManifestBuilder`, `PayloadJarBuilder`, `ContainerJarBuilder`, `JarFixtures`, `GoldenFiles` |
| **Tests** | `FakeModContextTest` (records items, blocks, channels, commands and events correctly), `JarBuilderTest` (the generated fixtures are valid ZIPs with the expected structure) |
| **DoD** | A 20-line unit test can check common code against three simulated MC versions (the example from chapter 32.7 runs) |
| **Depends on** | M2, M3 |
| **Effort** | 3 days |

### Step 11 — the loader conformance harness

| | |
|---|---|
| **Classes/files** | `testing.conformance`: `LoaderConformanceHarness`, `LoaderVersion`, `SyntheticContainer`, `ResolutionProbe`; a `conformanceTest` source set with the 8 test cases from chapter 32.4; `.github/workflows/conformance.yml`; `docs/internals/loader-assumption.md` |
| **Tests** | `nestedUnsatisfiableIsDropped`, `exactlyOneSelected`, `providesExclusivity`, `breaksExclusivity`, `javaDependencyEvaluated`, `environmentEvaluated`, `runtimeDeduplication`, `containerRangeError` — each against loaders 0.14.21, 0.15.11, 0.16.9, 0.16.14, 0.17.3, 0.19.3 |
| **DoD** | All 8 cases × 6 loader versions green; the CI workflow opens an issue automatically on failure; `docs/internals/loader-assumption.md` written |
| **Depends on** | step 10 |
| **Effort** | 4 days |

> **This step is a gate.** If it fails, the architecture is refuted and the fallback path (slim JARs as the primary
> product) must be evaluated before any further steps. That is why it sits **before** the Gradle plugin, not after.
>
> **Result: 48 of 48 green.** The assumption holds unchanged from 0.14.21 through 0.19.3, across the solver rewrite
> between 0.14 and 0.15 and the candidate-type rename in 0.16. The matrix gained 0.19.3, which did not exist when
> the plan was written; the loader API the harness drives turned out to be stable enough that spanning six lines
> costs no more than spanning five.
>
> Two corrections the measurement forced, both recorded in `docs/internals/loader-assumption.md`:
>
> * **`environment` is not evaluated by the solver.** It is applied during *discovery* — `ModDiscoverer` skips a mod
>   that does not load in the current environment and records it in the `envDisabledMods` map, which `ModResolver`
>   merely receives. That is stronger than chapter 13.7 assumed (a client-only payload on a dedicated server never
>   reaches the solver at all), but a harness that skipped the discovery step would have reported the opposite of
>   the truth.
> * **Nested candidates must be linked to their parent and passed flat.** `createPlain(…, nestedMods)` records the
>   children, but the discoverer additionally calls `addParent` on each, and `resolve` receives roots and nested
>   candidates together. An unlinked nested candidate is an orphan the solver never considers.

---

## Milestone M5 — the Gradle plugin (steps 12–16)

### Step 12 — plugin scaffold, matrix, DSL

| | |
|---|---|
| **Classes** | `gradle`: `SettingsPlugin`, `CommonPlugin`, `VersionPlugin`, `UniversalPlugin`<br>`gradle.matrix`: `MatrixParser` (a custom TOML parser or `tomlj`; decision: **`tomlj`**, since the plugin runs on Java 17 and has no Java 8 restriction), `MatrixModel`, `VersionEntry`, `MatrixValueSource`, `MatrixValidator`<br>`gradle.dsl`: all extensions and specs from chapter 21.5 |
| **Files** | `src/main/resources/META-INF/gradle-plugins/*.properties` (4 plugin IDs) |
| **Tests** | `MatrixParserTest` (required fields, unknown keys ⇒ `OMNI-1161`, `minecraft ∉ minecraftRange` ⇒ `OMNI-1160`), `SettingsPluginTest` (TestKit: auto-inclusion, orphan directories ⇒ `OMNI-1163`), `DslDefaultsTest` |
| **DoD** | A minimal project (1 payload) syncs and shows all expected tasks; `--configuration-cache` without problems |
| **Depends on** | M1 (the plugin uses `format` directly) |
| **Effort** | 4 days |

### Step 13 — the version module pipeline

| | |
|---|---|
| **Classes** | `gradle.task`: `MergeAccessWidenerTask`, `MergeResourcesTask` (+ `ResourceMergePlan`, `LangMerger`, `MergeReportWriter`), `GeneratePayloadModJsonTask`, `GeneratePayloadDescriptorTask`, `PayloadJarTask`, `ValidatePayloadTask`<br>`gradle.loom`: `LoomConfigurator` (Loom API calls encapsulated, so Loom updates are absorbed in one place), `OmniDependencyHandlers` (`omniMod`, `omniOptionalMod`, `omniInclude`, `omniIncludeCommon`) |
| **Tests** | `AccessWidenerMergeTest` (golden file, dedup, sorting, header check), `ResourceMergePlanTest`, `LangMergerTest`, `ModJsonGeneratorTest` (a golden file of the payload `fabric.mod.json` from chapter 11.9), `PayloadDescriptorGeneratorTest`, `PayloadJarTaskTest`, TestKit: `SingleVersionBuildTest` |
| **DoD** | `:versions:mc-1.21.4:omniPayload` produces a payload whose structure matches chapter 10.4 exactly; `validatePayload` green; the task is `UP-TO-DATE`-capable and cacheable |
| **Depends on** | step 12 |
| **Effort** | 6 days |

### Step 14 — container assembly

| | |
|---|---|
| **Classes** | `gradle.task`: `GenerateOmniManifestTask`, `GenerateContainerModJsonTask`, `AssembleUniversalJarTask`, `OmniReportTask`, `BuildSlimJarsTask`<br>`gradle.jar`: `DeterministicZipWriter`, `ClassfileScanner`, `ResourceDigest` |
| **Tests** | `ManifestGeneratorTest` (the golden file of chapter 11.2 from synthetic payloads), `ContainerModJsonGeneratorTest` (union ranges, `depends.java` = minimum), `DeterministicZipWriterTest` (ordering, timestamps, STORED for nested JARs), `ClassfileScannerTest`, TestKit: `ThreeVersionProjectTest`, `MixedJavaProjectTest` (17/21/25), `ReproducibilityTest`, `SlimJarTest` |
| **DoD** | `./gradlew buildUniversalJar` produces a JAR whose structure matches chapter 10.2; two builds are SHA-256-identical; slim JARs are runnable single-version mods |
| **Depends on** | step 13 |
| **Effort** | 5 days |

### Step 15 — the validator

| | |
|---|---|
| **Classes** | `gradle.validate`: `Rule`, `RuleContext`, `RuleSet` (34 rule implementations, one class each), `ReferenceScanner` (constant pool scan), `ReachabilityAnalyzer` (transitive references for rule 26), `MixinConfigChecker`, `AccessWidenerChecker`, `MappingsLookup` (reads Loom's tiny mappings for `OMNI-1121`), `ReportFormatter`, `ValidationResult`<br>`gradle.task`: `ValidateUniversalJarTask`, `ValidateExternalJarTask` |
| **Tests** | One positive and one negative test per rule (68 tests) against synthetic JARs from `testing`; `ReportFormatterTest` (a golden file of the output from chapter 31.3); `RuleIgnoreTest` (non-disableable rules ⇒ `OMNI-1003`) |
| **DoD** | All 34 rules implemented and tested from both sides; the report matches the golden file; runtime < 3 s for a 5 MiB JAR |
| **Depends on** | step 14 |
| **Effort** | 7 days |

### Step 16 — runs, scaffolding, integration tests, publishing

| | |
|---|---|
| **Classes** | `gradle.task`: `AddMinecraftVersionTask`, `RemoveMinecraftVersionTask`, `UniversalRunTask`, `ServerBootTestTask`, `ClientSmokeTestTask`, `VerifyReproducibleTask`<br>`gradle.scaffold`: `PackageRenamer` (AST-free but reliable rewriting of `package`/`import`/FQCNs via a token scan), `TemplateWriter`, `CiMatrixPatcher`<br>`gradle.itest`: `FabricServerInstaller`, `ScenarioRunner`, `LogAssertions`<br>`gradle.publish`: `ModrinthPublisher`, `CurseForgePublisher`, `GithubReleasePublisher`, `GameVersionExpander` (range → a concrete version list via the platform APIs)<br>`testing`: `omni-itest-probe` (a small Fabric mod, built per matrix version) |
| **Tests** | `AddVersionTaskTest` (matrix, directory, package rename, then a green build), `RemoveVersionTaskTest` (the baseline increase is reported), `PackageRenamerTest` (30 cases including strings, comments, `package-info`), `GameVersionExpanderTest` (against recorded API responses), `ScenarioRunnerTest`, plus the real integration tests of the example mod |
| **DoD** | The 7 scenarios from chapter 32.5 run locally and in CI; `addMinecraftVersion` produces an immediately buildable module; `publishUniversal --dry-run` shows the correct `game_versions` |
| **Depends on** | step 15 |
| **Effort** | 8 days |

---

## Milestone M6 — the example mod and the proof (steps 17–18)

### Step 17 — `UniversalExampleMod` with one payload

| | |
|---|---|
| **Files** | The complete structure from chapter 35.2, initially only `versions/mc-1.21.4`: `Platform1214(+Factory)`, `Registries1214`, `Networking1214` (+`ByteSink/Source1214`, `ClientNet1214`), `PlayerRef1214`, `OreGenService1214`, `HudRenderService1214`, mixins, datagen; `common`: `ExampleMod`, `ExampleModClient`, `RubyLogic`, `RubyBehavior`, `RubyContent`, `ExampleNetworking`, `ExampleCommands`, `ExampleEvents`, `ExampleConfig`, `api/ExampleModApi`, resources, icon |
| **Tests** | `RubyLogicTest`, `ExampleModInitTest` (FakeModContext), `ExampleConfigTest`; `runClient1214` and `runServer1214` verified manually; `integrationTestMc1214` automated |
| **DoD** | Item, block, command, networking (both directions), events and one mixin work in the real game; the universal JAR with one payload validates; the integration test is green |
| **Depends on** | M5 |
| **Effort** | 5 days |

### Step 18 — the second and third versions, the full matrix

| | |
|---|---|
| **Files** | `versions/mc-1.21.1` and `versions/mc-1.20.1` (created with `addMinecraftVersion --copy-from=mc1214`, then adjusted); a version-specific resource (`models/item/ruby.json` for 1.20.1); `shared.accesswidener` + payload-specific AWs; the `allowOverride` declaration |
| **Tests** | Integration tests mc1201/mc1211/mc1214 plus `unsupported`, `oldFabricApi`, `wrongJava`, `lenient`; client smoke tests mc1201 and mc1214 |
| **DoD** | **The central acceptance criterion:** *one* file `universal-example-mod-1.0.0-universal.jar` starts on 1.20.1/Java 17, 1.21.1/Java 21 and 1.21.4/Java 21, activates the correct payload in each case (evidenced in the log), and on 1.19.2 produces the message from chapter 29.2 without a `NoClassDefFoundError` |
| **Depends on** | step 17 |
| **Effort** | 6 days |

---

## Milestone M7 — shipping (steps 19–21)

### Step 19 — documentation

| | |
|---|---|
| **Files** | All 33 pages from chapter 38.1 including `internals/` and `adr/`; `mkdocs.yml`; the `docs-snippets` source set with all Java examples from the docs; `.github/workflows/docs.yml` |
| **Tests** | `ErrorCodeDocumentationTest`, `docs-snippets:compileJava`, a link check (`lychee`), `AdrIndexTest` (every decision referenced in the ADRs exists) |
| **DoD** | The site builds and deploys; every error code has an anchor; all documentation code examples compile against the published API |
| **Depends on** | M6 |
| **Effort** | 6 days |

### Step 20 — the template repository

| | |
|---|---|
| **Files** | `fabricmultiloader-template`: the wrapper, `settings.gradle.kts`, `build.gradle.kts`, `gradle/fabricmultiloader.toml` (3 versions), `gradle/libs.versions.toml`, `common` (one item, one command, one event, one test), `versions/mc-*` (three adapters, one mixin each), `.github/workflows/build.yml`, `README.md` (a fill-in guide), a `LICENSE` placeholder, `.gitignore`, `renovate.json` (matrix updates), `bootstrap.sh`/`bootstrap.ps1` (asks for the mod ID/name/package and renames everything) |
| **Tests** | A CI job `template-smoke`: clone the template, run `bootstrap` with test values, build, validate and boot a server |
| **DoD** | `git clone` → `./bootstrap.sh` → `./gradlew buildUniversalJar` in under 10 minutes on a fresh machine; GitHub's “Use this template” works |
| **Depends on** | step 19 |
| **Effort** | 3 days |

### Step 21 — release 1.0.0

| | |
|---|---|
| **Files** | `release.yml`, `japicmp` baselines, `CHANGELOG.md` for 1.0.0, the Maven repository setup (`maven.fabricmultiloader.dev` or Maven Central), the Gradle Plugin Portal entry |
| **Tests** | The full release pipeline as a dry run; `conformance.yml` green; an installation test of the published artifacts in a fresh project (not from the composite build) |
| **DoD** | Artifacts publicly resolvable; the template references only published versions; the example mod on Modrinth (a test project) as **one** file with four game version tags |
| **Depends on** | step 20 |
| **Effort** | 2 days |

---

## Summary and critical path

| Milestone | Steps | Effort |
|---|---|---|
| M0 scaffold | 1 | 1 day |
| M1 `format` | 2–5 | 11 days |
| M2 `api` | 6 | 4 days |
| M3 `runtime` | 7–9 | 12 days |
| M4 `testing` + the conformance **gate** | 10–11 | 7 days |
| M5 Gradle plugin | 12–16 | 30 days |
| M6 example mod + acceptance | 17–18 | 11 days |
| M7 docs, template, release | 19–21 | 11 days |
| **Total** | | **≈ 87 person-days** (≈ 4.5 months for one person at 50 % availability) |

**Critical path:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 10 → **11 (gate)** → 12 → 13 → 14 → 15 → 17 → 18 → 19 → 21.

**Parallelisable:** step 6 (`api`) and step 9 (the `runtime` adapters) can run alongside the remaining M1 work;
step 15 (the validator, 34 independent rules) and step 19 (documentation) distribute well across several
contributors.

**Earliest usable intermediate state:** after step 14 a buildable universal JAR exists; after step 18 the project's
core claim is proven. An alpha release makes sense after step 18, a beta after step 19.

**Ordering rationale at the two non-obvious points:**

1. **`format` before everything else**, because the runtime and the Gradle plugin use the same code — that is the
   only way build-time and runtime decisions cannot diverge. Starting with the plugin would inevitably produce a
   second, deviating implementation of the version algebra.
2. **The conformance gate (11) before the Gradle plugin (12–16)**, because the load-bearing assumption is verifiable
   there for roughly 7 days of effort, whereas refuting it after 30 days of plugin work would overturn the entire
   packaging strategy.

---

Continue with [the answers to the 25 hard technical questions](part-13-hard-questions.md).
