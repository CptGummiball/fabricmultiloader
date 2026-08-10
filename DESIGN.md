# FabricMultiLoader — Technisches Architektur- und Implementierungsdokument

**Status:** Final (implementierungsbereit)
**Dokumentversion:** 1.0.0
**Zielprodukt:** FabricMultiLoader 1.0.0 (Runtime-Library + Gradle-Toolchain + Containerformat *Omni*)
**Datum:** 2026-08-10

---

## Navigationsindex

| Kapitel | Datei |
|---|---|
| 1–4 Executive Summary, Ziele, Nicht-Ziele, Anforderungen | dieses Dokument |
| 5 Fabric/JVM-Machbarkeitsanalyse | [part-01-feasibility.md](docs/design/part-01-feasibility.md) |
| 6–9 Architekturvarianten, finale Entscheidung, Runtime-Architektur, Bootstrap-Sequenz | [part-02-architecture.md](docs/design/part-02-architecture.md) |
| 10–12 Omni-Containerformat, Metadata-Schema, Version Resolver | [part-03-container-format.md](docs/design/part-03-container-format.md) |
| 13–15 Classloading, Java-Kompatibilität, Mapping-Strategie | [part-04-classloading.md](docs/design/part-04-classloading.md) |
| 16–17 Mixin-Architektur, Access-Widener-Architektur | [part-05-mixins-aw.md](docs/design/part-05-mixins-aw.md) |
| 18–19, 26–28 Common API, Version-Adapter-API, Client/Server, Networking, Registries/Events | [part-06-api.md](docs/design/part-06-api.md) |
| 20–25 Gradle-Plugin, DSL, Repository-Struktur, Build-Pipeline, Dependencies, Ressourcen | [part-07-gradle.md](docs/design/part-07-gradle.md) |
| 29–33 Error Handling, Diagnostics, Validation, Testing, CI/CD | [part-08-quality.md](docs/design/part-08-quality.md) |
| 34–38 Distribution, Beispielmod, Migration, neue MC-Versionen, Dokumentationsarchitektur | [part-09-project.md](docs/design/part-09-project.md) |
| 39–42 Security, Performance, Kompatibilitätsgrenzen, Versionierung | [part-10-nfr.md](docs/design/part-10-nfr.md) |
| 43 Architecture Decision Records | [part-11-adrs.md](docs/design/part-11-adrs.md) |
| 44 Implementierungsplan | [part-12-implementation-plan.md](docs/design/part-12-implementation-plan.md) |
| Harte technische Fragen (25 Antworten) | [part-13-hard-questions.md](docs/design/part-13-hard-questions.md) |
| 45–46 Reality Check, finale Architekturzusammenfassung | [part-14-reality-check.md](docs/design/part-14-reality-check.md) |

---

## 1. Executive Summary

### 1.1 Das Ergebnis in einem Satz

FabricMultiLoader erzeugt aus einem Gradle-Multi-Project eine einzige, für Spieler klar erkennbare Mod-Datei
(`examplemod-2.0.0-universal.jar`), die auf mehreren Minecraft-Versionen läuft, weil sie pro unterstützter
Minecraft-Version genau ein **vollständig separat gebautes und remapptes Payload-Modul** enthält — und weil die
Auswahl des passenden Payloads **vom Fabric Loader selbst** getroffen wird, nicht von selbstgebautem Classloading.

### 1.2 Der zentrale technische Kunstgriff

Die Universal-JAR ist **kein exotisches Containerformat mit eigenem ClassLoader**, sondern eine ganz normale
Fabric-Mod, die per **Jar-in-Jar (JiJ)** mehrere vollständige Fabric-Mods enthält:

```
examplemod-2.0.0-universal.jar          ← Container-Mod, mod id "examplemod"
├─ fabric.mod.json                      ← depends.minecraft = Union aller Payload-Ranges
├─ META-INF/omni-container.json         ← Omni-Manifest (Wahrheitsquelle für Runtime + Tooling)
├─ com/example/common/**.class          ← plattformneutraler Common-Code (Java 17, keine MC-Referenzen)
└─ META-INF/jars/
   ├─ fabricmultiloader-runtime-1.0.0.jar   ← die Library selbst, eigene Fabric-Mod, Java 8
   ├─ examplemod-mc1201.jar             ← Payload: depends { minecraft "1.20.1", java ">=17" }
   ├─ examplemod-mc1211.jar             ← Payload: depends { minecraft ">=1.21 <1.21.2", java ">=21" }
   └─ examplemod-mc1214.jar             ← Payload: depends { minecraft ">=1.21.4 <1.21.5", java ">=21" }
```

