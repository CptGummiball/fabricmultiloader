# 29. Error Handling

## 29.1 Normatives Meldungsformat

Jede FabricMultiLoader-Meldung — Build-Zeit wie Laufzeit — hat exakt diese Struktur:

```
<CODE>  <Titel in einer Zeile>

  <Was wurde erkannt / welche Objekte sind beteiligt>

  <Warum ist das ein Problem — eine bis drei Zeilen>

  Fix:
    · <konkreter Schritt 1, mit Datei/Zeile/Befehl>
    · <konkreter Schritt 2>

  Docs: https://fabricmultiloader.dev/docs/errors#<code-lowercase>
```

Verbindliche Regeln:

1. **Kein Stacktrace ohne Erklärung.** Wo eine `Throwable`-Ursache existiert, wird sie als `cause` angehängt,
   aber die Message steht *über* dem Stacktrace.
2. **Kein Fehlercode ohne Doku-Anker.** `docs/errors.md` enthält für jeden Code einen Abschnitt; ein Test
   (`ErrorCodeDocumentationTest`) prüft, dass jeder in `ErrorCode` definierte Code dort vorkommt und umgekehrt.
3. **Keine Meldung ohne mindestens einen Fix-Vorschlag.**
4. **Ist-Zustand immer vollständig.** Erkannte MC-, Loader-, API-, Java-Version und Seite werden in jeder
   Laufzeitmeldung ausgegeben, auch wenn sie für den konkreten Fehler nicht ursächlich sind — Supportfälle werden
   damit in einer Runde lösbar.

## 29.2 Die wichtigste Meldung: kein passendes Payload

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

Zweiter, häufigerer Fall — MC passt, aber eine Nebenbedingung nicht:

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

Diese Meldung ist der wichtigste Grund, warum der Container **nicht** hart auf den Payload-Alias `depends` —
der Loader hätte hier nur „requires examplemod-impl which is missing“ gemeldet (Kapitel 11.8).

## 29.3 Vollständiger Fehlercode-Katalog

### 1xxx — Build-Zeit (Gradle-Plugin, Validator)

| Code | Bedeutung |
|---|---|
| 1001 | Matrixdatei fehlt oder ist nicht lesbar |
| 1002 | Unbekanntes Feld im Omni-Manifest |
| 1010 | Überlappende Payload-Domänen bei gleicher `priority` |
| 1011 | Manifest-Constraints ≠ Payload-`fabric.mod.json`-Constraints |
| 1012 | Zwei Payloads unterscheiden sich nur in `requires.mods` |
| 1013 | Lücke in der MC-Versionsabdeckung (Info) |
| 1014 | Container-`depends.java` ≠ Minimum der Payloads |
| 1015 | Payload vollständig von höher priorisierten Payloads verdeckt |
| 1021 | Handgeschriebene `fabric.mod.json` in Modulressourcen |
| 1022 | Payload enthält `META-INF/omni-container.json` |
| 1023 | Container enthält `assets/` oder `data/` |
| 1024 | Container deklariert `mixins` oder `accessWidener` |
| 1030 | Mixin-Config-, Refmap- oder AW-Name nicht eindeutig über alle Payloads |
| 1031 | Referenziertes Refmap fehlt |
| 1032 | Refmap enthält Klassen, die nicht im Payload liegen |
| 1033 | Refmap ohne zugehörige Mixin-Config (Warnung) |
| 1034 | Mixin-Package verletzt Namenskonvention |
| 1035 | `ConditionalMixinPlugin`-Isolation verletzt (Zugriff auf Runtime-Bootstrap oder MC) |
| 1036 | Verbotene Referenz: eigener ClassLoader / Loader-Interna |
| 1040 | Container-Klasse überschreitet `baselineJavaMajor` |
| 1041 | Payload-Klasse hat abweichenden Classfile-Major |
| 1042 | Container-Klasse referenziert Minecraft/Fabric-API/Mixin |
| 1043 | Container-Klasse außerhalb `commonPackages` |
| 1044 | Package-Überlappung zwischen Payloads oder mit Common |
| 1045 | Client-Referenz außerhalb eines `clientOnly`-Packages |
| 1046 | `classfileMajor` unvereinbar mit `requires.java` |
| 1047 | `baselineJavaMajor` ≠ Minimum der Payload-Java-Anforderungen |
| 1048 | Genestete Bibliothek mit zu hohem Classfile-Major (Warnung) |
| 1049 | Multi-Release-Artefakte im Container |
| 1050 | Offene obere MC-Grenze (Warnung) |
| 1051 | `javaRange`-Minimum unter der MC-Anforderung (Warnung) |
| 1060 | Reproduzierbarkeitsverletzung |
| 1070 | Ressourcen-Digest weicht zwischen Payloads ab (Warnung) |
| 1080 | Mapping-Inkonsistenz innerhalb eines Payloads |
| 1081 | `shared`-Versionen mit unterschiedlichem Mapping-Provider |
| 1082 | AW-Namespace im Payload ≠ `intermediary` |
| 1083 | Gepinntes Mapping-Layer (Warnung) |
| 1090 | Erforderliches Toolchain-JDK nicht verfügbar |
| 1100–1110 | Mixin-Config-Regeln (Kapitel 16.3) |
| 1120–1124 | Access-Widener-Regeln (Kapitel 17.6) |
| 1130 | Deklarierte Capability ohne Implementierung |
| 1140 | Doppelter Entrypoint (DSL + Annotation) |
| 1141 | Kein `common`-Entrypoint deklariert |
| 1150 | Common-erreichbarer Code referenziert Client-Package |
| 1160 | `minecraft` liegt nicht in `minecraftRange` |
| 1161 | Unbekannter Schlüssel in der Matrixdatei |
| 1162 | Matrixeintrag ohne Verzeichnis |
| 1163 | Verzeichnis ohne Matrixeintrag |
| 1170 | Doppelter ZIP-Eintrag beim Assemblieren |
| 1180 | `omniMod`-Artefakt ist keine Fabric-Mod |
| 1181 | Fabric-Mod in `omniIncludeCommon` |
| 1182 | Zweite Fabric-API-Version im Payload (Warnung) |
| 1183 | Verbotene Bibliotheksreferenz in `:common` |
| 1184 | Kotlin ohne `fabric-language-kotlin` (Warnung) |
| 1185 | MC-abhängige Version im `libs.versions.toml` (Warnung) |
| 1186 | Klassen-Shadowing zwischen `shared` und Version-Modul |
| 1187 | `shared`-Versionen mit unterschiedlichem Java-Release |
| 1200 | Undeklarierter Ressourcen-Override |
| 1201 | Mixin/AW/Refmap in `common`-Ressourcen |
| 1202 | Datagen-Entrypoint im Release-Payload |

