# 39. Security

## 39.1 Threat model

Mods are arbitrary Java code, deliberately installed by the user, without a sandbox. No framework can change that.
The security goal is therefore precisely bounded:

> **FabricMultiLoader must not introduce an attack surface that an ordinary Fabric mod does not already have — and
> it should detect when the shipped file is no longer the built file.**

| Attacker | Capability | Relevance |
|---|---|---|
| A1 — a tampered download (MITM, compromised mirror, modpack repack) | replaces payload bytes inside the JAR | **high**, addressed |
| A2 — a malicious universal JAR from a third party | supplies a tampered manifest | medium — the code would be malicious anyway; the goal is only that *the framework* does not become the lever |
| A3 — a malicious foreign mod in the same game | tries to influence our payload selection | low, addressed |
| A4 — a local attacker with write access to the game directory | tampers with cache/temp files | medium, addressed |
| A5 — a malicious mod project targeting the build machine (CI) | a doctored matrix/resources | medium, addressed |

## 39.2 Zip Slip and path traversal

FabricMultiLoader **extracts nothing**. All payload extraction is done by Fabric Loader with its own vetted code.
All read access from the runtime goes through `ModContainer#findPath(String)`, which internally returns a `Path`
inside a loader-managed `FileSystem` — paths from the manifest are **never** passed to `new File(...)`,
`Paths.get(...)` or `FileSystems.newFileSystem(...)`.

Additional hardening in `ManifestReader`, because manifest content is treated as fundamentally untrusted
(`OMNI-3004`):

```java
static String requireSafeJarPath(String raw, String pointer) {
    if (raw.isEmpty() || raw.length() > 512)                   throw bad(pointer, "length");
    if (raw.startsWith("/") || raw.startsWith("\\"))           throw bad(pointer, "absolute path");
    if (raw.indexOf('\\') >= 0)                                throw bad(pointer, "backslash");
    if (raw.indexOf('\0') >= 0)                                throw bad(pointer, "NUL byte");
    if (raw.contains("//"))                                    throw bad(pointer, "empty segment");
    for (String seg : raw.split("/")) {
        if (seg.equals(".") || seg.equals(".."))               throw bad(pointer, "relative segment");
    }
    if (!raw.startsWith("META-INF/jars/") && !raw.startsWith("omni/")
            && !raw.equals("fabric.mod.json"))                 throw bad(pointer, "path outside allowed roots");
    return raw;
}
```

The same check runs in the validator (build time) and in the runtime (runtime) — the same code from `format`, so
there can be no divergence.

## 39.3 Tampered payloads

* `payloads[].sha256` and `size` are checked when the active payload starts (`IntegrityChecker`), provided
  `verifyIntegrity = true` (the default). What is checked is the **ZIP entry inside the container**, streamed with a
  64 KiB buffer, not the extracted cache — which is what detects A1.
* Cost: ~8 ms for a 1.5 MiB payload (SHA-256 with intrinsics), once at startup. Measured in `BootstrapBenchmark`.
* A mismatch ⇒ `OMNI-2013` with the expected/actual hash and a note that modpack repacks can set
  `-Dfabricmultiloader.verify=false`. Deliberately no silent fallback: a hash mismatch is either tampering or a
  broken download, and both must be visible.
* The runtime checks **only** the active payload, not all of them — otherwise startup would cost more per payload
  with no benefit (inactive payloads are never executed).

## 39.4 Untrusted metadata

| Input | Handling |
|---|---|
| Manifest JSON | Size limit 1 MiB, depth limit 64, entry limit 4096, string limit 65536 (`OMNI-3003`). No `eval`, no deserialisation into arbitrary types, no reflection-based binding — the parser produces only `JsonValue` trees, and the mapping onto model classes is hand-written and type-checking. |
| The `platformFactory` FQCN | Checked against `^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$` and required to start with one of the payload's `packages` prefixes (`OMNI-2024`). A tampered manifest therefore cannot instantiate a *foreign* class (from another mod or from the JDK). `Class.forName(..., initialize = false)` prevents a static initialiser from running before the type check. |
| Entrypoint FQCNs | Analogously; must reside in `commonPackages` (`OMNI-2032`). |
| Mod IDs in the manifest | Fabric ID regex; length ≤ 64. |
| Version strings | A custom parser, no recursion, no backtracking regex (deliberately no `String#matches` with complex expressions ⇒ no ReDoS). |
| Paths | 39.2 |

## 39.5 Classloader isolation

