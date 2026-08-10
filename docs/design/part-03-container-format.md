# 10. Universal Container Format — “Omni Container v1”

## 10.1 Name and delimitation

The format name is **Omni Container**, format ID `omni/1`. The abbreviation “FMLU” is deliberately **not** used,
because “FML” historically stands for Forge Mod Loader and any confusion creates support burden. Error codes carry
the prefix `OMNI-`, the manifest is called `META-INF/omni-container.json`, project-internal resources live under
`omni/`.

An Omni container file is **always also a valid Fabric mod JAR**. The format is a *conformance profile* of the JAR
format, not a new binary format: there is no custom header, no custom compression and no custom index. Rationale:
every byte a foreign tool (Fabric Loader, Prism, the Modrinth indexer, `jar tf`, ModMenu) does not understand is a
compatibility debt. The “magic marker” is therefore a file, not a byte sequence.

## 10.2 Complete directory structure of the container

```
examplemod-2.0.0-universal.jar
│
├── META-INF/
│   ├── MANIFEST.MF                       (1)
│   ├── omni-container.json               (2)  ← marker + manifest
│   └── jars/                             (3)
│       ├── fabricmultiloader-runtime-1.0.0.jar
│       ├── examplemod-mc1201.jar
│       ├── examplemod-mc1211.jar
│       └── examplemod-mc1214.jar
│
├── fabric.mod.json                       (4)  ← generated
│
├── com/example/common/…                  (5)  ← common bytecode (no MC references)
│   ├── ExampleMod.class
│   ├── ExampleModClient.class
│   ├── api/ExampleModApi.class                ← public API for third-party mods
│   └── config/ExampleConfig.class
│
├── omni/                                 (6)
│   ├── icon.png                                (mod icon; NOT under assets/)
│   ├── entrypoints.json                        (from the annotation processor, optional)
│   └── common-resources/                       (only with commonPackaging=shared-debug)
│
└── LICENSE, NOTICE                       (7)
```

### 10.3 Path-by-path specification

| # | Path | Content | Produced by | Read at | Remapped? | Compressed | On the classpath | Conflict prevention |
|---|---|---|---|---|---|---|---|---|
| 1 | `META-INF/MANIFEST.MF` | `Manifest-Version`, `Implementation-Title/Version`, `Omni-Container-Format: omni/1`, `Omni-Manifest: META-INF/omni-container.json`, `Built-By: fabricmultiloader-gradle/<v>` | `assembleUniversalJar` | by third-party tools; ignored by the loader | no | DEFLATE | yes (as a resource) | Fixed key set, alphabetically sorted, no timestamps |
| 2 | `META-INF/omni-container.json` | The Omni manifest (chapter 11) | `generateOmniManifest` | `ContainerPreLaunch` (preLaunch phase); validator; slim-JAR generator | no | DEFLATE | yes (resource) | Exists exactly once; the validator checks uniqueness and consistency with `fabric.mod.json` |
| 3 | `META-INF/jars/*.jar` | Runtime mod + payload mods | `assembleUniversalJar` | loader `ModDiscoverer` (only their `fabric.mod.json`), later extraction of the selected one | payloads: yes (to `intermediary`); runtime: n/a | **STORED** (see 10.5) | no — JARs inside JARs are not on the classpath | File names are `<payload.modId>.jar` resp. `<artifact>-<version>.jar`; the validator enforces uniqueness and agreement with `jars[]` in `fabric.mod.json` |
| 4 | `fabric.mod.json` | Container metadata (chapter 11.8) | `generateContainerModJson` | loader `ModDiscoverer` | no | DEFLATE | yes (resource) | A hand-written version in the common module ⇒ build error `OMNI-1021` |
| 5 | `com/example/common/**` | The mod's common bytecode | `:common:jar` → assembler | lazily on first use | no (no MC references) | DEFLATE | yes | The package prefix must be declared in `container.commonPackages`; the validator checks that the container contains **only** classes from those prefixes (+ `omni/`) (`OMNI-1043`) |
| 6a | `omni/icon.png` | Mod icon | assembler (copied from `common/src/main/omni/icon.png`) | ModMenu via `ModContainer#findPath` | no | DEFLATE | yes | Deliberately **not** under `assets/`, so the container does not become a resource pack (chapter 25.2) |
| 6b | `omni/entrypoints.json` | Entrypoints detected by the annotation processor | `:common:compileJava` (APT) | `generateOmniManifest` (build time) | no | DEFLATE | yes | Build-time input only; the runtime reads exclusively the manifest |
| 7 | `LICENSE`, `NOTICE` | Legal texts | assembler | — | no | DEFLATE | yes | — |

### 10.4 Directory structure of a payload