### 2xxx — Laufzeit

| Code | Bedeutung |
|---|---|
| 2001 | Manifest fehlt oder nicht parsbar → Container beschädigt |
| 2002 | Manifest-Schemaversion oder `minRuntime` > unterstützt |
| 2003 | Kein passendes Payload |
| 2004 | Mehrere Payloads gleichzeitig aktiv |
| 2010 | Minecraft-Mod-Container nicht vorhanden (unbekanntes Launch-Setup) |
| 2011 | Payload-Deskriptor widerspricht Container-Manifest |
| 2012 | Manifest-Mod-ID ≠ tragende Mod-ID |
| 2013 | SHA-256-Prüfung des aktiven Payloads fehlgeschlagen |
| 2020 | `platformFactory`-Klasse nicht gefunden |
| 2021 | `platformFactory` hat eine Ausnahme geworfen |
| 2022 | `platformFactory` implementiert `PlatformFactory` nicht |
| 2023 | `platformFactory` lieferte `null` |
| 2030 | Common-Entrypoint-Klasse nicht gefunden |
| 2031 | Common-Entrypoint hat eine Ausnahme geworfen |
| 2040 | Payload-Lifecycle-Hook hat eine Ausnahme geworfen |
| 2100 | Standalone-Payload ohne Container (Info, nur Dev/Slim) |
| 2101 | Nicht-strikter Modus: Mod bleibt deaktiviert (Warnung) |
| 2200 | Conditional-Mixin-Config nicht lesbar (Warnung, fail-open) |
| 2201 | Conditional-Mixin-Entscheidung (Debug) |

### 3xxx — Format/Parser

| Code | Bedeutung |
|---|---|
| 3001 | Pflichtfeld fehlt (mit JSON-Pointer) |
| 3002 | Typfehler (mit JSON-Pointer, Soll/Ist) |
| 3003 | Eingabelimit überschritten (Größe/Tiefe/Anzahl) |
| 3004 | Ungültige Mod-ID / ungültiger Identifier |
| 3010 | Versionsstring nicht parsbar (Warnung, `UNKNOWN`) |
| 3011 | Ungültiges Version-Predicate |

### 4xxx — API-Missbrauch (Programmierfehler des Modautors)

| Code | Bedeutung |
|---|---|
| 4001 | Ungültiger Lifecycle-Übergang |
| 4002 | Registry-/Networking-Aufruf in falscher Phase |
| 4010 | `ServiceRegistry#get` für nicht registrierten Typ |
| 4011 | `Capability` nicht verfügbar, aber ohne Prüfung genutzt |
| 4012 | `unwrap` mit falschem Zieltyp |
| 4013 | `ChannelHandle#sendToServer` auf dem Server aufgerufen (oder umgekehrt) |

