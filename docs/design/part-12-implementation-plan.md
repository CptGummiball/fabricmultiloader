# 44. Implementierungsplan

Reihenfolge ist bindend: Jeder Schritt baut nur auf bereits fertigen Schritten auf. „Definition of Done“ (DoD)
ist immer maschinell prüfbar. Aufwandsangaben sind Erfahrungswerte für einen erfahrenen Java-/Gradle-Entwickler.

## Meilenstein M0 — Repository-Gerüst (Schritt 1)

### Schritt 1 — Repo, Build, CI-Skelett

| | |
|---|---|
| **Dateien** | `settings.gradle.kts` (includes: `format`, `api`, `runtime`, `processor`, `gradle-plugin`, `testing`, `example`), Root-`build.gradle.kts` (Konventions-Plugins: `java-library`, `--release`-Setzung, `maven-publish`, `japicmp`), `gradle/libs.versions.toml`, `gradle.properties` (`org.gradle.parallel=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`), Wrapper (Gradle 8.12), `LICENSE` (aktuell proprietär, „All Rights Reserved"; Umstellung auf Apache-2.0 vor der ersten produktiven Nutzung durch Dritte — zwingend, weil die Runtime per Jar-in-Jar in fremde Mod-JARs eingebettet wird und das Weiterverbreitung ist), `NOTICE`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`, `.editorconfig`, `.gitattributes` (`* text=auto eol=lf`), `.github/workflows/build.yml` (nur Kompilieren + Tests) |
| **Konventionen** | `format`/`api`/`runtime`/`processor`: `options.release = 8`, Checkstyle-Regel „kein `var`, keine Records, kein `sealed`“; `gradle-plugin`/`testing`: `release = 17` |
| **Tests** | ein Smoke-Test pro Modul, damit `check` überall greift |
| **DoD** | `./gradlew build` grün; `./gradlew build --configuration-cache` beim zweiten Lauf „reused“; CI grün; `japicmp` aktiv (noch ohne Baseline) |
| **Abhängigkeiten** | — |
| **Aufwand** | 1 Tag |

---

## Meilenstein M1 — `format` (Schritte 2–5)

Alles hier ist reine, minecraftfreie Logik und vollständig unit-testbar. M1 ist die Grundlage für Runtime **und**
Gradle-Plugin; ein Fehler hier propagiert überall hin, deshalb steht es zuerst und mit der höchsten
Testabdeckung (Ziel: 95 % Zeilen).

### Schritt 2 — JSON-Parser und Fehlerinfrastruktur

| | |
|---|---|
| **Klassen** | `format.json`: `Json`, `JsonValue`, `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull`, `JsonReader`, `JsonWriter`, `JsonPointer`, `JsonLimits`, `JsonFormatException`<br>`format.error`: `ErrorCode` (Enum mit `id()`, `title()`, `severity()`), `OmniException`, `OmniApiMisuseException`, `Messages` (Textbausteine, eine Methode pro Meldung) |
| **Tests** | `JsonParserTest` (RFC-8259-Suite + 40 Fehlerfälle mit erwarteter Zeile/Spalte), `JsonWriterRoundTripTest`, `JsonLimitsTest` (Größe/Tiefe/Anzahl/Stringlänge ⇒ `OMNI-3003`), `JsonPointerTest`, `ErrorCodeUniquenessTest` (keine doppelten IDs, IDs im richtigen Bereich) |
| **DoD** | Parser besteht die Suite; jede Fehlermeldung enthält Zeile, Spalte und Quellzeile mit Caret; Writer-Ausgabe ist bytestabil |
| **Aufwand** | 2 Tage |

### Schritt 3 — Versionsalgebra

| | |
|---|---|
| **Klassen** | `format.version`: `SemVer`, `SemVerParser`, `VersionPredicate` (+ `Comparison`, `AnyPredicate`, `AndPredicate`), `VersionPredicateParser`, `Interval`, `VersionRange`, `JavaVersions`, `MinecraftVersions` (`ordinal()`, `normalize()`) |
| **Tests** | `SemVerParseTest` (parametrisiert über die vollständige Tabelle aus Kapitel 12.2), `SemVerCompareTest` (inkl. Prerelease-Ordnung, Build-Neutralität, `UNKNOWN`), `VersionPredicateParseTest`, `VersionRangeAlgebraTest` (union/intersect/subtract; Eigenschaftstests: Idempotenz, Kommutativität von union/intersect, `a \ a = ∅`, `(a ∪ b) \ b ⊆ a`), `UnionNormalizationTest`, `JavaVersionsTest`, `MinecraftOrdinalTest` (1.16.5, 1.20.1, 1.21.4, 26.1, 26.10, 27.0 — Monotonie) |
| **DoD** | 500 generierte Zufallsintervalle erfüllen alle Algebra-Eigenschaften; `toPredicates()` ist round-trip-stabil |
| **Abhängigkeiten** | Schritt 2 |
| **Aufwand** | 3 Tage |

### Schritt 4 — Manifest-Modell und Parser

| | |
|---|---|
| **Klassen** | `format.manifest`: `ContainerManifest`, `ContainerInfo`, `RuntimeRef`, `EntrypointSet`, `PayloadDescriptor`, `Requirements`, `EnvironmentConstraint`, `MappingsInfo`, `MixinConfigRef`, `DiagnosticsInfo`, `ManifestReader`, `ManifestWriter`, `PayloadDescriptorReader/Writer`, `SafePaths` (Pfadvalidierung aus Kapitel 39.2), `Identifiers` (Mod-ID-/FQCN-Validierung) |
| **Tests** | `ManifestReaderTest` (Pflichtfelder, Typfehler mit Pointer, unbekannte Felder, `minRuntime`, `schemaVersion`), `ManifestRoundTripTest` (bytegleich, kanonische Schlüsselreihenfolge), `SafePathsTest` (25 Angriffsmuster: absolut, `..`, Backslash, NUL, doppelter Slash, außerhalb erlaubter Wurzeln), `IdentifiersTest` |
| **DoD** | Golden-File des Beispielmanifests aus Kapitel 11.2 wird gelesen, geschrieben und ist bytegleich; alle Angriffsmuster abgelehnt |
| **Abhängigkeiten** | Schritte 2–3 |
| **Aufwand** | 2 Tage |

### Schritt 5 — Resolver und Disjunktheit

| | |
|---|---|
| **Klassen** | `format.payload`: `PayloadResolver`, `PayloadMatcher`, `MatchResult`, `Rejection`, `Constraint` (Enum), `Domain`, `DomainCell`, `DomainDisjunctifier`, `ResolutionReport`<br>`format.hash`: `Sha256` |
| **Tests** | `PayloadMatcherTest` (jede Constraint-Art einzeln + kombiniert; Vollständigkeit der Rejection-Liste), `DomainDisjunctifierTest` (30 Szenarien: catch-all + spezifisch, Java-Varianten, client/server, vollständige Verdeckung ⇒ `OMNI-1015`, leere Restmenge), `ResolutionReportTest` (Golden-File der beiden Berichte aus Kapitel 29.2), `Sha256Test` |
| **DoD** | Für 200 generierte Payload-Mengen gilt nach Disjunktifizierung: paarweise Schnitt leer **und** Union unverändert; Berichte entsprechen dem Golden-File zeichengenau |
| **Abhängigkeiten** | Schritte 2–4 |
| **Aufwand** | 4 Tage |

---

## Meilenstein M2 — `api` (Schritt 6)

### Schritt 6 — Die vollständige Entwickler-SPI

| | |
|---|---|
| **Klassen** | `api`: `UniversalMod`, `UniversalClientMod`, `UniversalServerMod`, `UniversalPreLaunch`, `UniversalEntrypoint` (Annotation), `ModContext`, `Side`, `Id`, `ModLogger`, `LifecyclePhase`, `Capability`, `Capabilities`, `ServiceRegistry`, `FabricMultiLoader`, `ImplementedByMod`/`ImplementedByFramework` (Annotationen)<br>`api.platform`: `Platform`, `PlatformFactory`, `PlatformInfo`, `AbstractPlatform`, `PreLaunchContext`, `CrashContext`<br>`api.registry`: `Registries`, `ItemSpec`, `BlockSpec`, `SoundSpec`, `ItemGroupSpec`, `ItemHandle`, `BlockHandle`, `SoundHandle`, `ItemGroupHandle`, `Rarity`, `ItemBehavior`, `UseContext`, `BlockUseContext`, `UseResult`, `Hand`<br>`api.net`: `Networking`, `ChannelSpec`, `ChannelHandle`, `PayloadCodec`, `ByteSink`, `ByteSource`, `C2SReceiver`, `S2CReceiver`, `TypedPayloadApi`<br>`api.command`: `Commands`, `CommandSpec`, `Arg`, `CommandInvocation`, `CommandSender`, `Permission`<br>`api.event`: `Events`, `Subscription`, `EventKey`, `ServerRef`, `BlockBreakHandler`, `ItemUseHandler`<br>`api.ref`: `Unwrappable`, `PlayerRef`, `WorldRef`, `ItemStackRef`, `BlockPosRef`<br>`api.text`: `Text`, `TextColor`, `TextStyle`, `ClickAction`, `HoverAction`<br>`api.config`: `ConfigHandle`, `ConfigCodec`<br>`api.resource`: `ResourceReloadListener`, `PackHandle` |
| **Tests** | `ApiSurfaceTest` (jede öffentliche Klasse trägt `@ImplementedByMod` oder `@ImplementedByFramework`; alle `@ImplementedByMod`-Interfaces haben ausschließlich abstrakte Methoden, die für 1.0 vorgesehen sind), `Java8ComplianceTest` (Classfile-Major aller `api`-Klassen == 52), `SpecBuilderTest` (Builder-Validierung, Unveränderlichkeit, `equals`/`hashCode`), `TextModelTest` |
| **DoD** | `api` kompiliert mit `--release 8`; `japicmp`-Baseline gesetzt; Javadoc ohne Doclint-Fehler; alle Beispielsignaturen aus Kapitel 18/19/27/28 kompilieren gegen diesen Stand |
| **Abhängigkeiten** | M1 |
| **Aufwand** | 4 Tage |

---

## Meilenstein M3 — `runtime` (Schritte 7–9)

### Schritt 7 — Bootstrap, Environment, Container-Discovery

| | |
|---|---|
| **Klassen** | `runtime.log`: `Log`, `Slf4jBridge`, `Formatter`<br>`runtime.env`: `EnvironmentDetector`, `Environment`<br>`runtime.boot`: `RuntimeBootstrap`, `RuntimeRegistry`, `ContainerRuntime`, `LifecycleStateMachine`, `IntegrityChecker`<br>`runtime.diag`: `DiagnosticReport`, `ReportWriter`, `DebugDump`<br>`runtime.entrypoint`: `ContainerPreLaunch`<br>Ressource: `src/main/resources/fabric.mod.json` (Mod `fabricmultiloader`, `depends` fabricloader ≥0.14.0, java ≥8) |
| **Tests** | `EnvironmentDetectorTest`, `ContainerDiscoveryTest`, `PayloadActivationTest` (1/0/n Payloads), `LifecycleStateMachineTest`, `IntegrityCheckerTest`, `DiagnosticReportTest` (Golden-File), `LogBridgeTest` (zwei Classpath-Varianten: mit/ohne SLF4J), `AtomicReportWriteTest` (Temp+Move, nicht schreibbares Verzeichnis bricht nicht ab) |
| **DoD** | Alle Tests grün gegen `FakeFabricLoader`; Classfile-Major aller Runtime-Klassen == 52; kein Import aus `net.fabricmc.loader.impl` (ArchUnit-artiger Bytecode-Test `ForbiddenReferencesTest`) |
| **Abhängigkeiten** | M1, M2 |
| **Aufwand** | 4 Tage |

### Schritt 8 — Payload-Aktivierung, Context, Lifecycle-Entrypoints

| | |
|---|---|
| **Klassen** | `runtime.payload`: `PlatformLoader`, `PayloadActivation`<br>`runtime.context`: `ModContextImpl`, `ServiceRegistryImpl`, `CapabilityResolver`, `ModLoggerImpl`, `PreLaunchContextImpl`, `CrashContextImpl`<br>`runtime.entrypoint`: `PayloadPreLaunch`, `PayloadMain`, `PayloadClient`, `PayloadServer`<br>`runtime.boot`: `CommonBootstrap` (instanziiert die Entrypoint-Klassen des Modcodes), `DevFallback` |
| **Tests** | `PlatformLoaderTest` (fehlende Klasse, falscher Typ, `null`, werfende Factory ⇒ `OMNI-2020…2023`; FQCN außerhalb der Payload-Packages ⇒ `OMNI-2024`), `CommonBootstrapTest`, `ModContextImplTest`, `ServiceRegistryImplTest` (Registrierung nur in der erlaubten Phase), `CapabilityResolverTest`, `DevFallbackTest`, `EntrypointOrderTest` (Container vor Payload, idempotent bei Doppelaufruf) |
| **DoD** | Vollständiger Lifecycle mit einer Fake-Platform durchlaufbar; alle 4xxx-Fehlerpfade getestet |
| **Abhängigkeiten** | Schritt 7 |
| **Aufwand** | 4 Tage |

### Schritt 9 — Versionsstabile Adapter und Mixin-Plugin

| | |
|---|---|
| **Klassen** | `runtime.adapter`: `CommandsImpl` (Brigadier über `CommandRegistrationCallback`), `EventsImpl` (stabile Fabric-API-Events), `TextConverter`, `FeedbackAdapter` (Capability-basiert)<br>`runtime.mixin`: `ConditionalMixinPlugin`, `ConfigLocator`, `PluginLog`, `Condition` |
| **Tests** | `ConditionalMixinPluginTest` (Bedingungsmatrix, fail-open, Debug-Ausgabe), `MixinPluginIsolationTest` (Bytecode-Test: Plugin referenziert nur JDK/`format`/`FabricLoader`-API ⇒ `OMNI-1035`), `CommandSpecTranslationTest` (gegen ein Brigadier-Fake), `EventsImplTest` (gegen Fabric-API-Fakes) |
| **DoD** | `CommandsImpl`/`EventsImpl` kompilieren gegen Fabric API 0.92.2 (1.20.1) **und** 0.114.0 (1.21.4) — geprüft durch zwei zusätzliche `compileOnly`-Sourcesets im Modul `runtime`; Isolation des Mixin-Plugins bytecodegeprüft |
| **Abhängigkeiten** | Schritt 8 |
| **Aufwand** | 4 Tage |

> Anmerkung zur Architektur: `runtime` deklariert Fabric API ausschließlich `compileOnly` und in **zwei**
> Varianten (ältestes und neuestes Matrix-Ziel), um sicherzustellen, dass die „versionsstabilen“ Adapter
> tatsächlich stabil sind. Schlägt einer der beiden Kompiliervorgänge fehl, gehört die Funktionalität nicht in
> die Runtime, sondern in die Payloads.

---

## Meilenstein M4 — `testing` und Loader-Conformance (Schritte 10–11)

### Schritt 10 — Test-Harness

| | |
|---|---|
| **Klassen** | `testing`: `FakeFabricLoader`, `FakeModContext` (+ `RecordedRegistrations`), `FakePlatform`, `FakeComponents`, `ManifestBuilder`, `PayloadJarBuilder`, `ContainerJarBuilder`, `JarFixtures`, `GoldenFiles` |
| **Tests** | `FakeModContextTest` (zeichnet Items, Blöcke, Kanäle, Commands, Events korrekt auf), `JarBuilderTest` (erzeugte Fixtures sind valide ZIPs mit erwarteter Struktur) |
| **DoD** | Ein 20-zeiliger Unit-Test kann Common-Code gegen drei simulierte MC-Versionen prüfen (Beispiel aus Kapitel 32.7 läuft) |
| **Abhängigkeiten** | M2, M3 |
| **Aufwand** | 3 Tage |

### Schritt 11 — Loader-Conformance-Harness

| | |
|---|---|
| **Klassen/Dateien** | `testing`: `LoaderConformanceHarness`, `LoaderVersion`, `SyntheticContainer`, `ResolutionProbe`; `conformanceTest`-Sourceset mit den 8 Testfällen aus Kapitel 32.4; `.github/workflows/conformance.yml` |
| **Tests** | `NestedUnsatisfiableIsDropped`, `ExactlyOneSelected`, `ProvidesExclusivity`, `BreaksExclusivity`, `JavaDependencyEvaluated`, `EnvironmentEvaluated`, `RuntimeDeduplication`, `ContainerRangeError` — jeweils gegen Loader 0.14.21, 0.15.11, 0.16.9, 0.16.14, 0.17.x |
| **DoD** | Alle 8 Fälle × 5 Loader-Versionen grün; CI-Workflow erstellt bei Fehlschlag automatisch ein Issue; `docs/internals/loader-assumption.md` verfasst |
| **Abhängigkeiten** | Schritt 10 |
| **Aufwand** | 4 Tage |

> **Dieser Schritt ist ein Gate.** Schlägt er fehl, ist die Architektur widerlegt und der Rückfallpfad
> (Slim-Jars als Primärprodukt) muss vor allen weiteren Schritten bewertet werden. Deshalb steht er **vor** dem
> Gradle-Plugin und nicht danach.

---

## Meilenstein M5 — Gradle-Plugin (Schritte 12–16)

### Schritt 12 — Plugin-Gerüst, Matrix, DSL

| | |
|---|---|
| **Klassen** | `gradle`: `SettingsPlugin`, `CommonPlugin`, `VersionPlugin`, `UniversalPlugin`<br>`gradle.matrix`: `MatrixParser` (eigener TOML-Parser oder `tomlj`; Entscheidung: **`tomlj`**, weil das Plugin auf Java 17 läuft und keine Java-8-Beschränkung hat), `MatrixModel`, `VersionEntry`, `MatrixValueSource`, `MatrixValidator`<br>`gradle.dsl`: alle Extensions und Specs aus Kapitel 21.5 |
| **Dateien** | `src/main/resources/META-INF/gradle-plugins/*.properties` (4 Plugin-IDs) |
| **Tests** | `MatrixParserTest` (Pflichtfelder, unbekannte Schlüssel ⇒ `OMNI-1161`, `minecraft ∉ minecraftRange` ⇒ `OMNI-1160`), `SettingsPluginTest` (TestKit: Auto-Include, verwaiste Verzeichnisse ⇒ `OMNI-1163`), `DslDefaultsTest` |
| **DoD** | Ein Minimalprojekt (1 Payload) synct und zeigt alle erwarteten Tasks; `--configuration-cache` ohne Probleme |
| **Abhängigkeiten** | M1 (Plugin nutzt `format` direkt) |
| **Aufwand** | 4 Tage |

### Schritt 13 — Version-Modul-Pipeline

| | |
|---|---|
| **Klassen** | `gradle.task`: `MergeAccessWidenerTask`, `MergeResourcesTask` (+ `ResourceMergePlan`, `LangMerger`, `MergeReportWriter`), `GeneratePayloadModJsonTask`, `GeneratePayloadDescriptorTask`, `PayloadJarTask`, `ValidatePayloadTask`<br>`gradle.loom`: `LoomConfigurator` (Loom-API-Aufrufe gekapselt, damit Loom-Updates an einer Stelle nachgezogen werden), `OmniDependencyHandlers` (`omniMod`, `omniOptionalMod`, `omniInclude`, `omniIncludeCommon`) |
| **Tests** | `AccessWidenerMergeTest` (Golden-File, Dedup, Sortierung, Header-Prüfung), `ResourceMergePlanTest`, `LangMergerTest`, `ModJsonGeneratorTest` (Golden-File der Payload-`fabric.mod.json` aus Kapitel 11.9), `PayloadDescriptorGeneratorTest`, `PayloadJarTaskTest`, TestKit: `SingleVersionBuildTest` |
| **DoD** | `:versions:mc-1.21.4:omniPayload` erzeugt ein Payload, dessen Struktur exakt Kapitel 10.4 entspricht; `validatePayload` grün; Task ist `UP-TO-DATE`-fähig und cachebar |
| **Abhängigkeiten** | Schritt 12 |
| **Aufwand** | 6 Tage |

### Schritt 14 — Container-Assemblierung

| | |
|---|---|
| **Klassen** | `gradle.task`: `GenerateOmniManifestTask`, `GenerateContainerModJsonTask`, `AssembleUniversalJarTask`, `OmniReportTask`, `BuildSlimJarsTask`<br>`gradle.jar`: `DeterministicZipWriter`, `ClassfileScanner`, `ResourceDigest` |
| **Tests** | `ManifestGeneratorTest` (Golden-File von Kapitel 11.2 aus synthetischen Payloads), `ContainerModJsonGeneratorTest` (Union-Ranges, `depends.java` = Minimum), `DeterministicZipWriterTest` (Reihenfolge, Zeitstempel, STORED für Nested-Jars), `ClassfileScannerTest`, TestKit: `ThreeVersionProjectTest`, `MixedJavaProjectTest` (17/21/25), `ReproducibilityTest`, `SlimJarTest` |
| **DoD** | `./gradlew buildUniversalJar` erzeugt eine JAR, deren Struktur Kapitel 10.2 entspricht; zwei Builds sind SHA-256-identisch; Slim-Jars sind lauffähige Einzelversionsmods |
| **Abhängigkeiten** | Schritt 13 |
| **Aufwand** | 5 Tage |

### Schritt 15 — Validator

| | |
|---|---|
| **Klassen** | `gradle.validate`: `Rule`, `RuleContext`, `RuleSet` (34 Regelimplementierungen als je eine Klasse), `ReferenceScanner` (Konstantenpool-Scan), `ReachabilityAnalyzer` (transitive Referenzen für Regel 26), `MixinConfigChecker`, `AccessWidenerChecker`, `MappingsLookup` (liest Looms Tiny-Mappings für `OMNI-1121`), `ReportFormatter`, `ValidationResult`<br>`gradle.task`: `ValidateUniversalJarTask`, `ValidateExternalJarTask` |
| **Tests** | Pro Regel ein Positiv- und ein Negativtest (68 Tests) auf synthetischen JARs aus `testing`; `ReportFormatterTest` (Golden-File der Ausgabe aus Kapitel 31.3); `RuleIgnoreTest` (nicht abschaltbare Regeln ⇒ `OMNI-1003`) |
| **DoD** | Alle 34 Regeln implementiert und beidseitig getestet; Bericht entspricht dem Golden-File; Laufzeit < 3 s für eine 5-MiB-JAR |
| **Abhängigkeiten** | Schritt 14 |
| **Aufwand** | 7 Tage |

### Schritt 16 — Runs, Scaffolding, Integrationstests, Publishing

| | |
|---|---|
| **Klassen** | `gradle.task`: `AddMinecraftVersionTask`, `RemoveMinecraftVersionTask`, `UniversalRunTask`, `ServerBootTestTask`, `ClientSmokeTestTask`, `VerifyReproducibleTask`<br>`gradle.scaffold`: `PackageRenamer` (AST-freies, aber zuverlässiges Umschreiben von `package`/`import`/FQCN über Tokenscan), `TemplateWriter`, `CiMatrixPatcher`<br>`gradle.itest`: `FabricServerInstaller`, `ScenarioRunner`, `LogAssertions`<br>`gradle.publish`: `ModrinthPublisher`, `CurseForgePublisher`, `GithubReleasePublisher`, `GameVersionExpander` (Range → konkrete Versionsliste über die Plattform-APIs)<br>`testing`: `omni-itest-probe` (kleine Fabric-Mod, pro Matrixversion gebaut) |
| **Tests** | `AddVersionTaskTest` (Matrix, Verzeichnis, Package-Rename, danach grüner Build), `RemoveVersionTaskTest` (Baseline-Anhebung wird gemeldet), `PackageRenamerTest` (30 Fälle inkl. Strings, Kommentare, `package-info`), `GameVersionExpanderTest` (gegen aufgezeichnete API-Antworten), `ScenarioRunnerTest`, echte Integrationstests der Beispielmod |
| **DoD** | Die 7 Szenarien aus Kapitel 32.5 laufen lokal und in CI; `addMinecraftVersion` erzeugt ein sofort baubares Modul; `publishUniversal --dry-run` zeigt korrekte `game_versions` |
| **Abhängigkeiten** | Schritt 15 |
| **Aufwand** | 8 Tage |

---

## Meilenstein M6 — Beispielmod und Beweisführung (Schritte 17–18)

### Schritt 17 — `UniversalExampleMod` mit einem Payload

| | |
|---|---|
| **Dateien** | Vollständige Struktur aus Kapitel 35.2, zunächst nur `versions/mc-1.21.4`: `Platform1214(+Factory)`, `Registries1214`, `Networking1214` (+`ByteSink/Source1214`, `ClientNet1214`), `PlayerRef1214`, `OreGenService1214`, `HudRenderService1214`, Mixins, Datagen; `common`: `ExampleMod`, `ExampleModClient`, `RubyLogic`, `RubyBehavior`, `RubyContent`, `ExampleNetworking`, `ExampleCommands`, `ExampleEvents`, `ExampleConfig`, `api/ExampleModApi`, Ressourcen, Icon |
| **Tests** | `RubyLogicTest`, `ExampleModInitTest` (FakeModContext), `ExampleConfigTest`; `runClient1214` und `runServer1214` manuell verifiziert; `integrationTestMc1214` automatisiert |
| **DoD** | Item, Block, Command, Networking (beide Richtungen), Events und ein Mixin funktionieren im echten Spiel; Universal-JAR mit einem Payload validiert; Integrationstest grün |
| **Abhängigkeiten** | M5 |
| **Aufwand** | 5 Tage |

### Schritt 18 — Zweite und dritte Version, volle Matrix

| | |
|---|---|
| **Dateien** | `versions/mc-1.21.1` und `versions/mc-1.20.1` (über `addMinecraftVersion --copy-from=mc1214` erzeugt, dann angepasst); versionsspezifische Ressource (`models/item/ruby.json` für 1.20.1); `shared.accesswidener` + payloadspezifische AW; `allowOverride`-Deklaration |
| **Tests** | Integrationstests mc1201/mc1211/mc1214 + `unsupported`, `oldFabricApi`, `wrongJava`, `lenient`; Client-Smoke mc1201 + mc1214 |
| **DoD** | **Die zentrale Abnahme:** *Eine* Datei `universal-example-mod-1.0.0-universal.jar` startet auf 1.20.1/Java 17, 1.21.1/Java 21 und 1.21.4/Java 21, aktiviert jeweils das richtige Payload (im Log nachgewiesen), und liefert auf 1.19.2 die Meldung aus Kapitel 29.2 ohne `NoClassDefFoundError` |
| **Abhängigkeiten** | Schritt 17 |
| **Aufwand** | 6 Tage |

---

## Meilenstein M7 — Auslieferung (Schritte 19–21)

### Schritt 19 — Dokumentation

| | |
|---|---|
| **Dateien** | Alle 33 Seiten aus Kapitel 38.1 inklusive `internals/` und `adr/`; `mkdocs.yml`; `docs-snippets`-Sourceset mit allen Java-Beispielen der Doku; `.github/workflows/docs.yml` |
| **Tests** | `ErrorCodeDocumentationTest`, `docs-snippets:compileJava`, Linkcheck (`lychee`), `AdrIndexTest` (jede in ADRs referenzierte Entscheidung existiert) |
| **DoD** | Site baut und deployt; jeder Fehlercode hat einen Anker; alle Doku-Codebeispiele kompilieren gegen die veröffentlichte API |
| **Abhängigkeiten** | M6 |
| **Aufwand** | 6 Tage |

### Schritt 20 — Template-Repository

| | |
|---|---|
| **Dateien** | `fabricmultiloader-template`: Wrapper, `settings.gradle.kts`, `build.gradle.kts`, `gradle/fabricmultiloader.toml` (3 Versionen), `gradle/libs.versions.toml`, `common` (ein Item, ein Command, ein Event, ein Test), `versions/mc-*` (drei Adapter, je ein Mixin), `.github/workflows/build.yml`, `README.md` (Ausfüllanleitung), `LICENSE`-Platzhalter, `.gitignore`, `renovate.json` (Matrix-Updates), `bootstrap.sh`/`bootstrap.ps1` (fragt Mod-ID/Name/Package ab und benennt alles um) |
| **Tests** | CI-Job `template-smoke`: klont das Template, führt `bootstrap` mit Testwerten aus, baut, validiert und bootet einen Server |
| **DoD** | `git clone` → `./bootstrap.sh` → `./gradlew buildUniversalJar` in < 10 Minuten auf einem frischen Rechner; GitHub-„Use this template“ funktioniert |
| **Abhängigkeiten** | Schritt 19 |
| **Aufwand** | 3 Tage |

### Schritt 21 — Release 1.0.0

| | |
|---|---|
| **Dateien** | `release.yml`, `japicmp`-Baselines, `CHANGELOG.md` 1.0.0, Maven-Repository-Setup (`maven.fabricmultiloader.dev` oder Maven Central), Gradle-Plugin-Portal-Eintrag |
| **Tests** | Vollständige Release-Pipeline im Dry-Run; `conformance.yml` grün; Installationstest der veröffentlichten Artefakte in einem frischen Projekt (nicht aus dem Composite-Build) |
| **DoD** | Artefakte öffentlich auflösbar; Template referenziert nur veröffentlichte Versionen; Beispielmod auf Modrinth (Testprojekt) als **eine** Datei mit vier Game-Version-Tags |
| **Abhängigkeiten** | Schritt 20 |
| **Aufwand** | 2 Tage |

---

## Zusammenfassung und kritischer Pfad

| Meilenstein | Schritte | Aufwand |
|---|---|---|
| M0 Gerüst | 1 | 1 Tag |
| M1 `format` | 2–5 | 11 Tage |
| M2 `api` | 6 | 4 Tage |
| M3 `runtime` | 7–9 | 12 Tage |
| M4 `testing` + Conformance-**Gate** | 10–11 | 7 Tage |
| M5 Gradle-Plugin | 12–16 | 30 Tage |
| M6 Beispielmod + Abnahme | 17–18 | 11 Tage |
| M7 Doku, Template, Release | 19–21 | 11 Tage |
| **Summe** | | **≈ 87 Personentage** (≈ 4,5 Monate bei einer Person mit 50 % Verfügbarkeit) |

**Kritischer Pfad:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 10 → **11 (Gate)** → 12 → 13 → 14 → 15 → 17 → 18 → 19 → 21.

**Parallelisierbar:** Schritt 6 (`api`) und Schritt 9 (`runtime`-Adapter) können neben M1-Restarbeiten laufen;
Schritt 15 (Validator, 34 unabhängige Regeln) und Schritt 19 (Doku) sind gut auf mehrere Beitragende
verteilbar.

**Frühester nutzbarer Zwischenstand:** Nach Schritt 14 existiert eine baubare Universal-JAR; nach Schritt 18
ist die Kernbehauptung des Projekts bewiesen. Ein Alpha-Release ist nach Schritt 18 sinnvoll, ein Beta nach
Schritt 19.

**Reihenfolge-Begründung an den zwei nichtoffensichtlichen Stellen:**

1. **`format` vor allem anderen**, weil Runtime und Gradle-Plugin denselben Code benutzen — nur so können
   Build-Zeit- und Laufzeitentscheidungen nicht divergieren. Würde man mit dem Plugin beginnen, entstünde
   zwangsläufig eine zweite, abweichende Implementierung der Versionsalgebra.
2. **Conformance-Gate (11) vor dem Gradle-Plugin (12–16)**, weil die tragende Annahme dort mit ~7 Tagen Aufwand
   verifizierbar ist, während ihre Widerlegung nach 30 Tagen Plugin-Arbeit die gesamte Verpackungsstrategie
   umwerfen würde.

---

Weiter mit [Antworten auf die 25 harten technischen Fragen](part-13-hard-questions.md).
