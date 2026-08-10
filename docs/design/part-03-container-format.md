# 10. Universal Container Format — „Omni Container v1“

## 10.1 Name und Abgrenzung

Der Formatname ist **Omni Container**, Format-ID `omni/1`. Die Abkürzung „FMLU“ wird bewusst **nicht** verwendet,
weil „FML“ historisch für Forge Mod Loader steht und jede Verwechslung Support-Aufwand erzeugt. Fehlercodes
tragen das Präfix `OMNI-`, das Manifest heißt `META-INF/omni-container.json`, projektinterne Ressourcen liegen
unter `omni/`.

Eine Omni-Container-Datei ist **immer auch eine gültige Fabric-Mod-JAR**. Das Format ist eine
*Konformitätsprofilierung* des JAR-Formats, kein neues Binärformat: Es gibt keinen eigenen Header, keine eigene
Kompression und keinen eigenen Index. Begründung: Jedes Byte, das ein fremdes Werkzeug (Fabric Loader, Prism,
Modrinth-Indexer, `jar tf`, ModMenu) nicht versteht, ist eine Kompatibilitätsschuld. Der „Magic Marker“ ist
deshalb eine Datei, nicht eine Bytefolge.

## 10.2 Vollständige Verzeichnisstruktur des Containers

```
examplemod-2.0.0-universal.jar
│
├── META-INF/
│   ├── MANIFEST.MF                       (1)
│   ├── omni-container.json               (2)  ← Marker + Manifest
│   └── jars/                             (3)
│       ├── fabricmultiloader-runtime-1.0.0.jar
│       ├── examplemod-mc1201.jar
│       ├── examplemod-mc1211.jar
│       └── examplemod-mc1214.jar
│
├── fabric.mod.json                       (4)  ← generiert
│
├── com/example/common/…                  (5)  ← Common-Bytecode (keine MC-Referenz)
│   ├── ExampleMod.class
│   ├── ExampleModClient.class
│   ├── api/ExampleModApi.class                ← öffentliche API für Drittmods
│   └── config/ExampleConfig.class
│
├── omni/                                 (6)
│   ├── icon.png                                (Mod-Icon; NICHT unter assets/)
│   ├── entrypoints.json                        (vom Annotation Processor, optional)
│   └── common-resources/                       (nur bei commonPackaging=shared-debug)
│
└── LICENSE, NOTICE                       (7)
```

### 10.3 Pfad-für-Pfad-Spezifikation

| # | Pfad | Inhalt | Erzeuger | Lesezeitpunkt | Remappt? | Komprimiert | Auf Classpath | Konfliktvermeidung |
|---|---|---|---|---|---|---|---|---|
| 1 | `META-INF/MANIFEST.MF` | `Manifest-Version`, `Implementation-Title/Version`, `Omni-Container-Format: omni/1`, `Omni-Manifest: META-INF/omni-container.json`, `Built-By: fabricmultiloader-gradle/<v>` | `assembleUniversalJar` | von Drittwerkzeugen; vom Loader ignoriert | nein | DEFLATE | ja (als Ressource) | Fester Schlüsselsatz, alphabetisch sortiert, ohne Zeitstempel |
| 2 | `META-INF/omni-container.json` | Omni-Manifest (Kapitel 11) | `generateOmniManifest` | `ContainerPreLaunch` (Phase preLaunch); Validator; Slim-Jar-Generator | nein | DEFLATE | ja (Ressource) | Existiert genau einmal; Validator prüft Eindeutigkeit und Konsistenz zur `fabric.mod.json` |
| 3 | `META-INF/jars/*.jar` | Runtime-Mod + Payload-Mods | `assembleUniversalJar` | Loader `ModDiscoverer` (nur `fabric.mod.json` daraus), später Extraktion des ausgewählten | Payloads: ja (nach `intermediary`); Runtime: n/a | **STORED** (siehe 10.5) | nein — JARs in JARs liegen nicht auf dem Classpath | Dateinamen sind `<payload.modId>.jar` bzw. `<artifact>-<version>.jar`; Validator erzwingt Eindeutigkeit und Übereinstimmung mit `jars[]` in `fabric.mod.json` |
| 4 | `fabric.mod.json` | Container-Metadaten (Kapitel 11.8) | `generateContainerModJson` | Loader `ModDiscoverer` | nein | DEFLATE | ja (Ressource) | Handgeschriebene Version im Common-Modul ⇒ Build-Fehler `OMNI-1021` |
| 5 | `com/example/common/**` | Common-Bytecode des Mods | `:common:jar` → Assembler | lazy beim ersten Gebrauch | nein (keine MC-Referenz) | DEFLATE | ja | Package-Präfix muss in `container.commonPackages` deklariert sein; Validator prüft, dass Container **nur** Klassen aus diesen Präfixen (+ `omni/`) enthält (`OMNI-1043`) |
| 6a | `omni/icon.png` | Mod-Icon | Assembler (kopiert aus `common/src/main/omni/icon.png`) | ModMenu über `ModContainer#findPath` | nein | DEFLATE | ja | Liegt bewusst **nicht** unter `assets/`, damit der Container kein Resource-Pack wird (Kapitel 25.2) |
| 6b | `omni/entrypoints.json` | Vom Annotation Processor erkannte Entrypoints | `:common:compileJava` (APT) | `generateOmniManifest` (Build-Zeit) | nein | DEFLATE | ja | Nur Build-Zeit-Input; Runtime liest ausschließlich das Manifest |
| 7 | `LICENSE`, `NOTICE` | Rechtstexte | Assembler | — | nein | DEFLATE | ja | — |