## 29.4 Exception-Modell

```java
package dev.fabricmultiloader.format.error;

public class OmniException extends RuntimeException {
    private final ErrorCode code;
    private final String report;          // mehrzeilige, formatierte Meldung (kann null sein)

    public OmniException(ErrorCode code, String report)                  { … }
    public OmniException(ErrorCode code, String report, Throwable cause) { … }

    public ErrorCode code()  { return code; }
    public String    report(){ return report; }

    @Override public String getMessage() { return code.id() + "  " + firstLine() + "\n\n" + report; }
}

/** Nur für 4xxx: signalisiert einen Fehler im Modcode, nicht im Framework. */
public final class OmniApiMisuseException extends OmniException { … }
```

Warum eine `RuntimeException` und keine geprüfte Ausnahme: Der Bootstrap läuft in Fabric-Entrypoints, deren
Signaturen keine geprüften Ausnahmen erlauben. Die vollständige Message wird von
`EntrypointUtils`/`FormattedException` unverändert bis in die Fabric-Fehler-GUI bzw. den Serverlog
durchgereicht — deshalb steckt der komplette Bericht in `getMessage()` und nicht in einem separaten Kanal.

Es wird **keine** Loader-interne Klasse (`net.fabricmc.loader.impl.FormattedException`,
`FabricGuiEntry`) reflektiv angesprochen. Der Preis: Die GUI zeigt „Mod initialization failed“ als Titel und
unseren Bericht als Detailtext. Der Gewinn: Die Runtime funktioniert unverändert über alle Loader-Versionen
0.14–0.17+.

## 29.5 Strikter und nicht strikter Modus

| Modus | Auslöser | Verhalten bei `OMNI-2003` |
|---|---|---|
| **strict** (Default) | `container.strict = true`, kein Override | `OmniException` aus `preLaunch` ⇒ Spiel startet nicht. Begründung: Eine halb geladene Mod führt zu Folgefehlern, die niemand mehr zuordnen kann. |
| **lenient** | `container.strict = false` oder `-Dfabricmultiloader.strict=false` | Warnung `OMNI-2101`, Container deaktiviert sich, Spiel startet. Für Server-Admins und Modpack-Ersteller, die eine Mod temporär tolerieren wollen. Der Container registriert **nichts** und `FabricMultiLoader.isActive("examplemod")` liefert `false`. |
| **verbose-strict** | `-Dfabricmultiloader.strict=verbose` | wie strict, zusätzlich Volldump von Manifest und Umgebung |

Der Systemproperty-Override gilt global; `-Dfabricmultiloader.strict.examplemod=false` erlaubt es pro Mod.

## 29.6 Verhalten bei beschädigter JAR

| Schaden | Erkennung | Meldung |
|---|---|---|
| JAR abgeschnitten / kein gültiges ZIP | Fabric `ModDiscoverer` | Loader: „Could not open mod jar“, mit Dateiname |
| `fabric.mod.json` fehlt | Fabric | Loader-Meldung |
| `META-INF/omni-container.json` fehlt, aber `MANIFEST.MF` deklariert Omni | Runtime, Container-Scan | `OMNI-2001` inkl. SHA-256 der Datei und Hinweis „erneut herunterladen“ |
| Payload-Jar entfernt | Fabric (nested Jar in `jars[]` fehlt) | Loader-Meldung; zusätzlich `OMNI-2003`, das das fehlende Payload als „not loaded“ ausweist |
| Payload manipuliert | Runtime `IntegrityChecker` | `OMNI-2013` mit Soll-/Ist-Hash |
| Manifest manipuliert (Mod-ID geändert) | Runtime | `OMNI-2012` |

---

# 30. Diagnostics

## 30.1 Startbanner

Immer, auf `INFO`, eine Zeile pro Container (Kapitel 9.8). Bewusst knapp: Ein Modpack mit 40 Universal-Mods soll
den Log nicht fluten.

## 30.2 Diagnosebericht

Geschrieben bei jedem Fehlschlag und zusätzlich bei jedem Start, wenn `-Dfabricmultiloader.report=always` gesetzt
ist. Ort: `<gameDir>/.fabricmultiloader/<modId>-diagnostic.txt`. Atomar (Temp + `ATOMIC_MOVE`).

Inhalt: Zeitstempel, vollständige Umgebung, Container-Metadaten, alle Payloads mit jedem Constraint und dessen
Auswertung, Capability-Listen, Liste aller geladenen Mods mit Versionen (alphabetisch), aktive Systemproperties
mit Präfix `fabricmultiloader.`, sowie — falls vorhanden — der ursächliche Stacktrace.

Zusätzlich `<gameDir>/.fabricmultiloader/<modId>-last-launch.json` bei Erfolg (maschinenlesbar, für Modpack-Tools
und Support-Bots).

