# 39. Security

## 39.1 Bedrohungsmodell

Mods sind beliebiger, vom Nutzer bewusst installierter Java-Code ohne Sandbox. Ein Framework kann daran nichts
ändern. Das Sicherheitsziel ist deshalb präzise begrenzt:

> **FabricMultiLoader darf keine Angriffsfläche einführen, die eine gewöhnliche Fabric-Mod nicht ohnehin hat —
> und es soll erkennen, wenn die ausgelieferte Datei nicht mehr die gebaute Datei ist.**

| Angreifer | Fähigkeit | Relevanz |
|---|---|---|
| A1 — Manipulierter Download (MITM, kompromittierter Mirror, Modpack-Repack) | ersetzt Payload-Bytes in der JAR | **hoch**, adressiert |
| A2 — Bösartige Universal-JAR eines Drittanbieters | liefert manipuliertes Manifest | mittel — der Code wäre ohnehin bösartig; Ziel ist nur, dass *das Framework* nicht zum Hebel wird |
| A3 — Bösartige Fremdmod im selben Spiel | versucht, unsere Payload-Auswahl zu beeinflussen | niedrig, adressiert |
| A4 — Lokaler Angreifer mit Schreibrechten im Spielverzeichnis | manipuliert Cache-/Temp-Dateien | mittel, adressiert |
| A5 — Bösartiges Modprojekt gegen den Build-Rechner (CI) | präparierte Matrix/Ressourcen | mittel, adressiert |

## 39.2 Zip-Slip und Path Traversal

FabricMultiLoader **entpackt nichts**. Sämtliche Payload-Extraktion macht der Fabric Loader mit seinem eigenen,
geprüften Code. Alle Lesezugriffe der Runtime laufen über `ModContainer#findPath(String)`, das intern einen
`Path` in einem vom Loader verwalteten `FileSystem` liefert — Pfade aus dem Manifest werden **nie** an
`new File(...)`, `Paths.get(...)` oder `FileSystems.newFileSystem(...)` übergeben.

Zusätzliche Absicherung im `ManifestReader`, weil Manifestinhalte grundsätzlich als nicht vertrauenswürdig
behandelt werden (`OMNI-3004`):

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

Dieselbe Prüfung läuft im Validator (Build-Zeit) und in der Runtime (Laufzeit) — derselbe Code aus `format`,
also keine Divergenz.

## 39.3 Manipulierte Payloads

* `payloads[].sha256` und `size` werden beim Start des aktiven Payloads geprüft (`IntegrityChecker`), sofern
  `verifyIntegrity = true` (Default). Geprüft wird der **ZIP-Eintrag im Container**, gestreamt mit 64-KiB-Puffer,
  nicht der extrahierte Cache — damit wird A1 erkannt.
* Kosten: ~8 ms für ein 1,5-MiB-Payload (SHA-256 mit Intrinsics), einmalig beim Start. Messwert aus
  `BootstrapBenchmark`.
* Abweichung ⇒ `OMNI-2013` mit Soll-/Ist-Hash und dem Hinweis, dass Modpack-Repacks
  `-Dfabricmultiloader.verify=false` setzen können. Bewusst kein stiller Fallback: Ein Hash-Mismatch ist
  entweder Manipulation oder ein defekter Download, und beides muss sichtbar sein.
* Die Runtime prüft **nur** das aktive Payload, nicht alle — sonst würde der Start pro Payload teurer, ohne
  Nutzen (inaktive Payloads werden nie ausgeführt).

## 39.4 Nicht vertrauenswürdige Metadaten

| Eingabe | Behandlung |
|---|---|
| Manifest-JSON | Größenlimit 1 MiB, Tiefenlimit 64, Eintragslimit 4096, Stringlimit 65536 (`OMNI-3003`). Kein `eval`, keine Deserialisierung in beliebige Typen, kein Reflection-basiertes Binding — der Parser erzeugt nur `JsonValue`-Bäume, das Mapping auf Modellklassen ist handgeschrieben und typprüfend. |
| `platformFactory`-FQCN | Wird gegen `^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$` geprüft und muss mit einem der `packages`-Präfixe des Payloads beginnen (`OMNI-2024`). Damit kann ein manipuliertes Manifest nicht eine *fremde* Klasse (etwa aus einer anderen Mod oder aus dem JDK) instanziieren. `Class.forName(..., initialize = false)` verhindert, dass ein statischer Initializer vor der Typprüfung läuft. |
| Entrypoint-FQCN | analog, muss in `commonPackages` liegen (`OMNI-2032`). |
| Mod-IDs im Manifest | Fabric-ID-Regex; Länge ≤ 64. |
| Versionsstrings | eigener Parser, keine Rekursion, keine Backtracking-Regex (bewusst kein `String#matches` mit komplexen Ausdrücken ⇒ kein ReDoS). |
| Pfade | 39.2 |

