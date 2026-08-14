# 29. Error Handling

## 29.1 Normative message format

Every FabricMultiLoader message — build time as well as runtime — has exactly this structure:

```
<CODE>  <one-line title>

  <what was detected / which objects are involved>

  <why this is a problem — one to three lines>

  Fix:
    · <concrete step 1, with file/line/command>
    · <concrete step 2>

  Docs: https://fabricmultiloader.dev/docs/errors#<code-lowercase>
```

Binding rules:

1. **No stack trace without an explanation.** Where a `Throwable` cause exists it is attached as `cause`, but the
   message stands *above* the stack trace.
2. **No error code without a doc anchor.** `docs/errors.md` has a section for every code; a test
   (`ErrorCodeDocumentationTest`) checks that every code defined in `ErrorCode` appears there and vice versa.
3. **No message without at least one suggested fix.**
4. **The actual state is always complete.** The detected Minecraft, loader, API and Java versions and the side are
   printed in every runtime message, even when they are not causal for the specific failure — support cases then
   become solvable in a single round trip.

## 29.2 The most important message: no matching payload

```
OMNI-2003  FabricMultiLoader could not start Universal Example Mod

  Detected environment
    Minecraft      1.22.3
    Fabric Loader  0.17.4
    Fabric API     0.131.0
    Java           25 (Eclipse Adoptium 25.0.2)
    Side           CLIENT
    Mod            examplemod 2.0.0  (container)
    Runtime        fabricmultiloader 1.0.0

  This build of Universal Example Mod contains 3 version-specific implementations.
  None of them accepts the environment above:

    payload  mc1201   Minecraft >=1.20.1 <1.20.2   — REJECTED: Minecraft 1.22.3 is outside this range
                      Java >=17                     ok
                      fabric-api >=0.92.2           ok
    payload  mc1211   Minecraft >=1.21 <1.21.2      — REJECTED: Minecraft 1.22.3 is outside this range
    payload  mc1214   Minecraft >=1.21.4 <1.21.5    — REJECTED: Minecraft 1.22.3 is outside this range

  Supported Minecraft versions
    1.20.1
    1.21 – 1.21.1
    1.21.4

  Fix:
    · install one of the supported Minecraft versions, or
    · update Universal Example Mod:  https://modrinth.com/mod/examplemod
    · report a missing version:      https://github.com/example/examplemod/issues

  A full diagnostic report was written to
    .minecraft/.fabricmultiloader/examplemod-diagnostic.txt

  Docs: https://fabricmultiloader.dev/docs/errors#omni-2003
```

A second, more frequent case — MC matches but a side condition does not:

```
OMNI-2003  FabricMultiLoader could not start Universal Example Mod

  Detected environment
    Minecraft      1.21.4
    Fabric Loader  0.16.9
    Fabric API     0.110.0        ← too old
    Java           21
    Side           SERVER

    payload  mc1214   Minecraft >=1.21.4 <1.21.5    ok
                      Java >=21                     ok
                      fabric-api >=0.114.0          — REJECTED: 0.110.0 installed
                      cloth-config >=15.0.0 <16.0.0 — REJECTED: not installed
    payload  mc1211   Minecraft >=1.21 <1.21.2      — REJECTED: 1.21.4 is outside this range
    payload  mc1201   Minecraft >=1.20.1 <1.20.2    — REJECTED: 1.21.4 is outside this range

  Fix:
    · update Fabric API to 0.114.0 or newer for Minecraft 1.21.4
      https://modrinth.com/mod/fabric-api/versions?g=1.21.4
    · install Cloth Config 15.x
      https://modrinth.com/mod/cloth-config/versions?g=1.21.4

  Docs: https://fabricmultiloader.dev/docs/errors#omni-2003
```

This message is the single most important reason why the container does **not** hard-`depends` on the payload
alias — the loader would only have reported “requires examplemod-impl which is missing” (chapter 11.8).

## 29.3 Complete error code catalogue

### 1xxx — build time (Gradle plugin, validator)