## 30.3 Crash-Report-Integration

Minecrafts Crash-Reports unterstützen benutzerdefinierte Abschnitte; die API dafür ist versionsabhängig, deshalb
liegt sie im Payload:

```java
@Override
public void installCrashContext(CrashContext ctx) {
    ctx.add("Active payload", "mc1214 (Minecraft 1.21.4, Java 21, Yarn 1.21.4+build.8)");
    ctx.add("Container", "examplemod 2.0.0 (omni/1, runtime 1.0.0)");
    ctx.add("Capabilities", "registries, commands, networking.typed, components");
}
```

`CrashContextImpl` sammelt die Einträge und der Payload-Adapter hängt sie über die versionsspezifische
API an (`CrashReportCallables`/`SystemDetails`, je Version unterschiedlich benannt). Ergebnis: In jedem
Crash-Report steht, welches Payload aktiv war — der wichtigste Datenpunkt für Bugreports einer
Multi-Version-Mod.

## 30.4 Debug-Modus

| Property | Wirkung |
|---|---|
| `-Dfabricmultiloader.debug=true` | Volldump: Manifest, Resolution-Report, Timing pro Phase, Payload-Extraktionspfad, Classloader-Identität |
| `-Dfabricmultiloader.debug.timing=true` | nur Timing (ns-Auflösung) |
| `-Dfabricmultiloader.verify=false` | SHA-256-Prüfung aus (für Modpack-Repacks, die Payloads rekomprimieren) |
| `-Dfabricmultiloader.strict=false\|verbose` | s. 29.5 |
| `-Dfabricmultiloader.report=always` | Bericht auch bei Erfolg |
| `-Dfabricmultiloader.slim=true` | erlaubt Standalone-Payload außerhalb Dev |

## 30.5 Laufzeit-Introspektion für Dritte

```java
package dev.fabricmultiloader.api;

public final class FabricMultiLoader {
    public static boolean isActive(String containerModId);
    public static java.util.Optional<String> activePayload(String containerModId);
    public static java.util.Optional<PlatformInfo> platformInfo(String containerModId);
    public static java.util.List<String> containers();
    public static String diagnosticReport(String containerModId);   // derselbe Text wie die Datei
}
```

Zusätzlich pro Container ein ObjectShare-Eintrag `"<modId>:omni"` mit einem `ContainerHandle`, damit fremde Mods
und Werkzeuge ohne Compile-Abhängigkeit auf die Runtime zugreifen können (reflektiv oder über `instanceof`).

Der Debug-Befehl `/fmlu` wird von der Runtime über `CommandsImpl` registriert, aber nur, wenn
`-Dfabricmultiloader.debug=true` gesetzt ist — im Normalbetrieb existiert er nicht, um keine Befehlsnamen zu
belegen. `/fmlu list`, `/fmlu info <modid>`, `/fmlu report <modid>`.

---

# 31. Validation

## 31.1 `./gradlew validateUniversalJar`

Der Validator arbeitet **ausschließlich auf der fertigen JAR** — nicht auf Gradle-Modellen. Damit prüft er das
Artefakt, das tatsächlich veröffentlicht wird, und kann auch auf fremde Universal-JARs angewendet werden
(`./gradlew validateExternalJar --jar=path/to/foo-universal.jar`).

Ausgabe: `build/reports/omni/validation.txt` (menschenlesbar), `validation.json` (maschinenlesbar, für CI-Annots),
Exit-Code ≠ 0 bei Fehlern, konfigurierbar auch bei Warnungen (`validation { failOnWarnings }`).

## 31.2 Die 34 Regeln

