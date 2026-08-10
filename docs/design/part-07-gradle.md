# 20. Gradle Plugin

## 20.1 Vier Plugins, ein Artefakt

Artefakt: `dev.fabricmultiloader:fabricmultiloader-gradle:1.0.0` (auch über das Gradle Plugin Portal).

| Plugin-ID | Ziel | Aufgabe |
|---|---|---|
| `dev.fabricmultiloader.settings` | `settings.gradle.kts` | Repositories, Auto-Include der Module aus der Matrix, Konsistenzprüfung der Verzeichnisse |
| `dev.fabricmultiloader.common` | `:common` | Toolchain + `--release baseline`, API-Dependency, Annotation Processor, Verbot von MC-Dependencies |
| `dev.fabricmultiloader.version` | `:versions:mc-*` | Loom anwenden und konfigurieren, Dependencies aus der Matrix, Metadaten generieren, Ressourcen mergen, AW mergen, Payload bauen |
| `dev.fabricmultiloader.universal` | Root-Projekt | DSL, Manifest-Generierung, Assembler, Validator, Slim-Jars, Run-Aliase, Integrationstests, Publishing |

## 20.2 Warum keine Cross-Project-Konfiguration

Ein einziges Root-Plugin, das `subprojects { apply(loom) … }` ausführt, wäre bequem, aber:

* **Gradle Project Isolation** (ab Gradle 8.x als Opt-in, künftig Default) verbietet, dass ein Projekt das
  Modell eines anderen Projekts liest oder verändert. Cross-Project-Konfiguration würde den Build
  zukunftsunfähig machen.
* **Configuration Cache**: Cross-Project-Zugriffe erzeugen Invalidierungen bei jeder Änderung in einem
  beliebigen Modul.
* **IDE-Import**: IntelliJ importiert Module unabhängig; ein Modul, das erst durch Root-Konfiguration
  vollständig wird, führt zu unvollständigen Klassenpfaden bis zum zweiten Sync.

Deshalb: Jedes Modul wendet sein Plugin selbst an (3–5 Zeilen) und liest die gemeinsame Konfiguration aus
`gradle/fabricmultiloader.toml`. Die Matrix-Datei ist die einzige geteilte Wahrheit, und sie ist eine
**Datei**, kein Gradle-Modell — damit isolationssicher.

## 20.3 Die Matrix-Datei `gradle/fabricmultiloader.toml`

```toml
# ============================================================================
#  FabricMultiLoader — Versionsmatrix
#  Einzige Wahrheitsquelle. Wird von allen vier Plugins gelesen.
#  Änderungen hier wirken auf Build, IDE, Metadaten, Validator, CI und Release.
# ============================================================================

[format]
version = 1                       # Schemaversion dieser Datei

[mod]
id            = "examplemod"
version       = "2.0.0"
name          = "Universal Example Mod"
description   = "Ein Beispiel für FabricMultiLoader."
license       = "MIT"
authors       = ["Example Author"]
group         = "com.example"
basePackage   = "com.example"
commonPackage = "com.example.common"
icon          = "omni/icon.png"

[mod.contact]
homepage = "https://example.github.io/examplemod/"
sources  = "https://github.com/example/examplemod"
issues   = "https://github.com/example/examplemod/issues"

[container]
baselineJava     = 17            # kleinstes Java der Matrix; Ziel-Bytecode von :common
commonPackaging  = "shared"      # shared | embedded
payloadAlias     = "examplemod-impl"
strict           = true
verifyIntegrity  = true
fabricApiMode    = "AGGREGATE"   # AGGREGATE | MODULES

[framework]
runtime = "1.0.0"                # dev.fabricmultiloader:fabricmultiloader-runtime
api     = "1.0.0"
loom    = "1.9.2"

# ---------------------------------------------------------------------------
#  Payloads. Reihenfolge irrelevant; 'priority' steuert Range-Subtraktion.
# ---------------------------------------------------------------------------

[versions.mc1201]
minecraft     = "1.20.1"
minecraftRange= ">=1.20.1 <1.20.2"
mappings      = "yarn:1.20.1+build.10"
loader        = "0.14.21"
loaderRange   = ">=0.14.21"
fabricApi     = "0.92.2+1.20.1"
fabricApiRange= ">=0.92.2"
java          = 17
javaRange     = ">=17"
environment   = "*"
priority      = 0
snapshots     = false
capabilities  = ["registries", "commands", "networking.v1", "events.lifecycle", "tags"]

[versions.mc1201.dependencies]
clothConfig      = "11.1.118"
clothConfigRange = ">=11.0.0 <12.0.0"
modmenu          = "7.2.2"

[versions.mc1211]
minecraft     = "1.21.1"
minecraftRange= ">=1.21 <1.21.2"
mappings      = "yarn:1.21.1+build.3"
loader        = "0.15.11"
loaderRange   = ">=0.15.11"
fabricApi     = "0.102.0+1.21.1"
fabricApiRange= ">=0.102.0"
java          = 21
javaRange     = ">=21"
environment   = "*"
priority      = 0
snapshots     = false
capabilities  = ["registries", "commands", "networking.v1", "networking.typed", "events.lifecycle", "tags", "components"]

[versions.mc1211.dependencies]
clothConfig      = "15.0.128"
clothConfigRange = ">=15.0.0 <16.0.0"
modmenu          = "11.0.3"

[versions.mc1214]
minecraft     = "1.21.4"
minecraftRange= ">=1.21.4 <1.21.5"
mappings      = "yarn:1.21.4+build.8"
loader        = "0.16.9"
loaderRange   = ">=0.16.9"
fabricApi     = "0.114.0+1.21.4"
fabricApiRange= ">=0.114.0"
java          = 21
javaRange     = ">=21"
environment   = "*"
priority      = 0
snapshots     = false
capabilities  = ["registries", "commands", "networking.v1", "networking.typed", "events.lifecycle", "tags", "components"]

[versions.mc1214.dependencies]
clothConfig      = "15.0.140"
clothConfigRange = ">=15.0.0 <16.0.0"
modmenu          = "13.0.0"

# Vorbereitet für das neue Mojang-Versionsschema mit Java 25:
# [versions.mc261]
# minecraft      = "26.1"
# minecraftRange = ">=26.1 <26.2"
# mappings       = "yarn:26.1+build.1"
# loader         = "0.17.0"
# loaderRange    = ">=0.17.0"
# fabricApi      = "0.130.0+26.1"
# fabricApiRange = ">=0.130.0"
# java           = 25
# javaRange      = ">=25"
# environment    = "*"
# priority       = 0
# capabilities   = ["registries", "commands", "networking.v1", "networking.typed",
#                   "events.lifecycle", "tags", "components"]

[publish.modrinth]
projectId  = "AbCdEfGh"
loaders    = ["fabric"]
dependencies = [ { projectId = "P7dR8mSH", type = "required" } ]   # Fabric API

[publish.curseforge]
projectId = "123456"
```

**Feldsemantik (Auszug der nichtoffensichtlichen Felder):**