| Code | Meaning |
|---|---|
| 1001 | Matrix file missing or unreadable |
| 1002 | Unknown field in the Omni manifest |
| 1010 | Overlapping payload domains with equal `priority` |
| 1011 | Manifest constraints ≠ payload `fabric.mod.json` constraints |
| 1012 | Two payloads differ only in `requires.mods` |
| 1013 | Gap in Minecraft version coverage (info) |
| 1014 | Container `depends.java` ≠ minimum of the payloads |
| 1015 | Payload entirely shadowed by higher-priority payloads |
| 1016 | Effective payload domain is not expressible as one constraint set (needs different Java ranges or sides per Minecraft range) |
| 1021 | Hand-written `fabric.mod.json` in module resources |
| 1022 | Payload contains `META-INF/omni-container.json` |
| 1023 | Container contains `assets/` or `data/` |
| 1024 | Container declares `mixins` or `accessWidener` |
| 1030 | Mixin config, refmap or AW name not unique across all payloads |
| 1031 | Referenced refmap missing |
| 1032 | Refmap contains classes not present in the payload |
| 1033 | Refmap without a matching mixin config (warning) |
| 1034 | Mixin package violates the naming convention |
| 1035 | `ConditionalMixinPlugin` isolation violated (access to the runtime bootstrap or MC) |
| 1036 | Forbidden reference: custom ClassLoader / loader internals |
| 1040 | Container class exceeds `baselineJavaMajor` |
| 1041 | Payload class has a differing class file major |
| 1042 | Container class references Minecraft/Fabric API/Mixin |
| 1043 | Container class outside `commonPackages` |
| 1044 | Package overlap between payloads or with common |
| 1045 | Client reference outside a `clientOnly` package |
| 1046 | `classfileMajor` incompatible with `requires.java` |
| 1047 | `baselineJavaMajor` ≠ minimum of the payload Java requirements |
| 1048 | Nested library with too high a class file major (warning) |
| 1049 | Multi-release artifacts in the container |
| 1050 | Open upper MC bound (warning) |
| 1051 | `javaRange` minimum below the MC requirement (warning) |
| 1060 | Reproducibility violation |
| 1070 | Resource digest differs between payloads (warning) |
| 1080 | Mapping inconsistency inside a payload |
| 1081 | `shared` versions with different mapping providers |
| 1082 | AW namespace in the payload ≠ `intermediary` |
| 1083 | Pinned mapping layer (warning) |
| 1090 | Required toolchain JDK unavailable |
| 1100–1110 | Mixin config rules (chapter 16.3) |
| 1120–1124 | Access widener rules (chapter 17.6) |
| 1130 | Declared capability without an implementation |
| 1140 | Duplicate entrypoint (DSL + annotation) |
| 1141 | No `common` entrypoint declared |
| 1150 | Common-reachable code references a client package |
| 1160 | `minecraft` not inside `minecraftRange` |
| 1161 | Unknown key in the matrix file |
| 1162 | Matrix entry without a directory |
| 1163 | Directory without a matrix entry |
| 1170 | Duplicate ZIP entry during assembly |
| 1180 | `omniMod` artifact is not a Fabric mod |
| 1181 | Fabric mod in `omniIncludeCommon` |
| 1182 | Second Fabric API version in the payload (warning) |
| 1183 | Forbidden library reference in `:common` |
| 1184 | Kotlin without `fabric-language-kotlin` (warning) |
| 1185 | MC-dependent version in `libs.versions.toml` (warning) |
| 1186 | Class shadowing between `shared` and a version module |
| 1187 | `shared` versions with different Java release levels |
| 1200 | Undeclared resource override |
| 1201 | Mixin/AW/refmap in `common` resources |
| 1202 | Datagen entrypoint in a release payload |

### 2xxx — runtime

| Code | Meaning |
|---|---|
| 2001 | Manifest missing or unparseable → container corrupted |
| 2002 | Manifest schema version or `minRuntime` > supported |
| 2003 | No matching payload |
| 2004 | Several payloads active simultaneously |
| 2010 | Minecraft mod container absent (unknown launch setup) |
| 2011 | Payload descriptor contradicts the container manifest |
| 2012 | Manifest mod ID ≠ carrying mod ID |
| 2013 | SHA-256 check of the active payload failed |
| 2020 | `platformFactory` class not found |
| 2021 | `platformFactory` threw an exception |
| 2022 | `platformFactory` does not implement `PlatformFactory` |
| 2023 | `platformFactory` returned `null` |
| 2024 | `platformFactory` outside the payload's declared packages |
| 2030 | Common entrypoint class not found |
| 2031 | Common entrypoint threw an exception |
| 2032 | Entrypoint outside the declared common packages |
| 2033 | Entrypoint does not implement its phase interface |
| 2040 | A payload lifecycle hook threw an exception |
| 2050 | Runtime classes loaded from an unexpected source (warning) |
| 2100 | Standalone payload without a container (info, dev/slim only) |
| 2101 | Non-strict mode: the mod stays deactivated (warning) |
| 2200 | Conditional mixin config unreadable (warning, fail-open) |
| 2201 | Conditional mixin decision (debug) |

### 3xxx — format/parser

| Code | Meaning |
|---|---|
| 3000 | Malformed JSON document (syntax error, with line, column and a caret) |
| 3001 | Required field missing (with a JSON pointer) |
| 3002 | Type error (with a JSON pointer, expected/actual) |
| 3003 | Input limit exceeded (size/depth/count) |
| 3004 | Invalid mod ID / invalid identifier |
| 3010 | Version string unparseable (warning, `UNKNOWN`) |
| 3011 | Invalid version predicate |

### 4xxx — API misuse (a programming error by the mod author)

| Code | Meaning |
|---|---|
| 4001 | Invalid lifecycle transition |
| 4002 | Registry/networking call in the wrong phase |
| 4010 | `ServiceRegistry#get` for an unregistered type |
| 4011 | `Capability` unavailable but used without a check |
| 4012 | `unwrap` with the wrong target type |
| 4013 | `ChannelHandle#sendToServer` called on the server (or vice versa) |

## 29.4 Exception model

```java
package dev.fabricmultiloader.format.error;

public class OmniException extends RuntimeException {
    private final ErrorCode code;
    private final String report;          // the multi-line formatted message (may be null)

    public OmniException(ErrorCode code, String report)                  { … }
    public OmniException(ErrorCode code, String report, Throwable cause) { … }

    public ErrorCode code()  { return code; }
    public String    report(){ return report; }

    @Override public String getMessage() { return code.id() + "  " + firstLine() + "\n\n" + report; }
}

/** For 4xxx only: signals an error in the mod code, not in the framework. */
public final class OmniApiMisuseException extends OmniException { … }
```

Why a `RuntimeException` and not a checked exception: the bootstrap runs inside Fabric entrypoints whose signatures
do not permit checked exceptions. The complete message is passed through unchanged by
`EntrypointUtils`/`FormattedException` into the Fabric error GUI resp. the server log — which is why the full report
sits in `getMessage()` and not in a separate channel.