| # | Regel | Codes | Schwere |
|---|---|---|---|
| 1 | Struktur: `fabric.mod.json`, `META-INF/omni-container.json`, `MANIFEST.MF` vorhanden und konsistent | 2001, 1002 | Fehler |
| 2 | Manifest gegen Schema `omni/1` gültig, keine unbekannten Felder | 1002, 3001, 3002 | Fehler |
| 3 | Alle im Manifest deklarierten Payloads existieren als ZIP-Einträge | 1170 | Fehler |
| 4 | Alle Payload-Einträge sind in `fabric.mod.json.jars[]` deklariert und umgekehrt | 1011 | Fehler |
| 5 | SHA-256 und Größe jedes Payloads stimmen | 2013 | Fehler |
| 6 | Payload-Domänen sind paarweise disjunkt | 1010, 1012, 1015 | Fehler |
| 7 | Manifest-Constraints == Payload-`fabric.mod.json`-`depends` | 1011 | Fehler |
| 8 | Container-`depends.minecraft` == Union der effektiven Payload-Ranges | 1011 | Fehler |
| 9 | Container-`depends.java` == Minimum der Payload-Java-Minima | 1014, 1047 | Fehler |
| 10 | Lückenanalyse der MC-Abdeckung | 1013 | Info |
| 11 | Offene obere MC-Grenzen | 1050 | Warnung |
| 12 | Container enthält keine `assets/`, `data/` | 1023 | Fehler |
| 13 | Container deklariert keine `mixins`, keinen `accessWidener` | 1024 | Fehler |
| 14 | Container-Klassen liegen nur in `commonPackages` | 1043 | Fehler |
| 15 | Container-Klassen referenzieren kein Minecraft/Fabric-API/Mixin | 1042 | Fehler |
| 16 | Container-Classfile-Majors ≤ `baselineJavaMajor` | 1040 | Fehler |
| 17 | Payload-Classfile-Majors == `classfileMajor` und passend zu `requires.java` | 1041, 1046 | Fehler |
| 18 | Keine Multi-Release-Strukturen | 1049 | Fehler |
| 19 | Package-Disjunktheit zwischen allen Payloads und Common | 1044 | Fehler |
| 20 | Jede Mixin-Config des Payloads ist registriert und existiert | 1109, 1110 | Fehler |
| 21 | Mixin-Config-Regeln (Package, Klassenliste, `required`, `compatibilityLevel`) | 1100–1107 | Fehler |
| 22 | Refmap vorhanden, valide, nur eigene Klassen | 1031, 1032, 1033 | Fehler/Warnung |
| 23 | Config-, Refmap- und AW-Namen über alle Payloads eindeutig | 1030 | Fehler |
| 24 | AW im Payload hat Namespace `intermediary` und ist deklariert | 1082, 1123 | Fehler |
| 25 | AW-Ziele in den Mappings des Payloads auflösbar | 1121 | Warnung |
| 26 | Client-Referenzen nur in `clientOnly`-Packages; kein Common-Pfad dorthin | 1045, 1150 | Fehler |
| 27 | Client-Mixins nur in `environment: client`-Configs | 1105, 1106 | Fehler |
| 28 | Entrypoints: mindestens ein `common`, alle Klassen im Container vorhanden, korrektes Interface | 1141, 2030 | Fehler |
| 29 | `platformFactory` jedes Payloads existiert im Payload und implementiert `PlatformFactory` | 2020, 2022 | Fehler |
| 30 | Deklarierte Capabilities werden von der Platform-Klasse bedient | 1130 | Warnung |
| 31 | Keine unerwarteten Duplikate (identische Pfade aus mehreren Quellen), keine Signaturdateien, keine leeren Verzeichnisse | 1170 | Fehler |
| 32 | Keine verbotenen Referenzen (eigener ClassLoader, Loader-Interna) in Container und Payloads | 1036 | Fehler |
| 33 | Runtime-Mod eingebettet, Version im `depends`-Bereich, Hash korrekt | 1011 | Fehler |
| 34 | Reproduzierbarkeit: Zeitstempel, Eintragsreihenfolge, Kompressionsmethoden wie spezifiziert | 1060 | Fehler |

## 31.3 Beispielausgabe

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

## 31.4 Regelabschaltung mit Begründungspflicht

```kotlin
validation {
    ignore("OMNI-1121", because = "AW-Ziel existiert nur ab 1.21; Eintrag bleibt für Klarheit im shared-AW")
}
```

`because` ist ein Pflichtparameter. Ignorierte Regeln werden im Report als `IGNORED` mit Begründung aufgeführt,
damit ein Reviewer sie sieht. Regeln der Klasse „Fehler“ mit Sicherheitsrelevanz (5, 6, 7, 8, 9, 16, 17, 19, 32)
sind **nicht** abschaltbar (`OMNI-1003`).

---

# 32. Testing

## 32.1 Teststufen

| Stufe | Umfang | Laufzeit | Ort |
|---|---|---|---|
| **T1 Unit** | `format`, `runtime` (ohne Minecraft), `gradle-plugin`-Logik | < 20 s | `*/src/test/java` |
| **T2 Gradle-Funktionstests** | TestKit: echte Builds synthetischer Projekte | < 4 min | `gradle-plugin/src/functionalTest` |
| **T3 Loader-Conformance** | echter Fabric Loader gegen synthetische Container über die Loader-Matrix | < 5 min | `testing/src/conformanceTest` |
| **T4 Integration** | echte Minecraft-Server pro Matrixversion mit der echten Universal-JAR | 8–20 min | `example` + `ServerBootTestTask` |
| **T5 Client-Smoke** | echter Minecraft-Client bis zum Titelbildschirm (Xvfb) | 10–25 min | CI-only |

## 32.2 T1 — Unit-Tests (konkrete Testklassen)

`format`:

| Testklasse | Prüft |
|---|---|
| `SemVerParseTest` | alle Normalisierungsregeln aus Kapitel 12.2 (Tabelle als parametrisierter Test) |
| `SemVerCompareTest` | SemVer-2.0.0-Ordnung inkl. Prerelease, Build-Neutralität, `UNKNOWN` |
| `VersionPredicateParseTest` | `*`, `=`, `>=`, `>`, `<=`, `<`, AND-Ketten, Fehlerfälle (`OMNI-3011`) |
| `VersionPredicateEquivalenceTest` | **differenziell gegen `net.fabricmc.loader.api…VersionPredicate`**, 4096 generierte Fälle × jede Loader-Version der Matrix |
| `VersionRangeAlgebraTest` | union/intersect/subtract, Grenzfälle an Prerelease-Grenzen, Idempotenz, Kommutativität |
| `DomainDisjunctifierTest` | 30 Szenarien inkl. „mc1214 schlägt mcModern“, Java-Varianten, client/server-Varianten, `OMNI-1015` |
| `ManifestReaderTest` | Pflichtfelder, Typfehler mit Pointer, unbekannte Felder, Limits (`OMNI-3003`) |
| `ManifestRoundTripTest` | Read→Write→Read ist bytegleich; kanonische Schlüsselreihenfolge |
| `JsonParserTest` | RFC-8259-Suite, Positionsangaben, Limits |
| `PayloadMatcherTest` | jede Constraint-Art einzeln und kombiniert; Vollständigkeit der Rejection-Liste |
| `UnionNormalizationTest` | Verschmelzung angrenzender Intervalle, kanonische Predicate-Ausgabe |
| `Sha256Test` | Streaming-Hash gegen JDK-Referenz |
| `JavaVersionsTest` | 8/11/17/21/25/30, `1.8.0_402`, Classfile-Major-Formel |
| `ErrorCodeDocumentationTest` | jeder Code hat einen Doku-Abschnitt und umgekehrt |
| `MessagesSnapshotTest` | Golden-File-Vergleich der formatierten Meldungen (fängt versehentliche Textänderungen) |

`runtime` (mit `fabricmultiloader-testing`):

| Testklasse | Prüft |
|---|---|
| `EnvironmentDetectorTest` | gegen `FakeFabricLoader` (Interface-Fassade, in `testing`) |
| `ContainerDiscoveryTest` | mehrere Container, kein Container, defektes Manifest |
| `PayloadActivationTest` | genau-eins, keins, mehrere (`OMNI-2003/2004`) |
| `LifecycleStateMachineTest` | erlaubte/verbotene Übergänge, Idempotenz, `OMNI-4001` |
| `IntegrityCheckerTest` | korrekter/falscher Hash, abgeschaltete Prüfung |
| `DevFallbackTest` | Standalone-Payload in Dev und mit `-Dfabricmultiloader.slim=true` |
| `DiagnosticReportTest` | Golden-File der beiden Berichte aus Kapitel 29.2 |
| `ConditionalMixinPluginTest` | Bedingungsauswertung, fail-open bei defekter Config |
| `LogBridgeTest` | mit und ohne SLF4J auf dem Classpath (zwei Classpath-Varianten via Surefire-Profile) |

`gradle-plugin` (T1-Anteil, ohne Gradle-Runtime):

`MatrixParserTest`, `ClassfileScannerTest`, `ReferenceScannerTest`, `ResourceMergePlanTest`,
`AccessWidenerMergeTest`, `RuleSetTest`, `ReportFormatterTest`, `ManifestGeneratorTest`,
`ModJsonGeneratorTest` (Golden Files für beide `fabric.mod.json`-Varianten).

## 32.3 T2 — Gradle-Funktionstests (TestKit)

| Test | Szenario | Erwartung |
|---|---|---|
| `MinimalProjectTest` | 1 Payload, kein Mixin, keine AW | Build grün, JAR-Struktur exakt wie spezifiziert |
| `ThreeVersionProjectTest` | 1.20.1/1.21.1/1.21.4 | 3 Payloads, disjunkte Ranges, Union korrekt |
| `MixedJavaProjectTest` | Java 17 + 21 + 25 Payloads | Classfile-Majors 61/65/69, Container 61, `depends.java >=17` |
| `OverlapRejectedTest` | überlappende Ranges, gleiche Priorität | Build scheitert mit `OMNI-1010`, Meldungstext geprüft |
| `PrioritySubtractionTest` | catch-all + spezifisch | effektive Ranges wie in Kapitel 12.7 |
| `ResourceOverrideTest` | undeklarierter Override bei `strictOverrides` | `OMNI-1200` |
| `LangMergeTest` | `mergeLanguageFiles` | Schlüsselvereinigung, sortierte Ausgabe |
| `ContainerPurityTest` | MC-Import in `:common` | `OMNI-1042` |
| `ReproducibilityTest` | zweimal bauen | identischer SHA-256 |
| `ConfigurationCacheTest` | `--configuration-cache` zweimal | zweiter Lauf „reused“, 0 Probleme |
| `UpToDateTest` | zweiter Lauf ohne Änderung | alle Tasks `UP-TO-DATE` |
| `AddVersionTaskTest` | `addMinecraftVersion` | Matrixeintrag, Verzeichnis, Stubs, danach grüner Build |
| `SlimJarTest` | `buildSlimJars` | ein lauffähiges Standalone-Jar pro Payload |

