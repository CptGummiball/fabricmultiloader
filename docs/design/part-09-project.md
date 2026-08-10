# 34. Distribution

## 34.1 Principle

For players there is **one** file. On Modrinth and CurseForge it is published as **one** file upload with
**multiple** game version tags. Both platforms support that natively (several `game_versions` per file); no trick,
no special handling.

## 34.2 File names

| Artifact | Name | Purpose |
|---|---|---|
| Universal JAR | `examplemod-2.0.0-universal.jar` | the download for players |
| Checksum | `examplemod-2.0.0-universal.jar.sha256` | verification, modpack tools |
| Slim JAR (optional) | `examplemod-2.0.0+mc1.21.4.jar` | a single version, should anybody need it |
| API artifact | `examplemod-api-2.0.0.jar` | Maven, for third-party mods |
| Sources (optional) | `examplemod-2.0.0-sources.jar` | per version module, Maven only |

The classifier `-universal` is the default and can be changed via `container { archiveClassifier }`. Deliberately
**no** `+` in the main file name: some launchers, mod managers and web servers treat `+` in URLs inconsistently.

## 34.3 Modrinth

`publishModrinth` (implementation: a dedicated publisher in the plugin, HTTP API v2, no third-party plugin
dependency; `minotaur` can be used as an alternative):

```
POST /v2/version
{
  "project_id":     "AbCdEfGh",
  "version_number": "2.0.0",
  "name":           "Universal Example Mod 2.0.0",
  "version_type":   "release",
  "loaders":        ["fabric"],
  "game_versions":  ["1.20.1","1.21","1.21.1","1.21.4"],
  "featured":       true,
  "dependencies": [
    { "project_id": "P7dR8mSH", "dependency_type": "required" },        // Fabric API
    { "project_id": "9s6osm5g", "dependency_type": "optional" }         // Cloth Config
  ],
  "changelog":      "<content of CHANGELOG.md, section 2.0.0>",
  "file_parts":     ["file"],
  "primary_file":   "file"
}
```

**Deriving `game_versions` from the matrix** — the point where automation really pays off: the list of **concretely
existing** MC versions is computed from the effective MC ranges by intersecting them with Modrinth's version index
(`GET /v2/tag/game_version`). `>=1.21 <1.21.2` therefore automatically becomes `1.21` and `1.21.1`,
`>=1.21.4 <1.21.5` becomes `1.21.4`. Snapshots are included only when `snapshots = true` is set in the matrix. The
result is printed before upload and can be inspected with `--dry-run`.

## 34.4 CurseForge

Upload via the upload API (`POST /api/projects/<id>/upload-file`) with `gameVersions` as a list of CurseForge
version IDs. The IDs are resolved via `GET /api/game/versions` and derived from the matrix the same way.
Additionally the tags `Fabric` (modloader) and `Java 17`/`Java 21`/`Java 25` (Java version tags, if the project uses
them) are set — with a note in the documentation that CurseForge permits only **one** Java entry per file and that
the **lowest** is therefore used.

`relations`: Fabric API as `requiredDependency`, optional mods as `optionalDependency`.

## 34.5 Changelog

`CHANGELOG.md` in Keep-a-Changelog format; the publisher extracts the section for the current version and
automatically appends a generated block:

```markdown
### Supported versions

| Minecraft | Java | Fabric Loader | Fabric API |
|---|---|---|---|
| 1.20.1 | 17+ | 0.14.21+ | 0.92.2+ |
| 1.21 – 1.21.1 | 21+ | 0.15.11+ | 0.102.0+ |
| 1.21.4 | 21+ | 0.16.9+ | 0.114.0+ |

This is a single universal file — the same download works on every version listed above.
SHA-256: 7c9a1f…e2
```

The block comes from `omniReport` and is therefore always correct.

## 34.6 GitHub release

Tag `v2.0.0`, title `Universal Example Mod 2.0.0`, body = the changelog section plus the version table, assets: the
universal JAR, `SHA256SUMS.txt`, `validation.txt`. Attaching the validation report is deliberate: it evidences that
the artifact was checked and helps with support requests.