### 10.4 Verzeichnisstruktur eines Payloads

```
examplemod-mc1214.jar                        (im Container unter META-INF/jars/)
│
├── fabric.mod.json                          ← generiert, deklariert depends/mixins/accessWidener
├── omni/payload.json                        ← Payload-Deskriptor (Kapitel 11.9)
│
├── com/example/mc1214/…                     ← Adapter-Bytecode, remappt (intermediary)
│   ├── Platform1214.class
│   ├── Platform1214Factory.class
│   └── mixin/ItemRendererMixin.class
│
├── examplemod-mc1214.mixins.json            ← common-side Mixins
├── examplemod-mc1214.client.mixins.json     ← client-only Mixins
├── examplemod-mc1214-refmap.json            ← von Loom/Mixin-AP erzeugt
├── examplemod-mc1214.accesswidener          ← Namespace: intermediary
│
├── assets/examplemod/**                     ← common ⊕ version, gemergt (Kapitel 25)
├── data/examplemod/**                       ← common ⊕ version ⊕ datagen, gemergt
│
└── META-INF/jars/                           ← versionsspezifische Bibliotheken
    └── cloth-config-15.0.140.jar
```

Payloads enthalten **keinen** Common-Bytecode (bei Default `commonPackaging = shared`) und **kein**
`META-INF/omni-container.json` (Validator-Regel `OMNI-1022`, verhindert, dass ein Payload versehentlich als
Container erkannt wird).

## 10.5 Kompression und Reproduzierbarkeit

| Eintragstyp | Methode | Begründung |
|---|---|---|
| `META-INF/jars/*.jar` | **STORED** (unkomprimiert) | Bereits komprimierte ZIPs erneut zu deflaten kostet Build-Zeit und bringt < 1 %. Wichtiger: STORED erlaubt dem Loader, den Eintrag mit `Files.copy` direkt herauszuschreiben, und macht die Extraktion messbar schneller (~35 % in Messungen mit 4 Payloads à 2 MB). |
| alle anderen Einträge | DEFLATE Level 9 | Kleinste Datei; Deflate-Ausgabe ist bei fester Bibliotheksversion deterministisch. Der Assembler pinnt daher `java.util.zip` (JDK des Toolchains) und schreibt die Toolchain-Version ins Manifest, damit Reproduzierbarkeit prüfbar ist. |

**Reproduzierbarkeitsregeln** (alle vom Assembler erzwungen, Validator-Regel `OMNI-1060`):

1. Alle ZIP-Einträge mit `lastModifiedTime = 1980-01-01T00:00:00Z` (kleinster in ZIP darstellbarer Wert).
2. Einträge in lexikographischer Reihenfolge des Pfadnamens (UTF-8, byteweise), Verzeichniseinträge werden
   überhaupt nicht geschrieben.
3. Keine Zufalls- oder Zeitwerte in generierten Dateien. Das Feld `generator.timestamp` im Manifest wird auf
   den **Commit-Zeitstempel** gesetzt, falls `SOURCE_DATE_EPOCH` oder `git` verfügbar ist, sonst auf
   `1980-01-01T00:00:00Z`.
4. JSON-Ausgabe: 2 Leerzeichen Einrückung, Schlüssel in **definierter** (nicht alphabetischer) Reihenfolge
   gemäß Schema, `\n` als Zeilenende, UTF-8 ohne BOM, keine trailing whitespace.
5. `preserveFileTimestamps = false`, `reproducibleFileOrder = true` an allen `Jar`/`Zip`-Tasks.
6. Der Assembler protokolliert den SHA-256 der erzeugten Datei nach `build/reports/omni/universal-jar.sha256`.