Der Fabric Loader liest beim Start **nur JSON-Metadaten** aller genesteten Kandidaten, füttert sie in seinen
SAT-basierten `ModSolver` und lädt anschließend **ausschließlich** die Kandidaten, deren Constraints erfüllbar sind.
Payloads für andere Minecraft-Versionen werden dabei **nie entpackt, nie geöffnet, nie auf den Classpath gelegt und
nie von der JVM verifiziert**. Damit sind die vier klassischen Killerprobleme gelöst, *ohne* dass FabricMultiLoader
sie selbst lösen muss:

| Problem | Lösung durch diese Architektur |
|---|---|
| Mixins fremder Versionen dürfen nicht validiert werden | Die Mixin-Config steht in der `fabric.mod.json` des Payloads. Nicht geladenes Payload ⇒ Config wird nie registriert ⇒ Mixin-Klasse wird nie gelesen. |
| Access Widener sind versionsspezifisch, Loader kennt nur eine Datei pro Mod | Jedes Payload ist eine *eigene Mod* und hat *seine eigene* `accessWidener`-Deklaration. |
| Unterschiedliche Java-Classfile-Versionen in einer JAR | Payloads für Java 21 werden auf Java 17 vom Solver verworfen (`depends.java`), ihre Classfiles werden nie gelesen ⇒ kein `UnsupportedClassVersionError`. |
| Unterschiedliche Refmaps / Mappings | Jedes Payload wird von Loom separat gegen seine MC-Version kompiliert und remappt und bringt sein eigenes Refmap mit. |

Alles, was FabricMultiLoader zusätzlich beisteuert, ist damit **Komfort, Determinismus, Diagnose und Toolchain** —
nicht der riskante Kern. Das ist die Eigenschaft, die das Projekt langfristig wartbar macht.

### 1.3 Was FabricMultiLoader selbst beiträgt

1. **`fabricmultiloader-runtime`** — eine eigenständige, minecraftfreie Fabric-Mod (Java 8, ~60 KB), die
   * das Omni-Manifest liest und validiert,
   * die Umgebung erkennt (MC-, Loader-, Fabric-API-, Java-Version, `EnvType`),
   * verifiziert, dass **genau ein** Payload aktiv ist,
   * bei Fehlschlag einen vollständigen, menschenlesbaren Diagnosebericht erzeugt statt eines `NoClassDefFoundError`,
   * die Lifecycle-Kette Container → Payload → Common-Code deterministisch ausführt,
   * die Common-API (`ModContext`, `Platform`, `Registries`, `Networking`, `Commands`, `Events`, `Services`,
     `Capabilities`) bereitstellt.
2. **`fabricmultiloader-gradle`** — vier Gradle-Plugins, die Version-Module gegen Loom bauen, `fabric.mod.json`
   und Omni-Manifest **generieren**, Ressourcen deterministisch mergen, disjunkte Versionsbereiche **beweisen**,
   die Universal-JAR reproduzierbar assemblieren und vor dem Release validieren.
3. **Omni Container Format v1** — ein vollständig spezifiziertes, versioniertes Dateiformat inkl. Manifest-Schema,
   Payload-Deskriptoren, Checksummen und Kompatibilitätsregeln.
4. **Test- und CI-Infrastruktur**, die dieselbe Universal-JAR real gegen jede unterstützte
   Minecraft-Version bootet, bevor sie veröffentlicht wird.

### 1.4 Die eine tragende Annahme

Die Architektur steht und fällt mit einer einzigen Eigenschaft des Fabric Loaders:

> **Ein genestetes Mod-Candidate, dessen `depends` nicht erfüllbar sind und auf das kein geladener Mod angewiesen
> ist, wird vom `ModSolver` stillschweigend nicht ausgewählt — statt einen harten Resolutionsfehler auszulösen.**