| Feld | Bedeutung |
|---|---|
| `minecraft` | Exakte Version, gegen die Loom kompiliert (der „Build-Ziel-Punkt“). |
| `minecraftRange` | Bereich, in dem das Payload zur Laufzeit akzeptiert wird. Muss `minecraft` enthalten (`OMNI-1160`). Getrennte Felder, weil man gegen 1.21.1 baut, aber 1.21–1.21.1 unterstützt. |
| `snapshots` | `true` erweitert `minecraftRange` um Prerelease-Untergrenzen (`>=1.21.4-` statt `>=1.21.4`). |
| `capabilities` | Wird in Manifest und Payload-Deskriptor übernommen und validiert. |
| `[versions.X.dependencies]` | Freie Schlüssel; `<name>` = konkrete Version für den Compile/Dev-Run, `<name>Range` = Laufzeit-`depends`-Bereich. Ohne `Range` wird die Abhängigkeit nur `compileOnly`/`runtimeOnly` verwendet und **nicht** in `depends` geschrieben. |
| `fabricApiMode` | `AGGREGATE` ⇒ `depends: {"fabric-api": …}`; `MODULES` ⇒ pro genutztem Fabric-API-Modul ein `depends`-Eintrag (für Mods, die nur Einzelmodule brauchen). |

Der TOML-Parser des Plugins verlangt **alle** Pflichtfelder und lehnt unbekannte Schlüssel ab (`OMNI-1161`) —
Tippfehler wie `minecraftRnage` werden damit sofort zum Build-Fehler statt zu stiller Fehlkonfiguration.

## 20.4 Tasks — vollständige Liste

### Im Root-Projekt (`dev.fabricmultiloader.universal`)

| Task | Typ | Inputs | Outputs | Zweck |
|---|---|---|---|---|
| `generateOmniManifest` | `GenerateOmniManifestTask` | Matrix, DSL, Payload-Jars + deren `omni/payload.json`, `:common` `omni/entrypoints.json` | `build/omni/META-INF/omni-container.json` | Manifest inkl. Hashes, Größen, Classfile-Majors |
| `generateContainerModJson` | `GenerateContainerModJsonTask` | Matrix, DSL, Manifest | `build/omni/fabric.mod.json` | Container-`fabric.mod.json` mit Union-Ranges |
| `collectPayloads` | `Sync` | `:versions:*` `omniPayload`-Outputs | `build/omni/jars/` | Sammelt Payload-Jars deterministisch |
| `resolveFrameworkRuntime` | `Copy` | Konfiguration `omniRuntime` | `build/omni/jars/fabricmultiloader-runtime-<v>.jar` | Holt die Runtime-Mod aus Maven |
| `assembleUniversalJar` | `AssembleUniversalJarTask` (`Zip`-basiert) | alles obige + `:common:jar` + Icon + LICENSE | `build/libs/<mod>-<ver>-universal.jar` | Der Container |
| `buildUniversalJar` | `DefaultTask` (Aggregat) | — | — | `assembleUniversalJar` + `validateUniversalJar` |
| `validateUniversalJar` | `ValidateUniversalJarTask` | Universal-JAR | `build/reports/omni/validation.txt`, `.json`, Exit-Code | 34 Regeln (Kapitel 31) |
| `buildSlimJars` | `BuildSlimJarsTask` | Payloads, `:common:jar`, Manifest | `build/libs/slim/<mod>-<ver>+<mc>.jar` | Optionale Einzelversions-Artefakte |
| `omniReport` | `OmniReportTask` | Manifest | `build/reports/omni/matrix.md` + Konsole | Menschenlesbare Matrixübersicht für Release Notes |
| `addMinecraftVersion` | `AddMinecraftVersionTask` | CLI-Optionen | Matrix-Eintrag, Verzeichnis, `build.gradle.kts`, Quell-Stubs | Scaffolding (Kapitel 37) |
| `integrationTest` | `DefaultTask` (Aggregat) | — | — | alle `integrationTest<Payload>` |
| `integrationTest<PayloadId>` | `ServerBootTestTask` | Universal-JAR, Matrix | `build/reports/omni/itest-<id>.log` | Echter Serverstart (Kapitel 32.4) |
| `runClient<PayloadId>` / `runServer<PayloadId>` / `runDatagen<PayloadId>` | `DefaultTask` (Alias) | — | — | delegiert an `:versions:mc-X:runClient` etc. |
| `runUniversalServer<PayloadId>` | `UniversalRunTask` | Universal-JAR | Server-Instanz in `run/universal-<id>/` | Startet den echten Container interaktiv |
| `publishUniversal` | `DefaultTask` (Aggregat) | — | — | Modrinth + CurseForge + GitHub Release |

### In `:versions:mc-*` (`dev.fabricmultiloader.version`)

| Task | Typ | Zweck |
|---|---|---|
| `generatePayloadModJson` | `GeneratePayloadModJsonTask` | Payload-`fabric.mod.json` mit `depends`, `provides`, `breaks`, `mixins`, `accessWidener` |
| `generatePayloadDescriptor` | `GeneratePayloadDescriptorTask` | `omni/payload.json` |
| `mergeAccessWidener` | `MergeAccessWidenerTask` | `shared.accesswidener` ⊕ payload-AW (Kapitel 17.3) |
| `mergePayloadResources` | `MergeResourcesTask` | common ⊕ shared ⊕ version ⊕ datagen (Kapitel 25.3) |
| `processResources` | `ProcessResources` (Loom-Standard) | Platzhalter-Expansion |
| `remapJar` | Loom | named → intermediary |
| `omniPayload` | `PayloadJarTask` (`Zip`) | Nimmt `remapJar`-Output, injiziert generierte Metadaten und gemergte Ressourcen, entfernt verbotene Einträge |
| `validatePayload` | `ValidatePayloadTask` | Payload-lokale Regeln (Mixins, AW, Classfiles, Packages) — schnelle Rückmeldung ohne Universal-Build |
| `runClient`, `runServer`, `runDatagen` | Loom | Dev-Runs mit passendem `javaLauncher` |

### In `:common` (`dev.fabricmultiloader.common`)

| Task | Zweck |
|---|---|
| `compileJava` | mit `--release <baselineJava>` und aktiviertem Annotation Processor |
| `jar` | Common-Klassen + `omni/entrypoints.json` |
| `validateCommon` | Bytecode-Scan: keine MC-/Fabric-API-/Mixin-Referenzen, keine Klassen außerhalb `commonPackage` |
| `test` | Reine JVM-Unit-Tests |
| `apiJar` | Gefiltertes Artefakt `<mod>-api` für Drittmods (Kapitel 24.7) |

## 20.5 Task-Graph

```
:common:compileJava ─► :common:jar ──────────────────────────────────┐
        │                                                            │
        └─► :common:validateCommon                                   │
                                                                     │
:versions:mc-1.20.1:mergeAccessWidener ─┐                            │
:versions:mc-1.20.1:compileJava ────────┼─► remapJar ─┐              │
:versions:mc-1.20.1:mergePayloadResources ─────────────┤              │
:versions:mc-1.20.1:generatePayloadModJson ────────────┼─► omniPayload ─┐
:versions:mc-1.20.1:generatePayloadDescriptor ─────────┘        │      │
                                                     validatePayload   │
   … analog mc-1.21.1, mc-1.21.4 …                                     │
                                                                       ▼
                                            collectPayloads ─► generateOmniManifest
                                            resolveFrameworkRuntime ──┤
                                                                       ▼
                                                     generateContainerModJson
                                                                       ▼
                                                        assembleUniversalJar
                                                                       ▼
                                                        validateUniversalJar
                                                                       ▼
                                                            buildUniversalJar
```

Alle Tasks deklarieren Inputs/Outputs vollständig, sind `@CacheableTask` (außer den Run- und Aggregat-Tasks) und
Configuration-Cache-kompatibel: Kein `Project`-Zugriff in Task-Actions, alle Werte über `Property<T>`/
`ListProperty<T>`/`ConfigurableFileCollection`, Matrix-Lesen in einer `ValueSource`.

## 20.6 Loom-Integration

Das Version-Plugin konfiguriert Loom vollständig:

```kotlin
// vereinfachter Auszug aus dev.fabricmultiloader.version (Plugin-Implementierung, Kotlin)
val entry = Matrix.load(project).version(payloadId)

project.pluginManager.apply("fabric-loom")

project.extensions.configure<LoomGradleExtensionAPI> {
    accessWidenerPath.set(project.layout.buildDirectory.file("omni/accesswidener/$awName"))
    mixin { defaultRefmapName.set("${mod.id}-$payloadId-refmap.json") }
    runs {
        named("client") { ideConfigGenerated(true); runDir = "../../run/$payloadId-client" }
        named("server") { ideConfigGenerated(true); runDir = "../../run/$payloadId-server" }
        create("datagen") {
            server()
            name  = "Data Generation ($payloadId)"
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${project.file("src/main/generated")}")
            vmArg("-Dfabric-api.datagen.modid=${mod.id}")
            runDir = "../../run/$payloadId-datagen"
            ideConfigGenerated(true)
        }
    }
}

project.dependencies {
    add("minecraft", "com.mojang:minecraft:${entry.minecraft}")
    add("mappings", entry.mappingsNotation(project))        // yarn / mojang / layered
    add("modImplementation", "net.fabricmc:fabric-loader:${entry.loader}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${entry.fabricApi}")
    add("modImplementation", "dev.fabricmultiloader:fabricmultiloader-runtime:${framework.runtime}")
    add("implementation", project.project(":common"))       // Compile + Dev-Runtime, NICHT im Payload-Jar
    add("compileOnly", "dev.fabricmultiloader:fabricmultiloader-api:${framework.api}")
}
```

Drei Feinheiten, die den Unterschied machen:

1. **`implementation(project(":common"))`** ist korrekt und nicht `compileOnly`: Gradles `jar`-Task packt nur die
   eigenen Klassen des Moduls, also landet Common **nicht** im Payload — aber es ist im Dev-Run-Classpath
   vorhanden. Genau das gewünschte Verhalten, ohne Sonderkonstruktion.
2. **`modImplementation` der Runtime**: Sie ist eine Fabric-Mod, muss also im Dev-Run als Mod geladen werden.
   Loom remappt sie (No-Op, weil keine MC-Referenzen).
3. **`runDir` außerhalb des Moduls** (`../../run/<payloadId>-client`): Alle Run-Verzeichnisse liegen zentral
   unter `run/`, was Aufräumen, `.gitignore` und Vergleiche zwischen Versionen erleichtert.

## 20.7 IDE-Unterstützung (IntelliJ IDEA)

| Anforderung | Umsetzung |
|---|---|
| Ein IDE-Modul pro MC-Version mit eigener MC-Abhängigkeit | Ergibt sich aus dem Gradle-Multi-Project; Loom liefert pro Modul die dekompilierten Sourcen der passenden MC-Version. |
| Run Configurations pro Version | `ideConfigGenerated(true)` erzeugt „Minecraft Client (mc1201)“, „Minecraft Server (mc1201)“, „Data Generation (mc1201)“ usw. |
| Richtiges JDK pro Modul | Toolchain je Modul (`17` für mc1201, `21` für mc1211/mc1214, `25` für mc261). IntelliJ übernimmt die Toolchain beim Gradle-Sync. |
| Debugging | Normale Loom-Runs; Breakpoints in `:common` und im Version-Modul funktionieren gleichzeitig, weil Common als Projekt-Dependency (nicht als Jar) eingebunden ist. |
| Debugging der **Universal-JAR** | `runUniversalServer<PayloadId>` startet einen echten Server mit `-agentlib:jdwp=…,address=5005,suspend=n`; das Plugin generiert zusätzlich eine „Attach to Universal (mc1214)“-Remote-Run-Config über `idea`-XML in `.idea/runConfigurations/`. |
| Keine „generated sources“-Fallen | Es gibt **keine** synchronisierten Quellbäume. Generiert werden ausschließlich Metadaten (JSON) und gemergte Ressourcen — nie Java-Quellcode, der bearbeitet werden könnte. Das ist eine bewusste Entscheidung gegen Source-Merging (Kapitel 24.8). |
| Datagen-Output als Ressourcenverzeichnis | `src/main/generated` wird als Ressourcen-SrcDir registriert und in IntelliJ als „generated“ markiert. |

Zusätzlich generiert `dev.fabricmultiloader.universal` eine `.idea/runConfigurations/`-Gruppe „FabricMultiLoader“
mit `buildUniversalJar`, `validateUniversalJar` und `integrationTest` als Gradle-Run-Configs, damit die drei
wichtigsten Aktionen ohne Terminal erreichbar sind.

---

# 21. Gradle DSL

## 21.1 `settings.gradle.kts` (vollständig)

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")            { name = "Fabric" }
        maven("https://maven.fabricmultiloader.dev/")   { name = "FabricMultiLoader" }
    }
}

plugins {
    id("dev.fabricmultiloader.settings") version "1.0.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// Liest gradle/fabricmultiloader.toml und ruft für jede [versions.X] ein
// include(":versions:mc-<minecraft>") auf; include(":common") immer.
// Prüft, dass jedes Verzeichnis existiert und keine verwaisten Verzeichnisse
// vorhanden sind (OMNI-1162 / OMNI-1163).
fabricMultiLoaderSettings {
    // Optionale Overrides; im Normalfall leer:
    // matrixFile.set(file("gradle/fabricmultiloader.toml"))
    // versionProjectPath.set("versions")
}

rootProject.name = "universal-example-mod"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")          { name = "Fabric" }
        maven("https://maven.terraformersmc.com/")    { name = "TerraformersMC" }   // ModMenu
        maven("https://maven.shedaniel.me/")          { name = "Shedaniel" }        // Cloth Config
        maven("https://maven.fabricmultiloader.dev/") { name = "FabricMultiLoader" }
    }
}
```

## 21.2 Root-`build.gradle.kts` (vollständig)

```kotlin
plugins {
    id("dev.fabricmultiloader.universal")
}

fabricMultiLoader {

    // --- Modidentität: überschreibt/ergänzt [mod] aus der Matrix -----------
    mod {
        // id / version / name / description / license / authors / contact
        // kommen aus gradle/fabricmultiloader.toml und müssen hier nicht wiederholt werden.

        // Entrypoints: entweder hier oder per @UniversalEntrypoint (Annotation Processor).
        entrypoint("com.example.common.ExampleMod")
        clientEntrypoint("com.example.common.ExampleModClient")
        // serverEntrypoint("com.example.common.ExampleModServer")

        // Zusätzliche Loader-Metadaten für den Container:
        conflicts("examplemod-legacy", "*")
        breaks("brokenmod", "<1.5.0")
        custom("modmenu", mapOf("links" to mapOf("modmenu.discord" to "https://discord.gg/example")))
    }

    // --- Container-Optionen ------------------------------------------------
    container {
        commonPackaging.set(CommonPackaging.SHARED)      // oder EMBEDDED
        strict.set(true)
        verifyIntegrity.set(true)
        archiveClassifier.set("universal")               // -> examplemod-2.0.0-universal.jar
        includeLicense.set(true)
        reproducible.set(true)
    }

    // --- Ressourcen --------------------------------------------------------
    resources {
        strictOverrides.set(true)     // undeklarierte Overrides sind Fehler
        mergeLanguageFiles.set(true)  // assets/*/lang/*.json key-weise mergen
        // erlaubte Overrides explizit deklarieren (Kapitel 25.4):
        allowOverride("assets/examplemod/lang/en_us.json")
        allowOverride("assets/examplemod/models/item/ruby.json")
    }

    // --- Validierung -------------------------------------------------------
    validation {
        failOnWarnings.set(false)
        // Einzelregeln abschaltbar, mit Pflichtbegründung im Build-Log:
        // ignore("OMNI-1121", because = "AW-Ziel existiert nur in 1.21.4, gewollt")
    }

    // --- Slim-Jars (optional) ---------------------------------------------
    slimJars {
        enabled.set(false)
    }

    // --- Integrationstests -------------------------------------------------
    integrationTests {
        enabled.set(true)
        ticks.set(200)                       // Server läuft 200 Ticks, dann /stop
        acceptEula.set(true)                 // schreibt eula=true in die Testinstanz
        timeout.set(java.time.Duration.ofMinutes(6))
        extraMods("net.fabricmc.fabric-api:fabric-api")   // pro Payload passende Version aus der Matrix
    }

    // --- Distribution ------------------------------------------------------
    publishing {
        modrinth {
            enabled.set(true)
            token.set(providers.environmentVariable("MODRINTH_TOKEN"))
            // gameVersions/loaders/dependencies werden aus der Matrix abgeleitet
        }
        curseforge {
            enabled.set(true)
            token.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
        }
        githubRelease {
            enabled.set(true)
            repository.set("example/examplemod")
            token.set(providers.environmentVariable("GITHUB_TOKEN"))
        }
        maven {
            publishApiArtifact.set(true)     // com.example:examplemod-api:2.0.0
        }
    }
}
```

## 21.3 `common/build.gradle.kts` (vollständig)

```kotlin
plugins {
    id("dev.fabricmultiloader.common")
}