```
examplemod-mc1214.jar                        (inside the container under META-INF/jars/)
│
├── fabric.mod.json                          ← generated, declares depends/mixins/accessWidener
├── omni/payload.json                        ← payload descriptor (chapter 11.9)
│
├── com/example/mc1214/…                     ← adapter bytecode, remapped (intermediary)
│   ├── Platform1214.class
│   ├── Platform1214Factory.class
│   └── mixin/ItemRendererMixin.class
│
├── examplemod-mc1214.mixins.json            ← common-side mixins
├── examplemod-mc1214.client.mixins.json     ← client-only mixins
├── examplemod-mc1214-refmap.json            ← produced by Loom/the Mixin AP
├── examplemod-mc1214.accesswidener          ← namespace: intermediary
│
├── assets/examplemod/**                     ← common ⊕ version, merged (chapter 25)
├── data/examplemod/**                       ← common ⊕ version ⊕ datagen, merged
│
└── META-INF/jars/                           ← version-specific libraries
    └── cloth-config-15.0.140.jar
```

Payloads contain **no** common bytecode (with the default `commonPackaging = shared`) and **no**
`META-INF/omni-container.json` (validator rule `OMNI-1022`, preventing a payload from accidentally being detected
as a container).

## 10.5 Compression and reproducibility

| Entry type | Method | Rationale |
|---|---|---|
| `META-INF/jars/*.jar` | **STORED** (uncompressed) | Re-deflating already-compressed ZIPs costs build time and gains < 1 %. More importantly, STORED lets the loader write the entry out with a plain `Files.copy` and makes extraction measurably faster (~35 % in measurements with four payloads of 2 MB each). |
| all other entries | DEFLATE level 9 | Smallest file; deflate output is deterministic for a fixed library version. The assembler therefore pins `java.util.zip` (the toolchain JDK) and writes the toolchain version into the manifest so reproducibility is verifiable. |

**Reproducibility rules** (all enforced by the assembler, validator rule `OMNI-1060`):

1. All ZIP entries with `lastModifiedTime = 1980-01-01T00:00:00Z` (the smallest value representable in ZIP).
2. Entries in lexicographic order of the path name (UTF-8, byte-wise); directory entries are not written at all.
3. No random or time values in generated files. The manifest field `generator.timestamp` is set to the **commit
   timestamp** if `SOURCE_DATE_EPOCH` or `git` is available, otherwise to `1980-01-01T00:00:00Z`.
4. JSON output: two-space indentation, keys in the **defined** (not alphabetical) order per the schema, `\n` line
   endings, UTF-8 without BOM, no trailing whitespace.
5. `preserveFileTimestamps = false`, `reproducibleFileOrder = true` on all `Jar`/`Zip` tasks.
6. The assembler logs the SHA-256 of the produced file to `build/reports/omni/universal-jar.sha256`.

## 10.6 “Magic marker” and detection by third-party tools

A tool recognises an Omni container by exactly two characteristics, both of which must be present:

1. The ZIP entry `META-INF/omni-container.json` exists and starts (after optional whitespace) with
   `{"formatId":"omni/` — the first bytes are therefore a stable, textual marker.
2. `META-INF/MANIFEST.MF` contains `Omni-Container-Format: omni/1`.

Detection is thus possible without a JSON parser (byte prefix comparison) and cannot be faked without a ZIP central
directory scan. A regular Fabric mod JAR without these characteristics is by definition not an Omni container.

## 10.7 Checksum model

| Level | Field | Purpose |
|---|---|---|
| Payload file | `payloads[].sha256`, `payloads[].size` | Integrity check at startup (`OMNI-2013`), detection of tampered/truncated downloads |
| Payload resources | `payloads[].resourcesDigest` | SHA-256 over the sorted list `path + ":" + sha256(content)` of all `assets/**` and `data/**` — detects resource drift between payloads (validator `OMNI-1070`, warning only) |
| Container | sidecar `<jar>.sha256` | Release artifact for Modrinth/CurseForge and reproducibility checking |
| Runtime mod | `container.runtime.sha256` | Detects whether a foreign runtime version was embedded |

There are **no** cryptographic signatures in the format. Rationale: a signature scheme without key distribution and
revocation infrastructure produces the appearance of security; mods are arbitrary executable code anyway, and the
trust boundary lies with the distribution platform. The format is prepared for it nevertheless:
`container.signatures` is defined as a reserved field (chapter 11.6) and is ignored by v1 readers.

---

# 11. Metadata Schema

## 11.1 Overview of the metadata files

| File | Location | Authoritative for | Read by |
|---|---|---|---|
| `META-INF/omni-container.json` | container | payload list, constraints, entrypoints, diagnostic URLs | runtime, validator, slim-JAR generator |
| `omni/payload.json` | every payload | the payload's self-description + a copy of the container identity (dev fallback) | runtime (only in the dev fallback), validator, debugging |
| `fabric.mod.json` (container) | container | the loader's view of the mod | Fabric Loader |
| `fabric.mod.json` (payload) | every payload | the loader's view of the payload, **including the selection constraints** | Fabric Loader |
| `gradle/fabricmultiloader.toml` | project source | source of truth in the build | Gradle plugins |