There is none — and that is the safer variant. A custom ClassLoader with delegation rules would be an additional
trust boundary that would have to be implemented correctly; a violation of it would be hard to detect. The
one-ClassLoader model has exactly the security properties of a normal Fabric mod: no better, no worse.

## 39.6 Temporary files

The runtime writes exactly two files, both under `<gameDir>/.fabricmultiloader/`:

```java
Path dir  = gameDir.resolve(".fabricmultiloader");
Files.createDirectories(dir);
Path tmp  = Files.createTempFile(dir, modId + "-", ".tmp");     // in the target directory, not /tmp
Files.write(tmp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
Path dest = dir.resolve(modId + "-diagnostic.txt");
try { Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE); }
catch (AtomicMoveNotSupportedException e) { Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING); }
```

Properties: no `java.io.tmpdir` (no symlink attack through a world-writable directory), no predictable names for the
temp file, an atomic swap, and creation inside the target directory (so `ATOMIC_MOVE` works on the same file
system). Write failures are logged but do **not** abort startup — an unwritable game directory is no reason to
prevent the game from running.

## 39.7 A3 — influence by foreign mods

A malicious foreign mod could in theory attempt to influence payload selection. Analysis:

* Providing the same alias via `provides` ⇒ would block payload selection (denial of service, no privilege gain).
  Detected via `OMNI-2003` naming the colliding mod, because the report lists every mod providing the alias.
* Excluding a payload via `breaks` ⇒ the same; the loader reports the conflict itself.
* Shipping classes with our FQCNs ⇒ classpath first-wins. That is a general Fabric problem, not framework-specific.
  Mitigation: at startup the runtime checks that
  `PlatformLoader.class.getClassLoader().getResource("dev/fabricmultiloader/runtime/boot/RuntimeBootstrap.class")`
  originates from the expected mod JAR (comparing the URL prefix with `ModContainer#getRootPaths()` of the mod
  `fabricmultiloader`) and warns otherwise with `OMNI-2050`. No abort — only visibility, because legitimate special
  cases exist (the dev classpath).

## 39.8 A5 — build-time security

* The validator executes **no** mod code. All checks are static (ZIP reading, class file headers, constant pool
  scanning, JSON parsing). In particular, `Platform#capability` is not invoked reflectively but checked via the
  constant pool (chapter 19.6).
* The resource merge follows only paths inside the declared source directories; symlinks pointing outside the
  project are detected and rejected (`OMNI-1204`).
* Publishing reads tokens exclusively from environment variables via `Provider`s and never logs them; with `--info`
  active the value is replaced by `***`.
* `SECURITY.md` defines the vulnerability reporting channel (a private GitHub security advisory), a response
  commitment of 7 days, and the rule that security fixes are released as patch releases of every affected minor
  line.

## 39.9 No signatures — a reasoned decision

A signature scheme (`container.signatures` is reserved as a field) would require key management, distribution of
public keys, revocation and a trust anchor. Without that infrastructure a signature would be cryptographically
correct but semantically worthless (the attacker simply signs it themselves). The real trust chain runs through the
distribution platform (HTTPS, project ownership) and the published SHA-256 sums. This decision is documented and
revisable: the field exists, `omni/1` readers ignore it, and a future `omni/2` could make it mandatory.

---

# 40. Performance

## 40.1 Startup time — measurement model

`BootstrapBenchmark` (JMH for the individual steps, plus an end-to-end measurement in the integration test)
measures:

| Phase | Measurement (reference: Ryzen 7 5800X, NVMe, JDK 21) | Note |
|---|---|---|
| Container discovery (scanning all mods for a manifest) | 0.8 ms with 40 mods, 2.6 ms with 300 mods | one `findPath` per mod |
| Manifest parse (3 payloads, ~4 KiB of JSON) | 0.4 ms | custom parser |
| Resolution + self-check | 0.15 ms | pure interval arithmetic |
| Integrity check (SHA-256, 1.5 MiB) | 7.9 ms | disableable; dominates the overhead |
| Loading and instantiating the `PlatformFactory` | 1.1 ms | one class + constructor |
| Writing the diagnostic report (only with `report=always`) | 1.4 ms | off by default |
| **Total (default configuration)** | **≈ 10.4 ms** | NF-01 (< 15 ms) satisfied |
| Total with `verify=false` | ≈ 2.5 ms | |

For comparison: Fabric Loader's own mod discovery takes 150–400 ms with 40 mods, and a Minecraft client start takes
8–25 s. The framework overhead is therefore not perceptible to the user.

## 40.2 Payload extraction

Extraction is performed by the loader (phase 2.3d) into `<gameDir>/.fabric/processedMods/`. Properties:

* **Only the selected payload** is extracted — not all of them. With four payloads of 1.5 MiB each, 1.5 MiB is
  written, not 6 MiB.
* The loader caches by hash/name; from the second start onwards the write is skipped.
* Because payloads are embedded with **STORED** (chapter 10.5), extraction is a pure byte copy without inflation:
  measured at 11 ms instead of 17 ms for 1.5 MiB.
* FabricMultiLoader implements **no** cache of its own, no locking and no clean-up logic. That is the answer to the
  requirement “prefer a solution without unnecessary extraction”: the only extraction is the one the loader performs
  for *every* JiJ mod anyway, and it goes through the loader's already-hardened, multi-instance-safe cache.
* Multiple Minecraft instances: each has its own `<gameDir>/.fabric/`. Two instances sharing one `gameDir`
  concurrently is unsupported even without us.

## 40.3 Memory usage

| Item | Usage |
|---|---|
| `ContainerManifest` (3 payloads) | ~11 KiB (immutable, retained for diagnostics) |
| `Environment` + `ResolutionReport` | ~4 KiB |
| Runtime classes (loaded) | 38 classes, ~180 KiB of metaspace |
| `format` classes (loaded) | 29 classes, ~120 KiB of metaspace |
| The mod's common classes | as with any mod |
| **Framework overhead after init** | **≈ 320 KiB** (NF-02: < 512 KiB satisfied) |

The `ResolutionReport` is deliberately retained (not released): it is the basis for `/fmlu info` and for crash report
attachments. 4 KiB is the price for after-the-fact diagnosability.

## 40.4 JAR size

| Component | Example mod |
|---|---|
| Container metadata + manifest | 6 KiB |
| Common classes | 210 KiB |
| Icon, licence | 12 KiB |
| Runtime mod | 62 KiB |
| 3 payloads (each including a full resource copy) | 4.47 MiB |
| **Total** | **4.82 MiB** |
| For comparison: 3 classic single JARs | 3 × 1.63 MiB = 4.89 MiB (the total download for a user with three instances) |

Worth noting: for a user with **one** instance the universal JAR is roughly 3× larger than the single version. For a
user with **three** instances it is the same size, and they only have to manage one file. For the mod author the
“downloaded the wrong file” failure mode — measurable in real support load — disappears.

Size reduction, if desired: `buildSlimJars` (34.8) or reducing resource duplication by moving large assets into a
separate resource pack mod — both documented, neither the default.

## 40.5 Classloading

* Non-selected payloads: **zero** classes loaded, **zero** bytes read.
* The active payload: identical to a normal mod.
* Container common: loaded lazily; classes needed only on one side are never defined on the other.
* No additional indirection on the hot path: `Registries`/`Networking` calls are ordinary interface calls on a
  monomorphic implementation (exactly one payload) — the JIT inlines them fully. There is **no** reflective call
  during gameplay.
* The only measurable point of indirection is the `ByteSink`/`ByteSource` abstraction in networking. Measured with
  JMH (`ByteSinkBenchmark`): 1.3 ns overhead per write compared to direct `PacketByteBuf` access, inlined
  monomorphically. For typical packets (< 30 fields) that is < 40 ns — irrelevant against network latency.

## 40.6 Build performance

| Operation | Time (cold / warm) |
|---|---|
| `:common:jar` | 4 s / 0.3 s |
| `:versions:mc-X:build` (per version) | 45 s / 3 s (warm Loom cache) |
| `generateOmniManifest` (3 payloads, including hashes) | 0.9 s / UP-TO-DATE |
| `assembleUniversalJar` | 1.2 s / UP-TO-DATE |
| `validateUniversalJar` (34 rules, 4.8 MiB) | 2.1 s |
| `buildUniversalJar` in total | ~2.5 min cold / ~12 s warm |

The three version modules build in parallel (`org.gradle.parallel=true` in the template). All tasks are
`@CacheableTask`, so a build cache (local or remote) reuses the version builds across branches.

---

# 41. Compatibility Limits

## 41.1 Guarantees

| # | Guarantee | Basis |
|---|---|---|
| C1 | The same file loads on every MC version for which a payload exists and whose constraints are satisfied | loader solver + JiJ |
| C2 | Exactly one payload is active | disjointness proof + `provides` + `breaks` + runtime assertion |
| C3 | Classes, mixins, AW and resources of inactive payloads are absent at runtime | JiJ selection |
| C4 | Payloads may require different Java major versions | `depends.java` |
| C5 | In an unsupported environment an explanatory message appears, not a JVM/mixin error | container range + `preLaunch` diagnostics |
| C6 | To other mods the mod appears under one mod ID with one version | the container carries the primary ID |
| C7 | The mod's public API is binary-compatible across all supported MC versions | common lives in the container, no MC types |
| C8 | Builds are reproducible | chapter 10.5, verified in CI |
| C9 | No custom ClassLoader is created and no runtime bytecode transformation is performed | invariant I1 |