// Toolchain, --release, api-Dependency und Annotation Processor setzt das Plugin
// aus [container].baselineJava und [framework].api.

dependencies {
    // Reine JVM-Bibliotheken sind erlaubt, wenn sie in JEDER unterstützten
    // Umgebung funktionieren. Sie werden vom Assembler NICHT eingebettet;
    // dafür ist 'omniInclude' zuständig (Kapitel 24.5).
    // implementation("org.jetbrains:annotations:26.0.1")   // compileOnly-artig, nicht nötig zur Laufzeit

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("dev.fabricmultiloader:fabricmultiloader-testing:1.0.0")
}

tasks.test { useJUnitPlatform() }
```

## 21.4 `versions/mc-1.21.4/build.gradle.kts` (vollständig)

```kotlin
plugins {
    id("dev.fabricmultiloader.version")
}

fabricMultiLoaderVersion {
    payloadId.set("mc1214")            // Schlüssel in [versions.*]; Default aus dem Verzeichnisnamen ableitbar

    // Optionale, payload-lokale Feineinstellungen:
    clientOnlyPackages.add("com.example.mc1214.client")
    // allowForeignAccessWidener("cloth-config")
    // overrideCapability("commands", implementedByPayload = true)
}

dependencies {
    // Versionsspezifische Mod-Abhängigkeiten. Versionen kommen aus
    // [versions.mc1214.dependencies]; 'omniMod' schreibt zusätzlich den
    // passenden depends-Eintrag in die Payload-fabric.mod.json.
    omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    // Optionale Integration: kein depends, nur compileOnly + Dev-Runtime.
    omniOptionalMod("com.terraformersmc:modmenu", key = "modmenu")

    // Bibliothek, die IN das Payload eingebettet werden soll (JiJ im Payload):
    // omniInclude("com.example.libs:mylib:1.2.3")
}
```

## 21.5 DSL-Referenz (Auszug mit Typen)

```kotlin
interface FabricMultiLoaderExtension {
    val matrix: Provider<Matrix>                       // read-only Sicht auf die TOML
    fun mod(action: Action<ModSpec>)
    fun container(action: Action<ContainerSpec>)
    fun resources(action: Action<ResourceSpec>)
    fun validation(action: Action<ValidationSpec>)
    fun slimJars(action: Action<SlimJarSpec>)
    fun integrationTests(action: Action<IntegrationTestSpec>)
    fun publishing(action: Action<PublishingSpec>)
}

interface ModSpec {
    val id: Property<String>; val version: Property<String>; val name: Property<String>
    val description: Property<String>; val license: Property<String>
    val authors: ListProperty<String>; val contact: MapProperty<String, String>
    fun entrypoint(fqcn: String); fun clientEntrypoint(fqcn: String)
    fun serverEntrypoint(fqcn: String); fun preLaunchEntrypoint(fqcn: String)
    fun conflicts(modId: String, range: String); fun breaks(modId: String, range: String)
    fun custom(key: String, value: Any)
}

interface ContainerSpec {
    val commonPackaging: Property<CommonPackaging>     // SHARED | EMBEDDED
    val strict: Property<Boolean>
    val verifyIntegrity: Property<Boolean>
    val archiveClassifier: Property<String>
    val includeLicense: Property<Boolean>
    val reproducible: Property<Boolean>
    val extraFiles: ConfigurableFileCollection
}

interface FabricMultiLoaderVersionExtension {
    val payloadId: Property<String>
    val clientOnlyPackages: ListProperty<String>
    fun allowForeignAccessWidener(modId: String)
    fun overrideCapability(id: String, implementedByPayload: Boolean)
}

// Dependency-DSL-Erweiterungen im Version-Modul
fun DependencyHandler.omniMod(coordinateWithoutVersion: String, key: String,
                              configure: ExternalModuleDependency.() -> Unit = {})
fun DependencyHandler.omniOptionalMod(coordinateWithoutVersion: String, key: String,
                              configure: ExternalModuleDependency.() -> Unit = {})