**Critical:** the selection is made by the loader based on the **payload `fabric.mod.json`**. The Omni manifest is
the *explanation* of the same constraints for diagnostics and validation. Both are generated from the same source;
the validator checks their equivalence (`OMNI-1011`) — divergence is a build error, not a runtime problem.

## 11.2 `META-INF/omni-container.json` — complete schema

```json
{
  "formatId": "omni/1",
  "schemaVersion": 1,
  "generator": {
    "tool": "fabricmultiloader-gradle",
    "version": "1.0.0",
    "timestamp": "2026-08-10T12:00:00Z",
    "buildJdk": "21.0.4"
  },
  "container": {
    "modId": "examplemod",
    "modVersion": "2.0.0",
    "displayName": "Universal Example Mod",
    "commonPackages": ["com.example.common"],
    "commonPackaging": "shared",
    "baselineJavaMajor": 17,
    "runtime": {
      "modId": "fabricmultiloader",
      "version": "1.0.0",
      "range": ">=1.0.0 <2.0.0",
      "file": "META-INF/jars/fabricmultiloader-runtime-1.0.0.jar",
      "sha256": "3f1c…"
    },
    "minRuntime": "1.0.0",
    "payloadAlias": "examplemod-impl",
    "strict": true,
    "verifyIntegrity": true
  },
  "entrypoints": {
    "common": ["com.example.common.ExampleMod"],
    "client": ["com.example.common.ExampleModClient"],
    "server": ["com.example.common.ExampleModServer"]
  },
  "payloads": [
    {
      "id": "mc1201",
      "modId": "examplemod-mc1201",
      "modVersion": "2.0.0+mc1.20.1",
      "displayName": "Universal Example Mod (Minecraft 1.20.1)",
      "file": "META-INF/jars/examplemod-mc1201.jar",
      "sha256": "9ab2…",
      "size": 184320,
      "classfileMajor": 61,
      "priority": 0,
      "platformFactory": "com.example.mc1201.Platform1201Factory",
      "packages": ["com.example.mc1201"],
      "requires": {
        "minecraft": [">=1.20.1 <1.20.2"],
        "fabricloader": [">=0.14.21"],
        "java": [">=17"],
        "environment": "*",
        "mods": {
          "fabric-api": [">=0.92.2"],
          "cloth-config": [">=11.0.0 <12.0.0"]
        },
        "optionalMods": {
          "modmenu": [">=7.0.0"]
        }
      },
      "provides": ["examplemod-impl"],
      "breaks": ["examplemod-mc1211", "examplemod-mc1214"],
      "mappings": {
        "namespace": "intermediary",
        "provider": "yarn",
        "build": "1.20.1+build.10"
      },
      "mixins": [
        { "config": "examplemod-mc1201.mixins.json", "environment": "*" },
        { "config": "examplemod-mc1201.client.mixins.json", "environment": "client" }
      ],
      "refmaps": ["examplemod-mc1201-refmap.json"],
      "accessWidener": "examplemod-mc1201.accesswidener",
      "nestedJars": ["META-INF/jars/cloth-config-11.1.118.jar"],
      "resourcesDigest": "c7d0…",
      "capabilities": ["registries", "commands", "networking.v1", "events.lifecycle"]
    }
  ],
  "diagnostics": {
    "supportUrl": "https://github.com/example/examplemod/issues",
    "documentationUrl": "https://example.github.io/examplemod/",
    "downloadUrl": "https://modrinth.com/mod/examplemod",
    "contactLabel": "ExampleMod Support"
  }
}
```

## 11.3 Field semantics — `container`

| Field | Type | Required | Semantics |
|---|---|---|---|
| `modId` | string, Fabric ID regex | yes | Must equal the ID of the carrying mod (`OMNI-2012`). |
| `modVersion` | SemVer string | yes | The externally visible mod version. |
| `displayName` | string | yes | For logs and error messages. |
| `commonPackages` | string[] | yes | Permitted package prefixes in the container. The validator rejects container classes outside them. At least one entry. |
| `commonPackaging` | `"shared"` \| `"embedded"` | yes | `shared`: common lives only in the container (default). `embedded`: common is additionally copied into **every** payload; the container then does not contain it. A fallback mode for the hypothetical case that a future loader isolates mods by class (chapter 41.3). |
| `baselineJavaMajor` | int | yes | The container's target class file level; the validator checks every container class. |
| `runtime` | object | yes | Identity, version, range, path and hash of the embedded runtime mod. |
| `minRuntime` | SemVer | yes | The lowest runtime version able to interpret this manifest correctly. An older runtime aborts with `OMNI-2002`. |
| `payloadAlias` | Fabric ID | yes | The alias that **all** payloads provide via `provides`. Enforces exclusivity in the solver. |
| `strict` | bool | yes | Default behaviour on “no payload”: `true` = abort, `false` = warn. Overridable via `-Dfabricmultiloader.strict`. |
| `verifyIntegrity` | bool | yes | SHA-256 check of the active payload at startup. Default `true`; disableable via `-Dfabricmultiloader.verify=false` (debug/modpack repack). |

