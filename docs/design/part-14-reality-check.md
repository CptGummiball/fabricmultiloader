# 45. Reality Check

A complete, unfiltered classification of every requirement. Categories:

* **A — safely feasible**: uses exclusively documented Fabric/JVM mechanisms; verifiable.
* **B — feasible with a defined special solution**: needs a mechanism that is specified in this document.
* **C — feasible with limitations**: works, but has a named restriction or a price.
* **D — not sensible or not possible**: ruled out by the Fabric/JVM architecture; a replacement is named.

## 45.1 Category A — safely feasible

| Requirement | Why it is safe |
|---|---|
| One file, several MC versions in the `mods` folder | A container with a union `depends.minecraft` plus JiJ payloads; exclusively documented loader features |
| Version-specific mixin sets without cross-validation | The config lives in the payload mod; not loaded ⇒ never registered ⇒ never read |
| Version-specific access wideners | The “one AW file per mod” rule applies per payload |
| Version-specific refmaps | One Loom compilation per payload, unique names |
| Different Java major versions (17/21/25) in one file | `depends.java` plus the fact that inactive payloads are never defined |
| Different Fabric API minimum versions | Declared per payload |
| Different mapping providers per version | Payloads share no bytecode |
| Client-only/server-only payloads | `environment` in the payload metadata |
| One visible mod ID and version for Fabric and third parties | The container carries the primary ID; payloads are ModMenu children |
| `FabricLoader.isModLoaded("examplemod")` works | The container is a normal mod |
| No custom ClassLoader, no runtime bytecode transformation | Invariant I1, validator-checked |
| No class identity problems | Exactly one defining ClassLoader |
| Version-specific libraries per payload | Recursive JiJ inside the payload |
| Deterministic payload selection | A build-time disjointness proof + `provides` + `breaks` + a runtime assertion |
| Reproducible builds | Fixed timestamps/ordering/compression, verified in CI |
| A controlled error message on an unsupported MC version | The loader's own message with the range list **or** our report |
| A Gradle multi-project with per-version IntelliJ run configs | The standard Loom setup per module |
| Datagen per version | Loom standard |
| Unit-testing common code without Minecraft | A consequence of “no MC types in the common API” |
| Integration-testing the same file on several real servers | The Fabric installer + a probe mod |
| Modrinth/CurseForge as **one** file with several game version tags | Natively supported by both platforms |

## 45.2 Category B — feasible with a defined special solution

| Requirement | Special solution | Location |
|---|---|---|
| A good error message when MC matches but Fabric API/Java/a foreign mod does not | The container declares **no** hard `depends` on the payload alias; `preLaunch` evaluates the constraints itself and produces the report | 11.8, 29.2 |
| Guaranteeing “exactly one payload” | Fourfold: the disjointness proof (build), the `provides` alias (solver), mutual `breaks` (solver), the runtime assertion `OMNI-2003/2004` | 12.5, 9.6 |
| Priorities (catch-all + special case) despite an uncontrollable solver | `DomainDisjunctifier`: exact set subtraction at build time, the result going into the generated `depends` | 12.7 |
| No duplicate resource packs for the same mod | A container without `assets/`/`data/`; resources are merged into **every** payload; the icon lives under `omni/` | 25.1, ADR-009 |
| Library version collisions between several universal mods | The runtime as its own nested mod plus loader deduplication; a major transition with a new mod ID and package | 13.4, 42.3, ADR-008 |
| Shared access widener entries | Merged in the `named` namespace **before** the Loom remap | 17.3 |
| The dev loop without a container (`runClient1214`) | The payload is self-sufficient; `omni/payload.json` contains a copy of the container identity and entrypoints ⇒ a dev fallback with an identical lifecycle | 9.7 |
| Conditional mixins inside a payload | The declarative `ConditionalMixinPlugin` with an `omni.conditions` block, isolation-checked | 16.6 |
| Freedom from entrypoint boilerplate | The annotation processor produces `omni/entrypoints.json`, which flows into the manifest | 19.7 |
| Version-dependent features in common code without version comparisons | The `Capability<T>` system, declared in the manifest and validated | 18.8 |
| Access from common code to non-abstracted MC API | `ServiceRegistry` with the mod's own Minecraft-free interfaces | 18.7 |
| Network protocol differences (1.20.1 raw `PacketByteBuf` vs. 1.21.x `CustomPayload`) | `ChannelSpec` + `PayloadCodec` + `ByteSink`/`ByteSource`; the adapter also normalises threading | 27 |
| Integrity checking of shipped payloads | SHA-256 in the manifest, a streaming check of the active payload at startup | 39.3 |
| Meaningful crash reports | `Platform#installCrashContext` (the version-specific API lives in the payload) | 30.3 |
| Future-proofing against loader mod isolation | `commonPackaging = EMBEDDED`, implemented and covered in CI | 41.3 |
| Future-proofing against a break of the load-bearing assumption | A nightly conformance test across five loader versions with automatic issue creation; the fallback path `buildSlimJars` | 32.4, 41.2 |