fun DependencyHandler.omniInclude(coordinate: String)
```

Die vollständige, generierte DSL-Referenz entsteht aus KDoc/Javadoc der Extension-Interfaces und wird als
`docs/gradle-plugin.md` sowie als HTML publiziert (Kapitel 38.3).

---

# 22. Repository Structure

## 22.1 Framework-Repository `fabricmultiloader/fabricmultiloader`

```
fabricmultiloader/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradlew, gradlew.bat, gradle/wrapper/
├── LICENSE  (aktuell proprietär; Ziel: Apache-2.0, siehe LICENSE Abschnitt 4)
├── NOTICE
├── README.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── CHANGELOG.md
│
├── format/                       dev.fabricmultiloader.format        Java 8, 0 Abhängigkeiten
│   └── src/{main,test}/java/dev/fabricmultiloader/format/
│       ├── json/                 JsonValue, Json, JsonWriter, JsonPointer
│       ├── version/              SemVer, VersionPredicate, VersionRange, Interval, JavaVersions
│       ├── manifest/             ContainerManifest, PayloadDescriptor, Requirements, ManifestReader/Writer
│       ├── payload/              PayloadResolver, PayloadMatcher, MatchResult, Rejection, DomainDisjunctifier
│       ├── error/                ErrorCode, OmniException, Messages
│       └── hash/                 Sha256 (JDK-MessageDigest-Wrapper mit Streaming)
│
├── api/                          dev.fabricmultiloader.api           Java 8
│   └── src/main/java/dev/fabricmultiloader/api/
│       ├── (Root: UniversalMod, ModContext, Id, Side, ModLogger, Capability, …)
│       ├── platform/  registry/  net/  command/  event/  ref/  config/  resource/  text/
│
├── runtime/                      dev.fabricmultiloader.runtime       Java 8, Fabric-Mod
│   ├── src/main/java/dev/fabricmultiloader/runtime/
│   │   ├── entrypoint/           ContainerPreLaunch, PayloadPreLaunch, PayloadMain,
│   │   │                         PayloadClient, PayloadServer
│   │   ├── boot/                 RuntimeBootstrap, RuntimeRegistry, ContainerRuntime,
│   │   │                         LifecycleStateMachine, IntegrityChecker
│   │   ├── env/                  EnvironmentDetector, Environment
│   │   ├── payload/              PlatformLoader, PayloadActivation
│   │   ├── context/              ModContextImpl, ServiceRegistryImpl, CapabilityResolver
│   │   ├── adapter/              CommandsImpl, EventsImpl, TextConverter (versionsstabile Teile)
│   │   ├── diag/                 DiagnosticReport, ReportWriter, DebugDump, CrashContextImpl
│   │   ├── mixin/                ConditionalMixinPlugin, ConfigLocator, PluginLog
│   │   └── log/                  Log, Slf4jBridge, Formatter
│   └── src/main/resources/fabric.mod.json          (mod id: fabricmultiloader)
│
├── processor/                    dev.fabricmultiloader.processor     Java 8
│   └── src/main/java/…/UniversalEntrypointProcessor.java
│       + src/main/resources/META-INF/services/javax.annotation.processing.Processor
│
├── gradle-plugin/                dev.fabricmultiloader.gradle        Java 17 / Kotlin
│   └── src/main/kotlin/dev/fabricmultiloader/gradle/
│       ├── SettingsPlugin.kt  CommonPlugin.kt  VersionPlugin.kt  UniversalPlugin.kt
│       ├── dsl/               Extensions, Specs, CommonPackaging
│       ├── matrix/            MatrixParser (TOML), MatrixModel, MatrixValueSource
│       ├── task/              GenerateOmniManifestTask, GenerateContainerModJsonTask,
│       │                      GeneratePayloadModJsonTask, GeneratePayloadDescriptorTask,
│       │                      MergeAccessWidenerTask, MergeResourcesTask, PayloadJarTask,
│       │                      AssembleUniversalJarTask, ValidateUniversalJarTask,
│       │                      ValidatePayloadTask, BuildSlimJarsTask, ServerBootTestTask,
│       │                      UniversalRunTask, AddMinecraftVersionTask, OmniReportTask
│       ├── validate/          Rule, RuleSet, ClassfileScanner, ReferenceScanner, ReportFormatter
│       └── publish/           ModrinthPublisher, CurseForgePublisher, GithubReleasePublisher
│
├── testing/                      dev.fabricmultiloader.testing       Java 17
│   └── src/main/java/…/          FakeEnvironment, FakeModContext, ManifestBuilder,
│                                 JarFixtures, LoaderConformanceHarness, ServerHarness
│
├── example/                      UniversalExampleMod (Kapitel 35)
├── docs/                         (Kapitel 38)
└── .github/workflows/            (Kapitel 33)
```

## 22.2 Modulabhängigkeiten und Verantwortlichkeiten

| Modul | Verantwortlich für | Hängt ab von | Veröffentlicht als |
|---|---|---|---|
| `format` | Datenmodell, Parser, Versionsalgebra, Fehlercodes, Hashing | — | `dev.fabricmultiloader:fabricmultiloader-format` |
| `api` | Entwickler-SPI | `format` | `…-api` |
| `runtime` | Bootstrap, Lifecycle, Diagnose, versionsstabile Adapter | `format`, `api`, `fabric-loader` (compileOnly) | `…-runtime` (Fabric-Mod-Jar) |
| `processor` | Entrypoint-Ableitung | `format` | `…-processor` |
| `gradle-plugin` | gesamte Build-Toolchain | `format`, Loom (compileOnly) | `dev.fabricmultiloader:fabricmultiloader-gradle` + Plugin-Marker |
| `testing` | Test-Harness für Framework **und** Modprojekte | `format`, `api` | `…-testing` |
| `example` | Referenzimplementierung, wird in CI gebaut, validiert und gebootet | alle | nicht veröffentlicht |

`format` ist bewusst das Herz: Weil derselbe Code im Gradle-Plugin und in der Runtime läuft, können Build-Zeit-
und Laufzeitentscheidungen nicht divergieren.

## 22.3 Modprojekt-Struktur (Referenz)

```
universal-example-mod/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── fabricmultiloader.toml            ← die Matrix
│   ├── libs.versions.toml                ← nur Test-/Build-Bibliotheken
│   └── wrapper/
├── gradlew, gradlew.bat
├── LICENSE, README.md, CHANGELOG.md
│
├── common/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/common/…
│       ├── main/resources/
│       │   ├── assets/examplemod/…       ← gemeinsame Assets
│       │   └── data/examplemod/…         ← gemeinsame Daten
│       ├── main/accesswidener/shared.accesswidener
│       ├── main/omni/icon.png
│       └── test/java/com/example/common/…
│
├── versions/
│   ├── mc-1.20.1/
│   │   ├── build.gradle.kts
│   │   └── src/main/{java,resources,generated}/
│   ├── mc-1.21.1/
│   └── mc-1.21.4/
│
├── run/                                  ← alle Dev-Run-Verzeichnisse (gitignored)
└── .github/workflows/build.yml
```

Es gibt **kein** separates `universal/`-Modul: Der Container wird im Root-Projekt assembliert. Begründung: Der
Container hat keine eigenen Quellen (nur generierte Metadaten + Common-Jar + Payloads), ein eigenes Modul wäre
leerer Overhead und ein zusätzlicher IDE-Eintrag. `./gradlew buildUniversalJar` im Root ist damit der natürliche
Einstieg.

## 22.4 Package-Konventionen im Modprojekt

| Ort | Package | Regel |
|---|---|---|
| `:common` | `<basePackage>.common.**` | keine MC-Referenzen; öffentliche Mod-API unter `<basePackage>.common.api` |
| `:versions:mc-X` | `<basePackage>.<payloadId>.**` | z. B. `com.example.mc1214` |
| Mixins | `<basePackage>.<payloadId>.mixin` / `.client.mixin` | vom Validator erzwungen |
| Client-only | `<basePackage>.<payloadId>.client.**` | in `clientOnlyPackages` deklariert |

Die Package-Disjunktheit ist Validator-geprüft (`OMNI-1044`) und verhindert, dass zwei Payloads denselben FQCN
belegen — wichtig für `commonPackaging = EMBEDDED` und für eindeutige Stacktraces.

---

# 23. Build Pipeline

## 23.1 Vollständige Pipeline

```
① :common
   compileJava (--release 17, APT: UniversalEntrypointProcessor)
        │  → build/classes/java/main
        │  → build/generated/omni/entrypoints.json
   validateCommon        (Bytecode-Scan: keine MC/Fabric-API/Mixin-Referenzen,
        │                 nur commonPackage, Classfile-Major ≤ 61)
   jar                   → common-2.0.0.jar   (Klassen + omni/entrypoints.json)
   apiJar                → examplemod-api-2.0.0.jar (gefiltert auf …common.api)