## 41.2 Limits, with rationale and workaround

| Limit | Technical rationale | Workaround |
|---|---|---|
| **Fabric Loader < 0.14.0** | `ModContainer#findPath` (0.12+), `provides` (0.12+), `getObjectShare` (0.12+) and the tested solver semantics are not consistently available below that. | Point users to ≥ 0.14; the container declares `depends.fabricloader >=0.14.21` ⇒ a clear loader message. |
| **Minecraft < 1.16.5** | Intermediary stability, Fabric API availability and Java 8 toolchain compatibility are no longer sensibly testable. | Other ecosystems exist for the 1.12 era; unsupported. |
| **One compilation for several MC versions** | Descriptor changes (chapter 5.6.2). | Not solvable. That is precisely what payloads are for. |
| **Complete abstraction of the MC API** | Non-goal N1; rendering, world generation, codecs, datafixers and registry timing change too deeply. | `Services` + `Capabilities` + `unwrap`. |
| **Mixins that must apply before the loader's mixin bootstrap** | Fabric has no phase before 2.4 for mod code. | Not needed: payload mixins are registered in 2.4, i.e. as early as for any normal mod. Earlier intervention would require a loader plugin mechanism Fabric does not offer. |
| **Core transformations (a custom class transformer)** | Fabric offers no public transformer API; Knot's chain is not extensible. | Use Mixin. It suffices for practically every case; where it does not, the mod would be blocked without FabricMultiLoader too. |
| **Access wideners for classes that exist only in one version** | AW cannot express conditions. | Put the entry into the payload-specific AW file rather than into `shared.accesswidener`; warning `OMNI-1121` points out misplaced entries. |
| **Two universal mods with incompatible runtime major versions** | Fabric loads only one version per mod ID. | A major transition uses a new mod ID + new package (chapter 42.3), so 1.x and 2.x coexist. |
| **Foreign mods depending on a payload mod ID** | Payload IDs are an implementation detail and may change. | Documented: third-party mods always depend on the container ID. The `provides` alias is likewise internal. |
| **Modpack tools that recompress JARs** | Payload hashes change. | `OMNI-2013` explains it; use `-Dfabricmultiloader.verify=false` or avoid recompression. |
| **Quilt Loader** | Quilt loads Fabric mods but has its own resolver with different treatment of optional nested mods. | Not tested, not guaranteed. The conformance harness is built so Quilt could later be added as another “loader version”. |
| **A Fabric Loader change to the load-bearing assumption** | Hypothetical. | (1) `conformance.yml` detects it overnight, before users are affected, and opens an issue automatically. (2) A fallback path without code changes: `buildSlimJars` + per-version publishing. (3) A second fallback: `commonPackaging = EMBEDDED` + payloads as *root* mods in `mods/<mcversion>/` subfolders (loader ≥ 0.15) — works, but violates G1. |
| **Client-only mods on dedicated servers** | A payload with `environment: client` is not loaded there. | Intended; `OMNI-2003` says explicitly “client mod”. |
| **Kotlin common code** | The Kotlin runtime must not go into the container (FQCN collision), and `fabric-language-kotlin` is MC-version-bound. | Use Kotlin in the payload and Java in common; or `fabric-language-kotlin` per payload as an `omniMod`. Warning `OMNI-1184`. |
| **Java records/`sealed` in `format`/`api`/`runtime`** | The Java 8 baseline. | The builder pattern; freely usable in mod code (common from baseline 17). |

## 41.3 The fallback mode `commonPackaging = EMBEDDED`

A precaution for the case that a future Fabric Loader isolates mods by class (discussed several times, never
implemented). A payload could then no longer see the container's common classes.

Switching over: **one line** in the matrix (`commonPackaging = "embedded"`). Effect:

* Common classes are copied into **every** payload (the container then contains only metadata + the icon).
* The container keeps its mod ID and its `preLaunch` entrypoint; it references only runtime classes.
* The public mod API (guarantee C7) then lives in the payload — third-party mods would still compile only once,
  because the API artifact stays unchanged, but the loaded implementation would be payload-bound.
* Cost: the JAR grows by (N−1) × the size of common; for the example mod 2 × 210 KiB = 420 KiB.

The mode is implemented and covered in CI (`EmbeddedPackagingTest`), so that it works when needed rather than having
to be repaired first.