## 45.3 Category C — feasible with limitations

| Requirement | Limitation | Assessment / mitigation |
|---|---|---|
| Size of the universal JAR | ≈ the sum of the payloads including N copies of the resources; the example mod is 4.82 MiB instead of 1.63 MiB | Explicitly accepted by the project owner. For a user with three instances it is the same amount net. `buildSlimJars` as the way out. |
| Payload extraction on first start | The loader extracts the selected payload into `.fabric/processedMods/` (~11 ms for 1.5 MiB, STORED) | Unavoidable without a custom ClassLoader (which we rule out). It is the same mechanism as for every JiJ library; we add no cache of our own. |
| `isModLoaded("examplemod")` with `strict = false` | `true` even though no functionality is active | Not observable in the default mode (the start aborts). `FabricMultiLoader.isActive()` is the precise query, recommended to integrators in the docs. |
| Payloads appear in the mod list | Three additional entries (`examplemod-mc1201`, …) | Presented as children of the main mod via `custom.modmenu.parent` + `badges: ["library"]`; one entry with sub-items in ModMenu. Other mod list UIs show them flat. |
| Duplicated adapter code between payloads | A functionally identical mixin/adapter exists N times | Kept small by the handle/spec design (18–22 classes per payload versus 142 common classes). The optional `shared` source set covers adjacent versions. |
| Kotlin | The Kotlin runtime must not go into the container; `fabric-language-kotlin` is MC-version-bound | Kotlin in the payload, Java in common; `fabric-language-kotlin` per payload as an `omniMod`. Warning `OMNI-1184`. |
| The Java 8 restriction on `format`/`api`/`runtime` | No records, no `var`, no `sealed`, no `List.of` in the framework modules | The price of 1.16.5 reach. Enforced by `--release 8`, compensated by the builder pattern. Mod code is unaffected. |
| Modpacks that recompress JARs | Payload hashes break ⇒ `OMNI-2013` | The message names `-Dfabricmultiloader.verify=false`. Deliberately no silent fallback. |
| The CurseForge Java tag | Only **one** Java tag per file is possible | The lowest Java version is set; the full table appears in the description. |
| Client smoke tests in CI | GPU-less client starts are historically flaky | Server tests are blocking, client smoke tests are not; Xvfb + llvmpipe, with a documented retry. |
| Quilt Loader | A different resolver with different treatment of optional nested mods | Not tested, not guaranteed. The conformance harness can take Quilt on later as another “loader version”. |
| Open upper MC bounds (`>=1.21.4`) | Will inevitably break eventually | Permitted, but `OMNI-1050` warns; the template uses closed ranges. |
| AW entries for foreign-mod classes | A fragile coupling to foreign-mod internals | Permitted only with an explicit `allowForeignAccessWidener(...)` opt-in, otherwise `OMNI-1122`. |
| Removing an old MC version | May raise `baselineJava` ⇒ common is compiled with higher bytecode | `removeMinecraftVersion` points this out and suggests a changelog entry. |

## 45.4 Category D — not sensible or not possible