## 39.5 Classloader-Isolation

Es gibt keine — und das ist die sichere Variante. Ein eigener ClassLoader mit Delegationsregeln wäre eine
zusätzliche Vertrauensgrenze, die man korrekt implementieren müsste; ihre Verletzung wäre schwer zu erkennen.
Das Ein-ClassLoader-Modell hat exakt die Sicherheitseigenschaften einer normalen Fabric-Mod: keine besseren,
keine schlechteren.

## 39.6 Temporäre Dateien

Die Runtime schreibt genau zwei Dateien, beide unter `<gameDir>/.fabricmultiloader/`:

```java
Path dir  = gameDir.resolve(".fabricmultiloader");
Files.createDirectories(dir);
Path tmp  = Files.createTempFile(dir, modId + "-", ".tmp");     // im Zielverzeichnis, nicht in /tmp
Files.write(tmp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
Path dest = dir.resolve(modId + "-diagnostic.txt");
try { Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE); }
catch (AtomicMoveNotSupportedException e) { Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING); }
```

Eigenschaften: Kein `java.io.tmpdir` (kein Symlink-Angriff über ein weltschreibbares Verzeichnis), keine
vorhersagbaren Namen für die Temp-Datei, atomarer Austausch, Erzeugung im Zielverzeichnis (damit `ATOMIC_MOVE`
auf demselben Dateisystem funktioniert). Fehler beim Schreiben werden geloggt, brechen aber den Start **nicht** ab
— ein nicht schreibbares Spielverzeichnis ist kein Grund, das Spiel zu verhindern.

## 39.7 A3 — Beeinflussung durch Fremdmods

Eine bösartige Fremdmod könnte theoretisch versuchen, die Payload-Auswahl zu beeinflussen. Analyse:

* Über `provides` denselben Alias bereitstellen ⇒ würde die Payload-Auswahl blockieren (Denial of Service, kein
  Rechtegewinn). Erkannt durch `OMNI-2003` mit Nennung der kollidierenden Mod, weil der Bericht alle Mods listet,
  die den Alias bereitstellen.
* Über `breaks` ein Payload ausschließen ⇒ dasselbe; Loader meldet den Konflikt selbst.
* Klassen mit unseren FQCN mitliefern ⇒ Classpath-First-Wins. Das ist ein generelles Fabric-Problem, nicht
  frameworkspezifisch. Mitigation: Die Runtime prüft beim Start, dass
  `PlatformLoader.class.getClassLoader().getResource("dev/fabricmultiloader/runtime/boot/RuntimeBootstrap.class")`
  aus der erwarteten Mod-JAR stammt (Vergleich des URL-Präfixes mit `ModContainer#getRootPaths()` der Mod
  `fabricmultiloader`) und warnt sonst mit `OMNI-2050`. Kein Abbruch — nur Sichtbarkeit, weil legitime
  Sonderfälle (Dev-Classpath) existieren.

## 39.8 A5 — Build-Zeit-Sicherheit

* Der Validator führt **keinen** Modcode aus. Alle Prüfungen sind statisch (ZIP-Lesen, Classfile-Header,
  Konstantenpool-Scan, JSON-Parsen). Insbesondere wird `Platform#capability` nicht reflektiv aufgerufen, sondern
  über den Konstantenpool geprüft (Kapitel 19.6).
* Der Ressourcen-Merge folgt nur Pfaden innerhalb der deklarierten Quellverzeichnisse; Symlinks, die aus dem
  Projekt hinausführen, werden erkannt und abgelehnt (`OMNI-1204`).
* Das Publishing liest Tokens ausschließlich aus Umgebungsvariablen über `Provider`s und protokolliert sie nie;
  bei aktivem `--info` wird der Wert durch `***` ersetzt.