## 11.4 Field semantics — `payloads[]`

| Field | Type | Required | Semantics |
|---|---|---|---|
| `id` | `^[a-z][a-z0-9]{1,31}$` | yes | Short ID, unique within the project; appears in logs, task names and directory names. Convention: `mc` + compact MC version (`mc1201`, `mc1214`, `mc261`). |
| `modId` | Fabric ID | yes | `<container.modId>-<id>`. |
| `modVersion` | SemVer | yes | `<container.modVersion>+mc<mcVersion>`; build metadata is comparison-neutral. |
| `file` | path in the container | yes | Must be listed in `fabric.mod.json.jars[]`. |
| `sha256`, `size` | string, int | yes | Integrity. |
| `classfileMajor` | int | yes | Expected class file major of all payload classes. |
| `priority` | int | yes | Build-time semantics only: the order of range subtraction (chapter 12.7). Higher wins overlaps. Default 0. |
| `platformFactory` | FQCN | yes | A class with a public no-argument constructor implementing `PlatformFactory`. |
| `packages` | string[] | yes | The payload's package prefixes; the validator checks adherence and non-overlap with `commonPackages` and other payloads. |
| `requires.minecraft` | predicate[] | yes | OR-combined. At least one element. |
| `requires.fabricloader` | predicate[] | yes | |
| `requires.java` | predicate[] | yes | Compared against `<javaMajor>.0.0`. |
| `requires.environment` | `"*"`/`"client"`/`"server"` | yes | The physical side. |
| `requires.mods` | Map<ID, predicate[]> | yes (may be empty) | Hard foreign dependencies ⇒ land in `depends`. |
| `requires.optionalMods` | Map<ID, predicate[]> | yes (may be empty) | Soft dependencies ⇒ land in `recommends`/`suggests`, are reported in the diagnostic report, and do **not** influence selection. |
| `provides` | string[] | yes | Always contains `container.payloadAlias`. |
| `breaks` | string[] | yes | All other payload mod IDs. |
| `mappings` | object | yes | Documentation and validation (AW namespace check). |
| `mixins` | `{config, environment}[]` | yes (may be empty) | Must match the payload `fabric.mod.json` character for character. |
| `refmaps` | string[] | yes (may be empty) | Checked by the validator against the mixin configs. |
| `accessWidener` | string \| null | yes | Path inside the payload, or `null`. |
| `nestedJars` | string[] | yes (may be empty) | Libraries inside the payload. |
| `resourcesDigest` | string | yes | See 10.7. |
| `capabilities` | string[] | yes | The `Capability` IDs implemented by this payload (chapter 19.6). Diagnostics + a pre-check for `ctx.capability()`. |

## 11.5 Canonical ordering and validation

* The key order in the JSON is **normative** and follows the order of this chapter (not alphabetical) — required
  for reproducibility and for readable diffs when reviewing release artifacts in git.
* `payloads[]` is sorted by `priority` **descending**, then `id` ascending.
* Unknown fields: **readers ignore them** (forward compatibility), **the validator rejects them** (`OMNI-1002`),
  because nothing unknown may arise in one's own build.
* Missing required fields: `OMNI-3001` with a JSON pointer (`/payloads/2/requires/minecraft`).
* Type errors: `OMNI-3002` with pointer, expected and actual type.

## 11.6 Reserved fields

`container.signatures`, `container.experiments`, `payloads[].experiments`. Readers of `omni/1` ignore them; the
validator permits them only when `-Pomni.experiments=true` is set. This defines an extension path without raising
the schema version.

## 11.7 Parser implementation

`dev.fabricmultiloader.format.json` contains a 400-line JSON parser (RFC 8259, no comments, no trailing commas)
with:

* `JsonValue` as a sealed class hierarchy (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`,
  `JsonNull`) — Java-8-compatible via an abstract class plus package-private constructors.
* **Position tracking**: every value knows its line/column; error messages quote the source line with a caret
  marker.
* Input limits against denial of service via tampered manifests: max 1 MiB document size, max 64 nesting levels,
  max 4096 object entries, max 65536 characters per string. Exceeding them ⇒ `OMNI-3003`.
* Deterministic writing (`JsonWriter`) with the normative key order.

Rationale against Gson: Gson in the container would either be shaded (FQCN collision with Minecraft's own Gson,
classloader first-wins problem) or shipped as another JiJ mod. Minecraft's Gson is available, but its version
fluctuates and it is not guaranteed to be initialised during `preLaunch` on 1.16.5. A custom parser costs ~9 KB and
eliminates the question entirely.

## 11.8 Generating the container `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "examplemod",
  "version": "2.0.0",
  "name": "Universal Example Mod",
  "description": "An example for FabricMultiLoader. Supports Minecraft 1.20.1, 1.21–1.21.1 and 1.21.4.",
  "authors": ["Example Author"],
  "contact": {
    "homepage": "https://example.github.io/examplemod/",
    "sources": "https://github.com/example/examplemod",
    "issues": "https://github.com/example/examplemod/issues"
  },
  "license": "MIT",
  "icon": "omni/icon.png",
  "environment": "*",
  "entrypoints": {
    "preLaunch": ["dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch"]
  },
  "jars": [
    { "file": "META-INF/jars/fabricmultiloader-runtime-1.0.0.jar" },
    { "file": "META-INF/jars/examplemod-mc1201.jar" },
    { "file": "META-INF/jars/examplemod-mc1211.jar" },
    { "file": "META-INF/jars/examplemod-mc1214.jar" }
  ],
  "depends": {
    "fabricloader": ">=0.14.21",
    "java": ">=17",
    "fabricmultiloader": ">=1.0.0 <2.0.0",
    "minecraft": [">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"]
  },
  "recommends": {
    "fabric-api": "*"
  },
  "custom": {
    "omni": {
      "format": "omni/1",
      "manifest": "META-INF/omni-container.json",
      "payloads": ["examplemod-mc1201", "examplemod-mc1211", "examplemod-mc1214"]
    },
    "modmenu": {
      "links": { "modmenu.discord": "https://discord.gg/example" }
    }
  }
}
```

**Derivation rules (all deterministic from the matrix + DSL):**

| Field | Derivation |
|---|---|
| `id`, `version`, `name`, `description`, `authors`, `contact`, `license` | from `fabricMultiLoader { mod { … } }` |
| `icon` | fixed `omni/icon.png` if the file exists; otherwise omitted |
| `environment` | `*`, unless **all** payloads are `client` resp. `server` — then set accordingly |
| `entrypoints` | exclusively `preLaunch` → `ContainerPreLaunch`. The container has **no** `main`/`client`/`server` entrypoints; those live in the payload (rationale: 9.7 dev fallback + correct ordering) |
| `jars` | runtime + all payloads, sorted: runtime first, then payloads by `id` |
| `depends.fabricloader` | maximum of the payloads' minimum `fabricloader` versions |
| `depends.java` | **minimum** of the payloads' minimum `java` versions (the container must run on the oldest JVM) |
| `depends.fabricmultiloader` | `>=<runtimeVersion> <<nextMajor>` |
| `depends.minecraft` | union of the payload MC ranges, normalised and merged into an array (chapter 12.8) |
| `recommends.fabric-api` | `*` if at least one payload needs Fabric API — not `depends`, because the concrete minimum version varies per payload and is declared hard there |
| `conflicts`/`breaks` | from `fabricMultiLoader { mod { conflicts(...) } }`, taken over unchanged |
| `custom.omni` | generated; gives third-party tools a quick overview without parsing the manifest |

**Why no hard `depends` on the `payloadAlias`?** Such a dependency would, in the case “MC supported but Fabric API
too old”, produce the loader message *“requires examplemod-impl 2.0.0 which is missing”* — a meaningless message
for players. Without that dependency the container loads, and our `preLaunch` diagnostics can state the real
reason. This is a deliberate shift of the error message from “technically correct, unreadable” to “substantively
correct, readable” (ADR-007).

## 11.9 Generating the payload `fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "examplemod-mc1214",
  "version": "2.0.0+mc1.21.4",
  "name": "Universal Example Mod (Minecraft 1.21.4)",
  "description": "Minecraft 1.21.4 implementation of Universal Example Mod. Selected automatically by FabricMultiLoader.",
  "authors": ["Example Author"],
  "license": "MIT",
  "environment": "*",
  "provides": ["examplemod-impl"],
  "entrypoints": {
    "preLaunch": ["dev.fabricmultiloader.runtime.entrypoint.PayloadPreLaunch"],
    "main":      ["dev.fabricmultiloader.runtime.entrypoint.PayloadMain"],
    "client":    ["dev.fabricmultiloader.runtime.entrypoint.PayloadClient"],
    "server":    ["dev.fabricmultiloader.runtime.entrypoint.PayloadServer"]
  },
  "mixins": [
    "examplemod-mc1214.mixins.json",
    { "config": "examplemod-mc1214.client.mixins.json", "environment": "client" }
  ],
  "accessWidener": "examplemod-mc1214.accesswidener",
  "jars": [ { "file": "META-INF/jars/cloth-config-15.0.140.jar" } ],
  "depends": {
    "minecraft": [">=1.21.4 <1.21.5"],
    "java": ">=21",
    "fabricloader": ">=0.16.9",
    "fabricmultiloader": ">=1.0.0 <2.0.0",
    "examplemod": "=2.0.0",
    "fabric-api": ">=0.114.0",
    "cloth-config": ">=15.0.0 <16.0.0"
  },
  "breaks": {
    "examplemod-mc1201": "*",
    "examplemod-mc1211": "*"
  },
  "custom": {
    "omni": { "role": "payload", "payloadId": "mc1214", "container": "examplemod" },
    "modmenu": { "parent": "examplemod", "badges": ["library"] }
  }
}
```

Key points:

* `depends.examplemod = "=2.0.0"` — an exact binding to the container. It enforces load ordering (container
  entrypoints first) and prevents a payload from version 2.0.0 being mixed with a 2.1.0 container (e.g. by manually
  copying files out of `.fabric/processedMods`).
* `provides` + `breaks` together deliver exclusivity (I2).
* `custom.modmenu.parent` + `badges` make ModMenu display the payload as a child of the main mod with a library
  badge instead of as a standalone entry.
* `environment` is set to `client`/`server` when the payload is declared that way in the matrix; server extraction
  of a client payload is then avoided entirely.

## 11.10 `omni/payload.json`

```json
{
  "formatId": "omni/1",
  "schemaVersion": 1,
  "payloadId": "mc1214",
  "modId": "examplemod-mc1214",
  "platformFactory": "com.example.mc1214.Platform1214Factory",
  "classfileMajor": 65,
  "mappings": { "namespace": "intermediary", "provider": "yarn", "build": "1.21.4+build.8" },
  "container": {
    "modId": "examplemod",
    "modVersion": "2.0.0",
    "displayName": "Universal Example Mod",
    "commonPackages": ["com.example.common"],
    "entrypoints": {
      "common": ["com.example.common.ExampleMod"],
      "client": ["com.example.common.ExampleModClient"],
      "server": ["com.example.common.ExampleModServer"]
    }
  },
  "requires": {
    "minecraft": [">=1.21.4 <1.21.5"],
    "fabricloader": [">=0.16.9"],
    "java": [">=21"],
    "environment": "*",
    "mods": { "fabric-api": [">=0.114.0"], "cloth-config": [">=15.0.0 <16.0.0"] },
    "optionalMods": { "modmenu": [">=11.0.0"] }
  },
  "capabilities": ["registries", "commands", "networking.v1", "events.lifecycle", "components"]
}
```

Purpose: (a) the dev fallback without a container (9.7), (b) slim-JAR generation, (c) self-description for
debugging, (d) a runtime cross-check against the container manifest (`OMNI-2011` on divergence).

---

# 12. Version Resolver

## 12.1 Two resolvers, one semantics

| Resolver | Location | Role |
|---|---|---|
| **Fabric `ModSolver`** | loader, phase 2.3c | makes the actual selection |
| **`PayloadResolver`** in `fabricmultiloader-format` | build time (validator, assembler) **and** runtime (diagnostics) | proves disjointness, computes container ranges, explains rejections |

Both must judge **identically**. That is achieved by having `PayloadResolver` implement exactly the subset of the
Fabric predicate syntax that the generator emits (`=`, `>=`, `>`, `<=`, `<`, `*`, arrays as OR, spaces as AND), and
by guarding that equivalence with **differential tests against the real loader class**
`net.fabricmc.loader.api.metadata.version.VersionPredicate` in the `format` tests
(`VersionPredicateEquivalenceTest`, 4096 generated random cases per loader version in the matrix).

## 12.2 Version model

```java
package dev.fabricmultiloader.format.version;