| The original idea | Why not | What is built instead |
|---|---|---|
| **A runtime dispatcher selects version-specific classes from a shared class set** | Mixin configs and access wideners are registered resp. merged by the loader **before** any mod code. Sponge Mixin resolves mixin classes and their targets eagerly. A dispatcher running later cannot change that; a 1.20.1 mixin would fail at registration under 1.21.4. | The selection happens **earlier** than in the original idea — in the loader's solver, before any class is touched. The “bootstrap” survives as a lifecycle orchestrator and diagnostic instance, not as a classloading mechanism. |
| **A single compilation for all MC versions** | Bytecode references methods by name **and descriptor**. Intermediary guarantees name stability, not signature stability. `new Identifier(a,b)` → `Identifier.of(a,b)`, `PacketByteBuf` → `RegistryByteBuf`: such changes are unresolvable in one compilation. | N compilations, one file. The shared portion (85–89 %) is compiled exactly once — in the container, Minecraft-free. |
| **A custom ClassLoader for payloads** | Bypasses Knot's transformer chain: mixins and access wideners have no effect. Minecraft types would have to be delegated to the parent, whereupon Minecraft's registry, codec and reflection paths cannot find the payload classes. Duplicate class names in stack traces, unreliable breakpoints. | Exactly one ClassLoader (Knot). Isolation through non-existence rather than loader boundaries. |
| **Hooking payloads into the classpath afterwards as resources** | `KnotClassLoader#addURL` is not public API and changes between loader versions. By the time `preLaunch` runs, mixin configs are registered and the AW transformer is built — adding to them is not provided for. | Fabric JiJ: the loader performs extraction and classpath extension itself, at the right time. |
| **A multi-release JAR as the selection mechanism** | Selects by Java version, not Minecraft version (1.21.1 and 1.21.4 are both Java 21). Metadata (`fabric.mod.json`, mixin configs, refmaps) is not MR-capable. Knot's resource delegate guarantees no MR semantics. | `depends.java` for the Java axis, `depends.minecraft` for the MC axis — two separate, individually correct mechanisms. |
| **One shared access widener file for all versions** | The loader accepts exactly one AW file per mod; it is mapping-bound; Loom can remap against only one mappings version; member names can differ. | One AW per payload; shared entries merged before the remap. |
| **One shared mixin config with runtime filtering** | `IMixinConfigPlugin#shouldApplyMixin` prevents only application; `getMixins()` cannot retract entries; target resolution happens earlier. | One mixin set per payload; `ConditionalMixinPlugin` only for fine-grained control inside a version. |
| **A complete, version-neutral Minecraft API** | Rendering, world generation, codecs, components, datafixers and registry timing change too deeply and too often. A “complete” abstraction would be a permanent project permanently behind. | What is stable gets abstracted (lifecycle, simple registration, commands, networking payload data, stable events, resources, config, diagnostics). For everything else: `Services`, `Capabilities`, `unwrap`. The boundary is documented, not concealed. |
| **Core transformations beyond Mixin** | Fabric has no public transformer API; Knot's chain is not extensible. | Mixin. Where Mixin does not suffice, the mod is blocked without FabricMultiLoader too. |
| **Loading mods before the loader bootstraps Mixin** | No Fabric phase before 2.4 exists for mod code. | Not needed: payload mixins are registered in 2.4 — as early as for any normal mod. |
| **A cryptographic signature on the container** | Without key distribution, a trust anchor and revocation, a signature is semantically worthless (the attacker signs it themselves). | SHA-256 in the manifest + published sidecar sums; `container.signatures` is reserved and prepared for a future `omni/2`. |
| **A two-file solution downloading from the network** | Violates the core goal and the rules of Modrinth/CurseForge; a significant security problem. | Everything sits in the one file. |
| **A source preprocessor as a mandatory component** | A second, untyped language; worse IDE, review and refactoring experience; solves neither mixin nor AW nor packaging questions. | `:common` + the optional `shared` source set + adapters. An external preprocessor remains combinable. |
| **Payload selection via our own runtime priority rule** | The solver makes the selection before mod code runs. The solver's optimisation objective is not a specified tie-break. | Build-time disjointness (proven) + range subtraction for priorities. |