* `SECURITY.md` definiert den Meldeweg für Schwachstellen (private GitHub-Security-Advisory), eine
  Reaktionszusage von 7 Tagen und die Regel, dass Sicherheitsfixes als Patch-Release aller betroffenen
  Minor-Linien erscheinen.

## 39.9 Keine Signaturen — begründete Entscheidung

Ein Signaturschema (`container.signatures` ist als Feld reserviert) würde erfordern: Schlüsselverwaltung,
Verteilung öffentlicher Schlüssel, Widerruf, Vertrauensanker. Ohne diese Infrastruktur wäre eine Signatur
kryptografisch korrekt, aber semantisch wertlos (der Angreifer signiert einfach selbst). Die reale
Vertrauenskette läuft über die Distributionsplattform (HTTPS, Projektinhaberschaft) und die veröffentlichten
SHA-256-Summen. Diese Entscheidung ist dokumentiert und revidierbar: Das Feld existiert, `omni/1`-Reader
ignorieren es, ein künftiges `omni/2` könnte es verpflichtend machen.

---

# 40. Performance

## 40.1 Startzeit — Messmodell

`BootstrapBenchmark` (JMH für die Einzelschritte, plus eine Ende-zu-Ende-Messung im Integrationstest) misst:

| Phase | Messwert (Referenz: Ryzen 7 5800X, NVMe, JDK 21) | Anmerkung |
|---|---|---|
| Container-Discovery (Scan aller Mods nach Manifest) | 0,8 ms bei 40 Mods, 2,6 ms bei 300 Mods | ein `findPath` pro Mod |
| Manifest-Parse (3 Payloads, ~4 KiB JSON) | 0,4 ms | eigener Parser |
| Resolution + Selbstprüfung | 0,15 ms | reine Intervallarithmetik |
| Integritätsprüfung (SHA-256, 1,5 MiB) | 7,9 ms | abschaltbar; dominiert den Overhead |
| `PlatformFactory` laden + instanziieren | 1,1 ms | eine Klasse + Konstruktor |
| Diagnosebericht schreiben (nur bei `report=always`) | 1,4 ms | Default aus |
| **Summe (Default-Konfiguration)** | **≈ 10,4 ms** | NF-01 (< 15 ms) erfüllt |
| Summe mit `verify=false` | ≈ 2,5 ms | |

Zum Vergleich: Fabric Loaders eigene Mod-Discovery liegt bei 40 Mods im Bereich 150–400 ms, ein
Minecraft-Client-Start bei 8–25 s. Der Framework-Overhead ist damit nicht messbar für den Nutzer.

## 40.2 Payload-Extraktion

Die Extraktion macht der Loader (Phase 2.3d) nach `<gameDir>/.fabric/processedMods/`. Eigenschaften:

* **Nur das ausgewählte Payload** wird extrahiert — nicht alle. Bei 4 Payloads à 1,5 MiB werden also 1,5 MiB
  geschrieben, nicht 6 MiB.
* Der Loader cached hash-/namensbasiert; ab dem zweiten Start entfällt der Schreibvorgang.
* Weil Payloads mit **STORED** eingebettet sind (Kapitel 10.5), ist die Extraktion ein reiner Bytekopiervorgang
  ohne Inflate: gemessen 11 ms statt 17 ms für 1,5 MiB.
* FabricMultiLoader implementiert **keinen** eigenen Cache, kein Locking, keine Cleanup-Logik. Das ist die
  Antwort auf die Anforderung „bevorzuge eine Lösung ohne unnötige Extraktion“: Die einzige Extraktion ist die,
  die der Loader für *jede* JiJ-Mod ohnehin durchführt, und sie erfolgt über seinen bereits gehärteten,
  mehrinstanzfähigen Cache.
* Mehrere Minecraft-Instanzen: Jede hat ihr eigenes `<gameDir>/.fabric/`. Zwei Instanzen mit demselben `gameDir`
  gleichzeitig sind auch ohne uns nicht unterstützt.

## 40.3 Speicherverbrauch