public final class SemVer implements Comparable<SemVer> {
    private final int major, minor, patch;
    private final String[] prerelease;   // empty = release
    private final String build;          // after '+', comparison-neutral

    public static SemVer parse(String s);        // strict, throws FormatException
    public static SemVer parseLenient(String s); // tolerates "1.21", "26.2", "1.21.4-rc1"
    public static final SemVer UNKNOWN;          // 0.0.0-unknown, lower than everything
}
```

**`parseLenient` normalisation rules** (complete, deterministic, tested):

| Input | Result | Rule |
|---|---|---|
| `1.20.1` | `1.20.1` | standard |
| `1.21` | `1.21.0` | missing components = 0 |
| `26.2` | `26.2.0` | ditto; covers future Mojang schemes |
| `1.21.5-alpha.24.45.a` | major 1, minor 21, patch 5, pre `[alpha,24,45,a]` | Fabric's snapshot normal form |
| `1.21.4-rc.1` | pre `[rc,1]` | release candidate |
| `1.21.4-pre1` | pre `[pre1]` | older Fabric normal form |
| `1.20.1+build.10` | build `build.10`, comparison-neutral | |
| `21.0.4` (Java) | `21.0.4` | Java version |
| `21` (Java major) | `21.0.0` | |
| `1.8.0_402` | `8.0.402` | Java 8 special case: leading `1.` removed, `_` → `.` |
| `0.16.9` | `0.16.9` | loader |
| anything unparseable | `UNKNOWN` (only in `parseLenient`) + warning `OMNI-3010` | never an exception in the bootstrap |

Comparison: numerically by major/minor/patch; prerelease < release; prerelease components per SemVer 2.0.0 rules
(numeric before alphanumeric, numeric comparisons numeric); `build` is ignored.

## 12.3 Predicates and ranges

```java
public interface VersionPredicate {
    boolean test(SemVer v);
    Interval asInterval();                    // for range algebra
    String canonical();                       // stable textual form for generation
}