**No** loader-internal class (`net.fabricmc.loader.impl.FormattedException`, `FabricGuiEntry`) is addressed
reflectively. The price: the GUI shows “Mod initialization failed” as the title and our report as the detail text.
The gain: the runtime works unchanged across loader versions 0.14–0.17+.

## 29.5 Strict and non-strict mode

| Mode | Trigger | Behaviour on `OMNI-2003` |
|---|---|---|
| **strict** (default) | `container.strict = true`, no override | An `OmniException` from `preLaunch` ⇒ the game does not start. Rationale: a half-loaded mod produces follow-on errors nobody can attribute later. |
| **lenient** | `container.strict = false` or `-Dfabricmultiloader.strict=false` | Warning `OMNI-2101`, the container deactivates itself, the game starts. For server admins and modpack authors who want to tolerate a mod temporarily. The container registers **nothing**, and `FabricMultiLoader.isActive("examplemod")` returns `false`. |
| **verbose-strict** | `-Dfabricmultiloader.strict=verbose` | Like strict, plus a full dump of the manifest and environment |

The system property override is global; `-Dfabricmultiloader.strict.examplemod=false` allows it per mod.

## 29.6 Behaviour with a corrupted JAR

| Damage | Detection | Message |
|---|---|---|
| JAR truncated / not a valid ZIP | Fabric `ModDiscoverer` | Loader: “Could not open mod jar”, with the file name |
| `fabric.mod.json` missing | Fabric | loader message |
| `META-INF/omni-container.json` missing but `MANIFEST.MF` declares Omni | runtime, container scan | `OMNI-2001` including the file's SHA-256 and a “re-download” hint |
| Payload JAR removed | Fabric (a nested JAR listed in `jars[]` is missing) | loader message; additionally `OMNI-2003`, which lists the missing payload as “not loaded” |
| Payload tampered with | runtime `IntegrityChecker` | `OMNI-2013` with the expected/actual hash |
| Manifest tampered with (mod ID changed) | runtime | `OMNI-2012` |

---

# 30. Diagnostics

## 30.1 Start banner

Always, at `INFO`, one line per container (chapter 9.8). Deliberately terse: a modpack with 40 universal mods must
not flood the log.

## 30.2 Diagnostic report

Written on every failure, and additionally on every start when `-Dfabricmultiloader.report=always` is set.
Location: `<gameDir>/.fabricmultiloader/<modId>-diagnostic.txt`. Atomic (temp + `ATOMIC_MOVE`).

Content: timestamp, the complete environment, container metadata, every payload with every constraint and its
evaluation, capability lists, a list of all loaded mods with versions (alphabetical), active system properties with
the prefix `fabricmultiloader.`, and — if present — the causal stack trace.

Additionally `<gameDir>/.fabricmultiloader/<modId>-last-launch.json` on success (machine-readable, for modpack tools
and support bots).

## 30.3 Crash report integration

Minecraft crash reports support custom sections; the API for that is version-dependent, so it lives in the payload:

```java
@Override
public void installCrashContext(CrashContext ctx) {
    ctx.add("Active payload", "mc1214 (Minecraft 1.21.4, Java 21, Yarn 1.21.4+build.8)");
    ctx.add("Container", "examplemod 2.0.0 (omni/1, runtime 1.0.0)");
    ctx.add("Capabilities", "registries, commands, networking.typed, components");
}
```

`CrashContextImpl` collects the entries and the payload adapter attaches them via the version-specific API
(`CrashReportCallables`/`SystemDetails`, named differently per version). The result: every crash report states which
payload was active — the single most important data point for bug reports about a multi-version mod.

## 30.4 Debug mode

| Property | Effect |
|---|---|
| `-Dfabricmultiloader.debug=true` | Full dump: manifest, resolution report, timings per phase, payload extraction path, classloader identity |
| `-Dfabricmultiloader.debug.timing=true` | Timings only (nanosecond resolution) |
| `-Dfabricmultiloader.verify=false` | Disables the SHA-256 check (for modpack repacks that recompress payloads) |
| `-Dfabricmultiloader.strict=false\|verbose` | See 29.5 |
| `-Dfabricmultiloader.report=always` | Report even on success |
| `-Dfabricmultiloader.slim=true` | Permits a standalone payload outside dev |

## 30.5 Runtime introspection for third parties

```java
package dev.fabricmultiloader.api;

public final class FabricMultiLoader {
    public static boolean isActive(String containerModId);
    public static java.util.Optional<String> activePayload(String containerModId);
    public static java.util.Optional<PlatformInfo> platformInfo(String containerModId);
    public static java.util.List<String> containers();
    public static String diagnosticReport(String containerModId);   // the same text as the file
}
```

Additionally, one ObjectShare entry per container, `"<modId>:omni"`, holding a `ContainerHandle`, so foreign mods and
tools can access the runtime without a compile dependency (reflectively or via `instanceof`).

The debug command `/fmlu` is registered by the runtime through `CommandsImpl`, but only when
`-Dfabricmultiloader.debug=true` is set — in normal operation it does not exist, so no command names are occupied.
`/fmlu list`, `/fmlu info <modid>`, `/fmlu report <modid>`.

---

# 31. Validation

## 31.1 `./gradlew validateUniversalJar`

The validator works **exclusively on the finished JAR** — not on Gradle models. It therefore checks the artifact
that is actually published, and it can also be applied to foreign universal JARs
(`./gradlew validateExternalJar --jar=path/to/foo-universal.jar`).

Output: `build/reports/omni/validation.txt` (human-readable), `validation.json` (machine-readable, for CI
annotations), a non-zero exit code on errors, optionally also on warnings (`validation { failOnWarnings }`).