## 45.5 The one real residual risk — stated explicitly

The entire architecture rests on **one** loader property that is not formally specified:

> A nested mod candidate with unsatisfiable `depends`, which no loaded mod hard-depends on, is not selected by
> `ModSolver` — instead of producing a hard resolution failure.

Assessment:

* **Probability of change:** low. The property follows directly from modelling nested candidates as optional SAT
  variables and is the basis on which JiJ libraries with narrow MC ranges work at all. A change would break many
  existing mods.
* **Detection:** a nightly conformance test across five loader versions, with automatic issue creation and a release
  block (`conformance.yml`). A new loader is tested before users are affected.
* **Fallback path 1 (no code change):** `buildSlimJars` + publishing one file per MC version. That loses G1 (“one
  file”) but preserves everything else — common code, adapter architecture, toolchain, validation and tests stay
  unchanged.
* **Fallback path 2:** `commonPackaging = EMBEDDED` + payloads as root mods in `mods/<mcversion>/` subfolders
  (loader ≥ 0.15). One file per version, but the user must place them correctly.
* **Documentation:** `docs/internals/loader-assumption.md` is the central page for future maintainers and contains
  the derivation, the test list, the breakage scenarios and the fallback paths.

There is **no second** assumption of this weight in this design. All other mechanisms (JiJ, `depends` evaluation,
`provides` exclusivity, `environment`, per-mod mixin registration, per-mod AW, the one-ClassLoader model, `findPath`)
are documented, widely used Fabric features.

---

# 46. Final Architecture Summary

## 46.1 The system in twelve sentences

1. A universal JAR is an ordinary Fabric mod (“container”) carrying the developer's real mod ID.
2. The container contains no Minecraft-touching code, no mixins, no access widener and no `assets/`/`data/`
   entries — it is loadable on every supported version.
3. It carries the mod's platform-neutral common code (including the public mod API for third-party mods), compiled
   to the lowest Java level of the matrix.
4. Via Jar-in-Jar it contains the library mod `fabricmultiloader` and **one complete, separately built and remapped
   Fabric mod (“payload”) per supported MC version range**.
5. Every payload declares, in its own `fabric.mod.json`, its constraints (`minecraft`, `java`, `fabricloader`,
   `fabric-api`, foreign mods, `environment`), its mixin configs, its refmap and its access widener.
6. Fabric Loader's own SAT solver selects **exactly one** of them — before any class load, before mixin
   registration, before the access widener merge.
7. Payloads that are not selected are never extracted, never opened, never added to the classpath and never verified
   by the JVM; mixin, refmap, AW and class file version isolation are therefore complete.
8. The selection is deterministic, because the constraint domains are proven pairwise disjoint at **build time**
   (with range subtraction for priorities) and because the `provides` alias and mutual `breaks` enforce exclusivity.
9. `fabricmultiloader` verifies in `preLaunch` that exactly one payload is active, checks its SHA-256, runs the
   lifecycle chain container → payload → common, and otherwise produces a complete diagnostic report with the actual
   state, the constraint evaluation and an actionable instruction.
10. Mod code is written against a Minecraft-free common API (`ModContext`, `Registries`, `Networking`, `Commands`,
    `Events`, `Services`, `Capabilities`); version-specific divergence lives in slim payload adapters, with `unwrap`
    and `Services` as typed escape hatches.
11. The Gradle toolchain (four plugins, one TOML matrix as the source of truth) generates all metadata, merges
    resources and access wideners deterministically, assembles reproducibly and checks the finished artifact against
    34 rules.
12. There is no custom ClassLoader, no runtime bytecode transformation and no access to loader internals — the risky
    part of the problem is not solved but **avoided**.

## 46.2 The target project, as it finally works