Das ist das Verhalten der Loader-Reihen 0.14.x bis 0.17.x und der Grund, warum JiJ-Bibliotheken mit engen
Versionsbereichen im Ökosystem funktionieren. Weil das Fundament auf dieser Eigenschaft ruht, wird sie in
[Kapitel 5](docs/design/part-01-feasibility.md) technisch hergeleitet, in
[Kapitel 32](docs/design/part-08-quality.md) durch einen **Loader-Conformance-Test über die gesamte
Loader-Matrix in CI** abgesichert, und in
[Kapitel 41](docs/design/part-10-nfr.md) mit einem konkreten Rückfallpfad (`buildSlimJars`) versehen.
Es gibt in diesem Dokument keine zweite unbewiesene Annahme dieser Tragweite.

### 1.5 Was der Modentwickler davon sieht

```bash
git clone https://github.com/fabricmultiloader/fabricmultiloader-template my-mod
cd my-mod
./gradlew runClient1214            # normaler Loom-Dev-Loop, eine MC-Version
./gradlew test                     # Unit-Tests (Common-Code, JVM, ohne Minecraft)
./gradlew buildUniversalJar        # -> build/libs/my-mod-1.0.0-universal.jar
./gradlew validateUniversalJar     # 34 Prüfungen inkl. Classfile-Scan und Disjunktheitsbeweis
./gradlew integrationTest          # bootet dieselbe JAR auf 1.20.1 / 1.21.1 / 1.21.4
./gradlew addMinecraftVersion --mc=1.22 --yarn=1.22+build.3 --java=21   # neue Version scaffolden
```

Eine neue Minecraft-Version kostet: **ein TOML-Block, ein 4-zeiliges `build.gradle.kts`, ein Verzeichnis** —
und danach nur noch die tatsächlichen API-Anpassungen im Version-Modul.

---

## 2. Ziele

Priorisiert; bei Zielkonflikten gewinnt die niedrigere Nummer.

| # | Ziel | Messbares Kriterium |
|---|---|---|
| G1 | **Eine Datei für Spieler** | Genau ein Download-Artefakt pro Release, das auf allen deklarierten MC-Versionen im `mods`-Ordner funktioniert. Kein Installer, kein Extra-Schritt, keine Begleit-JAR. |
| G2 | **Entwicklerfreundlichkeit** | Migration einer bestehenden Single-Version-Fabric-Mod in ≤ 8 mechanischen Schritten; Hinzufügen einer MC-Version ohne Änderung an bestehenden Modulen; vollständige IntelliJ-Unterstützung inkl. Run Configs pro Version. |
| G3 | **Technische Stabilität** | Keine selbstgebauten ClassLoader, keine Bytecode-Transformationen zur Laufzeit, keine Reflection auf Loader-Interna im kritischen Pfad. Alle Mechanismen sind dokumentierte Fabric-Features. |
| G4 | **Isolation versionsspezifischen Codes** | Klassen, Mixins, Refmaps, Access Widener und Ressourcen einer nicht aktiven MC-Version sind zur Laufzeit **nicht auf dem Classpath** und werden von der JVM nicht gelesen. Verifiziert durch Test `PayloadIsolationTest`. |
| G5 | **Fabric-Kompatibilität** | Läuft mit Fabric Loader ≥ 0.14.0 ohne Loader-Patches. Fabric API bleibt eine normale `depends`-Beziehung pro Payload. Andere Mods sehen eine gewöhnliche Mod. |
| G6 | **Langfristige Wartbarkeit** | Semantische Versionierung mit Binärkompatibilitätsgarantie innerhalb Major 1; Format-Schemaversion getrennt von Library-Version; Forward-Compat-Regeln für unbekannte Manifest-Felder. |
| G7 | **Reproduzierbare Builds** | Zwei Builds desselben Commits erzeugen bytegleiche Universal-JARs (SHA-256-identisch). Erzwungen durch `preserveFileTimestamps=false`, `reproducibleFileOrder=true`, feste Entry-Reihenfolge, feste Zeitstempel, sortierte JSON-Ausgabe. |
| G8 | **Gute Fehlermeldungen** | Jeder Fehlerpfad hat einen stabilen Fehlercode (`OMNI-xxxx`), eine Ursachenbeschreibung, den erkannten Ist-Zustand, den erwarteten Soll-Zustand und eine Handlungsanweisung. Nie ein nackter `NoClassDefFoundError`. |
| G9 | **Vollständige Dokumentation** | 24 definierte Doc-Seiten (Kapitel 38), API-Referenz aus Javadoc, DSL-Referenz aus dem Gradle-Modell generiert. |