## 31.2 The 34 rules

| # | Rule | Codes | Severity |
|---|---|---|---|
| 1 | Structure: `fabric.mod.json`, `META-INF/omni-container.json`, `MANIFEST.MF` present and consistent | 2001, 1002 | error |
| 2 | Manifest valid against schema `omni/1`, no unknown fields | 1002, 3001, 3002 | error |
| 3 | Every payload declared in the manifest exists as a ZIP entry | 1170 | error |
| 4 | Every payload entry is declared in `fabric.mod.json.jars[]` and vice versa | 1011 | error |
| 5 | SHA-256 and size of every payload match | 2013 | error |
| 6 | Payload domains are pairwise disjoint | 1010, 1012, 1015 | error |
| 7 | Manifest constraints == payload `fabric.mod.json` `depends` | 1011 | error |
| 8 | Container `depends.minecraft` == union of the effective payload ranges | 1011 | error |
| 9 | Container `depends.java` == minimum of the payload Java minima | 1014, 1047 | error |
| 10 | Gap analysis of MC coverage | 1013 | info |
| 11 | Open upper MC bounds | 1050 | warning |
| 12 | The container contains no `assets/`, `data/` | 1023 | error |
| 13 | The container declares no `mixins`, no `accessWidener` | 1024 | error |
| 14 | Container classes reside only in `commonPackages` | 1043 | error |
| 15 | Container classes reference no Minecraft/Fabric API/Mixin | 1042 | error |
| 16 | Container class file majors ≤ `baselineJavaMajor` | 1040 | error |
| 17 | Payload class file majors == `classfileMajor` and consistent with `requires.java` | 1041, 1046 | error |
| 18 | No multi-release structures | 1049 | error |
| 19 | Package disjointness across all payloads and common | 1044 | error |
| 20 | Every mixin config of the payload is registered and exists | 1109, 1110 | error |
| 21 | Mixin config rules (package, class list, `required`, `compatibilityLevel`) | 1100–1107 | error |
| 22 | Refmap present, valid, containing only own classes | 1031, 1032, 1033 | error/warning |
| 23 | Config, refmap and AW names unique across all payloads | 1030 | error |
| 24 | The payload AW has namespace `intermediary` and is declared | 1082, 1123 | error |
| 25 | AW targets resolvable in the payload's mappings | 1121 | warning |
| 26 | Client references only in `clientOnly` packages; no common path leads there | 1045, 1150 | error |
| 27 | Client mixins only in `environment: client` configs | 1105, 1106 | error |
| 28 | Entrypoints: at least one `common`, all classes present in the container, correct interface | 1141, 2030 | error |
| 29 | Every payload's `platformFactory` exists in the payload and implements `PlatformFactory` | 2020, 2022 | error |
| 30 | Declared capabilities are served by the platform class | 1130 | warning |
| 31 | No unexpected duplicates (identical paths from several sources), no signature files, no empty directories | 1170 | error |
| 32 | No forbidden references (custom ClassLoader, loader internals) in container and payloads | 1036 | error |
| 33 | Runtime mod embedded, version inside the `depends` range, hash correct | 1011 | error |
| 34 | Reproducibility: timestamps, entry order, compression methods as specified | 1060 | error |

## 31.3 Example output

```
FabricMultiLoader Validation Report
  artifact  build/libs/examplemod-2.0.0-universal.jar
  size      4.82 MiB   sha256 7c9a1f…e2
  format    omni/1     schema 1     runtime 1.0.0
  validator fabricmultiloader-gradle 1.0.0

CONTAINER
  mod id            examplemod
  version           2.0.0
  baseline java     17  (classfile 61)
  common packages   com.example.common
  classes           142   (max classfile major found: 61)  OK
  resources         0 assets/ + 0 data/ entries             OK
  mixins            none                                    OK
  access widener    none                                    OK

PAYLOADS
  id      minecraft (effective)    java   env  classfile  mixins  aw   size      status
  mc1201  >=1.20.1 <1.20.2         >=17   *    61         2       yes  1.42 MiB  OK
  mc1211  >=1.21 <1.21.2           >=21   *    65         2       yes  1.51 MiB  OK
  mc1214  >=1.21.4 <1.21.5         >=21   *    65         2       yes  1.54 MiB  OK

DISJOINTNESS
  mc1201 ∩ mc1211 = ∅    mc1201 ∩ mc1214 = ∅    mc1211 ∩ mc1214 = ∅        OK
  union = [">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"]        OK
  container depends.minecraft matches union                                 OK

RULES   34 executed · 34 passed · 0 failed · 2 warnings · 1 info

  WARN  OMNI-1121  access widener target not found in mappings
        payload mc1201 · line 4
        mutable field net/minecraft/entity/LivingEntity activeItemStack …
        This entry has no effect on Minecraft 1.20.1.
        Fix: remove the line or move it to a payload where the field exists.

  WARN  OMNI-1050  open upper Minecraft bound
        payload mc1214 declares no upper bound in gradle/fabricmultiloader.toml
        …

  INFO  OMNI-1013  Minecraft coverage gaps: 1.20.2 – 1.20.6, 1.21.2 – 1.21.3

RESULT  PASS (0 errors)
```

## 31.4 Disabling rules with a mandatory justification

```kotlin
validation {
    ignore("OMNI-1121", because = "AW target exists only from 1.21; the entry stays in the shared AW for clarity")
}
```

`because` is a mandatory parameter. Ignored rules are listed in the report as `IGNORED` with their justification so
a reviewer sees them. Error-class rules with safety relevance (5, 6, 7, 8, 9, 16, 17, 19, 32) are **not** disableable
(`OMNI-1003`).