```
UniversalExampleMod                                   ./gradlew buildUniversalJar
├── common/                    → com/example/common/**            in the container, Java 17, 0 MC references
├── versions/mc-1.20.1/        → examplemod-mc1201.jar   MC 1.20.1  Java 17  class file 61
├── versions/mc-1.21.1/        → examplemod-mc1211.jar   MC 1.21.x  Java 21  class file 65
├── versions/mc-1.21.4/        → examplemod-mc1214.jar   MC 1.21.4  Java 21  class file 65
└── (future) versions/mc-26.1  → examplemod-mc261.jar    MC 26.1    Java 25  class file 69
                                        │
                                        ▼
                    build/libs/universal-example-mod-1.0.0-universal.jar
```

The user puts **the same file** into their `mods` folder:

| Environment | Outcome |
|---|---|
| Minecraft 1.20.1 + Fabric 0.14.21 + Java 17 | Payload `mc1201` active; `mc1211`, `mc1214`, `mc261` never touched |
| Minecraft 1.21.1 + Fabric 0.15.11 + Java 21 | Payload `mc1211` active |
| Minecraft 1.21.4 + Fabric 0.16.9 + Java 21 | Payload `mc1214` active |
| Minecraft 26.1 + Fabric 0.17.0 + Java 25 | Payload `mc261` active — on a JVM that likewise ignores the Java 17 payloads |
| Minecraft 1.19.2 | Fabric shows the permitted ranges; no `NoClassDefFoundError`, no mixin stack trace |
| Minecraft 1.21.4 with too old a Fabric API | `OMNI-2003` with `fabric-api >=0.114.0 — REJECTED: 0.110.0 installed` and a download link |

On a successful start, exactly one line appears in the log:

```
[FabricMultiLoader] examplemod 2.0.0 → payload 'mc1214' (examplemod-mc1214 2.0.0+mc1.21.4)
                    mc=1.21.4 loader=0.16.9 fabric-api=0.114.0 java=21 side=CLIENT
```

## 46.3 Why this architecture holds up long term

| Property | Rationale |
|---|---|
| **The loader does the hard work** | Selection, extraction, classpath, mixin registration, AW merging, deduplication — all of it existing, widely used Fabric machinery. FabricMultiLoader adds metadata, determinism, diagnostics and toolchain. |
| **Errors arise at build time, not on the player's machine** | 34 validator rules, a class file scan, a reference scan, a disjointness proof, golden-file tests, a reproducibility check. |
| **Additive extensibility** | A new MC version is a new module plus a TOML block; existing payloads are untouched. A new Java jump (21 → 25) is one field. |
| **Honest boundaries** | Non-abstractable areas are named (question 25) and have a defined route (`Services`, `Capabilities`, `unwrap`) instead of a leaky abstraction. |
| **One residual risk, actively monitored** | Nightly conformance tests across five loader versions, automatic issue creation, two prepared fallback paths, a dedicated documentation page. |
| **Testability as a by-product** | Because common code knows no MC types, the bulk of the mod logic is testable in milliseconds without Minecraft. |
| **A stable mod API across MC versions** | A mod's public API lives in the container and is therefore one compilation for all versions — an advantage classic multi-JAR publishing does not have. |
| **No bets on internals** | No reflection into `net.fabricmc.loader.impl`, no custom ClassLoader, no custom transformer, no loader patches. Loader updates are therefore uncritical. |

## 46.4 The project's acceptance criterion

The project succeeds when the following flow works reproducibly:

```bash
git clone https://github.com/fabricmultiloader/fabricmultiloader-template my-mod
cd my-mod && ./bootstrap.sh          # set the mod id, name and package
./gradlew runClient1214              # dev loop
./gradlew test                       # common logic without Minecraft
./gradlew buildUniversalJar          # one file
./gradlew validateUniversalJar       # 34 rules, 0 errors
./gradlew integrationTest            # the same file booted on 1.20.1 / 1.21.1 / 1.21.4
./gradlew publishUniversal           # one file, four game version tags
```

and when the player uses that one file on every supported version without ever knowing payloads exist — and, on an
unsupported version, receives a message that tells them what to do.

---

**End of the technical design.** Back to the [navigation index](../../DESIGN.md).