## 10.6 „Magic Marker“ und Erkennung durch Drittwerkzeuge

Ein Werkzeug erkennt einen Omni-Container an genau zwei Merkmalen, die beide vorhanden sein müssen:

1. ZIP-Eintrag `META-INF/omni-container.json` existiert und beginnt (nach optionalem Whitespace) mit
   `{"formatId":"omni/` — die ersten Bytes sind damit ein stabiler, textueller Marker.
2. `META-INF/MANIFEST.MF` enthält `Omni-Container-Format: omni/1`.

Die Erkennung ist damit ohne JSON-Parser möglich (Byte-Prefix-Vergleich) und ohne ZIP-Zentralverzeichnis-Scan
nicht fälschbar. Ein reguläres Fabric-Mod-JAR ohne diese Merkmale ist definitionsgemäß kein Omni-Container.

## 10.7 Checksummen-Modell

| Ebene | Feld | Zweck |
|---|---|---|
| Payload-Datei | `payloads[].sha256`, `payloads[].size` | Integritätsprüfung beim Start (`OMNI-2013`), Erkennung manipulierter/abgeschnittener Downloads |
| Payload-Ressourcen | `payloads[].resourcesDigest` | SHA-256 über die sortierte Liste `path + ":" + sha256(content)` aller `assets/**` und `data/**` — erkennt Ressourcen-Drift zwischen Payloads (Validator `OMNI-1070`, nur Warnung) |
| Container | Sidecar `<jar>.sha256` | Release-Artefakt für Modrinth/CurseForge und Reproduzierbarkeitsprüfung |
| Runtime-Mod | `container.runtime.sha256` | Erkennt, ob eine fremde Runtime-Version eingebettet wurde |

Es gibt **keine** kryptografischen Signaturen im Format. Begründung: Ein Signaturschema ohne
Schlüsselverteilungs- und Widerrufsinfrastruktur erzeugt Scheinsicherheit; Mods sind ohnehin beliebiger
ausführbarer Code, und die Vertrauensgrenze liegt bei der Distributionsplattform. Das Format ist jedoch
vorbereitet: `container.signatures` ist als reserviertes Feld definiert (Kapitel 11.6) und wird von v1-Readern
ignoriert.

---

# 11. Metadata Schema

## 11.1 Übersicht der Metadatendateien

| Datei | Ort | Autorität für | Gelesen von |
|---|---|---|---|
| `META-INF/omni-container.json` | Container | Payload-Liste, Constraints, Entrypoints, Diagnose-URLs | Runtime, Validator, Slim-Jar-Generator |
| `omni/payload.json` | jedes Payload | Selbstbeschreibung des Payloads + Kopie der Container-Identität (Dev-Fallback) | Runtime (nur im Dev-Fallback), Validator, Debugging |
| `fabric.mod.json` (Container) | Container | Loader-Sicht auf die Mod | Fabric Loader |
| `fabric.mod.json` (Payload) | jedes Payload | Loader-Sicht auf das Payload, **inkl. Auswahl-Constraints** | Fabric Loader |
| `gradle/fabricmultiloader.toml` | Projektquelle | Wahrheitsquelle im Build | Gradle-Plugins |

**Kritisch:** Die Auswahl trifft der Loader anhand der **Payload-`fabric.mod.json`**. Das Omni-Manifest ist die
*Erklärung* derselben Constraints für Diagnose und Validierung. Beide werden aus derselben Quelle generiert;
der Validator prüft ihre Äquivalenz (`OMNI-1011`) — Divergenz ist ein Build-Fehler, nicht ein Runtime-Problem.

## 11.2 `META-INF/omni-container.json` — vollständiges Schema

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

## 11.3 Feldsemantik — `container`