### 2.1 Explizite Nutzungsszenarien

* **S1** — Mod unterstützt 1.20.1 (Java 17) und 1.21.4 (Java 21) mit teilweise unterschiedlichen Fabric-API-Ständen.
* **S2** — Mod hat einen versionsspezifischen Mixin, dessen Ziel-Methodensignatur sich zwischen 1.20.1 und 1.21.4
  geändert hat.
* **S3** — Mod braucht auf 1.20.1 einen Access Widener für ein Feld, das auf 1.21.4 bereits public ist.
* **S4** — Mod hat eine optionale Cloth-Config-Integration, die je MC-Version eine andere Cloth-Version braucht.
* **S5** — Mod bietet eine eigene API für andere Mods; diese API muss über alle MC-Versionen **binärstabil** sein.
* **S6** — Mod erscheint auf Modrinth als **ein** File mit den Game-Version-Tags 1.20.1, 1.21, 1.21.1, 1.21.4.
* **S7** — Nutzer startet die JAR auf 1.19.2 → kontrollierte, verständliche Fehlermeldung.
* **S8** — Neue MC-Version 1.22 erscheint → Entwickler fügt Version hinzu, alte Payloads bleiben unangetastet.

---

## 3. Nicht-Ziele

Bewusst ausgeschlossen, mit Begründung. Diese Punkte werden im Reality Check (Kapitel 45) erneut aufgegriffen.

| # | Nicht-Ziel | Begründung |
|---|---|---|
| N1 | **Eine vollständige, versionsunabhängige Minecraft-API** | Das wäre ein zweites Architectury *plus* ein Kompatibilitätslayer über 5 Jahre MC-Umbau. FabricMultiLoader abstrahiert genau die Bereiche, die stabil abstrahierbar sind (Lifecycle, Registrierung einfacher Inhalte, Commands, Networking, Events, Config, Ressourcen) und stellt für alles andere einen sauberen, typsicheren **Escape Hatch** (`Services`, `Capabilities`) bereit. |
| N2 | **Ein einziger kompilierter Bytecode-Stand für alle MC-Versionen** | Technisch unmöglich, sobald sich Deskriptoren ändern (`new Identifier(a,b)` → `Identifier.of(a,b)`; `PacketByteBuf` → `RegistryByteBuf`). Bytecode referenziert Methoden über exakte Deskriptoren; ein einzelner Stand kann nicht beide auflösen. Deshalb: N Kompilate, eine Datei. |
| N3 | **Cross-Loader-Unterstützung (Forge/NeoForge/Quilt)** | Der Name ist Programm: Fabric. Quilt lädt Fabric-Mods, wird aber nicht getestet oder garantiert. Die Common-API ist bewusst so geschnitten, dass ein späteres `neoforge`-Payload-Backend möglich bliebe; das ist aber kein Ziel von 1.x. |
| N4 | **Runtime-Bytecode-Patching zur API-Angleichung** | Ein eigener Transformer, der z. B. `Identifier.<init>` auf `Identifier.of` umschreibt, wäre ein Kern-Transformer mit Konfliktpotenzial zu jeder anderen Mod und zu Mixin. Verstößt gegen G3. |
| N5 | **Eigener ClassLoader für Payloads** | Detailliert widerlegt in Kapitel 6 (Ansatz D) und Kapitel 13: bricht Mixin, Access Widener, `FabricLoader`-Entrypoints und erzeugt `ClassCastException` an jeder Grenze zu Minecraft-Typen. |
| N6 | **Unterstützung für Minecraft < 1.16.5** | Vor 1.16 fehlen Intermediary-Stabilität, `getObjectShare`, moderne Loader-Features. Untergrenze des offiziellen Supports: **1.16.5**; getestete Referenzmatrix: 1.20.1 / 1.21.1 / 1.21.4. |
| N7 | **Automatische Portierung von Modcode zwischen MC-Versionen** | Kein Codemod-Werkzeug, kein Source-Preprocessor als Pflichtbestandteil. Der Entwickler schreibt versionsspezifischen Code selbst; das Framework organisiert, isoliert und paketiert ihn. |
| N8 | **Verkleinerung der Universal-JAR unter die Summe der Payloads** | Explizit vom Auftraggeber akzeptiert. Deduplizierung von Klassen zwischen Payloads ist unmöglich (unterschiedliche Deskriptoren), Deduplizierung von Ressourcen wird bewusst *nicht* gemacht (Kapitel 25, Determinismus schlägt Größe). Für größenkritische Fälle existiert `buildSlimJars`. |