---

# 32. Testing

## 32.1 Test levels

| Level | Scope | Duration | Location |
|---|---|---|---|
| **T1 unit** | `format`, `runtime` (without Minecraft), `gradle-plugin` logic | < 20 s | `*/src/test/java` |
| **T2 Gradle functional** | TestKit: real builds of synthetic projects | < 4 min | `gradle-plugin/src/functionalTest` |
| **T3 loader conformance** | the real Fabric Loader against synthetic containers across the loader matrix | < 5 min | `testing/src/conformanceTest` |
| **T4 integration** | real Minecraft servers per matrix version with the real universal JAR | 8–20 min | `example` + `ServerBootTestTask` |
| **T5 client smoke** | a real Minecraft client up to the title screen (Xvfb) | 10–25 min | CI only |

## 32.2 T1 — unit tests (concrete test classes)

`format`:

| Test class | Checks |
|---|---|
| `SemVerParseTest` | all normalisation rules from chapter 12.2 (the table as a parameterised test) |
| `SemVerCompareTest` | SemVer 2.0.0 ordering including prereleases, build neutrality, `UNKNOWN` |
| `VersionPredicateParseTest` | `*`, `=`, `>=`, `>`, `<=`, `<`, AND chains, failure cases (`OMNI-3011`) |
| `VersionPredicateEquivalenceTest` | **differentially against `net.fabricmc.loader.api…VersionPredicate`**, 4096 generated cases × every loader version in the matrix |
| `VersionRangeAlgebraTest` | union/intersect/subtract, edge cases at prerelease boundaries, idempotence, commutativity |
| `DomainDisjunctifierTest` | 30 scenarios including “mc1214 beats mcModern”, Java variants, client/server variants, `OMNI-1015` |
| `ManifestReaderTest` | required fields, type errors with pointers, unknown fields, limits (`OMNI-3003`) |
| `ManifestRoundTripTest` | read→write→read is byte-identical; canonical key order |
| `JsonParserTest` | the RFC 8259 suite, position information, limits |
| `PayloadMatcherTest` | every constraint kind individually and combined; completeness of the rejection list |
| `UnionNormalizationTest` | merging of adjacent intervals, canonical predicate output |
| `Sha256Test` | streaming hash against the JDK reference |
| `JavaVersionsTest` | 8/11/17/21/25/30, `1.8.0_402`, the class file major formula |
| `ErrorCodeDocumentationTest` | every code has a doc section and vice versa |
| `MessagesSnapshotTest` | golden-file comparison of the formatted messages (catches accidental text changes) |

`runtime` (with `fabricmultiloader-testing`):

| Test class | Checks |
|---|---|
| `EnvironmentDetectorTest` | against `FakeFabricLoader` (an interface façade in `testing`) |
| `ContainerDiscoveryTest` | several containers, no container, a broken manifest |
| `PayloadActivationTest` | exactly one, none, several (`OMNI-2003/2004`) |
| `LifecycleStateMachineTest` | permitted/forbidden transitions, idempotence, `OMNI-4001` |
| `IntegrityCheckerTest` | correct/incorrect hash, check disabled |
| `DevFallbackTest` | standalone payload in dev and with `-Dfabricmultiloader.slim=true` |
| `DiagnosticReportTest` | golden files of the two reports from chapter 29.2 |
| `ConditionalMixinPluginTest` | condition evaluation, fail-open on a broken config |
| `LogBridgeTest` | with and without SLF4J on the classpath (two classpath variants via Surefire profiles) |

`gradle-plugin` (the T1 portion, without the Gradle runtime):

`MatrixParserTest`, `ClassfileScannerTest`, `ReferenceScannerTest`, `ResourceMergePlanTest`,
`AccessWidenerMergeTest`, `RuleSetTest`, `ReportFormatterTest`, `ManifestGeneratorTest`,
`ModJsonGeneratorTest` (golden files for both `fabric.mod.json` variants).

## 32.3 T2 — Gradle functional tests (TestKit)

| Test | Scenario | Expectation |
|---|---|---|
| `MinimalProjectTest` | 1 payload, no mixins, no AW | green build, JAR structure exactly as specified |
| `ThreeVersionProjectTest` | 1.20.1/1.21.1/1.21.4 | 3 payloads, disjoint ranges, correct union |
| `MixedJavaProjectTest` | Java 17 + 21 + 25 payloads | class file majors 61/65/69, container 61, `depends.java >=17` |
| `OverlapRejectedTest` | overlapping ranges, equal priority | the build fails with `OMNI-1010`, message text asserted |
| `PrioritySubtractionTest` | catch-all + specific | effective ranges as in chapter 12.7 |
| `ResourceOverrideTest` | an undeclared override with `strictOverrides` | `OMNI-1200` |
| `LangMergeTest` | `mergeLanguageFiles` | key union, sorted output |
| `ContainerPurityTest` | an MC import in `:common` | `OMNI-1042` |
| `ReproducibilityTest` | building twice | identical SHA-256 |
| `ConfigurationCacheTest` | `--configuration-cache` twice | the second run reports “reused”, 0 problems |
| `UpToDateTest` | a second run without changes | all tasks `UP-TO-DATE` |
| `AddVersionTaskTest` | `addMinecraftVersion` | matrix entry, directory, stubs, then a green build |
| `SlimJarTest` | `buildSlimJars` | one runnable standalone JAR per payload |

## 32.4 T3 — the loader conformance harness (the load-bearing assumption)