| Posten | Verbrauch |
|---|---|
| `ContainerManifest` (3 Payloads) | ~11 KiB (immutable, bleibt für Diagnose im Speicher) |
| `Environment` + `ResolutionReport` | ~4 KiB |
| Runtime-Klassen (geladen) | 38 Klassen, ~180 KiB Metaspace |
| `format`-Klassen (geladen) | 29 Klassen, ~120 KiB Metaspace |
| Common-Klassen des Mods | wie bei jeder Mod |
| **Framework-Overhead nach Init** | **≈ 320 KiB** (NF-02: < 512 KiB erfüllt) |

Der `ResolutionReport` wird bewusst behalten (nicht freigegeben): Er ist die Grundlage für `/fmlu info` und für
Crash-Report-Anhänge. 4 KiB sind der Preis für nachträgliche Diagnostizierbarkeit.

## 40.4 JAR-Größe

| Bestandteil | Beispielmod |
|---|---|
| Container-Metadaten + Manifest | 6 KiB |
| Common-Klassen | 210 KiB |
| Icon, Lizenz | 12 KiB |
| Runtime-Mod | 62 KiB |
| 3 Payloads (inkl. je vollständiger Ressourcenkopie) | 4,47 MiB |
| **Gesamt** | **4,82 MiB** |
| Vergleich: 3 klassische Einzel-JARs | 3 × 1,63 MiB = 4,89 MiB (Summe der Downloads für einen Nutzer mit 3 Instanzen) |

Bemerkenswert: Für einen Nutzer mit **einer** Instanz ist die Universal-JAR ca. 3× größer als die
Einzelversion. Für einen Nutzer mit **drei** Instanzen ist sie gleich groß, und er muss nur eine Datei
verwalten. Für den Modautor entfällt die Fehlerquelle „falsche Datei heruntergeladen“, die im Support real
messbar ist.

Größenreduktion, falls gewünscht: `buildSlimJars` (34.8) oder Reduktion der Ressourcenduplikation durch
Verschieben großer Assets in ein separates Resource-Pack-Mod — beides dokumentiert, beides nicht Default.

## 40.5 Classloading

* Nicht ausgewählte Payloads: **null** Klassen geladen, **null** Bytes gelesen.
* Aktives Payload: identisch zu einer normalen Mod.
* Container-Common: wird lazy geladen; Klassen, die nur auf einer Seite gebraucht werden, werden auf der anderen
  nie definiert.
* Keine zusätzliche Indirektion im Hot Path: `Registries`/`Networking`-Aufrufe sind normale Interface-Aufrufe auf
  eine monomorphe Implementierung (genau ein Payload) — JIT inlined sie vollständig. Es gibt **keinen**
  Reflection-Aufruf im Spielbetrieb.
* Der einzige messbare Indirektionspunkt ist die `ByteSink`/`ByteSource`-Abstraktion im Networking. Messung mit
  JMH (`ByteSinkBenchmark`): 1,3 ns Overhead pro Schreibvorgang gegenüber direktem `PacketByteBuf`-Zugriff,
  monomorph inlined. Bei typischen Paketen (< 30 Feldern) also < 40 ns — irrelevant gegenüber der
  Netzwerklatenz.

## 40.6 Build-Performance

| Vorgang | Zeit (kalt / warm) |
|---|---|
| `:common:jar` | 4 s / 0,3 s |
| `:versions:mc-X:build` (pro Version) | 45 s / 3 s (Loom-Cache warm) |
| `generateOmniManifest` (3 Payloads, inkl. Hashes) | 0,9 s / UP-TO-DATE |
| `assembleUniversalJar` | 1,2 s / UP-TO-DATE |
| `validateUniversalJar` (34 Regeln, 4,8 MiB) | 2,1 s |
| `buildUniversalJar` gesamt | ~2,5 min kalt / ~12 s warm |

Die drei Version-Module bauen parallel (`org.gradle.parallel=true` im Template). Alle Tasks sind
`@CacheableTask`, sodass ein Build-Cache (lokal oder remote) die Version-Builds zwischen Branches
wiederverwendet.

---

# 41. Compatibility Limits

## 41.1 Garantien