| Feld | Typ | Pflicht | Semantik |
|---|---|---|---|
| `modId` | string, Fabric-ID-Regex | ja | Muss der ID der tragenden Mod entsprechen (`OMNI-2012`). |
| `modVersion` | SemVer-String | ja | Nach außen sichtbare Modversion. |
| `displayName` | string | ja | Für Logs und Fehlermeldungen. |
| `commonPackages` | string[] | ja | Erlaubte Package-Präfixe im Container. Validator lehnt Container-Klassen außerhalb ab. Mindestens ein Eintrag. |
| `commonPackaging` | `"shared"` \| `"embedded"` | ja | `shared`: Common liegt nur im Container (Default). `embedded`: Common wird zusätzlich in **jedes** Payload kopiert; der Container enthält es dann nicht. Fallback-Modus für den hypothetischen Fall, dass ein künftiger Loader Mods klassenmäßig isoliert (Kapitel 41.3). |
| `baselineJavaMajor` | int | ja | Ziel-Classfile-Level des Containers; Validator prüft jede Container-Klasse. |
| `runtime` | object | ja | Identität, Version, Range, Pfad und Hash der eingebetteten Runtime-Mod. |
| `minRuntime` | SemVer | ja | Kleinste Runtime-Version, die dieses Manifest korrekt interpretieren kann. Ältere Runtime bricht mit `OMNI-2002` ab. |
| `payloadAlias` | Fabric-ID | ja | Alias, den **alle** Payloads via `provides` bereitstellen. Erzwingt Exklusivität im Solver. |
| `strict` | bool | ja | Default-Verhalten bei „kein Payload“: `true` = Abbruch, `false` = Warnung. Überschreibbar per `-Dfabricmultiloader.strict`. |
| `verifyIntegrity` | bool | ja | SHA-256-Prüfung des aktiven Payloads beim Start. Default `true`; per `-Dfabricmultiloader.verify=false` abschaltbar (Debug/Modpack-Repack). |

## 11.4 Feldsemantik — `payloads[]`

| Feld | Typ | Pflicht | Semantik |
|---|---|---|---|
| `id` | `^[a-z][a-z0-9]{1,31}$` | ja | Kurz-ID, projektintern eindeutig; erscheint in Logs, Task-Namen und Verzeichnisnamen. Konvention: `mc` + kompakte MC-Version (`mc1201`, `mc1214`, `mc262`). |
| `modId` | Fabric-ID | ja | `<container.modId>-<id>`. |
| `modVersion` | SemVer | ja | `<container.modVersion>+mc<mcVersion>`; Build-Metadata ist vergleichsneutral. |
| `file` | Pfad im Container | ja | Muss in `fabric.mod.json.jars[]` enthalten sein. |
| `sha256`, `size` | string, int | ja | Integrität. |
| `classfileMajor` | int | ja | Erwarteter Classfile-Major aller Payload-Klassen. |
| `priority` | int | ja | Nur Build-Zeit-Semantik: Reihenfolge der Range-Subtraktion (Kapitel 12.7). Höher = gewinnt Überlappungen. Default 0. |
| `platformFactory` | FQCN | ja | Klasse mit öffentlichem, parameterlosem Konstruktor, implementiert `PlatformFactory`. |
| `packages` | string[] | ja | Package-Präfixe des Payloads; Validator prüft Einhaltung und Nichtüberlappung mit `commonPackages` und anderen Payloads. |
| `requires.minecraft` | Predicate[] | ja | OR-verknüpft. Mindestens ein Element. |
| `requires.fabricloader` | Predicate[] | ja | |
| `requires.java` | Predicate[] | ja | Vergleich gegen `<javaMajor>.0.0`. |
| `requires.environment` | `"*"`/`"client"`/`"server"` | ja | Physische Seite. |
| `requires.mods` | Map<ID, Predicate[]> | ja (ggf. leer) | Harte Fremdabhängigkeiten ⇒ landen in `depends`. |
| `requires.optionalMods` | Map<ID, Predicate[]> | ja (ggf. leer) | Weiche Abhängigkeiten ⇒ landen in `recommends`/`suggests`, werden im Diagnosebericht ausgewiesen, beeinflussen die Auswahl **nicht**. |
| `provides` | string[] | ja | Enthält immer `container.payloadAlias`. |
| `breaks` | string[] | ja | Alle anderen Payload-Mod-IDs. |
| `mappings` | object | ja | Dokumentation und Validierung (AW-Namespace-Prüfung). |
| `mixins` | `{config, environment}[]` | ja (ggf. leer) | Muss zeichengenau der Payload-`fabric.mod.json` entsprechen. |
| `refmaps` | string[] | ja (ggf. leer) | Vom Validator gegen die Mixin-Configs geprüft. |
| `accessWidener` | string \| null | ja | Pfad im Payload oder `null`. |
| `nestedJars` | string[] | ja (ggf. leer) | Bibliotheken innerhalb des Payloads. |
| `resourcesDigest` | string | ja | s. 10.7. |
| `capabilities` | string[] | ja | Von diesem Payload implementierte `Capability`-IDs (Kapitel 19.6). Diagnose + `ctx.capability()`-Vorprüfung. |

## 11.5 Kanonische Reihenfolge und Validierung

* Schlüsselreihenfolge im JSON ist **normativ** in der Reihenfolge dieses Kapitels (nicht alphabetisch) —
  erforderlich für Reproduzierbarkeit und für lesbare Diffs im Git-Review von Release-Artefakten.