② :versions:mc-1.21.4        (analog für jede Matrixversion, parallelisierbar)
   mergeAccessWidener    common/shared.accesswidener ⊕ payload-AW  (Namespace named)
        │                → build/omni/accesswidener/examplemod-mc1214.accesswidener
   compileJava (--release 21, Mixin-AP → Refmap)
        │                → build/classes, build/devlibs/…-refmap.json
   processResources      Platzhalter ${version} etc.
   mergePayloadResources common/resources ⊕ shared/resources ⊕ version/resources ⊕ generated
        │                → build/omni/resources/            (+ Konfliktreport)
   generatePayloadModJson    → build/omni/meta/fabric.mod.json
   generatePayloadDescriptor → build/omni/meta/omni/payload.json
   jar                   Klassen + Mixin-Configs + Refmap
   remapJar (Loom)       named → intermediary, remappt auch die AW-Datei
        │                → build/libs/mc-1.21.4-2.0.0.jar
   omniPayload           nimmt remapJar-Output; ersetzt/ergänzt:
        │                  · fabric.mod.json        (generiert)
        │                  · omni/payload.json      (generiert)
        │                  · assets/**, data/**     (gemergt)
        │                entfernt: META-INF/omni-container.json (falls vorhanden),
        │                          leere Verzeichnisse, *.kotlin_module, Signaturen
        │                → build/omni/payload/examplemod-mc1214.jar
   validatePayload       payload-lokale Regeln (schnelles Feedback)

③ Root
   collectPayloads          → build/omni/jars/examplemod-mc*.jar          (Sync, deterministisch)
   resolveFrameworkRuntime  → build/omni/jars/fabricmultiloader-runtime-1.0.0.jar
   generateOmniManifest     liest jedes Payload-Jar:
        │                     · SHA-256, Größe, Classfile-Major (Scan)
        │                     · omni/payload.json (Constraints, Capabilities, Mixins, AW)
        │                     · Ressourcen-Digest
        │                   führt DomainDisjunctifier aus (Range-Subtraktion)
        │                   → build/omni/META-INF/omni-container.json
   generateContainerModJson → build/omni/fabric.mod.json  (Union-Ranges)
   assembleUniversalJar     Zip: MANIFEST.MF, fabric.mod.json, omni-container.json,
        │                        Common-Klassen, omni/icon.png, LICENSE,
        │                        META-INF/jars/* (STORED)
        │                   → build/libs/examplemod-2.0.0-universal.jar
        │                   → build/reports/omni/universal-jar.sha256
   validateUniversalJar     34 Regeln → build/reports/omni/validation.{txt,json}
   buildUniversalJar        Aggregat
```

## 23.2 Schritt-Details

### `mergePayloadResources`

Inputs (in Präzedenzreihenfolge, später gewinnt): `common/src/main/resources`,
`shared/src/main/resources` (falls vorhanden), `versions/mc-X/src/main/resources`,
`versions/mc-X/src/main/generated`. Details in Kapitel 25.

### `omniPayload`

Ein `Zip`-Task, kein `Jar`-Task — bewusst, damit kein `MANIFEST.MF` automatisch entsteht (Payloads brauchen
keines) und die Eintragsreihenfolge vollständig kontrolliert ist.

Entfernungsliste (`excludeFromPayload`): `META-INF/omni-container.json`, `META-INF/*.SF`, `META-INF/*.RSA`,
`META-INF/*.DSA`, `META-INF/INDEX.LIST`, `**/*.kotlin_module` (falls kein Kotlin konfiguriert),
`**/.DS_Store`, `**/Thumbs.db`, leere Verzeichniseinträge.

### `generateOmniManifest`

Liest die fertigen Payload-Jars — nicht die Gradle-Modelle der Version-Projekte. Damit ist der Task
isolationssicher und cachefähig, und das Manifest beschreibt garantiert *das ausgelieferte Artefakt*, nicht
eine Absicht.

Klassifikation der Classfile-Majors erfolgt über einen Stream-Scan: Für jeden `.class`-Eintrag werden die ersten
8 Bytes gelesen (`CAFEBABE`, Minor, Major); abweichende Majors innerhalb eines Payloads führen zu
`OMNI-1041` mit Auflistung.

### `assembleUniversalJar`

```kotlin
abstract class AssembleUniversalJarTask : DefaultTask() {
    @get:InputFile      abstract val containerModJson: RegularFileProperty
    @get:InputFile      abstract val omniManifest: RegularFileProperty
    @get:InputFile      abstract val commonJar: RegularFileProperty
    @get:InputFiles     abstract val nestedJars: ConfigurableFileCollection
    @get:InputFile @get:Optional abstract val icon: RegularFileProperty
    @get:InputFiles     abstract val extraFiles: ConfigurableFileCollection
    @get:Input          abstract val modId: Property<String>
    @get:Input          abstract val modVersion: Property<String>
    @get:Input          abstract val commonPackages: ListProperty<String>
    @get:OutputFile     abstract val outputJar: RegularFileProperty
    @get:OutputFile     abstract val checksumFile: RegularFileProperty