## 32.4 T3 — Loader-Conformance-Harness (die tragende Annahme)

Ziel: Beweisen, dass jede unterstützte Fabric-Loader-Version genestete Mods mit unerfüllbaren `depends`
**verwirft statt zu scheitern**.

```java
// testing/src/main/java/dev/fabricmultiloader/testing/LoaderConformanceHarness.java
public final class LoaderConformanceHarness {
    /**
     * Baut einen synthetischen Container mit N Payloads, startet den echten Fabric Loader
     * headless (ohne Minecraft: eigener GameProvider-freier Testpfad über
     * net.fabricmc.loader.impl.discovery + ModSolver, aufgerufen als Bibliothek),
     * und liefert die Menge der ausgewählten Mod-IDs.
     */
    public Set<String> resolve(LoaderVersion loader, SyntheticContainer container, FakeEnv env);
}
```

Realisierung: Der Loader wird als **Bibliothek** (`net.fabricmc:fabric-loader:<v>`) in einen isolierten
`URLClassLoader` des Testprozesses geladen — das ist der einzige Ort im Projekt, an dem ein eigener ClassLoader
existiert, und er liegt ausschließlich im Testcode (Validator-Regel 32 gilt für Produktionsartefakte). Die
Interaktion erfolgt reflektiv über die `impl`-Klassen; bricht ein Loader-Update diese Reflection, schlägt der
Test fehl und die Annahme wird manuell nachgeprüft — genau das gewünschte Frühwarnsystem.

| Conformance-Test | Erwartung |
|---|---|
| `NestedUnsatisfiableIsDropped` | Payload mit `minecraft 1.20.1` wird auf MC 1.21.4 verworfen, Container lädt |
| `ExactlyOneSelected` | Bei drei disjunkten Payloads wird genau eines gewählt |
| `ProvidesExclusivity` | Zwei Payloads mit gleichem `provides` können nie beide geladen werden |
| `BreaksExclusivity` | wechselseitige `breaks` werden respektiert |
| `JavaDependencyEvaluated` | `depends.java >=21` verwirft das Payload auf Java 17 |
| `EnvironmentEvaluated` | `environment: client` wird auf dem Server nicht geladen |
| `RuntimeDeduplication` | Zwei Container mit Runtime 1.0.0 und 1.1.0 ⇒ nur 1.1.0 geladen |
| `ContainerRangeError` | MC außerhalb der Union ⇒ Loader-Fehler mit den Bereichen im Text |

Matrix: Loader `0.14.21`, `0.15.11`, `0.16.9`, `0.16.14`, `0.17.x` (jeweils die neueste Patchversion) —
in CI täglich (`schedule`) und bei jedem Release. Ein neuer Loader wird damit **vor** den Nutzern getestet.

## 32.5 T4 — Integrationstests mit echten Minecraft-Servern

`ServerBootTestTask` pro Payload:

```
1. Arbeitsverzeichnis  build/omni/itest/<payloadId>/   (leer)
2. Fabric-Server-Launcher erzeugen:
     java -jar fabric-installer-1.0.3.jar server \
          -mcversion <matrix.minecraft> -loader <matrix.loader> \
          -dir . -downloadMinecraft
3. eula.txt schreiben (nur wenn integrationTests.acceptEula = true;
   sonst Task-Fehler mit Erklärung)
4. server.properties: level-type=flat, online-mode=false, max-players=1,
   view-distance=4, spawn-protection=0, level-seed=omni
5. mods/ füllen:
     · examplemod-2.0.0-universal.jar          (das echte Artefakt)
     · fabric-api-<matrix.fabricApi>.jar
     · pro [versions.X.dependencies] mit Range: die deklarierte Mod
     · omni-itest-probe.jar                    (aus fabricmultiloader-testing)
6. Start: java -Xmx2G -Dfabricmultiloader.report=always -jar fabric-server-launch.jar nogui
7. Die Probe-Mod:
     · liest FabricMultiLoader.activePayload("examplemod")
     · prüft erwartete payloadId (per -Domni.itest.expect=mc1214)
     · prüft, dass genau ein Payload geladen ist
     · lässt <ticks> Ticks laufen, ruft dann einen Mod-Command über die
       Server-Konsole auf (/ruby info) und prüft die Antwort im Log
     · schreibt build/omni/itest/<id>/result.json und stoppt den Server
8. Task prüft: Exitcode 0, result.json.status == "ok",
   Log frei von "OMNI-2", "Mixin apply failed", "Exception in thread"
```

