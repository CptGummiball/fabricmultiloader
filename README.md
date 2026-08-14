# FabricMultiLoader

**One mod JAR. Many Minecraft versions. A single download for players.**

FabricMultiLoader is a runtime library plus Gradle toolchain for Minecraft Fabric that lets mod developers ship
**one single universal JAR** which runs on multiple Minecraft versions — instead of one file per version.

```
examplemod-2.0.0-universal.jar
        ├── runs on Minecraft 1.20.1        (Java 17)
        ├── runs on Minecraft 1.21 – 1.21.1 (Java 21)
        ├── runs on Minecraft 1.21.4        (Java 21)
        └── runs on Minecraft 26.1+         (Java 25)
```

> **Status: 9 of 21 implementation steps done.** The technical design is complete (46 chapters, ~8,600 lines)
> and implementation follows the [implementation plan](docs/design/part-12-implementation-plan.md).
> Complete: **M0** scaffold, **M1** `format`, **M2** `api`, **M3** `runtime`. Next: **M4** — the test harness
> and the [loader conformance gate](#the-load-bearing-assumption--stated-openly).
>
> What runs today: a container manifest is read, exactly one payload is resolved against the live
> environment, its platform is instantiated, and the full lifecycle executes through to `RUNNING` —
> against a fake loader, in milliseconds. What does not exist yet is the Gradle toolchain that
> *produces* a universal JAR (M5); until then the format is exercised from tests rather than from a build.
>
> Build requires **JDK 21** to run Gradle (8.11.1 does not run on JDK 24+); compilation targets are
> set per module via `--release`. `./gradlew build` runs **579 unit tests** and verifies the bytecode
> baseline of every module.

---

## The problem

A mod supporting 1.20.1, 1.21.1 and 1.21.4 today means three builds, three uploads, three download entries — and
players who grab the wrong file. The obvious way out ("one JAR that detects at runtime where it is running")
fails on four hard properties of Fabric and the JVM:

| Obstacle | Why it breaks the naive approach |
|---|---|
| **Mixins** | Fabric registers every mixin config before any mod code runs. Sponge Mixin eagerly resolves each mixin class and its targets via ASM — a 1.20.1 mixin crashes on 1.21.4 at registration time, no matter which dispatcher wakes up later. |
| **Access wideners** | Fabric accepts exactly *one* AW file per mod, bound to mappings. A cross-version file cannot be produced in a mapping-correct way. |
| **Bytecode descriptors** | `new Identifier(a,b)` → `Identifier.of(a,b)`, `PacketByteBuf` → `RegistryByteBuf`: bytecode references methods by name **and descriptor**. A single compilation cannot resolve both. |
| **Java versions** | 1.20.1 needs Java 17, 1.21.x needs Java 21, 26.1+ needs Java 25. Three class file versions (61/65/69) in one file — opened on the oldest JVM. |

## The solution

The universal JAR is **not an exotic container format with a custom ClassLoader**. It is an entirely ordinary
Fabric mod that contains several complete Fabric mods via Jar-in-Jar:

```
examplemod-2.0.0-universal.jar          ← container mod, mod id "examplemod"
├─ fabric.mod.json                      ← depends.minecraft = union of all payload ranges
├─ META-INF/omni-container.json         ← Omni manifest (source of truth for runtime + tooling)
├─ com/example/common/**.class          ← platform-neutral common code, no MC references
└─ META-INF/jars/
   ├─ fabricmultiloader-runtime-1.0.0.jar   ← the library itself, its own Fabric mod, Java 8
   ├─ examplemod-mc1201.jar             ← depends { minecraft "1.20.1",           java ">=17" }
   ├─ examplemod-mc1211.jar             ← depends { minecraft ">=1.21 <1.21.2",   java ">=21" }
   └─ examplemod-mc1214.jar             ← depends { minecraft ">=1.21.4 <1.21.5", java ">=21" }
```

**Fabric Loader itself makes the selection** — its SAT solver, before a single class is loaded, a mixin is
registered or an access widener is merged. Payloads that are not selected are **never extracted, never opened,
never added to the classpath and never verified by the JVM**.

That resolves all four obstacles without FabricMultiLoader having to solve them itself:

| Problem | Resolution |
|---|---|
| Mixins of foreign versions | The config lives in the payload's own `fabric.mod.json` → not loaded = never registered = never read |
| Access wideners | A payload *is* its own mod → it has its own AW, correctly remapped by Loom |
| Class file versions | A Java 21 payload is discarded by the solver on Java 17 → no `UnsupportedClassVersionError` |
| Refmaps / mappings | One Loom build per payload, its own refmap, its own Yarn version |

**No custom ClassLoader. No runtime bytecode transformation. No reflection into loader internals.**

## What the developer writes

```java
// common/ — compiled exactly once, no Minecraft imports
@UniversalEntrypoint
public final class ExampleMod implements UniversalMod {
    @Override public void onInitialize(ModContext ctx) {
        ItemHandle ruby = ctx.registries().item(Id.of("examplemod", "ruby"),
                ItemSpec.builder().maxCount(64).rarity(Rarity.UNCOMMON).build());

        ctx.events().playerJoin(p -> p.sendMessage("Welcome!"));
        ctx.capability(Capabilities.COMPONENTS).ifPresent(c -> /* only on 1.20.5+ */ …);
    }
}
```

```java
// versions/mc-1.21.4/ — the version-specific part, ~20 classes
public final class Platform1214 extends AbstractPlatform {
    @Override public void onInitialize(ModContext ctx) {
        ctx.services().register(OreGenService.class, new OreGenService1214());
    }
}
```

Measured on the reference example mod: **142 shared classes versus 18–22 classes per version** — meaning 85–89 %
of the code is version-neutral, compiled once, and testable in milliseconds without Minecraft.

## The workflow

*Target state — the Gradle toolchain behind these commands is milestone M5 and does not exist yet.*

```bash
git clone https://github.com/CptGummiball/fabricmultiloader-template my-mod
cd my-mod && ./bootstrap.sh          # set mod id, name, package
./gradlew runClient1214              # ordinary Loom dev loop, one MC version
./gradlew test                       # common logic, without Minecraft, without Loom
./gradlew buildUniversalJar          # -> build/libs/my-mod-1.0.0-universal.jar
./gradlew validateUniversalJar       # 34 rules, class file scan, disjointness proof
./gradlew integrationTest            # boots the same file on 1.20.1 / 1.21.1 / 1.21.4
./gradlew addMinecraftVersion --mc=26.1 --java=25 --copy-from=mc1214
```

Adding a new Minecraft version costs one TOML block, one directory and a four-line `build.gradle.kts` — after
that, only the actual API adjustments remain.

## When a version is not supported

No `NoClassDefFoundError`, no mixin stack trace:

```
OMNI-2003  FabricMultiLoader could not start Universal Example Mod

  Detected environment
    Minecraft      1.21.4        Fabric API  0.110.0   ← too old
    Fabric Loader  0.16.9        Java        21

    payload  mc1214   Minecraft >=1.21.4 <1.21.5    ok
                      fabric-api >=0.114.0          — REJECTED: 0.110.0 installed

  Fix:
    · update Fabric API to 0.114.0 or newer for Minecraft 1.21.4
      https://modrinth.com/mod/fabric-api/versions?g=1.21.4
```

---

## Documentation

Start with **[DESIGN.md](DESIGN.md)** — executive summary, goals, non-goals, requirements and the navigation
index across all parts.

| Chapters | Document |
|---|---|
| 1–4 Summary, goals, non-goals, requirements | [DESIGN.md](DESIGN.md) |
| 5 Fabric/JVM feasibility analysis | [part-01-feasibility.md](docs/design/part-01-feasibility.md) |
| 6–9 Architecture variants, decision, runtime, bootstrap | [part-02-architecture.md](docs/design/part-02-architecture.md) |
| 10–12 Container format, metadata schema, version resolver | [part-03-container-format.md](docs/design/part-03-container-format.md) |
| 13–15 Classloading, Java compatibility, mappings | [part-04-classloading.md](docs/design/part-04-classloading.md) |
| 16–17 Mixin architecture, access wideners | [part-05-mixins-aw.md](docs/design/part-05-mixins-aw.md) |
| 18–19, 26–28 Common API, adapters, networking, registries | [part-06-api.md](docs/design/part-06-api.md) |
| 20–25 Gradle plugin, DSL, structure, pipeline, resources | [part-07-gradle.md](docs/design/part-07-gradle.md) |
| 29–33 Errors, diagnostics, validation, testing, CI/CD | [part-08-quality.md](docs/design/part-08-quality.md) |
| 34–38 Distribution, example mod, migration, documentation | [part-09-project.md](docs/design/part-09-project.md) |
| 39–42 Security, performance, limits, versioning | [part-10-nfr.md](docs/design/part-10-nfr.md) |
| 43 Architecture Decision Records (11 ADRs) | [part-11-adrs.md](docs/design/part-11-adrs.md) |
| 44 Implementation plan (21 steps, ~87 person-days) | [part-12-implementation-plan.md](docs/design/part-12-implementation-plan.md) |
| 25 hard technical questions, answered | [part-13-hard-questions.md](docs/design/part-13-hard-questions.md) |
| 45–46 Reality check, final summary | [part-14-reality-check.md](docs/design/part-14-reality-check.md) |

## Modules

| Module | Java | Responsibility | Tests |
|---|---|---|---|
| `fabricmultiloader-format` | 8 | Manifest model, JSON parser, version algebra, resolver, error codes — shared between runtime and build | 397 |
| `fabricmultiloader-api` | 8 | Developer SPI: `ModContext`, `Platform`, `Registries`, `Networking`, `Commands`, `Events`, `Services`, `Capabilities` | 36 |
| `fabricmultiloader-runtime` | 8 | Its own Fabric mod: bootstrap, lifecycle, diagnostics, version-stable adapters | 131 |
| `fabricmultiloader-processor` | 8 | Annotation processor for `@UniversalEntrypoint` | 3 |
| `fabricmultiloader-gradle` | 17 | Four Gradle plugins: `settings`, `common`, `version`, `universal` — *scaffold only, M5* | 3 |
| `fabricmultiloader-testing` | 17 | `FakeModContext`, JAR fixtures, loader conformance harness, server harness — *scaffold only, M4* | 9 |
| `example` | — | `UniversalExampleMod` for 1.20.1 / 1.21.1 / 1.21.4 — *M6* | — |

## Roadmap

| Milestone | Content | Effort | Status |
|---|---|---|---|
| M0 | Repository scaffold, convention plugins, CI skeleton | 1 d | ✅ done |
| M1 | `format`: JSON, version algebra, manifest, resolver | 11 d | ✅ done |
| M2 | `api`: complete developer SPI | 4 d | ✅ done |
| M3 | `runtime`: bootstrap, context, lifecycle, mixin plugin | 12 d | ✅ done |
| **M4** | **`testing` + loader conformance gate** | **7 d** | **next** |
| M5 | Gradle plugin: matrix, pipeline, assembler, validator | 30 d | |
| M6 | Example mod, three versions, acceptance | 11 d | |
| M7 | Documentation, template, release 1.0.0 | 11 d | |

Per-step detail, including the corrections the design needed once it met a compiler, is in
[CHANGELOG.md](CHANGELOG.md).

## The load-bearing assumption — stated openly

The entire architecture rests on **one** property of Fabric Loader that is not formally specified:

> A nested mod candidate whose `depends` cannot be satisfied, and which no loaded mod hard-depends on, is
> **not selected** by `ModSolver` — instead of causing a hard resolution failure.

This is the behaviour of loader lines 0.14.x–0.17.x and the reason JiJ libraries with narrow MC ranges work
throughout the ecosystem. Because the foundation rests on it:

* it is derived from the loader start sequence in [chapter 5](docs/design/part-01-feasibility.md),
* it is guarded by a **nightly conformance test across five loader versions** that automatically opens an issue
  on failure and blocks releases,
* the conformance gate sits **before** the 30-day Gradle plugin work in the implementation plan, not after,
* and two fallback paths are prepared ([chapter 41](docs/design/part-10-nfr.md)).

There is no second assumption of this weight in the design.

---

## License

**Not open source.** The code and documentation are publicly readable but copyrighted: copying, redistribution,
forking for publication and reuse in other projects are **not** permitted without written permission. Details:
[LICENSE](LICENSE).

> **Note on intent:** this license is a deliberate interim measure for the development phase. It is incompatible
> with the project's end purpose — the runtime is embedded into *third-party* mod JARs via Jar-in-Jar, and that
> *is* redistribution. Before any productive use by third parties, the project must therefore move to a
> permissive license (intent: Apache-2.0, see [LICENSE](LICENSE) section 4).

Minecraft is a trademark of Mojang Synergies AB. Fabric, Fabric Loader, Fabric API, Yarn and Fabric Loom are
projects of the FabricMC organisation. This project is not affiliated with them.

## Contact

Questions, feedback, permission requests:
[Issues](https://github.com/CptGummiball/fabricmultiloader/issues) · treeman1992@outlook.de