Goal: prove that every supported Fabric Loader version **discards** nested mods with unsatisfiable `depends`
instead of failing.

```java
// testing/src/main/java/dev/fabricmultiloader/testing/LoaderConformanceHarness.java
public final class LoaderConformanceHarness {
    /**
     * Builds a synthetic container with N payloads, starts the real Fabric Loader
     * headlessly (without Minecraft: a dedicated GameProvider-free test path through
     * net.fabricmc.loader.impl.discovery + ModSolver, invoked as a library),
     * and returns the set of selected mod IDs.
     */
    public Set<String> resolve(LoaderVersion loader, SyntheticContainer container, FakeEnv env);
}
```

Realisation: the loader is loaded as a **library** (`net.fabricmc:fabric-loader:<v>`) into an isolated
`URLClassLoader` of the test process — the only place in the project where a custom ClassLoader exists, and it lives
exclusively in test code (validator rule 32 applies to production artifacts). Interaction happens reflectively via
the `impl` classes; if a loader update breaks that reflection, the test fails and the assumption is re-checked
manually — exactly the desired early warning system.

| Conformance test | Expectation |
|---|---|
| `NestedUnsatisfiableIsDropped` | A payload with `minecraft 1.20.1` is discarded on MC 1.21.4, the container loads |
| `ExactlyOneSelected` | With three disjoint payloads, exactly one is selected |
| `ProvidesExclusivity` | Two payloads with the same `provides` can never both be loaded |
| `BreaksExclusivity` | Mutual `breaks` are respected |
| `JavaDependencyEvaluated` | `depends.java >=21` discards the payload on Java 17 |
| `EnvironmentEvaluated` | `environment: client` is not loaded on the server |
| `RuntimeDeduplication` | Two containers with runtime 1.0.0 and 1.1.0 ⇒ only 1.1.0 loads |
| `ContainerRangeError` | MC outside the union ⇒ a loader error with the ranges in its text |

Matrix: loaders `0.14.21`, `0.15.11`, `0.16.9`, `0.16.14`, `0.17.x` (the newest patch of each) — nightly in CI
(`schedule`) and on every release. A new loader is therefore tested **before** users are.

## 32.5 T4 — integration tests with real Minecraft servers

`ServerBootTestTask` per payload:

```
1. working directory  build/omni/itest/<payloadId>/   (empty)
2. create the Fabric server launcher:
     java -jar fabric-installer-1.0.3.jar server \
          -mcversion <matrix.minecraft> -loader <matrix.loader> \
          -dir . -downloadMinecraft
3. write eula.txt (only when integrationTests.acceptEula = true;
   otherwise the task fails with an explanation)
4. server.properties: level-type=flat, online-mode=false, max-players=1,
   view-distance=4, spawn-protection=0, level-seed=omni
5. populate mods/:
     · examplemod-2.0.0-universal.jar          (the real artifact)
     · fabric-api-<matrix.fabricApi>.jar
     · one mod per [versions.X.dependencies] entry with a range
     · omni-itest-probe.jar                    (from fabricmultiloader-testing)
6. start: java -Xmx2G -Dfabricmultiloader.report=always -jar fabric-server-launch.jar nogui
7. the probe mod:
     · reads FabricMultiLoader.activePayload("examplemod")
     · asserts the expected payloadId (via -Domni.itest.expect=mc1214)
     · asserts that exactly one payload is loaded
     · runs <ticks> ticks, then invokes a mod command via the server console
       (/ruby info) and checks the response in the log
     · writes build/omni/itest/<id>/result.json and stops the server
8. the task checks: exit code 0, result.json.status == "ok",
   the log free of "OMNI-2", "Mixin apply failed", "Exception in thread"
```

The test uses the **identical** universal JAR for all payloads — exactly the proof demanded by the brief, “the same
file in every environment”.

| Test case | Environment |
|---|---|
| `itest mc1201` | MC 1.20.1, Java 17, loader 0.14.21, Fabric API 0.92.2 |
| `itest mc1211` | MC 1.21.1, Java 21, loader 0.15.11, Fabric API 0.102.0 |
| `itest mc1214` | MC 1.21.4, Java 21, loader 0.16.9, Fabric API 0.114.0 |
| `itest unsupported` | MC 1.19.2 ⇒ the expected controlled abort; the log **must** contain the range list and **must not** contain a `NoClassDefFoundError` |
| `itest wrongJava` | MC 1.21.4 started with Java 17 ⇒ the loader refuses; the log contains the Java requirement |
| `itest oldFabricApi` | MC 1.21.4 with Fabric API 0.110.0 ⇒ `OMNI-2003` with the correct reason |
| `itest lenient` | as `unsupported`, but with `-Dfabricmultiloader.strict=false` ⇒ the server starts, `OMNI-2101` in the log |

## 32.6 T5 — client smoke test

The client is booted up to `ClientLifecycleEvents.CLIENT_STARTED` (this event has been stable since 1.16); the probe
mod then calls `MinecraftClient#scheduleStop`. Execution runs under `xvfb-run` on Linux with software rendering via
Mesa/llvmpipe. For MC versions where Fabric offers the client gametest API, a title screen screenshot is additionally
stored as a CI artifact.

The client test is **not** release-blocking (in CI it runs with `continue-on-error: false` only on `main`, but with a
documented retry), because GPU-less client starts have historically been flaky. The server test is blocking.

## 32.7 Test helpers for mod developers (`fabricmultiloader-testing`)