Der Test verwendet die **identische** Universal-JAR für alle Payloads — genau der im Auftrag geforderte Nachweis
„dieselbe Datei in jeder Umgebung“.

| Testfall | Umgebung |
|---|---|
| `itest mc1201` | MC 1.20.1, Java 17, Loader 0.14.21, Fabric API 0.92.2 |
| `itest mc1211` | MC 1.21.1, Java 21, Loader 0.15.11, Fabric API 0.102.0 |
| `itest mc1214` | MC 1.21.4, Java 21, Loader 0.16.9, Fabric API 0.114.0 |
| `itest unsupported` | MC 1.19.2 ⇒ erwarteter kontrollierter Abbruch, Log **muss** die Bereichsliste enthalten und **darf keinen** `NoClassDefFoundError` enthalten |
| `itest wrongJava` | MC 1.21.4 mit Java 17 gestartet ⇒ Loader lehnt ab; Log enthält Java-Anforderung |
| `itest oldFabricApi` | MC 1.21.4 mit Fabric API 0.110.0 ⇒ `OMNI-2003` mit korrekter Begründung |
| `itest lenient` | wie `unsupported`, aber `-Dfabricmultiloader.strict=false` ⇒ Server startet, `OMNI-2101` im Log |

## 32.6 T5 — Client-Smoke-Test

Der Client wird bis `ClientLifecycleEvents.CLIENT_STARTED` gebootet (dieses Event existiert stabil ab 1.16), die
Probe-Mod ruft dann `MinecraftClient#scheduleStop`. Ausführung unter `xvfb-run` auf Linux; Software-Rendering
über Mesa/llvmpipe. Für MC-Versionen, in denen Fabric die Client-Gametest-API anbietet, wird zusätzlich ein
Titelbildschirm-Screenshot als CI-Artefakt gespeichert.

Der Client-Test ist **nicht** blockierend für Releases (er ist in CI als `continue-on-error: false` nur auf
`main` aktiv, aber mit dokumentiertem Retry), weil GPU-lose Client-Starts in CI historisch instabil sind. Der
Server-Test ist blockierend.

## 32.7 Testhilfen für Modentwickler (`fabricmultiloader-testing`)

```java
// Unit-Test von Common-Code ohne Minecraft
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

`FakeModContext` zeichnet alle Registrierungen, Kanäle, Commands und Event-Abonnements auf. Damit ist der
gesamte Common-Code — also der Großteil der Modlogik — **ohne Minecraft und ohne Loom** testbar, in
Millisekunden. Das ist ein Nebenprodukt der Architektur (P1: keine MC-Typen in der Common-API) und ein echtes
Argument für Modautoren.

---

# 33. CI/CD

## 33.1 Workflows

| Workflow | Trigger | Zweck |
|---|---|---|
| `build.yml` | push, pull_request | Framework bauen, T1+T2, Beispielmod bauen + validieren |
| `integration.yml` | push auf `main`, pull_request mit Label `integration`, nightly | T4 + T5 über die Matrix |
| `conformance.yml` | nightly, manuell, Release | T3 über die Loader-Matrix |
| `release.yml` | Tag `v*` | Vollpipeline + Maven-, Modrinth-, CurseForge-, GitHub-Release |
| `docs.yml` | push auf `main` (Pfad `docs/**`) | Doku-Site bauen und deployen |

## 33.2 `build.yml` (vollständig)

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

## 33.3 `integration.yml` (vollständig)

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

## 33.6 Laufzeitbudget

| Job | Dauer (Referenz, ubuntu-24.04) |
|---|---|
| `framework` | 4–6 min |
| `example` | 5–8 min (Loom-Cache warm: 2–3 min) |
| `server` (je Matrixeintrag, 6 parallel) | 5–9 min |
| `client-smoke` (2 parallel) | 12–20 min |
| `loader-matrix` (5 parallel) | 3–4 min |
| Release insgesamt | 25–35 min |

Der Loom-Cache (`~/.gradle/caches/fabric-loom`, dekompilierte Minecraft-Quellen) wird über
`gradle/actions/setup-gradle` mit gecacht; ohne Cache verlängert sich `example` um ~6 min pro MC-Version.

---

Weiter mit [Kapitel 34–38 — Distribution, Beispielmod, Migration, neue Versionen, Dokumentation](part-09-project.md).