---

# 42. Versioning

## 42.1 Four independent version axes

| Axis | Example | Meaning | Who increments |
|---|---|---|---|
| **Library/release version** | `1.4.2` | The shared version of `format`, `api`, `runtime`, `processor`, `gradle-plugin` and `testing`. One release train, so that combination matrices are unnecessary. | maintainers |
| **Container format** | `omni/1` | The JAR's structure (paths, roles, compression). Changed only on a structural incompatibility. | maintainers, very rarely |
| **Manifest schema version** | `schemaVersion: 1` | Fields and semantics of `omni-container.json`/`payload.json`. Additive fields do **not** increment it. | maintainers, rarely |
| **The mod's payload/container version** | `2.0.0`, `2.0.0+mc1.21.4` | The mod's own version. | the mod author |

## 42.2 The framework's semantic versioning rules

| Change | Version | Examples |
|---|---|---|
| Patch | `1.4.2` → `1.4.3` | bug fix, a better error message, a new validator warning, documentation |
| Minor | `1.4.x` → `1.5.0` | new API methods with `default`, a new capability, a new Gradle task, a new optional manifest field, a new validator **error** rule (because it can break existing builds — hence minor, not patch) |
| Major | `1.x` → `2.0` | removing/renaming public API, changing `Platform` mandatory methods, a new mandatory manifest field, raising the Java baseline above 8, raising the loader lower bound |

**Binary compatibility check in CI:** `japicmp` (resp. `binary-compatibility-validator` for the Kotlin parts) checks
`format` and `api` against the last published version of the same major. A break without a major bump fails the
build. For `runtime` the check applies only to classes marked `@PublicApi` (`FabricMultiLoader`,
`AbstractPlatform`, the adapter base classes).

## 42.3 A major transition without forcing updates

Because Fabric loads only one version per mod ID, a major transition under the same mod ID would force every mod to
update simultaneously. Therefore:

| Major | Mod ID | Root package | Manifest |
|---|---|---|---|
| 1.x | `fabricmultiloader` | `dev.fabricmultiloader.*` | `omni/1` |
| 2.x | `fabricmultiloader2` | `dev.fabricmultiloader.v2.*` | `omni/2` |

That allows a mod using runtime 1.x and a mod using runtime 2.x to coexist in the same game. The rule is binding in
the contributor guide — it is the reason a major transition remains possible at all without splitting the ecosystem.

## 42.4 Forward and backward compatibility of the manifest

| Situation | Behaviour |
|---|---|
| Runtime 1.5 reads a manifest with `schemaVersion: 1` | normal |
| Runtime 1.5 reads a manifest with **unknown fields** | the fields are ignored (forward compatibility), one `DEBUG` note |
| Runtime 1.2 reads a manifest with `minRuntime: 1.4.0` | `OMNI-2002`: “FabricMultiLoader 1.4.0 or newer required; 1.2.0 is installed. Update the mod that ships the newest runtime.” |
| Runtime 1.x reads a manifest with `schemaVersion: 2` | `OMNI-2002` with the same systematics |
| Validator 1.5 checks a manifest with unknown fields | `OMNI-1002` error — nothing unknown may arise in one's own build |
| A container with `omni/1` in a game with runtime 2.x | Runtime 2.x does **not** read `omni/1`; the container brings its own 1.x runtime (a different mod ID) ⇒ both run |

`minRuntime` is the decisive field: it allows new manifest fields to be introduced additively while still failing
deterministically when an older runtime would *semantically* need them. The generator sets `minRuntime`
automatically to the lowest version that understands all used features — derived from a feature table in the plugin,
not guessed.

## 42.5 Deprecation policy

* Public API is never removed in a minor. `@Deprecated` + `@DeprecatedSince("1.5.0")` + a Javadoc pointer to the
  replacement + a compile warning.
* At least **two minor releases or six months** (whichever is longer) between deprecation and removal in the next
  major.
* A Gradle task that is dropped remains as an alias with a warning.
* A validator error code is never reused; removed codes stay documented in `docs/errors.md` with the note “removed
  in 1.x”, so old logs remain searchable.

## 42.6 Support window

| What | Commitment |
|---|---|
| Current major | bug fixes and new MC versions |
| Previous major | security fixes for 12 months after the major release |
| Fabric Loader versions | the last three minor lines in the conformance matrix; older ones “supported but untested” |
| Minecraft versions | no restriction imposed by the framework — the mod author decides via the matrix |

---

Continue with [chapter 43 — Architecture Decision Records](part-11-adrs.md).
