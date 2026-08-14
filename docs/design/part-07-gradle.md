# 20. Gradle Plugin

## 20.1 Four plugins, one artifact

Artifact: `dev.fabricmultiloader:fabricmultiloader-gradle:1.0.0` (also via the Gradle Plugin Portal).

| Plugin ID | Applied to | Responsibility |
|---|---|---|
| `dev.fabricmultiloader.settings` | `settings.gradle.kts` | repositories, auto-inclusion of the modules from the matrix, consistency check of the directories |
| `dev.fabricmultiloader.common` | `:common` | toolchain + `--release baseline`, API dependency, annotation processor, ban on MC dependencies |
| `dev.fabricmultiloader.version` | `:versions:mc-*` | apply and configure Loom, dependencies from the matrix, generate metadata, merge resources, merge AW, build the payload |
| `dev.fabricmultiloader.universal` | root project | DSL, manifest generation, assembler, validator, slim JARs, run aliases, integration tests, publishing |

## 20.2 Why no cross-project configuration

A single root plugin doing `subprojects { apply(loom) … }` would be convenient, but:

* **Gradle project isolation** (opt-in from Gradle 8.x, the default in the future) forbids one project from reading
  or modifying another project's model. Cross-project configuration would make the build future-incompatible.
* **Configuration cache**: cross-project access causes invalidation on any change in any module.
* **IDE import**: IntelliJ imports modules independently; a module that only becomes complete through root
  configuration leads to incomplete classpaths until the second sync.

Therefore: every module applies its own plugin (3–5 lines) and reads the shared configuration from
`gradle/fabricmultiloader.toml`. The matrix file is the single shared truth, and it is a **file**, not a Gradle
model — hence isolation-safe.

## 20.3 The matrix file `gradle/fabricmultiloader.toml`