---

## 4. Anforderungen

### 4.1 Funktionale Anforderungen

| ID | Anforderung |
|---|---|
| F-01 | Eine Universal-JAR enthält 1..n Payloads für unterschiedliche Minecraft-Versionsbereiche. |
| F-02 | Zur Laufzeit ist **genau ein** Payload aktiv; die Auswahl ist deterministisch und reproduzierbar. |
| F-03 | Die Auswahl berücksichtigt: Minecraft-Version, Fabric-Loader-Version, Fabric-API-Version, Java-Major-Version, physische Umgebung (Client/Server), Anwesenheit und Version beliebiger anderer Mods. |
| F-04 | Payload-spezifische Mixin-Configs, Refmaps und Access Widener werden ausschließlich für das aktive Payload verarbeitet. |
| F-05 | Common-Code existiert genau einmal in der JAR und ist von allen Payloads referenzierbar. |
| F-06 | Ressourcen (assets/data) sind pro Payload eindeutig; es gibt keine zwei gleichzeitig aktiven Resource Packs derselben Mod. |
| F-07 | Für Fabric und andere Mods erscheint die Mod unter **einer** primären Mod-ID mit **einer** Version. |
| F-08 | Bei nicht unterstützter Umgebung erscheint eine strukturierte Fehlermeldung mit Ist-/Soll-Zustand; wahlweise harter Abbruch (Default) oder Warnung (`-Dfabricmultiloader.strict=false`). |
| F-09 | Payloads können versionsspezifische Bibliotheken als eigene genestete JARs mitbringen. |
| F-10 | Das Gradle-Plugin generiert alle Metadaten (`fabric.mod.json`, Omni-Manifest, Payload-Deskriptoren) — sie sind niemals handgepflegt. |
| F-11 | Ein Validator prüft die fertige JAR vor Veröffentlichung gegen 34 definierte Regeln. |
| F-12 | Der Entwickler kann pro MC-Version `runClient`, `runServer` und `runDatagen` starten. |
| F-13 | Die Universal-JAR kann in einer echten Serverinstanz jeder unterstützten MC-Version automatisiert gebootet werden. |
| F-14 | Die Mod-eigene öffentliche API (für Drittmods) ist über alle unterstützten MC-Versionen binärkompatibel. |

### 4.2 Nichtfunktionale Anforderungen

| ID | Anforderung | Zielwert |
|---|---|---|
| NF-01 | Startzeit-Overhead des Frameworks | < 15 ms (Manifest-Parse + Resolve + Verifikation), gemessen in `BootstrapBenchmark` |
| NF-02 | Zusätzlicher Heap-Verbrauch der Runtime | < 512 KB nach Initialisierung |
| NF-03 | Größenoverhead des Containers ohne Payloads | < 80 KB |
| NF-04 | Keine zusätzliche Entpackung über die Loader-eigene JiJ-Extraktion hinaus | 0 eigene Extraktionsschritte |
| NF-05 | Reproduzierbarkeit | SHA-256 zweier Builds desselben Commits identisch |
| NF-06 | Minimale Java-Version der Framework-Artefakte | Class-File-Major 52 (Java 8) für `format`, `api`, `runtime`, `processor` |
| NF-07 | Minimale Fabric-Loader-Version | 0.14.0 |
| NF-08 | Test-Coverage `format` + `runtime` (Zeilen) | ≥ 90 % |
| NF-09 | Alle Fehlerpfade mit stabilem Code und Doku-Anker | 100 % |
| NF-10 | Gradle Configuration Cache | vollständig kompatibel; alle Tasks mit deklarierten Inputs/Outputs |