## 34.7 Modpack and launcher compatibility

| Tool | Behaviour | Note |
|---|---|---|
| Prism / MultiMC | Treats the JAR as one mod; reads `fabric.mod.json` and shows the name/version | The `depends.minecraft` union means Prism's “does not match this instance” warning correctly stays away |
| Modrinth App | Shows one mod, one update | `game_versions` must be correct, otherwise the app filters the file out |
| Packwiz | A `.pw.toml` with one file and a hash | works unchanged |
| CurseForge App | One file | see the Java tag limitation in 34.4 |
| Server hosts with auto-update | One file | the `.sha256` sidecar allows an integrity check |
| Modpacks that recompress JARs (rare) | Payload hashes may break | `verifyIntegrity` yields `OMNI-2013` with a pointer to `-Dfabricmultiloader.verify=false`; chapter 39.4 |

## 34.8 When slim JARs make sense

`buildSlimJars` produces a standalone JAR per payload (payload + common + container metadata, reduced to that
payload). Documented use cases:

* Size-critical distribution (e.g. server-side auto-downloads with bandwidth limits).
* Platforms that do not allow multiple game version tags.
* A fallback path should the load-bearing loader assumption break in a future loader version (chapter 41.2) — a slim
  release would then be possible without a code change.

Disabled by default, so the “one file” promise is not diluted.

---

# 35. Example Mod — `UniversalExampleMod`

## 35.1 Scope

Supports Minecraft **1.20.1**, **1.21 – 1.21.1**, **1.21.4**. Contains: a shared entrypoint, an item with behaviour,
a block with a BlockItem, a command, networking in both directions, a common event handler, three version adapters,
one version-specific mixin each, shared and version-specific resources, datagen, unit tests.

## 35.2 Complete project structure

```
example/                                     (inside the framework repo; the root project in the template)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── fabricmultiloader.toml               ← the matrix from chapter 20.3
│   └── libs.versions.toml
│
├── common/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/common/
│       │   ├── ExampleMod.java                       @UniversalEntrypoint
│       │   ├── ExampleModClient.java                 @UniversalEntrypoint(CLIENT)
│       │   ├── ExampleCommands.java
│       │   ├── ExampleEvents.java
│       │   ├── RubyLogic.java                        pure business logic, unit-tested
│       │   ├── RubyBehavior.java                     ItemBehavior
│       │   ├── RubyContent.java                      recipe/loot specifications (datagen + runtime)
│       │   ├── api/ExampleModApi.java                the public API for third-party mods
│       │   ├── api/RubyView.java
│       │   ├── config/ExampleConfig.java             JSON via the format parser
│       │   ├── hook/ItemRenderHooks.java             called from mixins
│       │   ├── hook/ItemRenderContext.java
│       │   ├── net/ExampleNetworking.java
│       │   ├── net/ChargeRequest.java
│       │   ├── service/OreGenService.java
│       │   └── service/HudRenderService.java
│       ├── main/resources/
│       │   ├── assets/examplemod/lang/en_us.json
│       │   ├── assets/examplemod/lang/de_de.json
│       │   ├── assets/examplemod/textures/item/ruby.png
│       │   ├── assets/examplemod/textures/block/ruby_block.png
│       │   ├── assets/examplemod/models/item/ruby.json
│       │   └── data/examplemod/tags/blocks/ruby_related.json
│       ├── main/accesswidener/shared.accesswidener
│       ├── main/omni/icon.png
│       └── test/java/com/example/common/
│           ├── RubyLogicTest.java
│           ├── ExampleModInitTest.java               uses FakeModContext
│           └── ExampleConfigTest.java
│
├── versions/
│   ├── mc-1.20.1/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/com/example/mc1201/
│   │       │   ├── Platform1201.java
│   │       │   ├── Platform1201Factory.java
│   │       │   ├── registry/Registries1201.java
│   │       │   ├── registry/BehaviorItem1201.java
│   │       │   ├── registry/Mapping1201.java
│   │       │   ├── net/Networking1201.java
│   │       │   ├── net/ByteSink1201.java
│   │       │   ├── net/ByteSource1201.java
│   │       │   ├── net/ClientNet1201.java            (client-only)
│   │       │   ├── ref/PlayerRef1201.java
│   │       │   ├── service/OreGenService1201.java
│   │       │   ├── client/HudRenderService1201.java  (client-only)
│   │       │   ├── client/mixin/ItemRendererMixin.java
│   │       │   ├── mixin/MinecraftServerMixin.java
│   │       │   └── datagen/ExampleDataGen1201.java
│   │       ├── resources/
│   │       │   ├── examplemod-mc1201.mixins.json
│   │       │   ├── examplemod-mc1201.client.mixins.json
│   │       │   ├── examplemod-mc1201.accesswidener
│   │       │   └── assets/examplemod/models/item/ruby.json     ← override (1.20.1 model format)
│   │       └── generated/                                       ← datagen output (committed)
│   ├── mc-1.21.1/   (analogous, com.example.mc1211)
│   └── mc-1.21.4/   (analogous, com.example.mc1214)
│
├── run/                                     (gitignored)
└── .github/workflows/build.yml
```