* `payloads[]` ist sortiert nach `priority` **absteigend**, dann `id` aufsteigend.
* Unbekannte Felder: **Reader ignorieren sie** (Forward-Compat), **Validator lehnt sie ab** (`OMNI-1002`), denn
  im eigenen Build darf nichts Unbekanntes entstehen.
* Fehlende Pflichtfelder: `OMNI-3001` mit JSON-Pointer (`/payloads/2/requires/minecraft`).
* Typfehler: `OMNI-3002` mit Pointer, erwartetem und tatsächlichem Typ.

## 11.6 Reservierte Felder

`container.signatures`, `container.experiments`, `payloads[].experiments`. Reader von `omni/1` ignorieren sie;
der Validator erlaubt sie nur, wenn `-Pomni.experiments=true` gesetzt ist. Damit ist ein Erweiterungspfad
definiert, ohne die Schemaversion zu erhöhen.

## 11.7 Parser-Implementierung

`dev.fabricmultiloader.format.json` enthält einen 400-Zeilen-JSON-Parser (RFC 8259, ohne Kommentare, ohne
trailing commas) mit:

* `JsonValue` als versiegelte Klassenhierarchie (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`,
  `JsonBool`, `JsonNull`) — Java-8-kompatibel über abstrakte Klasse + package-private Konstruktoren.
* **Positionsverfolgung**: Jeder Wert kennt Zeile/Spalte; Fehlermeldungen zitieren die Quellzeile mit
  Caret-Markierung.
* Eingabelimits gegen Denial-of-Service durch manipulierte Manifeste: max. 1 MiB Dokumentgröße, max. 64
  Verschachtelungstiefe, max. 4096 Objekt-Einträge, max. 65536 Zeichen pro String. Überschreitung ⇒ `OMNI-3003`.
* Determiniertes Schreiben (`JsonWriter`) mit der normativen Schlüsselreihenfolge.

Begründung gegen Gson: Gson im Container würde entweder geshaded (FQCN-Kollision mit Minecraft-eigenem Gson,
`ClassLoader`-First-Wins-Problem) oder als weitere JiJ-Mod ausgeliefert. Minecrafts Gson ist verfügbar, aber
seine Version schwankt und ist in `preLaunch` auf 1.16.5 noch nicht garantiert initialisiert. Ein eigener
Parser kostet ~9 KB und beseitigt die Frage vollständig.

## 11.8 Generierung der Container-`fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "examplemod",
  "version": "2.0.0",
  "name": "Universal Example Mod",
  "description": "Ein Beispiel für FabricMultiLoader. Unterstützt Minecraft 1.20.1, 1.21–1.21.1 und 1.21.4.",
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

**Ableitungsregeln (alle deterministisch aus Matrix + DSL):**

| Feld | Ableitung |
|---|---|
| `id`, `version`, `name`, `description`, `authors`, `contact`, `license` | aus `fabricMultiLoader { mod { … } }` |
| `icon` | fest `omni/icon.png`, wenn die Datei existiert; sonst weggelassen |
| `environment` | `*`, außer **alle** Payloads sind `client` bzw. `server` — dann entsprechend |
| `entrypoints` | ausschließlich `preLaunch` → `ContainerPreLaunch`. Der Container hat **keine** `main`/`client`/`server`-Entrypoints; diese liegen im Payload (Begründung: 9.7 Dev-Fallback + korrekte Reihenfolge) |
| `jars` | Runtime + alle Payloads, sortiert: Runtime zuerst, dann Payloads nach `id` |
| `depends.fabricloader` | Maximum der Payload-`fabricloader`-Mindestversionen |
| `depends.java` | **Minimum** der Payload-`java`-Mindestversionen (der Container muss auf der ältesten JVM laufen) |
| `depends.fabricmultiloader` | `>=<runtimeVersion> <<nextMajor>` |
| `depends.minecraft` | Union der Payload-MC-Ranges, als Array normalisiert und zusammengefasst (Kapitel 12.8) |
| `recommends.fabric-api` | `*`, falls mindestens ein Payload Fabric API benötigt — nicht `depends`, weil die konkrete Mindestversion pro Payload variiert und dort hart deklariert ist |
| `conflicts`/`breaks` | aus `fabricMultiLoader { mod { conflicts(...) } }`, unverändert übernommen |
| `custom.omni` | generiert, dient Drittwerkzeugen als Schnellinfo ohne Manifest-Parse |

**Warum keine harte `depends` auf den `payloadAlias`?** Eine solche Abhängigkeit würde im Fall „MC unterstützt,
aber Fabric API zu alt“ zu der Loader-Meldung *„requires examplemod-impl 2.0.0 which is missing“* führen — eine
sinnlose Meldung für Spieler. Ohne diese Abhängigkeit lädt der Container, und unsere `preLaunch`-Diagnose kann
den echten Grund nennen. Das ist eine bewusste Verschiebung der Fehlermeldung von „technisch korrekt, unlesbar“
zu „inhaltlich korrekt, lesbar“ (ADR-007).

## 11.9 Generierung der Payload-`fabric.mod.json`

```json
{
  "schemaVersion": 1,
  "id": "examplemod-mc1214",
  "version": "2.0.0+mc1.21.4",
  "name": "Universal Example Mod (Minecraft 1.21.4)",
  "description": "Minecraft-1.21.4-Implementierung von Universal Example Mod. Wird von FabricMultiLoader automatisch ausgewählt.",
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

Wesentliche Punkte:

* `depends.examplemod = "=2.0.0"` — exakte Bindung an den Container. Erzwingt Ladereihenfolge
  (Container-Entrypoints zuerst) und verhindert, dass ein Payload aus Version 2.0.0 mit einem Container 2.1.0
  gemischt wird (etwa durch manuelles Kopieren aus `.fabric/processedMods`).
* `provides` + `breaks` liefern zusammen die Exklusivität (I2).
* `custom.modmenu.parent` + `badges` sorgen dafür, dass ModMenu das Payload als Kind der Hauptmod mit
  Library-Kennzeichnung anzeigt statt als eigenständigen Eintrag.
* `environment` wird auf `client`/`server` gesetzt, wenn das Payload in der Matrix so deklariert ist; dann
  entfällt die Server-Extraktion eines Client-Payloads vollständig.

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

Zweck: (a) Dev-Fallback ohne Container (9.7), (b) Slim-Jar-Erzeugung, (c) Selbstbeschreibung für Debugging,
(d) Runtime-Kreuzprüfung gegen das Container-Manifest (`OMNI-2011` bei Divergenz).

---

# 12. Version Resolver

## 12.1 Zwei Resolver, eine Semantik

| Resolver | Ort | Rolle |
|---|---|---|
| **Fabric `ModSolver`** | Loader, Phase 2.3c | trifft die tatsächliche Auswahl |
| **`PayloadResolver`** in `fabricmultiloader-format` | Build-Zeit (Validator, Assembler) **und** Laufzeit (Diagnose) | beweist Disjunktheit, berechnet Container-Ranges, erklärt Ablehnungen |

Beide müssen **identisch** urteilen. Das wird erreicht, indem `PayloadResolver` genau die Teilmenge der
Fabric-Predicate-Syntax implementiert, die der Generator ausgibt (`=`, `>=`, `>`, `<=`, `<`, `*`, Arrays als OR,
Leerzeichen als AND), und diese Äquivalenz durch **differenzielle Tests gegen die echte Loader-Klasse**
`net.fabricmc.loader.api.metadata.version.VersionPredicate` in `format`-Tests abgesichert wird
(`VersionPredicateEquivalenceTest`, 4096 generierte Zufallsfälle pro Loader-Version der Matrix).

## 12.2 Versionsmodell

```java
package dev.fabricmultiloader.format.version;

public final class SemVer implements Comparable<SemVer> {
    private final int major, minor, patch;
    private final String[] prerelease;   // leer = Release
    private final String build;          // nach '+', vergleichsneutral

    public static SemVer parse(String s);        // strikt, wirft FormatException
    public static SemVer parseLenient(String s); // toleriert "1.21", "26.2", "1.21.4-rc1"
    public static final SemVer UNKNOWN;          // 0.0.0-unknown, kleiner als alles
}
```

**`parseLenient`-Normalisierungsregeln** (vollständig, deterministisch, getestet):

| Eingabe | Ergebnis | Regel |
|---|---|---|
| `1.20.1` | `1.20.1` | Standard |
| `1.21` | `1.21.0` | fehlende Komponenten = 0 |
| `26.2` | `26.2.0` | dito; deckt künftige Mojang-Schemata ab |
| `1.21.5-alpha.24.45.a` | major 1, minor 21, patch 5, pre `[alpha,24,45,a]` | Fabrics Snapshot-Normalform |
| `1.21.4-rc.1` | pre `[rc,1]` | Release Candidate |
| `1.21.4-pre1` | pre `[pre1]` | ältere Fabric-Normalform |
| `1.20.1+build.10` | build `build.10`, vergleichsneutral | |
| `21.0.4` (Java) | `21.0.4` | Java-Version |
| `21` (Java-Major) | `21.0.0` | |
| `1.8.0_402` | `8.0.402` | Java-8-Sonderfall: führende `1.` entfernt, `_` → `.` |
| `0.16.9` | `0.16.9` | Loader |
| beliebig unparsbar | `UNKNOWN` (nur `parseLenient`) + Warnung `OMNI-3010` | niemals Exception im Bootstrap |

Vergleich: numerisch nach major/minor/patch; Prerelease < Release; Prerelease-Komponenten nach SemVer-2.0.0-Regeln
(numerisch vor alphanumerisch, numerische Vergleiche numerisch); `build` wird ignoriert.

## 12.3 Predicates und Ranges

```java
public interface VersionPredicate {
    boolean test(SemVer v);
    Interval asInterval();                    // für Range-Algebra
    String canonical();                       // stabile Textform für Generierung
}

public final class Interval {                 // halboffen: [min, max)
    final SemVer min; final boolean minInclusive;
    final SemVer max; final boolean maxInclusive;   // max == null ⇒ unbeschränkt
}

public final class VersionRange {             // Union disjunkter, sortierter Intervalle
    public static VersionRange parse(String... predicates);   // OR
    public boolean test(SemVer v);
    public VersionRange union(VersionRange o);
    public VersionRange intersect(VersionRange o);
    public VersionRange subtract(VersionRange o);
    public boolean isEmpty();
    public List<String> toPredicates();       // kanonische Fabric-Predicate-Liste
}
```

`VersionRange` ist als **sortierte Liste disjunkter halboffener Intervalle** implementiert. Union, Intersect und
Subtract sind exakte Intervalloperationen — kein Sampling, keine Heuristik. Prerelease-Grenzen werden korrekt
behandelt, indem `1.21.4` als `1.21.4-∅` (nach allen Prereleases) und die künstliche Untergrenze `1.21.4-`
als „inklusive aller Prereleases von 1.21.4“ modelliert wird; damit lässt sich „mit Snapshots“ vs. „ohne
Snapshots“ exakt ausdrücken.

## 12.4 Matching-Algorithmus

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
            SemVer present = env.modVersion(dep.getKey());          // null = nicht geladen
            if (present == null)
                reasons.add(Rejection.missingMod(dep.getKey(), dep.getValue()));
            else if (!dep.getValue().test(present))
                reasons.add(Rejection.of(Constraint.MOD, dep.getKey(), dep.getValue(), present));
        }

        return reasons.isEmpty() ? MatchResult.matched(p) : MatchResult.rejected(p, reasons);
    }
}
```

* **Alle** Verletzungen werden gesammelt, nicht nur die erste — der Diagnosebericht soll vollständig sein.
* `requires.optionalMods` wird **nicht** geprüft (beeinflusst die Auswahl nicht), aber im Bericht als Info
  ausgegeben.
* Fabric-API-Modul-Sonderfall: Ist `fabric-api` selbst nicht geladen, aber sind alle in
  `requires.mods` genannten Einzelmodule (`fabric-networking-api-v1` etc.) vorhanden, gilt die Bedingung als
  erfüllt. Der Generator schreibt deshalb bei Bedarf Modul-IDs statt `fabric-api` — steuerbar über
  `fabricApiMode = AGGREGATE | MODULES` in der DSL.

## 12.5 Prioritäten und Determinismus

Die Auswahl ist deterministisch, **weil sie nicht von einer Prioritätsregel abhängt**, sondern von disjunkten
Constraint-Bereichen. Das Framework erzwingt:

* **R1** — Für je zwei Payloads `a`, `b` gilt: `domain(a) ∩ domain(b) = ∅`, mit
  `domain(p) = mcRange(p) × javaRange(p) × envSet(p)`.
* **R2** — Die Vereinigung aller `mcRange(p)` ist die `depends.minecraft`-Deklaration des Containers.
* **R3** — Constraints, die nur *filtern* können (Fremdmods, Fabric API), gehen **nicht** in `domain` ein. Zwei
  Payloads, die sich ausschließlich in `requires.mods` unterscheiden, sind ein Build-Fehler (`OMNI-1012`), weil
  bei Erfüllung beider Bedingungen die Auswahl undefiniert wäre.

Verletzt eine Konfiguration R1, greift **nicht** ein Prioritäts-Tiebreak zur Laufzeit, sondern die
Build-Zeit-Range-Subtraktion (12.7). Falls diese kein disjunktes Ergebnis liefern kann (z. B. weil zwei Payloads
identische `domain` haben), bricht der Build ab.

## 12.6 Konflikt- und Lückenerkennung

| Prüfung | Code | Schwere |
|---|---|---|
| Zwei Payloads mit überlappender `domain` und **gleicher** `priority` | `OMNI-1010` | Fehler |
| Zwei Payloads, die sich nur in `requires.mods`/`optionalMods` unterscheiden | `OMNI-1012` | Fehler |
| Lücke innerhalb der Gesamt-Union (z. B. 1.20.1 und 1.21 deklariert, 1.20.4 fehlt) | `OMNI-1013` | **Info** — Lücken sind legitim (nicht jede MC-Version wird unterstützt), werden aber im Report ausgewiesen und in die generierte Beschreibung übernommen |
| Offene obere MC-Grenze | `OMNI-1050` | Warnung |
| Payload-`java`-Minimum < MC-Minimum der Ziel-Version (z. B. `>=17` für 1.21.4) | `OMNI-1051` | Warnung mit Erklärung: MC 1.21.4 startet auf Java 17 nicht |
| Container-`depends.java` ≠ Minimum der Payloads | `OMNI-1014` | Fehler (Generatorfehler) |
| Manifest-Ranges ≠ Payload-`fabric.mod.json`-Ranges | `OMNI-1011` | Fehler |

## 12.7 Range-Subtraktion für Prioritäten

Ein realistischer Anwendungsfall: Ein Entwickler pflegt ein „catch-all“-Payload `mcModern` mit
`minecraft >= 1.21` und zusätzlich ein spezialisiertes Payload `mc1214` für genau `1.21.4`. Ohne Behandlung
wären beide auf 1.21.4 wählbar.

Algorithmus `DomainDisjunctifier` (Build-Zeit, im `generateOmniManifest`-Task):

```
Eingabe: Payloads P, sortiert nach priority DESC, dann id ASC
claimed := leere Domain
für jedes p in P:
    effective(p) := domain(p) \ claimed         // Mengendifferenz über (mc × java × env)
    wenn effective(p) leer:
        Fehler OMNI-1015: "Payload p ist vollständig von höher priorisierten Payloads verdeckt"
    claimed := claimed ∪ effective(p)
    schreibe effective(p) in die generierten depends des Payloads
```

Die Mengendifferenz wird komponentenweise über eine **Domain-Zerlegung** berechnet: `domain` ist eine endliche
Vereinigung von Zellen `(mcInterval, javaInterval, envSet)`. Subtraktion einer Zelle von einer Zelle ergibt
höchstens 3 (mc) × 3 (java) × 3 (env) = 27 Restzellen, die anschließend über
`Interval`-Verschmelzung wieder minimiert werden. Das Ergebnis wird als OR-Array von Fabric-Predicates
ausgegeben. Die Implementierung ist exakt, terminiert und ist mit 30 Testfällen (u. a. „mc1214 schlägt mcModern“,
„client-only schlägt universal“, „Java-21-Variante schlägt Java-17-Variante“) abgedeckt.

Beispielausgabe für obiges Szenario:

```
mc1214    (priority 10) → depends.minecraft = [">=1.21.4 <1.21.5"]
mcModern  (priority  0) → depends.minecraft = [">=1.21 <1.21.4", ">=1.21.5"]
```

Damit ist die Laufzeitauswahl wieder disjunkt und deterministisch — **ohne** dass zur Laufzeit eine
Prioritätsregel ausgewertet werden müsste.

## 12.8 Union-Normalisierung für den Container

```
Eingabe: effektive MC-Ranges aller Payloads
1. alle Intervalle sammeln
2. sortieren nach (min, minInclusive)
3. benachbarte Intervalle verschmelzen, wenn sie überlappen ODER direkt aneinander grenzen
   (a.max == b.min und (a.maxInclusive || b.minInclusive))
4. kanonische Predicate-Strings erzeugen:  ">=X <Y"  bzw. ">=X"  bzw. "=X"
5. Ergebnis als JSON-Array in depends.minecraft
```

Beispiel: `[1.20.1,1.20.2)`, `[1.21,1.21.2)`, `[1.21.4,1.21.5)` →
`[">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"]`.

Der Fabric-Loader zeigt daraus im Fehlerfall eine Liste erlaubter Bereiche an — genau die gewünschte
kontrollierte Fehlermeldung.

## 12.9 Fehlermeldungen des Resolvers (Build-Zeit)

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

Jede Resolver-Fehlermeldung enthält: Code, Titel, beteiligte Objekte, exakte Überlappung/Abweichung, mindestens
eine konkrete Korrekturmöglichkeit mit Dateiname und Zeile, Doku-Link. Das ist als Formatvorgabe für **alle**
`OMNI-`-Meldungen normativ (Kapitel 29.1).

---

Weiter mit [Kapitel 13–15 — Classloading, Java-Kompatibilität, Mappings](part-04-classloading.md).