```java
// unit test of common code without Minecraft
ModContext ctx = FakeModContext.builder()
        .modId("examplemod").modVersion("2.0.0")
        .minecraft("1.21.4").fabricApi("0.114.0").java(21).side(Side.SERVER)
        .capability(Capabilities.COMPONENTS, new FakeComponents())
        .service(OreGenService.class, (v, p, min, max) -> { /* no-op */ })
        .build();

new ExampleMod().onInitialize(ctx);

assertThat(ctx.recordedItems()).containsKey(Id.of("examplemod", "ruby"));
assertThat(ctx.recordedChannels()).contains(Id.of("examplemod", "ruby_sync"));
```

`FakeModContext` records all registrations, channels, commands and event subscriptions. The entire common code —
i.e. the bulk of the mod logic — is therefore testable **without Minecraft and without Loom**, in milliseconds. That
is a by-product of the architecture (P1: no MC types in the common API) and a genuine argument for mod authors.

---

# 33. CI/CD

## 33.1 Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yml` | push, pull_request | build the framework, T1+T2, build and validate the example mod |
| `integration.yml` | push to `main`, a pull request labelled `integration`, nightly | T4 + T5 across the matrix |
| `conformance.yml` | nightly, manual, release | T3 across the loader matrix |
| `release.yml` | tag `v*` | the full pipeline + Maven, Modrinth, CurseForge and GitHub releases |
| `docs.yml` | push to `main` (path `docs/**`) | build and deploy the documentation site |

## 33.2 `build.yml` (complete)

```yaml
name: build

on:
  push:
    branches: [ main, "release/**" ]
  pull_request:

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  framework:
    name: framework (unit + functional)
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - name: Set up JDKs 17, 21, 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: |
            17
            21
            25

      - uses: gradle/actions/setup-gradle@v4
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Unit tests
        run: ./gradlew :format:test :api:test :runtime:test :processor:test --stacktrace

      - name: Gradle functional tests
        run: ./gradlew :gradle-plugin:test :gradle-plugin:functionalTest --stacktrace

      - name: Configuration cache check
        run: |
          ./gradlew :example:buildUniversalJar --configuration-cache
          ./gradlew :example:buildUniversalJar --configuration-cache | tee cc.log
          grep -q "Configuration cache entry reused" cc.log

      - name: Publish test report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            */build/reports/tests/**
            */build/test-results/**

  example:
    name: example mod (build + validate + reproducible)
    runs-on: ubuntu-24.04
    needs: framework
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: |
            17
            21
            25
      - uses: gradle/actions/setup-gradle@v4

      - name: Build universal jar
        run: ./gradlew :example:buildUniversalJar --stacktrace

      - name: Validate universal jar
        run: ./gradlew :example:validateUniversalJar -Pomni.failOnWarnings=true --stacktrace

      - name: Verify reproducibility
        run: ./gradlew :example:verifyReproducible --stacktrace

      - name: Matrix report
        run: ./gradlew :example:omniReport && cat example/build/reports/omni/matrix.md >> $GITHUB_STEP_SUMMARY

      - name: Checksums
        run: |
          cd example/build/libs
          sha256sum *.jar | tee SHA256SUMS.txt

      - uses: actions/upload-artifact@v4
        with:
          name: universal-jar
          path: |
            example/build/libs/*.jar
            example/build/libs/SHA256SUMS.txt
            example/build/reports/omni/**
```

## 33.3 `integration.yml` (complete)

```yaml
name: integration

on:
  push:
    branches: [ main ]
  pull_request:
    types: [ labeled, synchronize ]
  schedule:
    - cron: "17 3 * * *"
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build-jar:
    if: github.event_name != 'pull_request' || contains(github.event.pull_request.labels.*.name, 'integration')
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :example:buildUniversalJar
      - uses: actions/upload-artifact@v4
        with: { name: itest-jar, path: example/build/libs/*-universal.jar }

  server:
    name: server ${{ matrix.payload }} (mc ${{ matrix.minecraft }}, java ${{ matrix.java }})
    needs: build-jar
    runs-on: ubuntu-24.04
    strategy:
      fail-fast: false
      matrix:
        include:
          - payload: mc1201
            minecraft: "1.20.1"
            java: 17
            loader: "0.14.21"
            fabricApi: "0.92.2+1.20.1"
            expect: ok
          - payload: mc1211
            minecraft: "1.21.1"
            java: 21
            loader: "0.15.11"
            fabricApi: "0.102.0+1.21.1"
            expect: ok
          - payload: mc1214
            minecraft: "1.21.4"
            java: 21
            loader: "0.16.9"
            fabricApi: "0.114.0+1.21.4"
            expect: ok
          - payload: unsupported
            minecraft: "1.19.2"
            java: 17
            loader: "0.14.21"
            fabricApi: "0.76.0+1.19.2"
            expect: rejected-by-loader
          - payload: oldFabricApi
            minecraft: "1.21.4"
            java: 21
            loader: "0.16.9"
            fabricApi: "0.110.0+1.21.4"
            expect: omni-2003
          - payload: lenient
            minecraft: "1.19.2"
            java: 17
            loader: "0.14.21"
            fabricApi: "0.76.0+1.19.2"
            expect: lenient-warning
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "${{ matrix.java }}" }
      - uses: gradle/actions/setup-gradle@v4
      - uses: actions/download-artifact@v4
        with: { name: itest-jar, path: artifact }

      - name: Boot server
        run: |
          ./gradlew :example:integrationTestScenario \
            --scenario=${{ matrix.payload }} \
            --minecraft=${{ matrix.minecraft }} \
            --loader=${{ matrix.loader }} \
            --fabric-api=${{ matrix.fabricApi }} \
            --expect=${{ matrix.expect }} \
            --jar=$GITHUB_WORKSPACE/artifact/$(ls artifact) \
            --stacktrace

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: itest-${{ matrix.payload }}
          path: |
            example/build/omni/itest/**/logs/**
            example/build/omni/itest/**/result.json
            example/build/omni/itest/**/.fabricmultiloader/**

  client-smoke:
    name: client smoke ${{ matrix.payload }}
    needs: build-jar
    runs-on: ubuntu-24.04
    strategy:
      fail-fast: false
      matrix:
        include:
          - { payload: mc1201, minecraft: "1.20.1", java: 17, loader: "0.14.21", fabricApi: "0.92.2+1.20.1" }
          - { payload: mc1214, minecraft: "1.21.4", java: 21, loader: "0.16.9",  fabricApi: "0.114.0+1.21.4" }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "${{ matrix.java }}" }
      - uses: gradle/actions/setup-gradle@v4
      - uses: actions/download-artifact@v4
        with: { name: itest-jar, path: artifact }
      - name: Install Xvfb and Mesa
        run: sudo apt-get update && sudo apt-get install -y xvfb libgl1-mesa-dri mesa-utils
      - name: Boot client to title screen
        run: |
          xvfb-run -a --server-args="-screen 0 1280x720x24" \
            ./gradlew :example:clientSmokeTest \
              --scenario=${{ matrix.payload }} \
              --minecraft=${{ matrix.minecraft }} \
              --loader=${{ matrix.loader }} \
              --fabric-api=${{ matrix.fabricApi }} \
              --jar=$GITHUB_WORKSPACE/artifact/$(ls artifact)
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: client-smoke-${{ matrix.payload }}
          path: example/build/omni/csmoke/**
```