| # | Garantie | Grundlage |
|---|---|---|
| C1 | Dieselbe Datei lädt auf jeder MC-Version, für die ein Payload existiert und dessen Constraints erfüllt sind | Loader-Solver + JiJ |
| C2 | Genau ein Payload ist aktiv | Disjunktheitsbeweis + `provides` + `breaks` + Runtime-Assertion |
| C3 | Klassen, Mixins, AW und Ressourcen inaktiver Payloads sind zur Laufzeit nicht vorhanden | JiJ-Auswahl |
| C4 | Payloads dürfen unterschiedliche Java-Major-Versionen erfordern | `depends.java` |
| C5 | Bei nicht unterstützter Umgebung erscheint eine erklärende Meldung, kein JVM-/Mixin-Fehler | Container-Range + `preLaunch`-Diagnose |
| C6 | Die Mod erscheint für andere Mods unter einer Mod-ID mit einer Version | Container trägt die primäre ID |
| C7 | Die öffentliche API der Mod ist über alle unterstützten MC-Versionen binärkompatibel | Common im Container, keine MC-Typen |
| C8 | Builds sind reproduzierbar | Kapitel 10.5, verifiziert in CI |
| C9 | Es wird kein eigener ClassLoader erzeugt und keine Laufzeit-Bytecode-Transformation durchgeführt | Invariante I1 |

## 41.2 Grenzen, mit Begründung und Workaround

| Grenze | Technische Begründung | Workaround |
|---|---|---|
| **Fabric Loader < 0.14.0** | `ModContainer#findPath` (0.12+), `provides` (0.12+), `getObjectShare` (0.12+) und die getestete Solver-Semantik sind darunter nicht durchgängig vorhanden. | Nutzer auf ≥ 0.14 verweisen; Container deklariert `depends.fabricloader >=0.14.21` ⇒ klare Loader-Meldung. |
| **Minecraft < 1.16.5** | Intermediary-Stabilität, Fabric-API-Verfügbarkeit und Java-8-Kompatibilität der Toolchain sind nicht mehr sinnvoll testbar. | Für 1.12-Ära existieren andere Ökosysteme; keine Unterstützung. |
| **Ein Kompilat für mehrere MC-Versionen** | Deskriptoränderungen (Kapitel 5.6.2). | Nicht lösbar. Genau dafür gibt es Payloads. |
| **Vollständige Abstraktion der MC-API** | Nicht-Ziel N1; Rendering, Weltgenerierung, Codecs, DataFixer und Registry-Timing ändern sich zu tief. | `Services` + `Capabilities` + `unwrap`. |
| **Mixins, die vor dem Loader-Mixin-Bootstrap greifen müssen** | Es gibt in Fabric keine Phase vor 2.4 für Mod-Code. | Kein Bedarf: Payload-Mixins werden in 2.4 registriert, also so früh wie bei jeder normalen Mod. Frühere Eingriffe verlangen ohnehin einen Loader-Plugin-Mechanismus, den Fabric nicht anbietet. |
| **Core-Transformationen (eigener Class-Transformer)** | Fabric bietet keine öffentliche Transformer-API; Knots Kette ist nicht erweiterbar. | Mixin verwenden. Reicht für praktisch alle Fälle; wo nicht, ist die Mod auch ohne FabricMultiLoader blockiert. |
| **Access Widener auf Klassen, die nur in einer Version existieren** | AW kann keine Bedingungen ausdrücken. | Eintrag in die payload-spezifische AW-Datei statt in `shared.accesswidener`; Warnung `OMNI-1121` weist auf falsch platzierte Einträge hin. |
| **Zwei Universal-Mods mit inkompatiblen Runtime-Majorversionen** | Fabric lädt pro Mod-ID nur eine Version. | Major-Wechsel nutzt neue Mod-ID + neues Package (Kapitel 42.3), sodass 1.x und 2.x koexistieren. |
| **Fremdmods, die auf die Payload-Mod-ID depends** | Die Payload-IDs sind Implementierungsdetail und können sich ändern. | Dokumentiert: Drittmods hängen **immer** an der Container-ID. `provides`-Alias ist ebenfalls intern. |
| **Modpack-Tools, die JARs rekomprimieren** | Payload-Hashes ändern sich. | `OMNI-2013` erklärt es; `-Dfabricmultiloader.verify=false` oder Rekomprimierung vermeiden. |
| **Quilt Loader** | Quilt lädt Fabric-Mods, hat aber einen eigenen Resolver mit abweichender Behandlung optionaler genesteter Mods. | Nicht getestet, nicht garantiert. Der Conformance-Harness ist so gebaut, dass Quilt später als weitere „Loader-Version“ ergänzt werden könnte. |
| **Fabric-Loader-Änderung an der tragenden Annahme** | Hypothetisch. | (1) `conformance.yml` erkennt es nachts, bevor Nutzer betroffen sind, und öffnet automatisch ein Issue. (2) Rückfallpfad ohne Codeänderung: `buildSlimJars` + Veröffentlichung pro Version. (3) Zweiter Rückfallpfad: `commonPackaging = EMBEDDED` + Payload als *Root*-Mod in `mods/<mcversion>/`-Unterordnern (Loader ≥ 0.15) — funktioniert, verletzt aber G1. |
| **Client-only-Mods auf dedizierten Servern** | Payload mit `environment: client` wird dort nicht geladen. | Beabsichtigt; `OMNI-2003` sagt explizit „Client-Mod“. |
| **Kotlin-Common-Code** | Die Kotlin-Runtime darf nicht in den Container (FQCN-Kollision), und `fabric-language-kotlin` ist MC-versionsgebunden. | Kotlin im Payload verwenden, Common in Java; oder `fabric-language-kotlin` pro Payload als `omniMod`. Warnung `OMNI-1184`. |
| **Java-Records/`sealed` in `format`/`api`/`runtime`** | Java-8-Baseline. | Builder-Pattern; im Modcode (Common ab Baseline 17) frei nutzbar. |