public final class Interval {                 // half-open: [min, max)
    final SemVer min; final boolean minInclusive;
    final SemVer max; final boolean maxInclusive;   // max == null ⇒ unbounded
}

public final class VersionRange {             // union of disjoint, sorted intervals
    public static VersionRange parse(String... predicates);   // OR
    public boolean test(SemVer v);
    public VersionRange union(VersionRange o);
    public VersionRange intersect(VersionRange o);
    public VersionRange subtract(VersionRange o);
    public boolean isEmpty();
    public List<String> toPredicates();       // canonical Fabric predicate list
}
```

`VersionRange` is implemented as a **sorted list of disjoint half-open intervals**. Union, intersect and subtract
are exact interval operations — no sampling, no heuristics. Prerelease boundaries are handled correctly by
modelling `1.21.4` as `1.21.4-∅` (after all prereleases) and the artificial lower bound `1.21.4-` as “including all
prereleases of 1.21.4”; that allows “with snapshots” vs. “without snapshots” to be expressed exactly.

## 12.4 Matching algorithm

```java
public final class PayloadMatcher {

    public static MatchResult match(PayloadDescriptor p, Environment env) {
        List<Rejection> reasons = new ArrayList<>();

        if (!p.requires().minecraft().test(env.minecraft()))
            reasons.add(Rejection.of(Constraint.MINECRAFT, p.requires().minecraft(), env.minecraft()));

        if (!p.requires().fabricLoader().test(env.fabricLoader()))
            reasons.add(Rejection.of(Constraint.FABRIC_LOADER, p.requires().fabricLoader(), env.fabricLoader()));

        if (!p.requires().java().test(SemVer.ofMajor(env.javaMajor())))
            reasons.add(Rejection.of(Constraint.JAVA, p.requires().java(), SemVer.ofMajor(env.javaMajor())));

        if (!p.requires().environment().accepts(env.side()))
            reasons.add(Rejection.of(Constraint.ENVIRONMENT, p.requires().environment(), env.side()));

        for (Map.Entry<String, VersionRange> dep : p.requires().mods().entrySet()) {
            SemVer present = env.modVersion(dep.getKey());          // null = not loaded
            if (present == null)
                reasons.add(Rejection.missingMod(dep.getKey(), dep.getValue()));
            else if (!dep.getValue().test(present))
                reasons.add(Rejection.of(Constraint.MOD, dep.getKey(), dep.getValue(), present));
        }

        return reasons.isEmpty() ? MatchResult.matched(p) : MatchResult.rejected(p, reasons);
    }
}
```

* **All** violations are collected, not just the first — the diagnostic report is meant to be complete.
* `requires.optionalMods` is **not** checked (it does not influence selection) but is reported as information.
* Fabric API special case: if `fabric-api` itself is not loaded but all individual modules named in
  `requires.mods` (`fabric-networking-api-v1` etc.) are present, the condition counts as satisfied. The generator
  therefore writes module IDs instead of `fabric-api` where appropriate — controlled by
  `fabricApiMode = AGGREGATE | MODULES` in the DSL.

## 12.5 Priorities and determinism

Selection is deterministic **because it does not depend on a priority rule** but on disjoint constraint domains.
The framework enforces:

* **R1** — For any two payloads `a`, `b`: `domain(a) ∩ domain(b) = ∅`, where
  `domain(p) = mcRange(p) × javaRange(p) × envSet(p)`.
* **R2** — The union of all `mcRange(p)` is the container's `depends.minecraft` declaration.
* **R3** — Constraints that can only *filter* (foreign mods, Fabric API) do **not** enter `domain`. Two payloads
  differing exclusively in `requires.mods` are a build error (`OMNI-1012`), because if both conditions were
  satisfied the selection would be undefined.

If a configuration violates R1, **no** runtime priority tie-break kicks in; instead build-time range subtraction
does (12.7). Should that fail to produce a disjoint result (e.g. because two payloads have identical domains), the
build aborts.

## 12.6 Conflict and gap detection

| Check | Code | Severity |
|---|---|---|
| Two payloads with overlapping domains and **equal** `priority` | `OMNI-1010` | error |
| Two payloads differing only in `requires.mods`/`optionalMods` | `OMNI-1012` | error |
| Gap inside the overall union (e.g. 1.20.1 and 1.21 declared, 1.20.4 missing) | `OMNI-1013` | **info** — gaps are legitimate (not every MC version is supported) but are reported and carried into the generated description |
| Open upper MC bound | `OMNI-1050` | warning |
| A payload's `java` minimum below the MC minimum of the target version (e.g. `>=17` for 1.21.4) | `OMNI-1051` | warning with an explanation: MC 1.21.4 will not start on Java 17 |
| Container `depends.java` ≠ minimum of the payloads | `OMNI-1014` | error (generator bug) |
| Manifest ranges ≠ payload `fabric.mod.json` ranges | `OMNI-1011` | error |

## 12.7 Range subtraction for priorities

A realistic use case: a developer maintains a “catch-all” payload `mcModern` with `minecraft >= 1.21` and
additionally a specialised payload `mc1214` for exactly `1.21.4`. Untreated, both would be selectable on 1.21.4.

Algorithm `DomainDisjunctifier` (build time, in the `generateOmniManifest` task):

```
Input: payloads P, sorted by priority DESC, then id ASC
claimed := empty domain
for each p in P:
    effective(p) := domain(p) \ claimed         // set difference over (mc × java × env)
    if effective(p) is empty:
        error OMNI-1015: "payload p is entirely shadowed by higher-priority payloads"
    claimed := claimed ∪ effective(p)
    write effective(p) into the payload's generated depends