## 33.4 `conformance.yml`

```yaml
name: conformance

on:
  schedule: [ { cron: "41 2 * * *" } ]
  workflow_dispatch:
  push:
    tags: [ "v*" ]

jobs:
  loader-matrix:
    runs-on: ubuntu-24.04
    strategy:
      fail-fast: false
      matrix:
        loader: [ "0.14.21", "0.15.11", "0.16.9", "0.16.14", "0.17.0" ]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - uses: gradle/actions/setup-gradle@v4
      - name: Loader conformance
        run: ./gradlew :testing:conformanceTest -Pomni.loaderVersion=${{ matrix.loader }} --stacktrace
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: conformance-${{ matrix.loader }}, path: testing/build/reports/** }

  notify:
    needs: loader-matrix
    if: failure()
    runs-on: ubuntu-24.04
    steps:
      - name: Open issue on conformance failure
        uses: actions/github-script@v7
        with:
          script: |
            await github.rest.issues.create({
              owner: context.repo.owner, repo: context.repo.repo,
              title: `Loader conformance failed (${context.sha.substring(0,7)})`,
              labels: ['conformance', 'priority:high'],
              body: 'The load-bearing assumption (nested candidates with unsatisfiable depends are dropped) '
                  + 'could not be verified for at least one loader version. See the failed job artifacts. '
                  + 'Do not release until resolved — see docs/internals/loader-assumption.md.'
            })
```

## 33.5 `release.yml`

```yaml
name: release

on:
  push:
    tags: [ "v*" ]

permissions:
  contents: write

jobs:
  gate:
    uses: ./.github/workflows/build.yml
  conformance:
    uses: ./.github/workflows/conformance.yml
  integration:
    uses: ./.github/workflows/integration.yml

  publish:
    needs: [ gate, conformance, integration ]
    runs-on: ubuntu-24.04
    environment: release
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: |
            17
            21
            25
      - uses: gradle/actions/setup-gradle@v4

      - name: Build and validate
        run: ./gradlew build validateUniversalJar -Pomni.failOnWarnings=true

      - name: Publish framework to Maven
        run: ./gradlew publish
        env:
          ORG_GRADLE_PROJECT_mavenUser: ${{ secrets.MAVEN_USER }}
          ORG_GRADLE_PROJECT_mavenPassword: ${{ secrets.MAVEN_PASSWORD }}

      - name: Publish Gradle plugins
        run: ./gradlew publishPlugins
        env:
          GRADLE_PUBLISH_KEY: ${{ secrets.GRADLE_PUBLISH_KEY }}
          GRADLE_PUBLISH_SECRET: ${{ secrets.GRADLE_PUBLISH_SECRET }}

      - name: Publish example mod (Modrinth + CurseForge + GitHub)
        run: ./gradlew :example:publishUniversal
        env:
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Attach checksums to release
        run: |
          cd example/build/libs && sha256sum *.jar > SHA256SUMS.txt
          gh release upload "${GITHUB_REF_NAME}" *.jar SHA256SUMS.txt --clobber
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## 33.6 Runtime budget

| Job | Duration (reference, ubuntu-24.04) |
|---|---|
| `framework` | 4–6 min |
| `example` | 5–8 min (with a warm Loom cache: 2–3 min) |
| `server` (per matrix entry, 6 in parallel) | 5–9 min |
| `client-smoke` (2 in parallel) | 12–20 min |
| `loader-matrix` (5 in parallel) | 3–4 min |
| release in total | 25–35 min |

The Loom cache (`~/.gradle/caches/fabric-loom`, decompiled Minecraft sources) is cached along with
`gradle/actions/setup-gradle`; without the cache, `example` grows by roughly 6 minutes per MC version.

---

Continue with [chapters 34–38 — distribution, example mod, migration, new versions, documentation](part-09-project.md).