## 41.3 Der Fallback-Modus `commonPackaging = EMBEDDED`

Vorsorge für den Fall, dass ein künftiger Fabric Loader Mods klassenmäßig isoliert (mehrfach diskutiert, nie
umgesetzt). Dann könnte ein Payload die Common-Klassen des Containers nicht mehr sehen.

Umstellung: **eine Zeile** in der Matrix (`commonPackaging = "embedded"`). Wirkung:

* Common-Klassen werden in **jedes** Payload kopiert (der Container enthält dann nur Metadaten + Icon).
* Der Container behält seine Mod-ID und seinen `preLaunch`-Entrypoint; er referenziert nur Runtime-Klassen.
* Die öffentliche Mod-API (Garantie C7) liegt dann im Payload — Drittmods müssten weiterhin nur einmal
  kompilieren, weil das API-Artefakt unverändert bleibt, aber die geladene Implementierung wäre payloadgebunden.
* Kosten: JAR wächst um (N−1) × Common-Größe; für die Beispielmod 2 × 210 KiB = 420 KiB.

Der Modus ist implementiert und wird in CI mitgetestet (`EmbeddedPackagingTest`), damit er im Bedarfsfall
funktioniert und nicht erst reparieren werden muss.

---

# 42. Versioning

## 42.1 Vier unabhängige Versionsachsen

| Achse | Beispiel | Bedeutung | Wer erhöht |
|---|---|---|---|
| **Library-/Release-Version** | `1.4.2` | Gemeinsame Version von `format`, `api`, `runtime`, `processor`, `gradle-plugin`, `testing`. Ein Release-Zug, damit Kombinationsmatrizen entfallen. | Maintainer |
| **Containerformat** | `omni/1` | Struktur der JAR (Pfade, Rollen, Kompression). Änderung nur bei struktureller Inkompatibilität. | Maintainer, sehr selten |
| **Manifest-Schemaversion** | `schemaVersion: 1` | Felder und Semantik von `omni-container.json`/`payload.json`. Additive Felder erhöhen sie **nicht**. | Maintainer, selten |
| **Payload-/Container-Version des Mods** | `2.0.0`, `2.0.0+mc1.21.4` | Version der Mod selbst. | Modautor |

## 42.2 Semantic-Versioning-Regeln des Frameworks

| Änderung | Version | Beispiele |
|---|---|---|
| Patch | `1.4.2` → `1.4.3` | Bugfix, bessere Fehlermeldung, neue Validator-Warnung, Doku |
| Minor | `1.4.x` → `1.5.0` | neue API-Methoden mit `default`, neue Capability, neuer Gradle-Task, neues optionales Manifestfeld, neue Validator-**Fehler**regel (weil sie bestehende Builds brechen kann — deshalb minor, nicht patch) |
| Major | `1.x` → `2.0` | Entfernen/Umbenennen öffentlicher API, Änderung von `Platform`-Pflichtmethoden, neues Pflicht-Manifestfeld, Anhebung der Java-Baseline über 8, Anhebung der Loader-Untergrenze |