```toml
# ============================================================================
#  FabricMultiLoader — version matrix
#  Single source of truth. Read by all four plugins.
#  Changes here affect build, IDE, metadata, validator, CI and release.
# ============================================================================

[format]
version = 1                       # schema version of this file

[mod]
id            = "examplemod"
version       = "2.0.0"
name          = "Universal Example Mod"
description   = "An example for FabricMultiLoader."
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
baselineJava     = 17            # lowest Java of the matrix; target bytecode of :common
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
#  Payloads. Order is irrelevant; 'priority' drives range subtraction.
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

# Prepared for the new Mojang version scheme with Java 25:
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

**Field semantics (an excerpt of the non-obvious fields):**

| Field | Meaning |
|---|---|
| `minecraft` | The exact version Loom compiles against (the “build target point”). |
| `minecraftRange` | The range in which the payload is accepted at runtime. Must contain `minecraft` (`OMNI-1160`). Separate fields, because one builds against 1.21.1 but supports 1.21–1.21.1. |
| `snapshots` | `true` extends `minecraftRange` with prerelease lower bounds (`>=1.21.4-` instead of `>=1.21.4`). |
| `capabilities` | Carried into the manifest and the payload descriptor and validated. |
| `[versions.X.dependencies]` | Free-form keys; `<name>` = the concrete version for compile/dev run, `<name>Range` = the runtime `depends` range. Without a `Range`, the dependency is used only as `compileOnly`/`runtimeOnly` and is **not** written into `depends`. |
| `fabricApiMode` | `AGGREGATE` ⇒ `depends: {"fabric-api": …}`; `MODULES` ⇒ one `depends` entry per used Fabric API module (for mods that need only individual modules). |

The plugin's TOML parser requires **all** mandatory fields and rejects unknown keys (`OMNI-1161`) — typos like
`minecraftRnage` therefore become an immediate build error instead of a silent misconfiguration.

## 20.4 Tasks — complete list

### In the root project (`dev.fabricmultiloader.universal`)

| Task | Type | Inputs | Outputs | Purpose |
|---|---|---|---|---|
| `generateOmniManifest` | `GenerateOmniManifestTask` | matrix, DSL, payload JARs + their `omni/payload.json`, `:common` `omni/entrypoints.json` | `build/omni/META-INF/omni-container.json` | manifest including hashes, sizes, class file majors |
| `generateContainerModJson` | `GenerateContainerModJsonTask` | matrix, DSL, manifest | `build/omni/fabric.mod.json` | container `fabric.mod.json` with union ranges |
| `collectPayloads` | `Sync` | the `omniPayload` outputs of `:versions:*` | `build/omni/jars/` | collects payload JARs deterministically |
| `resolveFrameworkRuntime` | `Copy` | configuration `omniRuntime` | `build/omni/jars/fabricmultiloader-runtime-<v>.jar` | fetches the runtime mod from Maven |
| `assembleUniversalJar` | `AssembleUniversalJarTask` (`Zip`-based) | all of the above + `:common:jar` + icon + LICENSE | `build/libs/<mod>-<ver>-universal.jar` | the container |
| `buildUniversalJar` | `DefaultTask` (aggregate) | — | — | `assembleUniversalJar` + `validateUniversalJar` |
| `validateUniversalJar` | `ValidateUniversalJarTask` | the universal JAR | `build/reports/omni/validation.txt`, `.json`, exit code | 34 rules (chapter 31) |
| `buildSlimJars` | `BuildSlimJarsTask` | payloads, `:common:jar`, manifest | `build/libs/slim/<mod>-<ver>+<mc>.jar` | optional single-version artifacts |
| `omniReport` | `OmniReportTask` | manifest | `build/reports/omni/matrix.md` + console | human-readable matrix overview for release notes |
| `addMinecraftVersion` | `AddMinecraftVersionTask` | CLI options | matrix entry, directory, `build.gradle.kts`, source stubs | scaffolding (chapter 37) |
| `integrationTest` | `DefaultTask` (aggregate) | — | — | all `integrationTest<Payload>` |
| `integrationTest<PayloadId>` | `ServerBootTestTask` | universal JAR, matrix | `build/reports/omni/itest-<id>.log` | a real server boot (chapter 32.4) |
| `runClient<PayloadId>` / `runServer<PayloadId>` / `runDatagen<PayloadId>` | `DefaultTask` (alias) | — | — | delegates to `:versions:mc-X:runClient` etc. |
| `runUniversalServer<PayloadId>` | `UniversalRunTask` | universal JAR | server instance in `run/universal-<id>/` | starts the real container interactively |
| `publishUniversal` | `DefaultTask` (aggregate) | — | — | Modrinth + CurseForge + GitHub release |

### In `:versions:mc-*` (`dev.fabricmultiloader.version`)

| Task | Type | Purpose |
|---|---|---|
| `generatePayloadModJson` | `GeneratePayloadModJsonTask` | payload `fabric.mod.json` with `depends`, `provides`, `breaks`, `mixins`, `accessWidener` |
| `generatePayloadDescriptor` | `GeneratePayloadDescriptorTask` | `omni/payload.json` |
| `mergeAccessWidener` | `MergeAccessWidenerTask` | `shared.accesswidener` ⊕ payload AW (chapter 17.3) |
| `mergePayloadResources` | `MergeResourcesTask` | common ⊕ shared ⊕ version ⊕ datagen (chapter 25.3) |
| `processResources` | `ProcessResources` (Loom standard) | placeholder expansion |
| `remapJar` | Loom | named → intermediary |
| `omniPayload` | `PayloadJarTask` (`Zip`) | takes the `remapJar` output, injects the generated metadata and merged resources, removes forbidden entries |
| `validatePayload` | `ValidatePayloadTask` | payload-local rules (mixins, AW, class files, packages) — quick feedback without a universal build |
| `runClient`, `runServer`, `runDatagen` | Loom | dev runs with the matching `javaLauncher` |

### In `:common` (`dev.fabricmultiloader.common`)

| Task | Purpose |
|---|---|
| `compileJava` | with `--release <baselineJava>` and the annotation processor enabled |
| `jar` | common classes + `omni/entrypoints.json` |
| `validateCommon` | bytecode scan: no MC/Fabric API/Mixin references, no classes outside `commonPackage` |
| `test` | plain JVM unit tests |
| `apiJar` | the filtered `<mod>-api` artifact for third-party mods (chapter 24.7) |

## 20.5 Task graph

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
   … analogously mc-1.21.1, mc-1.21.4 …                                │
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

All tasks declare inputs/outputs completely, are `@CacheableTask` (except the run and aggregate tasks) and are
configuration-cache-compatible: no `Project` access inside task actions, all values via `Property<T>`/
`ListProperty<T>`/`ConfigurableFileCollection`, matrix reading inside a `ValueSource`.

## 20.6 Loom integration

The version plugin configures Loom completely:

```kotlin
// simplified excerpt from dev.fabricmultiloader.version (plugin implementation, Kotlin)
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
    add("implementation", project.project(":common"))       // compile + dev runtime, NOT in the payload jar
    add("compileOnly", "dev.fabricmultiloader:fabricmultiloader-api:${framework.api}")
}
```

Three subtleties that make the difference:

1. **`implementation(project(":common"))`** is correct rather than `compileOnly`: Gradle's `jar` task packages only
   the module's own classes, so common does **not** end up in the payload — but it is present on the dev run
   classpath. Exactly the desired behaviour, without any special construction.
2. **`modImplementation` for the runtime**: it is a Fabric mod, so it must be loaded as a mod in the dev run. Loom
   remaps it (a no-op, since there are no MC references).
3. **`runDir` outside the module** (`../../run/<payloadId>-client`): all run directories sit centrally under
   `run/`, which simplifies clean-up, `.gitignore` and comparisons between versions.

## 20.7 IDE support (IntelliJ IDEA)

| Requirement | Implementation |
|---|---|
| One IDE module per MC version with its own MC dependency | Falls out of the Gradle multi-project; Loom supplies the decompiled sources of the matching MC version per module. |
| Per-version run configurations | `ideConfigGenerated(true)` produces “Minecraft Client (mc1201)”, “Minecraft Server (mc1201)”, “Data Generation (mc1201)” and so on. |
| The right JDK per module | A toolchain per module (`17` for mc1201, `21` for mc1211/mc1214, `25` for mc261). IntelliJ adopts the toolchain on Gradle sync. |
| Debugging | Ordinary Loom runs; breakpoints work in `:common` and in the version module simultaneously, because common is included as a project dependency rather than a JAR. |
| Debugging the **universal JAR** | `runUniversalServer<PayloadId>` starts a real server with `-agentlib:jdwp=…,address=5005,suspend=n`; the plugin additionally generates an “Attach to Universal (mc1214)” remote run config via `idea` XML in `.idea/runConfigurations/`. |
| No “generated sources” traps | There are **no** synchronised source trees. Only metadata (JSON) and merged resources are generated — never Java source code that could be edited. This is a deliberate decision against source merging (chapter 24.8). |
| Datagen output as a resource directory | `src/main/generated` is registered as a resource srcDir and marked “generated” in IntelliJ. |

Additionally, `dev.fabricmultiloader.universal` generates an `.idea/runConfigurations/` group “FabricMultiLoader”
with `buildUniversalJar`, `validateUniversalJar` and `integrationTest` as Gradle run configs, so the three most
important actions are reachable without a terminal.

---

# 21. Gradle DSL

## 21.1 `settings.gradle.kts` (complete)

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

// Reads gradle/fabricmultiloader.toml and calls include(":versions:mc-<minecraft>")
// for every [versions.X]; include(":common") always.
// Checks that every directory exists and that there are no orphan directories
// (OMNI-1162 / OMNI-1163).
fabricMultiLoaderSettings {
    // optional overrides; normally empty:
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

## 21.2 Root `build.gradle.kts` (complete)

```kotlin
plugins {
    id("dev.fabricmultiloader.universal")
}