### 4.3 Referenz-Kompatibilitätsmatrix (Version 1.0.0)

| Minecraft | Java (min) | Fabric Loader (min) | Fabric API (min) | Mappings | Status |
|---|---|---|---|---|---|
| 1.16.5 | 8 | 0.14.0 | 0.42.0 | Yarn `1.16.5+build.10` | unterstützt, nicht in CI |
| 1.18.2 | 17 | 0.14.0 | 0.76.0 | Yarn `1.18.2+build.4` | unterstützt, nicht in CI |
| 1.20.1 | 17 | 0.14.21 | 0.92.2 | Yarn `1.20.1+build.10` | **CI-Referenz** |
| 1.20.4 | 17 | 0.15.0 | 0.97.2 | Yarn `1.20.4+build.3` | unterstützt, nicht in CI |
| 1.21 / 1.21.1 | 21 | 0.15.11 | 0.102.0 | Yarn `1.21.1+build.3` | **CI-Referenz** |
| 1.21.4 | 21 | 0.16.9 | 0.114.0 | Yarn `1.21.4+build.8` | **CI-Referenz** |
| 1.21.5 – 1.21.x | 21 | 0.16.10 | 0.119.2 | Yarn `1.21.5+build.1` | unterstützt, nicht in CI |
| **26.1 und neuer** (neues Mojang-Versionsschema) | **25** | ≥ 0.17.0 | ≥ 0.130.0 | Yarn `26.1+build.1` | **CI-Referenz ab Release** |
| zukünftig (z. B. `26.2`, `27.x`) | ≥ 25 | ≥ 0.17 | — | beliebig | durch Versionsmodell abgedeckt, siehe Kapitel 12 |

**Java-Sprünge in der Matrix.** Die Referenzmatrix enthält damit bewusst **drei** Java-Hauptversionen:
Java 17 (1.18–1.20.4), Java 21 (1.20.5–1.21.x) und Java 25 (26.1+, Classfile-Major 69). Der Fall
„eine Universal-JAR enthält Payloads mit drei verschiedenen Classfile-Versionen“ ist damit kein
theoretischer Sonderfall, sondern der Normalfall jeder Mod, die den Sprung von 1.21.x auf 26.1 mitgeht.
Die Architektur behandelt ihn über `depends.java` im Payload (Kapitel 12.4) und den Classfile-Scan des
Validators (Kapitel 14.4); der Container selbst bleibt zwingend auf dem **kleinsten** Java-Level der Matrix
(im Beispiel 17, Classfile-Major 61).

„Unterstützt, nicht in CI“ bedeutet: Das Framework hat keine bekannten Blocker, die Matrix ist im Template
vorkonfiguriert, aber die Referenz-CI bootet nur drei Versionen, um die Pipeline-Laufzeit unter 25 Minuten zu halten.
Modentwickler erweitern die Matrix in ihrem eigenen Projekt.

### 4.4 Begriffslexikon

| Begriff | Bedeutung in diesem Dokument |
|---|---|
| **Container** | Die Universal-JAR selbst; eine Fabric-Mod mit der primären Mod-ID des Entwicklers. |
| **Payload** | Eine vollständige, für genau eine MC-Versionsspanne gebaute und remappte Fabric-Mod innerhalb des Containers. |
| **Runtime** | Die Mod `fabricmultiloader` (Library), genestet in jedem Container. |
| **Common** | Plattformneutraler Modcode ohne Minecraft-Referenzen, im Container. |
| **Shared** | Optionaler Quellcode-Layer, der *in jedes Version-Modul hineinkompiliert* wird und Minecraft berühren darf. |
| **Omni** | Name des Containerformats (`omni/1`). Bewusst nicht „FMLU“, um jede Verwechslung mit Forge Mod Loader auszuschließen. |
| **Adapter / Platform** | Die versionsspezifische Implementierung der Common-SPI innerhalb eines Payloads. |
| **Matrix** | Die Datei `gradle/fabricmultiloader.toml`, einzige Wahrheitsquelle über unterstützte Versionen. |

---

Weiter mit [Kapitel 5 — Fabric/JVM-Machbarkeitsanalyse](docs/design/part-01-feasibility.md).