**Binärkompatibilitätsprüfung in CI:** `japicmp` (bzw. `binary-compatibility-validator` für Kotlin-Teile) prüft
`format` und `api` gegen die letzte veröffentlichte Version desselben Majors. Ein Bruch ohne Major-Bump schlägt
den Build fehl. Für `runtime` gilt die Prüfung nur für die als `@PublicApi` markierten Klassen
(`FabricMultiLoader`, `AbstractPlatform`, Adapter-Basisklassen).

## 42.3 Major-Wechsel ohne Zwangsupdate

Weil Fabric pro Mod-ID nur eine Version lädt, würde ein Major-Wechsel bei gleicher Mod-ID alle Mods zum
gleichzeitigen Update zwingen. Deshalb gilt:

| Major | Mod-ID | Root-Package | Manifest |
|---|---|---|---|
| 1.x | `fabricmultiloader` | `dev.fabricmultiloader.*` | `omni/1` |
| 2.x | `fabricmultiloader2` | `dev.fabricmultiloader.v2.*` | `omni/2` |

Damit können eine Mod mit Runtime 1.x und eine Mod mit Runtime 2.x im selben Spiel koexistieren. Die Regel ist
im Contributor Guide verbindlich festgeschrieben — sie ist der Grund, warum ein Major-Wechsel überhaupt möglich
bleibt, ohne das Ökosystem zu spalten.

## 42.4 Forward- und Backward-Compatibility des Manifests

| Situation | Verhalten |
|---|---|
| Runtime 1.5 liest Manifest `schemaVersion: 1` | normal |
| Runtime 1.5 liest Manifest mit **unbekannten Feldern** | Felder werden ignoriert (Forward-Compat), einmalige `DEBUG`-Notiz |
| Runtime 1.2 liest Manifest mit `minRuntime: 1.4.0` | `OMNI-2002`: „FabricMultiLoader 1.4.0 oder neuer erforderlich; installiert ist 1.2.0. Aktualisiere die Mod, die die neueste Runtime mitbringt.“ |
| Runtime 1.x liest Manifest `schemaVersion: 2` | `OMNI-2002` mit derselben Systematik |
| Validator 1.5 prüft Manifest mit unbekannten Feldern | `OMNI-1002` Fehler — im eigenen Build darf nichts Unbekanntes entstehen |
| Container mit `omni/1` in einem Spiel mit Runtime 2.x | Runtime 2.x liest `omni/1` **nicht**; der Container bringt seine eigene 1.x-Runtime mit (andere Mod-ID) ⇒ beide laufen |

`minRuntime` ist das entscheidende Feld: Es erlaubt, neue Manifestfelder additiv einzuführen und trotzdem
deterministisch zu scheitern, wenn eine ältere Runtime sie *semantisch* bräuchte. Der Generator setzt
`minRuntime` automatisch auf die niedrigste Version, die alle verwendeten Features versteht — abgeleitet aus
einer Feature-Tabelle im Plugin, nicht geraten.

## 42.5 Deprecation-Politik

* Öffentliche API wird nie in einem Minor entfernt. `@Deprecated` + `@DeprecatedSince("1.5.0")` +
  Javadoc-Hinweis auf den Ersatz + Compile-Warnung.
* Mindestens **zwei Minor-Releases oder 6 Monate** (was länger ist) zwischen Deprecation und Entfernung im
  nächsten Major.
* Ein Gradle-Task, der wegfällt, bleibt als Alias mit Warnung bestehen.
* Ein Validator-Fehlercode wird nie wiederverwendet; entfernte Codes bleiben in `docs/errors.md` mit dem
  Hinweis „entfernt in 1.x“ dokumentiert, damit alte Logs auffindbar bleiben.

## 42.6 Unterstützungszeitraum

| Was | Zusage |
|---|---|
| Aktueller Major | Bugfixes und neue MC-Versionen |
| Vorheriger Major | Sicherheitsfixes für 12 Monate nach dem Major-Release |
| Fabric-Loader-Versionen | die letzten 3 Minor-Linien in der Conformance-Matrix; ältere „unterstützt, aber ungetestet“ |
| Minecraft-Versionen | keine Einschränkung durch das Framework — das entscheidet der Modautor über seine Matrix |

---

Weiter mit [Kapitel 43 — Architecture Decision Records](part-11-adrs.md).