fabricMultiLoader {

    // --- mod identity: overrides/extends [mod] from the matrix --------------
    mod {
        // id / version / name / description / license / authors / contact
        // come from gradle/fabricmultiloader.toml and need not be repeated here.

        // Entrypoints: either here or via @UniversalEntrypoint (annotation processor).
        entrypoint("com.example.common.ExampleMod")
        clientEntrypoint("com.example.common.ExampleModClient")
        // serverEntrypoint("com.example.common.ExampleModServer")

        // Additional loader metadata for the container:
        conflicts("examplemod-legacy", "*")
        breaks("brokenmod", "<1.5.0")
        custom("modmenu", mapOf("links" to mapOf("modmenu.discord" to "https://discord.gg/example")))
    }

    // --- container options -------------------------------------------------
    container {
        commonPackaging.set(CommonPackaging.SHARED)      // or EMBEDDED
        strict.set(true)
        verifyIntegrity.set(true)
        archiveClassifier.set("universal")               // -> examplemod-2.0.0-universal.jar
        includeLicense.set(true)
        reproducible.set(true)
    }

    // --- resources -----------------------------------------------------------
    resources {
        strictOverrides.set(true)     // undeclared overrides are errors
        mergeLanguageFiles.set(true)  // merge assets/*/lang/*.json key by key
        // declare permitted overrides explicitly (chapter 25.4):
        allowOverride("assets/examplemod/lang/en_us.json")
        allowOverride("assets/examplemod/models/item/ruby.json")
    }

    // --- validation ----------------------------------------------------------
    validation {
        failOnWarnings.set(false)
        // individual rules can be disabled, with a mandatory justification in the build log:
        // ignore("OMNI-1121", because = "AW target exists only in 1.21.4, intentional")
    }

    // --- slim jars (optional) ------------------------------------------------
    slimJars {
        enabled.set(false)
    }

    // --- integration tests ---------------------------------------------------
    integrationTests {
        enabled.set(true)
        ticks.set(200)                       // the server runs 200 ticks, then /stop
        acceptEula.set(true)                 // writes eula=true into the test instance
        timeout.set(java.time.Duration.ofMinutes(6))
        extraMods("net.fabricmc.fabric-api:fabric-api")   // the matching version per payload from the matrix
    }

    // --- distribution --------------------------------------------------------
    publishing {
        modrinth {
            enabled.set(true)
            token.set(providers.environmentVariable("MODRINTH_TOKEN"))
            // gameVersions/loaders/dependencies are derived from the matrix
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

## 21.3 `common/build.gradle.kts` (complete)

```kotlin
plugins {
    id("dev.fabricmultiloader.common")
}

// The plugin sets the toolchain, --release, the api dependency and the annotation
// processor from [container].baselineJava and [framework].api.

dependencies {
    // Plain JVM libraries are permitted if they work in EVERY supported
    // environment. They are NOT embedded by the assembler; that is what
    // 'omniInclude' is for (chapter 24.5).
    // implementation("org.jetbrains:annotations:26.0.1")   // compileOnly-like, not needed at runtime

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("dev.fabricmultiloader:fabricmultiloader-testing:1.0.0")
}

tasks.test { useJUnitPlatform() }
```

## 21.4 `versions/mc-1.21.4/build.gradle.kts` (complete)

```kotlin
plugins {
    id("dev.fabricmultiloader.version")
}

fabricMultiLoaderVersion {
    payloadId.set("mc1214")            // key in [versions.*]; can be derived from the directory name

    // optional payload-local fine tuning:
    clientOnlyPackages.add("com.example.mc1214.client")
    // allowForeignAccessWidener("cloth-config")
    // overrideCapability("commands", implementedByPayload = true)
}

dependencies {
    // Version-specific mod dependencies. Versions come from
    // [versions.mc1214.dependencies]; 'omniMod' additionally writes the
    // matching depends entry into the payload fabric.mod.json.
    omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    // Optional integration: no depends, only compileOnly + dev runtime.
    omniOptionalMod("com.terraformersmc:modmenu", key = "modmenu")

    // A library to be embedded INTO the payload (JiJ inside the payload):
    // omniInclude("com.example.libs:mylib:1.2.3")
}
```

## 21.5 DSL reference (excerpt with types)

```kotlin
interface FabricMultiLoaderExtension {
    val matrix: Provider<Matrix>                       // read-only view of the TOML
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

// dependency DSL extensions in the version module
fun DependencyHandler.omniMod(coordinateWithoutVersion: String, key: String,
                              configure: ExternalModuleDependency.() -> Unit = {})
fun DependencyHandler.omniOptionalMod(coordinateWithoutVersion: String, key: String,
                              configure: ExternalModuleDependency.() -> Unit = {})
fun DependencyHandler.omniInclude(coordinate: String)
```

The complete, generated DSL reference is produced from the KDoc/Javadoc of the extension interfaces and published
as `docs/gradle-plugin.md` and as HTML (chapter 38.3).

---

# 22. Repository Structure

## 22.1 Framework repository `fabricmultiloader/fabricmultiloader`

```
fabricmultiloader/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradlew, gradlew.bat, gradle/wrapper/
├── LICENSE  (currently proprietary; target: Apache-2.0, see LICENSE section 4)
├── NOTICE
├── README.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── CHANGELOG.md
│
├── format/                       dev.fabricmultiloader.format        Java 8, 0 dependencies
│   └── src/{main,test}/java/dev/fabricmultiloader/format/
│       ├── json/                 JsonValue, Json, JsonWriter, JsonPointer
│       ├── version/              SemVer, VersionPredicate, VersionRange, Interval, JavaVersions
│       ├── manifest/             ContainerManifest, PayloadDescriptor, Requirements, ManifestReader/Writer
│       ├── payload/              PayloadResolver, PayloadMatcher, MatchResult, Rejection, DomainDisjunctifier
│       ├── error/                ErrorCode, OmniException, Messages
│       └── hash/                 Sha256 (a streaming wrapper around the JDK MessageDigest)
│
├── api/                          dev.fabricmultiloader.api           Java 8
│   └── src/main/java/dev/fabricmultiloader/api/
│       ├── (root: UniversalMod, ModContext, Id, Side, ModLogger, Capability, …)
│       ├── platform/  registry/  net/  command/  event/  ref/  config/  resource/  text/
│
├── runtime/                      dev.fabricmultiloader.runtime       Java 8, a Fabric mod
│   ├── src/main/java/dev/fabricmultiloader/runtime/
│   │   ├── entrypoint/           ContainerPreLaunch, PayloadPreLaunch, PayloadMain,
│   │   │                         PayloadClient, PayloadServer
│   │   ├── boot/                 RuntimeBootstrap, RuntimeRegistry, ContainerRuntime,
│   │   │                         LifecycleStateMachine, IntegrityChecker
│   │   ├── env/                  EnvironmentDetector, Environment
│   │   ├── payload/              PlatformLoader, PayloadActivation
│   │   ├── context/              ModContextImpl, ServiceRegistryImpl, CapabilityResolver
│   │   ├── adapter/              CommandRegistry, EventBus, TextConverter, Feedback
│   │   │                         (the halves that name no Minecraft type)
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
│       ├── dsl/               extensions, specs, CommonPackaging
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
├── example/                      UniversalExampleMod (chapter 35)
├── docs/                         (chapter 38)
└── .github/workflows/            (chapter 33)
```

## 22.2 Module dependencies and responsibilities

| Module | Responsible for | Depends on | Published as |
|---|---|---|---|
| `format` | data model, parsers, version algebra, error codes, hashing | — | `dev.fabricmultiloader:fabricmultiloader-format` |
| `api` | the developer SPI | `format` | `…-api` |
| `runtime` | bootstrap, lifecycle, diagnostics, version-stable adapters | `format`, `api`, `fabric-loader` (compileOnly) | `…-runtime` (a Fabric mod JAR) |
| `processor` | entrypoint derivation | `format` | `…-processor` |
| `gradle-plugin` | the entire build toolchain | `format`, Loom (compileOnly) | `dev.fabricmultiloader:fabricmultiloader-gradle` + plugin markers |
| `testing` | test harness for the framework **and** for mod projects | `format`, `api` | `…-testing` |
| `example` | reference implementation; built, validated and booted in CI | all | not published |

`format` is deliberately the heart: because the same code runs in the Gradle plugin and in the runtime, build-time
and runtime decisions cannot diverge.

## 22.3 Mod project structure (reference)

```
universal-example-mod/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── fabricmultiloader.toml            ← the matrix
│   ├── libs.versions.toml                ← test/build libraries only
│   └── wrapper/
├── gradlew, gradlew.bat
├── LICENSE, README.md, CHANGELOG.md
│
├── common/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/common/…
│       ├── main/resources/
│       │   ├── assets/examplemod/…       ← shared assets
│       │   └── data/examplemod/…         ← shared data
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
├── run/                                  ← all dev run directories (gitignored)
└── .github/workflows/build.yml
```

There is **no** separate `universal/` module: the container is assembled in the root project. Rationale: the
container has no sources of its own (only generated metadata + the common JAR + payloads), a dedicated module would
be empty overhead and an extra IDE entry. `./gradlew buildUniversalJar` in the root is therefore the natural entry
point.

## 22.4 Package conventions in the mod project

| Location | Package | Rule |
|---|---|---|
| `:common` | `<basePackage>.common.**` | no MC references; the public mod API under `<basePackage>.common.api` |
| `:versions:mc-X` | `<basePackage>.<payloadId>.**` | e.g. `com.example.mc1214` |
| Mixins | `<basePackage>.<payloadId>.mixin` / `.client.mixin` | enforced by the validator |
| Client-only | `<basePackage>.<payloadId>.client.**` | declared in `clientOnlyPackages` |

Package disjointness is validator-checked (`OMNI-1044`) and prevents two payloads from occupying the same FQCN —
important for `commonPackaging = EMBEDDED` and for unambiguous stack traces.

---

# 23. Build Pipeline

## 23.1 The complete pipeline

```
① :common
   compileJava (--release 17, APT: UniversalEntrypointProcessor)
        │  → build/classes/java/main
        │  → build/generated/omni/entrypoints.json
   validateCommon        (bytecode scan: no MC/Fabric API/Mixin references,
        │                 only commonPackage, class file major ≤ 61)
   jar                   → common-2.0.0.jar   (classes + omni/entrypoints.json)
   apiJar                → examplemod-api-2.0.0.jar (filtered to …common.api)

② :versions:mc-1.21.4        (analogously for every matrix version, parallelisable)
   mergeAccessWidener    common/shared.accesswidener ⊕ payload AW  (namespace named)
        │                → build/omni/accesswidener/examplemod-mc1214.accesswidener
   compileJava (--release 21, Mixin AP → refmap)
        │                → build/classes, build/devlibs/…-refmap.json
   processResources      placeholders ${version} etc.
   mergePayloadResources common/resources ⊕ shared/resources ⊕ version/resources ⊕ generated
        │                → build/omni/resources/            (+ conflict report)
   generatePayloadModJson    → build/omni/meta/fabric.mod.json
   generatePayloadDescriptor → build/omni/meta/omni/payload.json
   jar                   classes + mixin configs + refmap
   remapJar (Loom)       named → intermediary, also remaps the AW file
        │                → build/libs/mc-1.21.4-2.0.0.jar
   omniPayload           takes the remapJar output; replaces/adds:
        │                  · fabric.mod.json        (generated)
        │                  · omni/payload.json      (generated)
        │                  · assets/**, data/**     (merged)
        │                removes: META-INF/omni-container.json (if present),
        │                         empty directories, *.kotlin_module, signatures
        │                → build/omni/payload/examplemod-mc1214.jar
   validatePayload       payload-local rules (fast feedback)

③ root
   collectPayloads          → build/omni/jars/examplemod-mc*.jar          (Sync, deterministic)
   resolveFrameworkRuntime  → build/omni/jars/fabricmultiloader-runtime-1.0.0.jar
   generateOmniManifest     reads every payload JAR:
        │                     · SHA-256, size, class file major (scan)
        │                     · omni/payload.json (constraints, capabilities, mixins, AW)
        │                     · resource digest
        │                   runs the DomainDisjunctifier (range subtraction)
        │                   → build/omni/META-INF/omni-container.json
   generateContainerModJson → build/omni/fabric.mod.json  (union ranges)
   assembleUniversalJar     Zip: MANIFEST.MF, fabric.mod.json, omni-container.json,
        │                        common classes, omni/icon.png, LICENSE,
        │                        META-INF/jars/* (STORED)
        │                   → build/libs/examplemod-2.0.0-universal.jar
        │                   → build/reports/omni/universal-jar.sha256
   validateUniversalJar     34 rules → build/reports/omni/validation.{txt,json}
   buildUniversalJar        aggregate
```

## 23.2 Step details

### `mergePayloadResources`

Inputs (in precedence order, later wins): `common/src/main/resources`, `shared/src/main/resources` (if present),
`versions/mc-X/src/main/resources`, `versions/mc-X/src/main/generated`. Details in chapter 25.

### `omniPayload`

A `Zip` task, not a `Jar` task — deliberately, so that no `MANIFEST.MF` is created automatically (payloads need
none) and the entry order is fully controlled.

Removal list (`excludeFromPayload`): `META-INF/omni-container.json`, `META-INF/*.SF`, `META-INF/*.RSA`,
`META-INF/*.DSA`, `META-INF/INDEX.LIST`, `**/*.kotlin_module` (if Kotlin is not configured), `**/.DS_Store`,
`**/Thumbs.db`, empty directory entries.

### `generateOmniManifest`

Reads the finished payload JARs — not the Gradle models of the version projects. That makes the task isolation-safe
and cacheable, and guarantees that the manifest describes *the shipped artifact*, not an intention.

Class file major classification happens via a stream scan: for every `.class` entry the first 8 bytes are read
(`CAFEBABE`, minor, major); differing majors inside one payload cause `OMNI-1041` with a listing.

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

    @TaskAction fun assemble() { /* deterministic ZIP creation per chapter 10.5 */ }
}
```

Procedure: all entries are first collected in a `TreeMap<String, EntrySource>` (so that ordering depends solely on
the path), duplicates raise `OMNI-1170` naming both sources, and then everything is written sequentially. The
common JAR is embedded **unpacked** (classes directly), not as a nested JAR — it is not a mod.

## 23.3 Build error handling

| Situation | Reaction |
|---|---|
| A version module fails to compile | The build fails, but the other modules keep building (`--continue`-friendly, since the task trees are independent). The error message names the `payloadId` and the MC version. |
| A matrix version has no directory | `OMNI-1162` in the settings plugin, suggesting the exact `addMinecraftVersion` command |
| A directory has no matrix entry | `OMNI-1163`, suggesting either adding the entry or deleting the directory |
| Duplicate during assembly | `OMNI-1170` naming both source paths |
| Non-deterministic input detected (e.g. a timestamp in a generated file) | `OMNI-1060` |

## 23.4 Reproducibility — verification in the build

`verifyReproducible` (enabled in CI, locally via `-Pomni.verifyReproducible=true`): builds the container twice into
different output directories, compares the SHA-256 values and, on divergence, writes a diff report at ZIP entry
level (path, size, CRC32). That reliably catches regressions in the generators.

## 23.5 Code generation — what is generated and what is not

| Generated | Deliberately not generated |
|---|---|
| `fabric.mod.json` (container + payloads) | Java source code |
| `META-INF/omni-container.json`, `omni/payload.json` | mixin configs |
| merged access widener files | mod logic of any kind |
| merged resource trees | adapter implementations (only stubs during scaffolding) |
| `omni/entrypoints.json` (APT) | refmaps (Loom/the Mixin AP does that) |
| validation and matrix reports | |

Principle: **only things containing no substantive decision are generated.** Everything a developer must read,
understand and deliberately change stays hand-written. There is therefore no “generated sources you must not edit”
trap.

---

# 24. Dependency Management

## 24.1 The four dependency classes

| Class | Location | Gradle configuration | Ends up in `depends`? | Ends up in the artifact? |
|---|---|---|---|---|
| **Framework** (`api`, `runtime`) | container/all payloads | `api`/`modImplementation` (by the plugin) | container: `fabricmultiloader` | the runtime as a nested mod in the container |
| **Common library** (plain JVM, version-neutral) | `:common` | `implementation` + `omniIncludeCommon` | no | unpacked or as a nested JAR in the container |
| **Version mod dependency** (Fabric API, Cloth Config …) | `:versions:mc-X` | `omniMod(coord, key)` | yes, in the payload | no (the user installs it) |
| **Embedded version library** | `:versions:mc-X` | `omniInclude(coord)` | no | as a nested JAR **inside the payload** |

## 24.2 `omniMod` — a declared runtime dependency

```kotlin
omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig") {
    exclude(group = "net.fabricmc.fabric-api")
}
```

Effects:

1. `modImplementation("me.shedaniel.cloth:cloth-config-fabric:15.0.140")` — the version from
   `[versions.mc1214.dependencies].clothConfig`.
2. An entry `"cloth-config": ">=15.0.0 <16.0.0"` in the payload `fabric.mod.json`'s `depends` — the range from
   `clothConfigRange`.
3. An entry in `requires.mods` of the payload descriptor ⇒ it appears in the diagnostic report and is explained by
   `PayloadMatcher`.
4. The mod ID value is **not guessed**: the plugin reads the resolved artifact's `fabric.mod.json` and takes the
   real mod ID from it (`OMNI-1180` if the artifact is not a Fabric mod).

## 24.3 `omniOptionalMod` — an optional integration

```kotlin
omniOptionalMod("com.terraformersmc:modmenu", key = "modmenu")
```

* `modCompileOnly` + `modLocalRuntime` (the dev run has the mod, the payload does not declare it as `depends`).
* An entry in `recommends` resp. `requires.optionalMods`.
* At runtime the mod code checks `ctx.isModLoaded("modmenu")`; integration mixins are gated via
  `ConditionalMixinPlugin` (chapter 16.6).

## 24.4 Preventing incompatible combinations

| Risk | Protection |
|---|---|
| Two payloads bring different versions of the same library as nested JARs | Only one payload loads ⇒ only one version is active. Additionally Fabric JiJ dedup, should another mod bring the same library. |
| A library is accidentally embedded into the **container** although it is version-dependent | `validateCommon` forbids MC/Fabric API references in the container; `OMNI-1181` forbids Fabric mods (an artifact containing a `fabric.mod.json`) in the `omniIncludeCommon` configuration. |
| Fabric API pulled in transitively at the wrong version | For every `omniMod` declaration the plugin automatically sets `exclude(group = "net.fabricmc.fabric-api")` and `exclude(group = "net.fabricmc", module = "fabric-loader")` unless explicitly configured otherwise. `OMNI-1182` warns when a payload nevertheless contains a second Fabric API version. |
| Common code uses a library present only in newer MC versions (e.g. a newer Gson) | `validateCommon` permits in `:common` only references to the JDK `baselineJava` API, `dev.fabricmultiloader.**`, `net.fabricmc.loader.api.**` and artifacts explicitly declared in `omniIncludeCommon` (`OMNI-1183`). |
| Two universal mods with different runtime versions | Fabric JiJ dedup + the `depends` range (chapter 13.4). |
| Kotlin | If `:common` or a version module uses Kotlin, the plugin requires `fabric-language-kotlin` as an `omniMod` and warns (`OMNI-1184`), because its version is bound to the MC version. Kotlin is supported, but the Kotlin runtime must **not** be embedded into the container. |

## 24.5 Libraries inside the container

```kotlin
// common/build.gradle.kts
dependencies {
    omniIncludeCommon("com.example.libs:pure-jvm-lib:1.4.0")   // placed into the container as a nested JAR
}
```

Preconditions, checked by the plugin: no `fabric.mod.json` in the artifact (otherwise it is a mod — then it belongs
into the payload via `omniMod`), class file major ≤ `baselineJava`, no references to `net.minecraft`. Embedded
common libraries are shipped as a **nested JAR** (not unpacked) and made known to the loader via `jars[]` — with a
generated wrapper `fabric.mod.json` (`id = "<modid>-lib-<artifact>"`, `custom.modmenu.parent`), because Fabric
ignores nested JARs without metadata. Alternatively (the default for small libraries < 64 KB): unpacked embedding
with relocation via `omniRelocate("com.example.libs" to "com.example.common.shaded.libs")`, to rule out FQCN
collisions with other mods.

## 24.6 Version catalog vs. matrix

* `gradle/fabricmultiloader.toml` — **everything that depends on the Minecraft version.**
* `gradle/libs.versions.toml` — build and test tooling (JUnit, AssertJ, Mockito) independent of MC.

This separation is normative. An MC-dependent version in `libs.versions.toml` is a common mistake in multi-version
setups and is reported by `OMNI-1185` (heuristic: the version string contains `+1.` or `-mc`).

## 24.7 Publishing the mod API

```
:common:apiJar   → examplemod-api-2.0.0.jar   (only com.example.common.api.**  + manifest)
                   POM dependencies: dev.fabricmultiloader:fabricmultiloader-api:1.0.0 (compile)
```

Third-party mods:

```kotlin
dependencies {
    compileOnly("com.example:examplemod-api:2.0.0")
    // no modImplementation needed: the container supplies the classes at runtime
}
```

At runtime the third-party mod obtains the implementation via `ExampleModApi.get()` (ObjectShare, chapter 19.9) and
must declare `depends: {"examplemod": ">=2.0.0 <3.0.0"}` or `suggests`. Because the container is the same
compilation across all MC versions, **one** API artifact is correct for all MC versions — the central advantage over
the classic one-JAR-per-version release, where third-party mods must recompile per MC version.

## 24.8 Shared source code (`shared`) — a deliberate limitation

Optionally enabled:

```toml
[shared]
enabled  = true
srcDir   = "shared/src/main/java"
versions = ["mc1211", "mc1214"]        # only versions with an identical mapping provider
```

Effect: `shared/src/main/java` is added to the named version modules as an **additional** `srcDir` (no copying, no
sync, IDE-friendly, directly editable). Rules enforced by the validator:

* **No shadowing.** A class must not exist simultaneously in `shared` and in a version module (`OMNI-1186`).
  Divergence is solved via interfaces (common) or separate classes (version), not by overriding.
* **The same mapping provider** for all participating versions (`OMNI-1081`).
* **The same Java release level** for all participating versions; otherwise `shared` would have to compile against
  the minimum, leading to confusing errors (`OMNI-1187`).
* `shared` code may use MC types — that is its purpose. It is therefore **not** version-neutral but “valid for this
  subgroup of versions”.

A source preprocessor (`//#if MC>=12100`) is **not** built in. Rationale: it makes source code worse for
contributors, code review, IDE refactorings and static analysis; it moves complexity into a second, untyped
language; and it solves no problem that `shared` + adapters does not also solve. Anyone who still wants one can
apply Stonecutter in parallel — the plugin does not prevent it and documents the combination in
`docs/version-modules.md`.

---

# 25. Resources

## 25.1 The conflict problem and its resolution

If shared resources lived in the container and version-specific ones in the payload, there would be **two** resource
packs for the same mod (Fabric registers every mod with `assets/`/`data/` as a pack). The precedence between two mod
packs depends on mod load order and is therefore not reliably defined.

**Resolution:** the container contains **no** `assets/` and **no** `data/` entries (validator `OMNI-1023`). All
resources are merged into **every** payload at build time. At runtime there is therefore exactly one resource pack
for the mod. That costs disk space (a factor equal to the number of payloads) and is a deliberate trade of size for
determinism (non-goal N8).

The mod icon therefore lives under `omni/icon.png` — outside `assets/` — and is read via `ModContainer#findPath`,
not through the resource system.

## 25.2 Merge order

```
1. common/src/main/resources/**                     (lowest precedence)
2. shared/src/main/resources/**                     (only when [shared] is active and the version participates)
3. versions/mc-X/src/main/resources/**
4. versions/mc-X/src/main/generated/**              (datagen, highest precedence)
```

## 25.3 Merge rules per file type

| Pattern | Rule |
|---|---|
| All files | On an identical path, higher precedence wins. With **byte-identical** content: silent. With differing content: permitted only when the path is listed in `resources { allowOverride(...) }` — otherwise `OMNI-1200` (with `strictOverrides = true`) resp. a warning. |
| `assets/*/lang/*.json` | With `mergeLanguageFiles = true`: a **key-wise deep merge**, higher precedence winning per key. The result is written with sorted keys (reproducibility). No `allowOverride` needed. |
| `data/*/tags/**/*.json` | No auto-merge. Reason: tags have `replace` semantics and order significance; a naive merge would be substantively wrong. Full replacement with a mandatory `allowOverride`. The documentation recommends producing tags via datagen instead. |
| `fabric.mod.json` | **Forbidden** in `resources` (`OMNI-1021`) — it is generated. |
| `*.mixins.json`, `*.accesswidener`, `*-refmap.json` | Permitted only in version modules; forbidden in `common/src/main/resources` (`OMNI-1201`). |
| `.DS_Store`, `Thumbs.db`, `*.blend`, `*.xcf`, `*.psd` | silently discarded (an exclusion list extensible via `resources { exclude(...) }`) |
| Empty directories | not written |

## 25.4 Conflict report

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

* One datagen run per version module: `./gradlew runDatagen1214`. The output goes to
  `versions/mc-1.21.4/src/main/generated` and is committed (so CI builds without a datagen run and diffs are visible
  in review).
* The datagen provider code lives in the version module (it uses version-specific `FabricDataGenerator` APIs) but
  can take its inputs from the common module:

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

`RubyContent` lives in the common module and describes recipes/drops in a neutral data model (`RecipeSpec`,
`LootSpec`) — the same specification also used at runtime for tooltips. Datagen and runtime are therefore guaranteed
consistent, and the version-specific portion stays pure provider glue.

The `DataGeneratorEntrypoint` is registered in the payload `fabric.mod.json` **only in the dev run**: the plugin
writes it into a separate, dev-only `fabric.mod.json` (`build/omni/meta-dev/`) that is not contained in the payload
artifact (`OMNI-1202` checks that release payloads contain no `fabric-datagen` entrypoint).

## 25.6 Shaders, sounds, models

No special treatment: they are ordinary resources and follow the rules in 25.3. The only practically relevant point
is that shaders (`assets/<ns>/shaders/**`) and model formats can become incompatible between MC versions — then they
belong in `versions/mc-X/src/main/resources` and **not** in `common`, and the override mechanism with
`allowOverride` makes the deviation visible in review.

---

Continue with [chapters 29–33 — error handling, diagnostics, validation, testing, CI/CD](part-08-quality.md).