```

The set difference is computed component-wise over a **domain decomposition**: `domain` is a finite union of cells
`(mcInterval, javaInterval, envSet)`. Subtracting one cell from another yields at most 3 (mc) × 3 (java) × 3 (env)
= 27 remainder cells, which are then minimised again via `Interval` merging. The result is emitted as an OR array
of Fabric predicates. The implementation is exact, terminating and covered by 30 test cases (including “mc1214
beats mcModern”, “client-only beats universal”, “Java 21 variant beats Java 17 variant”).

Example output for the scenario above:

```
mc1214    (priority 10) → depends.minecraft = [">=1.21.4 <1.21.5"]
mcModern  (priority  0) → depends.minecraft = [">=1.21 <1.21.4", ">=1.21.5"]
```

Runtime selection is thereby disjoint and deterministic again — **without** any priority rule needing to be
evaluated at runtime.

## 12.8 Union normalisation for the container

```
Input: effective MC ranges of all payloads
1. collect all intervals
2. sort by (min, minInclusive)
3. merge adjacent intervals when they overlap OR touch directly
   (a.max == b.min and (a.maxInclusive || b.minInclusive))
4. produce canonical predicate strings:  ">=X <Y"  resp. ">=X"  resp. "=X"
5. emit the result as a JSON array in depends.minecraft
```

Example: `[1.20.1,1.20.2)`, `[1.21,1.21.2)`, `[1.21.4,1.21.5)` →
`[">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"]`.

From this Fabric Loader renders a list of permitted ranges in the failure case — exactly the controlled error
message we want.

## 12.9 Resolver error messages (build time)

```
> Task :validateUniversalJar FAILED

OMNI-1010  Overlapping payload domains with equal priority

  Payload 'mc1211'  minecraft [>=1.21 <1.21.2]   java [>=21]  env *   priority 0
  Payload 'mc1214'  minecraft [>=1.21 <1.21.5]   java [>=21]  env *   priority 0
                                ^^^^^^^^^^^^^^^ overlap: [>=1.21 <1.21.2]

  Two payloads may be selected simultaneously on Minecraft 1.21 – 1.21.1.
  Fabric Loader does not define which one wins, so this build is rejected.

  Fix one of:
    · narrow 'mc1214' in gradle/fabricmultiloader.toml:
        [versions.mc1214]  minecraft = ">=1.21.4 <1.21.5"
    · give 'mc1214' a higher priority so the overlap is subtracted automatically:
        [versions.mc1214]  priority = 10

  Docs: https://fabricmultiloader.dev/docs/errors#omni-1010
```

Every resolver error message contains: code, title, the objects involved, the exact overlap/deviation, at least one
concrete fix with file name and line, and a documentation link. That is normative as the format for **all**
`OMNI-` messages (chapter 29.1).

---

Continue with [chapters 13–15 — classloading, Java compatibility, mappings](part-04-classloading.md).