## 35.3 The produced artifact

```
build/libs/universal-example-mod-1.0.0-universal.jar        ~4.8 MiB
├── fabric.mod.json                          id=examplemod, depends.minecraft = 3 ranges
├── META-INF/omni-container.json             3 payloads, hashes, constraints
├── META-INF/MANIFEST.MF                     Omni-Container-Format: omni/1
├── com/example/common/…                     142 classes, class file 61
├── omni/icon.png
├── omni/entrypoints.json
├── LICENSE
└── META-INF/jars/
    ├── fabricmultiloader-runtime-1.0.0.jar  ~62 KiB
    ├── examplemod-mc1201.jar                ~1.42 MiB  (class file 61)
    ├── examplemod-mc1211.jar                ~1.51 MiB  (class file 65)
    └── examplemod-mc1214.jar                ~1.54 MiB  (class file 65)
```

## 35.4 Selected files in context

The central source files are shown in full in chapters 27/28 (`ExampleMod`, `ExampleModClient`,
`ExampleNetworking`, `ExampleCommands`, `ExampleEvents`, `Registries1201`, `Registries1214`, `Networking1201`,
`Networking1214`, `Platform1214`, `ItemRendererMixin` in both variants). In addition:

`common/src/main/java/com/example/common/RubyLogic.java` — pure business logic, with no dependency other than the
API:

```java
package com.example.common;

import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.ref.PlayerRef;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RubyLogic {

    private static final Map<UUID, Integer> CHARGE = new HashMap<UUID, Integer>();
    private static int maxCharge = 100;

    public static void configure(ExampleConfig config) { maxCharge = config.maxCharge(); }

    public static int chargeOf(PlayerRef player) { Integer v = CHARGE.get(player.uuid()); return v == null ? 0 : v; }
    public static int chargeOf(ItemStackRef stack) { return stack.count() * 2; }

    public static int charge(PlayerRef player, int amount) {
        int next = Math.min(maxCharge, chargeOf(player) + Math.max(0, amount));
        CHARGE.put(player.uuid(), next);
        return next;
    }

    public static void tick(long tickCount) {
        if (tickCount % 200 != 0) return;
        for (Map.Entry<UUID, Integer> e : CHARGE.entrySet()) {
            if (e.getValue() > 0) e.setValue(e.getValue() - 1);
        }
    }

    public static void onRubyBlockBroken(PlayerRef player) { charge(player, 5); }
}
```

`common/src/test/java/com/example/common/ExampleModInitTest.java`:

```java
package com.example.common;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.Side;
import dev.fabricmultiloader.testing.FakeModContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleModInitTest {

    @Test
    void registersContentOnEveryTargetVersion() {
        for (String mc : new String[] { "1.20.1", "1.21.1", "1.21.4" }) {
            FakeModContext ctx = FakeModContext.builder()
                    .modId("examplemod").modVersion("1.0.0")
                    .minecraft(mc).java(mc.startsWith("1.20") ? 17 : 21)
                    .fabricApi("0.114.0").side(Side.SERVER)
                    .build();

            new ExampleMod().onInitialize(ctx);

            assertThat(ctx.recordedItems().keySet())
                    .containsExactly(Id.of("examplemod", "ruby"), Id.of("examplemod", "ruby_block"));
            assertThat(ctx.recordedChannels())
                    .containsExactly(Id.of("examplemod", "ruby_sync"), Id.of("examplemod", "charge_request"));
            assertThat(ctx.recordedCommands()).containsExactly("ruby");
            assertThat(ctx.recordedEvents()).contains("playerJoin", "serverTick", "blockBroken");
        }
    }
}
```

This test runs in ~40 ms, needs no Minecraft, no Loom and no Gradle sync — and still covers the bulk of the mod
logic. That is the practical payoff of principle P1.

`versions/mc-1.20.1/src/main/java/com/example/mc1201/mixin/MinecraftServerMixin.java`:

```java
package com.example.mc1201.mixin;

import com.example.common.RubyLogic;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Demonstrates a mixin that looks identical on every target version yet still
 * exists per payload — because refmap and intermediary binding differ per version.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void examplemod$afterTick(CallbackInfo ci) {
        RubyLogic.tick(((MinecraftServer) (Object) this).getTicks());
    }
}
```

## 35.5 A version-specific resource as a demonstration

`assets/examplemod/models/item/ruby.json` exists twice:

* `common/src/main/resources/…` — the model in the format valid from 1.21.4,
* `versions/mc-1.20.1/src/main/resources/…` — the variant for the older format.

In `build.gradle.kts`:

```kotlin
resources {
    strictOverrides.set(true)
    allowOverride("assets/examplemod/models/item/ruby.json")
}
```

The merge report lists the override; without `allowOverride` the build fails with `OMNI-1200`. Every resource
deviation between versions is therefore visible in code review — one of the most common sources of error in
multi-version projects.

---

# 36. Migration Guide — an existing Fabric mod → FabricMultiLoader

Starting point: `ExampleMod` for Minecraft 1.21.4, a classic single-project Loom setup.

```
examplemod/
├── build.gradle.kts             (fabric-loom, MC 1.21.4)
├── gradle.properties
└── src/main/
    ├── java/com/example/examplemod/**
    └── resources/{fabric.mod.json, examplemod.mixins.json, examplemod.accesswidener, assets/, data/}
```

## 36.1 Step 1 — create the structure (mechanical, 10 minutes)

```bash
mkdir -p common/src/main/{java,resources,accesswidener,omni} common/src/test/java
mkdir -p versions/mc-1.21.4/src/main/{java,resources}
git mv src/main/java/com/example/examplemod versions/mc-1.21.4/src/main/java/com/example/mc1214
git mv src/main/resources/assets    common/src/main/resources/assets
git mv src/main/resources/data      common/src/main/resources/data
git mv src/main/resources/examplemod.mixins.json \
       versions/mc-1.21.4/src/main/resources/examplemod-mc1214.mixins.json
git mv src/main/resources/examplemod.accesswidener \
       versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener
git rm src/main/resources/fabric.mod.json          # generated from now on
```

All existing code initially lands **inside the version module**. That is intentional: after this step the mod is
already a working FabricMultiLoader mod with *one* payload. Everything that follows is improvement, not a
prerequisite.

## 36.2 Step 2 — matrix and build files (15 minutes)

`gradle/fabricmultiloader.toml` with `[mod]`, `[container] baselineJava = 21`, `[framework]` and one block
`[versions.mc1214]` (values taken from the old `gradle.properties`).