    @TaskAction fun assemble() { /* deterministische ZIP-Erzeugung gemäß Kapitel 10.5 */ }
}
```

Vorgehen: Alle Einträge werden zunächst in einer `TreeMap<String, EntrySource>` gesammelt (damit die Reihenfolge
allein vom Pfad abhängt), Duplikate erzeugen `OMNI-1170` mit beiden Quellen, dann wird sequenziell geschrieben.
Der Common-Jar wird **entpackt** eingebettet (Klassen direkt), nicht als nested Jar — er ist kein Mod.

## 23.3 Fehlerbehandlung im Build

| Situation | Reaktion |
|---|---|
| Ein Version-Modul kompiliert nicht | Der Build schlägt fehl, aber die anderen Module werden weiter gebaut (`--continue`-freundlich, da unabhängige Task-Bäume). Die Fehlermeldung nennt `payloadId` und MC-Version. |
| Eine Matrixversion hat kein Verzeichnis | `OMNI-1162` im Settings-Plugin, mit dem exakten `addMinecraftVersion`-Befehl als Vorschlag |
| Ein Verzeichnis hat keinen Matrixeintrag | `OMNI-1163`, Vorschlag: Eintrag ergänzen oder Verzeichnis löschen |
| Duplikat beim Zusammenbau | `OMNI-1170` mit beiden Quellpfaden |
| Nichtdeterministische Eingabe erkannt (z. B. Zeitstempel in einer generierten Datei) | `OMNI-1060` |

## 23.4 Reproduzierbarkeit — Verifikation im Build

`verifyReproducible` (in CI aktiv, lokal per `-Pomni.verifyReproducible=true`): Baut den Container zweimal in
verschiedene Ausgabeverzeichnisse, vergleicht SHA-256 und schreibt bei Abweichung einen Diff-Report auf
ZIP-Eintragsebene (Pfad, Größe, CRC32). Das fängt Regressionen in Generatoren zuverlässig.

## 23.5 Codegenerierung — was generiert wird und was nicht

| Generiert | Nicht generiert (bewusst) |
|---|---|
| `fabric.mod.json` (Container + Payloads) | Java-Quellcode |
| `META-INF/omni-container.json`, `omni/payload.json` | Mixin-Configs |
| gemergte Access-Widener-Dateien | Modlogik jeglicher Art |
| gemergte Ressourcenbäume | Adapter-Implementierungen (nur Stubs beim Scaffolding) |
| `omni/entrypoints.json` (APT) | Refmaps (das macht Loom/Mixin-AP) |
| Validierungs- und Matrixberichte | |

Grundsatz: **Generiert wird nur, was keine fachliche Entscheidung enthält.** Alles, was ein Entwickler lesen,
verstehen und bewusst ändern muss, bleibt handgeschrieben. Damit gibt es keine „generierten Quellen, die man
nicht bearbeiten darf“-Falle.

---

# 24. Dependency Management

## 24.1 Die vier Dependency-Klassen

| Klasse | Ort | Gradle-Konfiguration | Landet in `depends`? | Landet im Artefakt? |
|---|---|---|---|---|
| **Framework** (`api`, `runtime`) | Container/alle Payloads | `api`/`modImplementation` (vom Plugin) | Container: `fabricmultiloader` | Runtime als nested Mod im Container |
| **Common-Bibliothek** (reines JVM, versionsneutral) | `:common` | `implementation` + `omniIncludeCommon` | nein | entpackt oder als nested Jar im Container |
| **Versions-Mod-Abhängigkeit** (Fabric API, Cloth Config …) | `:versions:mc-X` | `omniMod(coord, key)` | ja, im Payload | nein (Nutzer installiert sie) |
| **Eingebettete Versions-Bibliothek** | `:versions:mc-X` | `omniInclude(coord)` | nein | als nested Jar **im Payload** |

## 24.2 `omniMod` — deklarierte Laufzeitabhängigkeit

```kotlin
omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig") {
    exclude(group = "net.fabricmc.fabric-api")
}
```

Wirkung:

1. `modImplementation("me.shedaniel.cloth:cloth-config-fabric:15.0.140")` — Version aus
   `[versions.mc1214.dependencies].clothConfig`.
2. Eintrag `"cloth-config": ">=15.0.0 <16.0.0"` in `depends` der Payload-`fabric.mod.json` — Bereich aus
   `clothConfigRange`.
3. Eintrag in `requires.mods` des Payload-Deskriptors ⇒ erscheint im Diagnosebericht und wird vom
   `PayloadMatcher` erklärt.
4. Der Mod-ID-Wert wird **nicht geraten**: Das Plugin liest die `fabric.mod.json` des aufgelösten Artefakts und
   entnimmt die echte Mod-ID (`OMNI-1180`, falls das Artefakt keine Fabric-Mod ist).

## 24.3 `omniOptionalMod` — optionale Integration

```kotlin
omniOptionalMod("com.terraformersmc:modmenu", key = "modmenu")
```

* `modCompileOnly` + `modLocalRuntime` (Dev-Run hat die Mod, das Payload deklariert sie nicht als `depends`).
* Eintrag in `recommends` bzw. `requires.optionalMods`.
* Zur Laufzeit prüft der Modcode `ctx.isModLoaded("modmenu")`; Integrations-Mixins werden über
  `ConditionalMixinPlugin` gegated (Kapitel 16.6).

## 24.4 Verhinderung inkompatibler Kombinationen

| Risiko | Schutzmechanismus |
|---|---|
| Zwei Payloads bringen unterschiedliche Versionen derselben Bibliothek als nested Jar | Nur ein Payload lädt ⇒ nur eine Version aktiv. Zusätzlich Fabric-JiJ-Dedup, falls ein anderer Mod dieselbe Bibliothek mitbringt. |
| Eine Bibliothek wird versehentlich in den **Container** eingebettet, obwohl sie versionsabhängig ist | `validateCommon` verbietet MC-/Fabric-API-Referenzen im Container; `OMNI-1181` verbietet Fabric-Mods (`fabric.mod.json` im Artefakt) in der `omniIncludeCommon`-Konfiguration. |
| Transitiv hereingezogene Fabric API in falscher Version | Das Plugin setzt für alle `omniMod`-Deklarationen automatisch `exclude(group = "net.fabricmc.fabric-api")` und `exclude(group = "net.fabricmc", module = "fabric-loader")`, sofern nicht explizit anders konfiguriert. `OMNI-1182` warnt, wenn ein Payload doch eine zweite Fabric-API-Version enthält. |
| Common-Code nutzt eine Bibliothek, die nur in neueren MC-Versionen vorhanden ist (z. B. neueres Gson) | `validateCommon` erlaubt in `:common` nur Referenzen auf JDK-`baselineJava`-API, `dev.fabricmultiloader.**`, `net.fabricmc.loader.api.**` und explizit in `omniIncludeCommon` deklarierte Artefakte (`OMNI-1183`). |
| Zwei Universal-Mods mit unterschiedlichen Runtime-Versionen | Fabric-JiJ-Dedup + `depends`-Range (Kapitel 13.4). |
| Kotlin | Wenn `:common` oder ein Version-Modul Kotlin nutzt, verlangt das Plugin `fabric-language-kotlin` als `omniMod` und warnt (`OMNI-1184`), weil dessen Version an die MC-Version gebunden ist. Kotlin ist unterstützt, aber die Kotlin-Runtime darf **nicht** in den Container eingebettet werden. |

## 24.5 Bibliotheken im Container

```kotlin
// common/build.gradle.kts
dependencies {
    omniIncludeCommon("com.example.libs:pure-jvm-lib:1.4.0")   // wird als nested Jar in den Container gelegt
}
```

Voraussetzungen, vom Plugin geprüft: kein `fabric.mod.json` im Artefakt (sonst wäre es eine Mod — dann gehört sie
per `omniMod` ins Payload), Classfile-Major ≤ `baselineJava`, keine Referenzen auf `net.minecraft`.
Eingebettete Common-Bibliotheken werden als **nested Jar** (nicht entpackt) mitgeliefert und dem Loader über
`jars[]` bekannt gemacht — allerdings mit einer generierten Wrapper-`fabric.mod.json`
(`id = "<modid>-lib-<artifact>"`, `custom.modmenu.parent`), weil Fabric nested Jars ohne Metadaten ignoriert.
Alternativ (Default für kleine Bibliotheken < 64 KB): entpackte Einbettung mit Relocation via
`omniRelocate("com.example.libs" to "com.example.common.shaded.libs")`, um FQCN-Kollisionen mit anderen Mods
auszuschließen.

## 24.6 Versionskatalog vs. Matrix

* `gradle/fabricmultiloader.toml` — **alles, was von der Minecraft-Version abhängt.**
* `gradle/libs.versions.toml` — Build- und Testwerkzeuge (JUnit, AssertJ, Mockito), die von MC unabhängig sind.

Diese Trennung ist normativ. Eine MC-abhängige Version im `libs.versions.toml` ist ein häufiger Fehler in
Multi-Version-Setups und wird von `OMNI-1185` gemeldet (Heuristik: Versionsstring enthält `+1.` oder `-mc`).

## 24.7 Publikation der Mod-API

```
:common:apiJar   → examplemod-api-2.0.0.jar   (nur com.example.common.api.**  + Manifest)
                   POM-Dependencies: dev.fabricmultiloader:fabricmultiloader-api:1.0.0 (compile)
```

Drittmods:

```kotlin
dependencies {
    compileOnly("com.example:examplemod-api:2.0.0")
    // kein modImplementation nötig: die Klassen liefert der Container zur Laufzeit
}
```

Zur Laufzeit erhält die Drittmod die Implementierung über
`ExampleModApi.get()` (ObjectShare, Kapitel 19.9) und muss `depends: {"examplemod": ">=2.0.0 <3.0.0"}` oder
`suggests` deklarieren. Weil der Container über alle MC-Versionen dasselbe Kompilat ist, ist **ein**
API-Artefakt für alle MC-Versionen korrekt — der zentrale Vorteil gegenüber der klassischen
Ein-Jar-pro-Version-Veröffentlichung, bei der Drittmods pro MC-Version neu kompilieren müssen.

## 24.8 Geteilter Quellcode (`shared`) — bewusste Begrenzung

Optional aktivierbar:

```toml
[shared]
enabled  = true
srcDir   = "shared/src/main/java"
versions = ["mc1211", "mc1214"]        # nur Versionen mit identischem Mapping-Provider
```

Wirkung: `shared/src/main/java` wird den genannten Version-Modulen als **zusätzliches** `srcDir` hinzugefügt
(kein Kopieren, kein Sync, IDE-tauglich, direkt editierbar). Regeln, vom Validator erzwungen:

* **Kein Shadowing.** Eine Klasse darf nicht gleichzeitig in `shared` und in einem Version-Modul existieren
  (`OMNI-1186`). Divergenz wird über Interfaces (Common) oder eigene Klassen (Version) gelöst, nicht über
  Überschreiben.
* **Gleicher Mapping-Provider** für alle beteiligten Versionen (`OMNI-1081`).
* **Gleicher Java-Release-Level** für alle beteiligten Versionen; sonst müsste `shared` auf das Minimum
  kompilieren, was zu verwirrenden Fehlern führt (`OMNI-1187`).
* `shared`-Code darf MC-Typen benutzen — das ist sein Zweck. Er ist damit **nicht** versionsneutral, sondern
  „für diese Untergruppe von Versionen gültig“.

Ein Source-Preprocessor (`//#if MC>=12100`) wird **nicht** eingebaut. Begründung: Er macht Quellcode für
Contributors, Code-Review, IDE-Refactorings und statische Analyse schlechter; er verlagert Komplexität in eine
zweite, nicht typgeprüfte Sprache; und er löst kein Problem, das `shared` + Adapter nicht ebenso löst. Wer ihn
trotzdem will, kann Stonecutter parallel anwenden — das Plugin verhindert es nicht und dokumentiert die
Kombination in `docs/version-modules.md`.

---

# 25. Resources

## 25.1 Das Konfliktproblem und seine Lösung

Wären gemeinsame Ressourcen im Container und versionsspezifische im Payload, gäbe es **zwei** Resource Packs
derselben Mod (Fabric registriert jede Mod mit `assets/`/`data/` als Pack). Die Präzedenz zwischen zwei
Mod-Packs ist von der Mod-Ladereihenfolge abhängig und damit nicht verlässlich definiert.

**Lösung:** Der Container enthält **keine** `assets/`- und **keine** `data/`-Einträge (Validator `OMNI-1023`).
Alle Ressourcen werden zur Build-Zeit in **jedes** Payload gemergt. Zur Laufzeit existiert damit genau ein
Resource Pack für die Mod. Das kostet Speicherplatz (Faktor = Anzahl Payloads) und ist ein bewusster Tausch von
Größe gegen Determinismus (Nicht-Ziel N8).

Das Mod-Icon liegt deshalb unter `omni/icon.png` — außerhalb von `assets/` — und wird über
`ModContainer#findPath` gelesen, nicht über das Resource-System.

## 25.2 Merge-Reihenfolge

```
1. common/src/main/resources/**                     (niedrigste Präzedenz)
2. shared/src/main/resources/**                     (nur wenn [shared] aktiv und Version beteiligt)
3. versions/mc-X/src/main/resources/**
4. versions/mc-X/src/main/generated/**              (Datagen, höchste Präzedenz)
```

## 25.3 Merge-Regeln je Dateityp

| Muster | Regel |
|---|---|
| Alle Dateien | Bei identischem Pfad gewinnt die höhere Präzedenz. Bei **bytegleichem** Inhalt: still. Bei abweichendem Inhalt: nur erlaubt, wenn der Pfad in `resources { allowOverride(...) }` steht — sonst `OMNI-1200` (bei `strictOverrides = true`) bzw. Warnung. |
| `assets/*/lang/*.json` | Bei `mergeLanguageFiles = true`: **key-weiser Deep-Merge**, höhere Präzedenz gewinnt pro Schlüssel. Ergebnis wird mit sortierten Schlüsseln geschrieben (Reproduzierbarkeit). Kein `allowOverride` nötig. |
| `data/*/tags/**/*.json` | Kein Auto-Merge. Grund: Tags haben `replace`-Semantik und Reihenfolgebedeutung; ein naiver Merge wäre fachlich falsch. Vollständige Überschreibung mit Pflicht-`allowOverride`. Die Doku empfiehlt stattdessen, Tags über Datagen zu erzeugen. |
| `fabric.mod.json` | In `resources` **verboten** (`OMNI-1021`) — wird generiert. |
| `*.mixins.json`, `*.accesswidener`, `*-refmap.json` | Nur in Version-Modulen erlaubt; in `common/src/main/resources` verboten (`OMNI-1201`). |
| `.DS_Store`, `Thumbs.db`, `*.blend`, `*.xcf`, `*.psd` | still verworfen (Ausschlussliste, erweiterbar über `resources { exclude(...) }`) |
| Leere Verzeichnisse | nicht geschrieben |

## 25.4 Konfliktbericht

`build/reports/omni/resource-merge-mc1214.txt`:

```
Resource merge report — payload mc1214 (Minecraft 1.21.4)
Generated by fabricmultiloader-gradle 1.0.0

 1234 files total
 1189 from common/src/main/resources
   43 from versions/mc-1.21.4/src/main/resources
    2 from versions/mc-1.21.4/src/main/generated

OVERRIDES (declared, 2)
  assets/examplemod/lang/en_us.json
      base     common/src/main/resources          (48 keys)
      override versions/mc-1.21.4/.../en_us.json  (3 keys)      → merged, 51 keys
  assets/examplemod/models/item/ruby.json
      base     common/src/main/resources          (sha 1a2b…)
      override versions/mc-1.21.4/.../ruby.json   (sha 9f8e…)   → replaced

IDENTICAL DUPLICATES (silently deduplicated, 6)
  assets/examplemod/textures/item/ruby.png
  …

EXCLUDED (3)
  common/src/main/resources/.DS_Store
  …
```

## 25.5 Datagen

* Ein Datagen-Lauf pro Version-Modul: `./gradlew runDatagen1214`. Output nach
  `versions/mc-1.21.4/src/main/generated`, wird eingecheckt (damit CI ohne Datagen-Lauf baut und Diffs im
  Review sichtbar sind).
* Der Datagen-Provider-Code liegt im Version-Modul (er benutzt versionsspezifische
  `FabricDataGenerator`-APIs), kann aber seine Eingaben aus dem Common-Modul beziehen:

```java
// versions/mc-1.21.4/src/main/java/com/example/mc1214/datagen/ExampleDataGen1214.java
public final class ExampleDataGen1214 implements DataGeneratorEntrypoint {
    @Override public void onInitializeDataGenerator(FabricDataGenerator gen) {
        FabricDataGenerator.Pack pack = gen.createPack();
        pack.addProvider((FabricDataOutput out) -> new RubyRecipeProvider1214(out, RubyContent.RECIPES));
        pack.addProvider((FabricDataOutput out) -> new RubyLootProvider1214(out, RubyContent.DROPS));
    }
}
```

`RubyContent` liegt im Common-Modul und beschreibt Rezepte/Drops in einem neutralen Datenmodell
(`RecipeSpec`, `LootSpec`) — dieselbe Spezifikation, die auch zur Laufzeit für Tooltips verwendet wird. Damit
sind Datagen und Laufzeit garantiert konsistent, und der versionsspezifische Anteil bleibt der reine
Provider-Klebecode.

Der `DataGeneratorEntrypoint` wird in der Payload-`fabric.mod.json` **nur im Dev-Run** registriert: Das Plugin
schreibt ihn in eine separate, dev-only `fabric.mod.json` (`build/omni/meta-dev/`), die im Payload-Artefakt
nicht enthalten ist (`OMNI-1202` prüft, dass Release-Payloads keinen `fabric-datagen`-Entrypoint enthalten).

## 25.6 Shader, Sounds, Modelle

Keine Sonderbehandlung: Sie sind gewöhnliche Ressourcen und folgen den Regeln aus 25.3. Praktisch relevant ist
nur, dass Shader (`assets/<ns>/shaders/**`) und Modell-Formate zwischen MC-Versionen inkompatibel werden können
— dann gehören sie in `versions/mc-X/src/main/resources` und **nicht** in `common`, und der Override-Mechanismus
mit `allowOverride` macht die Abweichung im Review sichtbar.

---

Weiter mit [Kapitel 29–33 — Error Handling, Diagnostics, Validation, Testing, CI/CD](part-08-quality.md).