`settings.gradle.kts`, the root `build.gradle.kts`, `common/build.gradle.kts` and
`versions/mc-1.21.4/build.gradle.kts` as in chapter 21 — 5 to 40 lines each. The old `build.gradle.kts` is deleted;
its `dependencies` move to `versions/mc-1.21.4/build.gradle.kts`, where
`modImplementation("me.shedaniel.cloth:…")` becomes
`omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig")`.

## 36.3 Step 3 — convert the entrypoint (20 minutes)

Before:

```java
public final class ExampleMod implements ModInitializer {
    @Override public void onInitialize() { … }
}
```

After — two classes, because the responsibility splits:

```java
// versions/mc-1.21.4/src/main/java/com/example/mc1214/Platform1214.java
public final class Platform1214 extends AbstractPlatform {
    @Override public void onInitialize(ModContext ctx) {
        // everything that needs Minecraft types — initially the entire old code
        ExampleModContent.registerAll();
        ExampleModNetworking.register();
    }
    // registries()/networking()/commands()/events() initially via the runtime defaults
    // resp. UnsupportedPlatformParts, as long as no common code uses them
}
```

```java
// common/src/main/java/com/example/common/ExampleMod.java
@UniversalEntrypoint
public final class ExampleMod implements UniversalMod {
    @Override public void onInitialize(ModContext ctx) {
        ctx.log().info("ExampleMod {} on Minecraft {}", ctx.modVersion(), ctx.platform().minecraft());
    }
}
```

At this point `common` is almost empty and the mod works fully.
`./gradlew buildUniversalJar validateUniversalJar runClient1214` must be green — **that is the milestone** at which
the migration is technically complete.

## 36.4 Step 4 — add the second version (the actual payoff)

```bash
./gradlew addMinecraftVersion --mc=1.21.1 --range=">=1.21 <1.21.2" \
    --yarn=1.21.1+build.3 --loader=0.15.11 --fabric-api=0.102.0+1.21.1 --java=21
```

The scaffolding produces the directory, `build.gradle.kts`, the matrix entry,
`Platform1211`/`Platform1211Factory`, empty mixin configs and an AW stub. Then: `cp -r` the 1.21.4 code into
`mc1211`, rename the package, compile, work through the errors. Every compiler error is a real API deviation —
exactly the work nobody can automate.

## 36.5 Step 5 — pull duplicates into `common` (iterative, optional)

Now abstraction pays off. In order of benefit:

| Priority | What | How |
|---|---|---|
| 1 | Pure business logic (computations, state, config) | move 1:1 into `common`, no changes needed |
| 2 | Registration lists | use the `Registries` SPI (chapter 19.4) |
| 3 | Networking | `ChannelSpec` + `PayloadCodec` (chapter 27) |
| 4 | Commands | `CommandSpec` (chapter 28.2) |
| 5 | Events | `Events` (chapter 28.3) |
| 6 | Datagen inputs | neutral specs in `common`, providers in the payload |
| — | Rendering, world generation, mixins | stay in the payload; bridged via hooks/services |

After this step typically 60–85 % of the code lives in `common`. Measured on the example mod: 142 common classes
versus 18–22 classes per payload.

## 36.6 Step 6 — third version, resources, release

Add 1.20.1 (as in step 4). Declare resource deviations with `allowOverride`. Run the validator, enable the
integration tests, configure `publishUniversal`. On Modrinth/CurseForge **keep** the old per-version files (they
keep working) and from now on publish only the universal file — with a changelog note that one file now covers all
versions.

## 36.7 Migration pitfalls

| Pitfall | Detection | Resolution |
|---|---|---|
| Static fields with MC types in `common` | `OMNI-1042` | use the handle pattern (`ItemHandle`) |
| `Registry.register` called directly from `common` | `OMNI-1042` | go through `ctx.registries()` |
| The old `fabric.mod.json` still in resources | `OMNI-1021` | delete it; everything comes from the matrix/DSL |
| A mixin config name without the `payloadId` | `OMNI-1030` on the second payload | rename it |
| Assets in the container instead of the payload | `OMNI-1023` | move assets to `common/src/main/resources` (they are merged into payloads) |
| Java 21 bytecode in common with 1.20.1 support | `OMNI-1040` | `[container] baselineJava = 17` |
| The Kotlin runtime in the container | `OMNI-1184` | `fabric-language-kotlin` per payload as an `omniMod` |
| Fear of a mod ID change | — | The mod ID stays **identical**; only additional payload IDs appear (as ModMenu children) |

---

# 37. Adding New Minecraft Versions

## 37.1 The workflow in one command

Starting point: 1.20.1, 1.21.1 and 1.21.4 are supported. **26.1 with Java 25** is released.

```bash
./gradlew addMinecraftVersion \
    --id=mc261 \
    --mc=26.1 \
    --range=">=26.1 <26.2" \
    --yarn=26.1+build.1 \
    --loader=0.17.0 \
    --fabric-api=0.130.0+26.1 \
    --java=25 \
    --copy-from=mc1214
```

## 37.2 What the task does exactly

| Step | Result |
|---|---|
| 1 | Inserts `[versions.mc261]` into `gradle/fabricmultiloader.toml` — at the right position (sorted), with all required fields and the template's `capabilities` |
| 2 | Creates `versions/mc-26.1/build.gradle.kts` (4 lines + the template's `dependencies` block) |
| 3 | Creates `versions/mc-26.1/src/main/{java,resources}` |
| 4 | With `--copy-from`, copies the template's Java sources and **renames the package** (`com.example.mc1214` → `com.example.mc261`), including all imports and `package` lines, as well as the class name suffixes (`Platform1214` → `Platform261`) |
| 5 | Copies and renames the mixin configs (`examplemod-mc261.mixins.json`, `.client.mixins.json`) and adjusts `package`, `refmap` and `compatibilityLevel` (`JAVA_25`) |
| 6 | Copies the AW file as `examplemod-mc261.accesswidener` |
| 7 | Checks `[container] baselineJava` — it stays at 17 (the minimum), no intervention needed; had the new version lowered the minimum, the task would adjust it and say so |
| 8 | Checks the disjointness of the new range against all existing ones and aborts with `OMNI-1010` if it overlaps |
| 9 | Adds the CI matrix entry in `.github/workflows/integration.yml` (via a YAML patch, marked with a comment) |
| 10 | Prints a checklist of the remaining manual steps |

## 37.3 The task's output

```
> Task :addMinecraftVersion

Added Minecraft 26.1 as payload 'mc261'.

  matrix       gradle/fabricmultiloader.toml            [versions.mc261]
  project      versions/mc-26.1/build.gradle.kts
  sources      versions/mc-26.1/src/main/java/com/example/mc261/   (22 files copied from mc1214)
  mixins       examplemod-mc261.mixins.json, examplemod-mc261.client.mixins.json  (compatibilityLevel JAVA_25)
  widener      examplemod-mc261.accesswidener
  ci           .github/workflows/integration.yml        (matrix entry added)

  container baseline java stays 17 (minimum of 17, 21, 21, 25)
  effective ranges after subtraction:
      mc1201  >=1.20.1 <1.20.2
      mc1211  >=1.21 <1.21.2
      mc1214  >=1.21.4 <1.21.5
      mc261   >=26.1 <26.2
  disjointness  OK

Next steps
  1. ./gradlew :versions:mc-26.1:build          — expect compile errors; each one is a real API change
  2. fix them in versions/mc-26.1/src/main/java/com/example/mc261/
  3. ./gradlew runClient261                     — verify in-game (requires JDK 25)
  4. ./gradlew runDatagen261                    — regenerate data if formats changed
  5. ./gradlew buildUniversalJar validateUniversalJar
  6. ./gradlew integrationTestMc261
  7. update capabilities in [versions.mc261] if the new version gained or lost features
  8. add 26.1 to the README support table (or run ./gradlew omniReport)
```

## 37.4 The Java 25 jump in detail

When moving to an MC version with a higher Java requirement, exactly this happens:

| Affected | Change | Automatic? |
|---|---|---|
| `[versions.mc261].java = 25`, `javaRange = ">=25"` | new | yes (CLI parameter) |
| The version module's toolchain | JDK 25, `options.release = 25` | yes (from the matrix) |
| `payload.classfileMajor` | 69 | yes (measured while building the manifest) |
| Payload `depends.java` | `>=25` | yes |
| Container `depends.java` | stays `>=17` | yes (the minimum) |
| Container bytecode | stays class file 61 | yes (unchanged) |
| Mixin `compatibilityLevel` | `JAVA_25` | yes (scaffolding) |
| CI job | an additional matrix entry with `java: 25` | yes |
| Developer JDK | JDK 25 must be available | the toolchain resolver downloads it, otherwise `OMNI-1090` |

What does **not** happen: the universal JAR does not become unusable on Java 17. The Java 25 payload is discarded by
the solver on a Java 17 JVM (`depends.java >=25`), and its class files with major 69 are never read. The mod
continues to work unchanged on 1.20.1/Java 17. That is the core of the value proposition — and the reason the
architecture absorbs the Java jump without a special case.

## 37.5 When the version scheme changes (`1.21.x` → `26.1`)

`SemVer.parseLenient("26.1")` yields `26.1.0`, `parseLenient("1.21.4")` yields `1.21.4`. The ordering
`1.21.4 < 26.1.0` is correct (major comparison). All ranges, unions and subtractions work unchanged. Fabric itself
normalises its `minecraft` mod version to the same scheme, so the container↔loader comparison stays consistent.

The only point needing attention: `minecraftOrdinal()` (chapter 18.5) — the compact form must remain monotonic.
Definition: `major * 10000 + minor * 100 + patch` ⇒ `1.21.4` → 12104, `26.1` → 260100. Monotonic, collision-free up
to minor 99/patch 99, documented and tested (`MinecraftOrdinalTest` with the cases 1.16.5, 1.20.1, 1.21.4, 26.1,
26.10, 27.0).

## 37.6 Removing a version

```bash
./gradlew removeMinecraftVersion --id=mc1201 --confirm
```

Deletes the matrix entry, the directory and the CI entry, updates `baselineJava` (which may rise from 17 to 21!) and
points out explicitly that the **container baseline rises** — meaning common code is recompiled and may now contain
Java 21 bytecode. That is a breaking change for users of the old MC version and is therefore accompanied by a
warning and a suggested changelog entry.

---

# 38. Documentation Architecture

## 38.1 Structure

```
docs/
├── index.md                         Landing page: what, for whom, in 60 seconds
├── getting-started.md               Quick start (template → first JAR in 10 minutes)
├── concepts.md                      Container, payload, common, adapter, runtime — terms and a diagram
├── architecture.md                  How it works (a condensed version of this document)
├── gradle-plugin.md                 Plugin IDs, tasks, configuration, build troubleshooting
├── gradle-dsl.md                    generated DSL reference (all extensions, properties, defaults)
├── matrix.md                        gradle/fabricmultiloader.toml — every field, every rule
├── version-modules.md              Structure of a version module, the shared source set, mapping providers
├── common-code.md                  What may live in common, anti-patterns, the Fabric event stability table
├── api-reference.md                Entry point into the Javadoc API reference (linked)
├── registries.md                   Items, blocks, sounds, item groups, deferred registration
├── networking.md                   ChannelSpec, codecs, threading, version differences
├── commands.md                     CommandSpec, argument types, permissions
├── events.md                       Event catalogue, subscriptions, lifecycle
├── mixins.md                       Payload mixins, naming scheme, conditional mixins, pitfalls
├── access-wideners.md              shared vs. payload, merging, the @Accessor alternative
├── resources.md                    Merge rules, allowOverride, datagen, language merging
├── dependencies.md                 omniMod/omniOptionalMod/omniInclude, Fabric API, Kotlin
├── client-server.md                Side separation on three levels
├── testing.md                      FakeModContext, unit tests, integration tests, CI
├── distribution.md                 Modrinth, CurseForge, file names, changelog, checksums
├── migration.md                    Chapter 36 as a how-to
├── adding-a-version.md             Chapter 37 as a how-to
├── removing-a-version.md           Including the baseline effect
├── errors.md                       Every OMNI code with cause, diagnosis and fix (the anchor target of all messages)
├── troubleshooting.md              Symptom-oriented: “mod does not appear”, “mixin crash”, “wrong payload”
├── faq.md                          20 questions including “isn't this risky?”, “how big does the JAR get?”
├── performance.md                  Measurements, startup time, extraction, caching
├── security.md                     Threat model, hashes, Zip Slip, temp files
├── compatibility.md               Guarantees and limits (chapter 41)
├── versioning.md                   SemVer, format, schema and plugin versions (chapter 42)
├── release-guide.md                For mod authors: from tag to published file
├── contributing.md                 For framework contributors: setup, code style, the Java 8 rule, review rules
├── internals/
│   ├── loader-assumption.md        The load-bearing assumption, its derivation, conformance tests, fallback plan
│   ├── boot-sequence.md            Phases, classes, timeline
│   ├── container-format.md         The Omni v1 specification (normative)
│   ├── manifest-schema.md          JSON schema, fields, forward compatibility
│   ├── resolver.md                 Version algebra, disjointness, range subtraction
│   ├── classloading.md             The one-ClassLoader model, collision cases
│   ├── validator-rules.md          All 34 rules in detail
│   └── adr/                        ADR-001 … ADR-011 (chapter 43)
└── api/                            generated Javadoc (api, format, runtime-public)
```

## 38.2 Content requirements per page (an excerpt of the most important)

| Page | Must contain |
|---|---|
| `getting-started.md` | Prerequisites (JDK, Gradle), `git clone` of the template, `runClient`, a first code change, `buildUniversalJar`, where the file ends up, what to read next. At most 10 minutes of reading, every command copy-pasteable. |
| `concepts.md` | The diagram from chapter 8.1, the five terms, the three isolation levels, one sentence on “why not a single compilation”. |
| `common-code.md` | Permitted/forbidden references, three anti-patterns with error codes, the event stability table, a decision tree “common vs. shared vs. payload”. |
| `mixins.md` | The naming scheme, why payload isolation works, `environment` assignment, conditional mixins with a complete config, the three documented pitfalls (foreign-mod targets, `targets` strings, `compatibilityLevel`). |
| `errors.md` | Per code: title, cause, “this is what the message looks like”, diagnostic steps, fix, related codes. Enforced by `ErrorCodeDocumentationTest`. |
| `internals/loader-assumption.md` | Derivation from the loader source with class names, the conformance test list, what happens if the assumption breaks, the fallback path. **The most important page for future maintainers.** |
| `compatibility.md` | The table from chapter 41 with guarantee/limit/rationale/workaround. |
| `contributing.md` | The Java 8 rule for `format`/`api`/`runtime` with rationale, the ban on custom ClassLoaders, the requirement of an error code + doc anchor + test for every new failure path, the golden-file workflow. |

## 38.3 Generation and checking

* Site: MkDocs Material, `mkdocs.yml` in the repo, deployment via `docs.yml` to GitHub Pages.
* Javadoc: `./gradlew javadocAll` produces a combined reference for `api` + `format`; doclet configuration with
  `-Xdoclint:all,-missing` and failure on broken links.
* DSL reference: generated from the KDoc of the extension interfaces (`./gradlew generateDslReference`), so
  documentation and code cannot diverge.
* CI checks: dead links (`lychee`), missing error code sections (`ErrorCodeDocumentationTest`), and that code blocks
  in `docs/**` compile (a `docs-snippets` source set holding the examples from `getting-started`, `common-code`,
  `networking` and `registries`).

The last point is decisive for longevity: **all Java examples in the documentation exist as real, compiled sources
in the repository** and are compiled against the current API on every build. An API overhaul that makes the docs
stale therefore breaks the build.

---

Continue with [chapters 39–42 — security, performance, compatibility limits, versioning](part-10-nfr.md).
