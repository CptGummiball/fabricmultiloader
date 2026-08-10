# 43. Architecture Decision Records

Format: Context / Decision / Alternatives / Consequences. Status aller ADRs: **accepted** (Stand 1.0.0).
Ort im Repo: `docs/internals/adr/ADR-0xx-*.md`.

---

## ADR-001 — Universal Container Architecture

**Status:** accepted · **Datum:** 2026-08-10 · **Betrifft:** Kapitel 7, 10

### Context

Eine Mod soll als eine Datei mehrere Minecraft-Versionen bedienen. Fabric verarbeitet Mixin-Configs und Access
Widener deklarativ und eagerly, bevor Modcode läuft (Kapitel 5.1). Ein Laufzeit-Dispatcher, der Klassen auswählt,
kann diese Verarbeitung nicht beeinflussen. Gleichzeitig sind Deskriptoränderungen der Minecraft-API zwischen
Versionen unvermeidbar, sodass ein einzelnes Kompilat prinzipiell nicht ausreichen kann.

### Decision

Die Universal-JAR ist eine gewöhnliche Fabric-Mod („Container“), die pro unterstützter Minecraft-Versionsspanne
eine vollständige, separat gebaute und remappte Fabric-Mod („Payload“) per Jar-in-Jar enthält. Die Auswahl trifft
der Fabric-Loader-eigene Dependency-Solver anhand generierter `depends`-Constraints (`minecraft`, `java`,
`fabricloader`, Fremdmods) und `provides`/`breaks`-Exklusivität.

### Alternatives

| Alternative | Ablehnungsgrund |
|---|---|
| Bootstrap wählt Klassen im selben Classpath (Ansatz A/C) | Mixin-Configs und AW des einzigen `fabric.mod.json` werden für **alle** Versionen registriert ⇒ garantierter Startcrash. |
| Payloads als Ressourcen + Reflection auf `KnotClassLoader` (Ansatz B) | Loader-Interna, Mixin-Bootstrap bereits abgeschlossen, AW-Transformer bereits gebaut. |
| Eigener ClassLoader (Ansatz D) | Umgeht die Transformerkette; Mixins und AW wirkungslos; Class-Identity-Brüche. |
| Multi-Release-JAR | Selektiert nach Java-, nicht nach Minecraft-Version; Metadaten nicht MR-fähig. |
| Zwei Dateien (Bootstrap + Nachladen) | Verstößt gegen das Kernziel und gegen Plattformregeln. |

### Consequences

* **Positiv:** Mixin-, AW-, Refmap- und Java-Isolation ergeben sich ohne eigenen Mechanismus. Kein
  ClassLoader, keine Laufzeit-Transformation, keine Loader-Interna. Debugging und Stacktraces bleiben normal.
* **Positiv:** Neue Minecraft-Versionen sind additiv; bestehende Payloads werden nicht angefasst.
* **Negativ:** Die JAR ist so groß wie die Summe der Payloads (bewusst akzeptiert).
* **Negativ/Risiko:** Abhängigkeit von einer Loader-Eigenschaft (optionale genestete Kandidaten werden bei
  unerfüllbaren `depends` verworfen). Abgesichert durch nächtliche Conformance-Tests über fünf Loader-Versionen
  mit automatischer Issue-Erstellung sowie zwei dokumentierte Rückfallpfade.
* **Folge:** Der gesamte Aufwand verschiebt sich in die Build-Toolchain — dort, wo Fehler zur Build-Zeit
  auffallen statt beim Spieler.

---

## ADR-002 — Version Payload Isolation ohne eigenen ClassLoader

**Status:** accepted · **Betrifft:** Kapitel 13, Invariante I1

### Context

Isolation versionsspezifischen Codes ist Kernanforderung. Der naheliegende Java-Weg ist ein eigener
ClassLoader pro Payload.

### Decision

FabricMultiLoader erzeugt **niemals** einen ClassLoader. Isolation entsteht ausschließlich dadurch, dass inaktive
Payloads nicht Teil des Classpath werden. Alle aktiven Klassen (Minecraft, Fabric API, Runtime, Container-Common,
aktives Payload) werden vom `KnotClassLoader` definiert. Der Validator verbietet Referenzen auf
ClassLoader-Konstruktoren, `URLClassLoader` und `net.fabricmc.loader.impl.**` in ausgelieferten Artefakten
(`OMNI-1036`).

### Alternatives

* Child-ClassLoader mit Parent-First für `net.minecraft.**`: Mixin und AW greifen nicht; Registry-/Codec-
  Reflection von Minecraft findet Payload-Klassen nicht; `ClassCastException` an jeder Grenze.
* Isolation über Java-Module (JPMS): Minecraft und Fabric laufen im unnamed module; Knot ist kein Modul-Layer;
  Mixin ist nicht modulkompatibel.
* Isolation über Package-Konventionen allein: verhindert kein eagerly-Laden von Mixin-Configs.

### Consequences

* Keine Class-Identity-Probleme, keine Delegationsregeln, keine Speicherlecks durch nicht freigegebene Loader.
* Payload und Common sehen sich gegenseitig ohne Vermittlung — die Common-API kann direkt Objekte austauschen.
* Einzige Ausnahme im gesamten Projekt: der Conformance-Test-Harness lädt Fabric-Loader-Versionen in eigene
  `URLClassLoader` — Testcode, nicht ausgeliefert, explizit dokumentiert.
* Falls ein künftiger Loader Mods klassenmäßig isoliert, greift `commonPackaging = EMBEDDED` (Kapitel 41.3), das
  bereits implementiert und getestet ist.

---

## ADR-003 — Mixin Strategy

**Status:** accepted · **Betrifft:** Kapitel 16

### Context

Sponge Mixin löst für jede Mixin-Klasse einer registrierten Config eagerly `ClassInfo` und deren Targets auf.
Ein 1.20.1-Mixin auf eine in 1.21.4 geänderte Signatur ist damit ein harter Startfehler, sobald seine Config
registriert wird. Fabric registriert die Configs aller **ausgewählten** Mods vor der `preLaunch`-Phase.

### Decision

Mixin-Sets sind ausschließlich payloadgebunden: Jede Mixin-Config steht in der `fabric.mod.json` **ihres
Payloads**. Es gibt keinen Laufzeit-Mixin-Dispatcher. Innerhalb eines Payloads steht für Feinsteuerung
(optionale Fremdmods, Config-Schalter) das deklarative `ConditionalMixinPlugin` bereit. Der Container deklariert
niemals Mixins. Namensschema `<modId>-<payloadId>[.client|.server].mixins.json` mit
`<modId>-<payloadId>-refmap.json`.

### Alternatives

| Alternative | Ablehnungsgrund |
|---|---|
| `IMixinConfigPlugin#shouldApplyMixin` als Hauptmechanismus | Verhindert Anwendung, nicht Laden/Validieren; Target-Auflösung passiert vorher. |
| Ein Config-Plugin, das `mixins` dynamisch reduziert | Fabric/Mixin parsen die `mixins`-Liste der JSON; ein Plugin kann Einträge nicht zurückziehen. |
| Nachträgliches `Mixins.addConfiguration` aus `preLaunch` | Nicht spezifiziert, Environment bereits in PREINIT, Refmap-/Mod-Zuordnung fehlt. |
| Ein gemeinsames Mixin-Set mit `targets`-Strings statt Klassenliteralen | Umgeht die Refmap-Prüfung, verschiebt Fehler in die Laufzeit, versionsfragil. |

### Consequences

* Ein 1.20.1-Mixin kann unter 1.21.4 nicht geladen werden, weil seine Klasse nicht auf dem Classpath liegt —
  die stärkste erreichbare Garantie.
* Mixin-Configs bleiben handgeschrieben (fachliche Entscheidungen), werden aber streng validiert (11 Regeln)
  und automatisch in die generierten Metadaten übernommen.
* `compatibilityLevel` kann pro Payload korrekt gesetzt werden (`JAVA_17`/`JAVA_21`/`JAVA_25`) — bei einer
  gemeinsamen Config unmöglich.
* Duplikation: Ein inhaltlich identischer Mixin muss pro Payload existieren. Gegenmaßnahme: Der Mixin bleibt
  ein Dreizeiler, der einen Common-Hook aufruft.

---

## ADR-004 — Access Widener Strategy

**Status:** accepted · **Betrifft:** Kapitel 17

### Context

Fabric erlaubt genau **eine** `accessWidener`-Datei pro Mod. AW-Dateien sind mappinggebunden (Namespace-Header
wird geprüft) und Member-Namen können zwischen MC-Versionen differieren. Loom kann eine AW-Datei nur gegen
**eine** Mappings-Version remappen.

### Decision

Ein Access Widener pro Payload, von Loom gegen die Mappings dieses Payloads remappt. Eine optionale gemeinsame
Quelle `common/src/main/accesswidener/shared.accesswidener` wird zur Build-Zeit **vor** dem Remap im
`named`-Namespace mit der payloadspezifischen Datei gemergt (dedupliziert, sortiert, mit Quellenkommentar). Der
Container deklariert keinen AW.

### Alternatives

* Eine gemeinsame, versionsübergreifende AW-Datei: nicht mappingkorrekt herstellbar; müsste handgeschrieben in
  Intermediary vorliegen.
* Runtime-Nachladen von AW-Einträgen: keine öffentliche API; Transformer ist zum Zeitpunkt von `preLaunch`
  bereits gebaut.
* Ausschließlich Reflection statt AW: löst `extendable` nicht und ist in Hot Paths teuer.
* Ausschließlich `@Accessor`/`@Invoker`: **gute** Alternative und in der Doku als Standardempfehlung geführt,
  aber kein Ersatz für `extendable`/`mutable` und für breiten Zugriff aus vielen Klassen.

### Consequences

* Das AW-Problem verschwindet vollständig, ohne eigene Transformer oder Reflection.
* Gemeinsame AW-Bedürfnisse bleiben an einer Stelle pflegbar; abweichende bleiben lokal.
* Ein Eintrag, dessen Ziel in einer Version fehlt, ist wirkungslos; der Validator warnt (`OMNI-1121`) statt zu
  scheitern, weil optionale Ziele legitim sind.
* Namespace-Fehler (der einzige harte Laufzeitfehler des AW-Systems) werden zur Build-Zeit gefangen
  (`OMNI-1082`).

---

## ADR-005 — Gradle/Loom Integration

**Status:** accepted · **Betrifft:** Kapitel 20–23

### Context

Loom muss pro Minecraft-Version einmal konfiguriert werden (eigene MC-, Mappings-, Fabric-API-Version, eigene
Toolchain). Gradle-Projekte müssen in `settings.gradle.kts` deklariert sein und können nicht dynamisch entstehen.
Gradle bewegt sich Richtung Project Isolation, wodurch Cross-Project-Konfiguration langfristig unzulässig wird.

### Decision

Vier separate Plugins (`settings`, `common`, `version`, `universal`), von denen jedes Modul das für sich
zuständige selbst anwendet. Geteilte Konfiguration liegt in der Datei `gradle/fabricmultiloader.toml`
(„Matrix“), die jedes Plugin unabhängig über eine `ValueSource` liest. Kein Root-Plugin konfiguriert
Unterprojekte. Payload-Artefakte entstehen aus `remapJar` + einem eigenen `omniPayload`-Zip-Task; der Container
wird im Root-Projekt ohne eigenes Modul assembliert.

### Alternatives

* Ein Root-Plugin mit `subprojects { … }`: bequem, aber isolations- und cache-feindlich; unvollständige
  IDE-Modelle beim ersten Sync.
* Alles in einem Projekt mit mehreren Source Sets: Ein Projekt kann nicht gegen mehrere MC-Versionen kompilieren
  (Loom ist projektgebunden).
* Konfiguration ausschließlich in der Kotlin-DSL: Version-Module müssten Root-Werte lesen ⇒
  Cross-Project-Zugriff.
* Loom-`include` für Payloads: erzeugt Nested-Jars, aber ohne Kontrolle über generierte Metadaten, Reihenfolge,
  Kompression und Reproduzierbarkeit.

### Consequences

* Der Build ist Configuration-Cache-kompatibel und Project-Isolation-tauglich.
* Eine neue MC-Version = TOML-Block + Verzeichnis + 4-Zeilen-Buildfile; `addMinecraftVersion` erzeugt alles.
* Die Matrix ist maschinenlesbar und wird von CI, Validator, Publishing und Scaffolding gleichermaßen genutzt —
  eine Wahrheitsquelle statt vier.
* Nachteil: Zwei Konfigurationsorte (TOML für versionsabhängige Werte, Kotlin-DSL für Modidentität und
  Assemblierung). Bewusst in Kauf genommen; die Trennlinie ist scharf und dokumentiert.

---

## ADR-006 — Java Version Compatibility

**Status:** accepted · **Betrifft:** Kapitel 14

### Context

Minecraft 1.16.5 verlangt Java 8, 1.18–1.20.4 Java 17, 1.20.5–1.21.x Java 21, ab 26.1 Java 25 (Classfile-Major
69). Eine Universal-JAR muss auf der **ältesten** unterstützten JVM geöffnet und teilweise ausgeführt werden,
während sie Bytecode für neuere JVMs enthält.

### Decision

* `format`, `api`, `runtime`, `processor` werden auf **Classfile 52 (Java 8)** kompiliert (`--release 8`), damit
  sie in jeder unterstützten Umgebung laden.
* Der Container-Common des Mods wird auf `baselineJava` = Minimum der Matrix kompiliert.
* Jedes Payload wird auf das Java-Level seiner MC-Version kompiliert und deklariert `depends.java`.
* Der Validator scannt jeden Classfile-Header und prüft Container ≤ Baseline, Payload == deklariertem Major und
  Major ↔ `requires.java`-Konsistenz.
* Multi-Release-JARs werden verboten.

### Alternatives

* Alles auf das höchste Java-Level: bricht auf älteren MC-Versionen.
* Alles auf Java 8: verbietet dem Modautor moderne Sprachfeatures ohne Not.
* MR-JARs: falsche Selektionsachse (Java statt Minecraft).
* Verlassen auf Lazy Classloading ohne Validierung: ein einziger versehentlicher Import in Common führt zum
  `UnsupportedClassVersionError` beim Spieler.

### Consequences

* Drei Classfile-Majors (61/65/69) in einer Datei sind unproblematisch, weil inaktive Payloads nie definiert
  werden.
* Der Preis ist die Java-8-Beschränkung der Framework-Module: keine Records, kein `var`, kein `sealed`, keine
  `List.of`. Durchgesetzt über `--release 8` statt Disziplin; kompensiert durch Builder-Pattern.
* `--release` (nicht `targetCompatibility`) fängt versehentliche Nutzung neuerer JDK-API zur Compile-Zeit.
* Der Java-Sprung auf 25 bei 26.1 kostet den Modautor genau einen Matrix-Eintrag.

---

## ADR-007 — Universal Metadata Format

**Status:** accepted · **Betrifft:** Kapitel 10, 11, 42

### Context

Runtime, Validator, Slim-Jar-Generator und Distributions-Publisher brauchen dieselbe Information über Payloads
und ihre Constraints. Der Loader trifft die Auswahl aber anhand der Payload-`fabric.mod.json`. Zwei
Informationsquellen können divergieren.

### Decision

* Ein eigenes Manifest `META-INF/omni-container.json` (`formatId: "omni/1"`, `schemaVersion: 1`) beschreibt
  Container, Payloads, Constraints, Hashes, Classfile-Majors, Entrypoints, Capabilities und Diagnose-URLs.
* Jedes Payload trägt zusätzlich `omni/payload.json` mit Selbstbeschreibung **und** einer Kopie der
  Container-Identität und -Entrypoints (ermöglicht Dev-Fallback und Slim-Jars).
* **Alle** Metadaten inklusive beider `fabric.mod.json` werden generiert; handgeschriebene sind ein Build-Fehler.
* Der Validator prüft die Äquivalenz von Manifest und `fabric.mod.json` (`OMNI-1011`) — Divergenz ist ein
  Build-Fehler, kein Laufzeitproblem.
* Der Container deklariert **keine** harte `depends` auf den Payload-Alias, sondern die Union der MC-Ranges;
  „kein Payload wählbar“ wird von unserer `preLaunch`-Diagnose gemeldet.
* Eigener JSON-Parser in `format` (kein Gson), mit Positionsangaben und Eingabelimits.
* Format-, Schema- und Library-Version sind unabhängige Achsen; `minRuntime` erlaubt additive Erweiterung mit
  deterministischem Fehlschlag auf zu alten Runtimes.

### Alternatives

| Alternative | Ablehnungsgrund |
|---|---|
| Nur `fabric.mod.json`-Dateien, kein eigenes Manifest | Kein Ort für Hashes, Capabilities, Classfile-Majors, Diagnose-URLs; Runtime müsste alle Nested-Jars öffnen. |
| Manifest als `.properties` oder eigenes Binärformat | Schlechter lesbar, schlechter diffbar, kein Nutzen. |
| Gson verwenden | FQCN-Kollision mit Minecrafts Gson (Classpath-First-Wins) oder Shading; in `preLaunch` auf alten Versionen nicht garantiert verfügbar. |
| Harte `depends` auf den Payload-Alias | Ergibt die unlesbare Loader-Meldung „requires examplemod-impl which is missing“ statt einer Erklärung. |
| Manifest handpflegen | Fehleranfällig, divergiert von der Realität, verhindert Hash-/Classfile-Angaben. |

### Consequences

* Eine Änderung wirkt an einer Stelle (Matrix/DSL) und propagiert konsistent in beide Metadatenwelten.
* Die Fehlermeldung bei nicht unterstützter Umgebung ist die bestmögliche, weil sie die Constraints selbst
  auswerten kann.
* 9 KiB eigener JSON-Parser als Preis für Unabhängigkeit — vertretbar und vollständig testbar.
* Forward-Compat-Regel (Reader ignorieren Unbekanntes, Validator lehnt es ab) erlaubt Formatentwicklung ohne
  Schemaversionssprünge.

---

## ADR-008 — Runtime als eigene genestete Mod statt Shading

**Status:** accepted · **Betrifft:** Kapitel 13.4, 42.3

### Context

Mehrere Universal-Mods im selben Spiel bringen jeweils FabricMultiLoader-Klassen mit. Bei eingebetteten
(geshadeten oder entpackten) Klassen entscheidet die Classpath-Reihenfolge, welche Version gewinnt — nicht
deterministisch, und eine ältere Runtime könnte ein neueres Manifest interpretieren.

### Decision

`fabricmultiloader-runtime` (inklusive `format` und `api`) ist eine eigenständige Fabric-Mod mit der ID
`fabricmultiloader`, die jeder Container per Jar-in-Jar mitbringt. Der Loader dedupliziert nach Mod-ID und wählt
die höchste kompatible Version. Jeder Container deklariert `depends: {"fabricmultiloader": ">=X <nextMajor>"}`.
Ein Major-Wechsel erhält eine neue Mod-ID (`fabricmultiloader2`) und ein neues Root-Package
(`dev.fabricmultiloader.v2`).

### Alternatives

* Relocation pro Mod (Shadow/jarjar): Beseitigt die Kollision, macht aber die öffentliche Mod-API unbrauchbar
  (Signaturen würden auf relozierte Typen zeigen), verrauscht Stacktraces und vergrößert jede JAR.
* Erwarten, dass der Nutzer die Library separat installiert: verstößt gegen das Kernziel „eine Datei“.
* Klassen entpackt in jeden Container legen: exakt das First-Wins-Problem.

### Consequences

* Prozessweit genau eine Runtime, deterministisch die neueste; klare Loader-Meldung bei Major-Inkompatibilität.
* Die öffentliche API einer Mod kann `dev.fabricmultiloader.api`-Typen in Signaturen verwenden, weil diese
  prozessweit eindeutig sind — Voraussetzung für Garantie C7.
* Ein zusätzlicher Nested-Jar-Eintrag (62 KiB) pro Container.
* Ein Major-Wechsel ist möglich, ohne das Ökosystem zum Stichtagsupdate zu zwingen.

---

## ADR-009 — Ressourcen werden in die Payloads gemergt

**Status:** accepted · **Betrifft:** Kapitel 25

### Context

Fabric registriert jede Mod mit `assets/` oder `data/` als eigenes Resource-Pack. Lägen gemeinsame Ressourcen im
Container und versionsspezifische im Payload, wären zwei Packs derselben Mod gleichzeitig aktiv; ihre Präzedenz
hängt von der Mod-Ladereihenfolge ab und ist nicht verlässlich definiert.

### Decision

Der Container enthält **keine** `assets/`- und `data/`-Einträge (Validator `OMNI-1023`). Alle Ressourcen werden
zur Build-Zeit in **jedes** Payload gemergt, in der Präzedenz common → shared → version → datagen. Abweichende
Dateien mit gleichem Pfad erfordern eine explizite `allowOverride`-Deklaration; Sprachdateien werden optional
key-weise gemergt. Das Mod-Icon liegt unter `omni/icon.png`, also außerhalb von `assets/`, damit der Container
kein Resource-Pack wird.

### Alternatives

* Zwei Packs mit definierter Priorität: Fabric bietet keine stabile, mod-übergreifende Prioritätsdeklaration für
  Mod-Packs.
* Ressourcen zur Laufzeit mergen (eigener Pack-Provider): erfordert versionsspezifische Resource-API,
  verlagert Komplexität in die Laufzeit, erschwert Debugging.
* Gemeinsame Ressourcen im Container, versionsspezifische im Payload, Konflikte verbieten: Verbot wäre zu
  restriktiv (Modelle/Shader ändern sich real zwischen Versionen).

### Consequences

* Zur Laufzeit existiert genau ein Resource-Pack pro Mod — Verhalten ist deterministisch und identisch zu einer
  normalen Mod.
* Ressourcen werden N-fach in der JAR gespeichert (Hauptanteil der Größe).
* Jede Abweichung zwischen Versionen ist im Merge-Report und im Code-Review sichtbar — eine häufige
  Fehlerquelle in Multi-Version-Projekten wird explizit.

---

## ADR-010 — Kein Source-Preprocessor als Pflichtbestandteil

**Status:** accepted · **Betrifft:** Kapitel 24.8

### Context

Etablierte Multi-Version-Projekte nutzen Kommentar-Preprocessoren (`//#if MC>=12100`), um denselben Quellcode für
mehrere MC-Versionen zu übersetzen. Das reduziert Duplikate deutlich.

### Decision

FabricMultiLoader enthält keinen Preprocessor und verlangt keinen. Quellcode-Teilung erfolgt über
(a) `:common` für minecraftfreien Code, (b) das optionale `shared`-Sourceset für MC-berührenden Code, der in
mehreren Version-Modulen **unverändert** kompiliert (Shadowing verboten), (c) Adapter/Services für Divergenz.
Ein extern angewandter Preprocessor wird nicht blockiert und die Kombination ist dokumentiert.

### Alternatives

* Eigener Preprocessor: zweite, nicht typgeprüfte Sprache im Projekt; schlechtere IDE-Unterstützung,
  Refactorings und Reviews; löst weder Mixin-, AW- noch Verpackungsfragen.
* Stonecutter als harte Abhängigkeit: bindet das Framework an ein Fremdprojekt und dessen Lebenszyklus.
* Codegenerierung von Java-Quellen: erzeugt „generierte Quellen, die man nicht bearbeiten darf“ — eine bekannte
  DX-Falle, besonders in IntelliJ.

### Consequences

* Aller Quellcode im Repository ist echter, kompilierbarer, refactorbarer Java-Code; jede Datei bedeutet
  dasselbe wie das, was der Compiler sieht.
* Mehr Duplikation im Adapter-Layer als mit Preprocessor — begrenzt, weil Adapter durch das Handle-/Spec-Design
  klein bleiben (Beispielmod: 18–22 Klassen pro Payload gegenüber 142 Common-Klassen).
* `shared` fängt den häufigen Fall „identischer Code für zwei benachbarte Versionen“ ohne Preprocessor ab.

---

## ADR-011 — Determinismus durch Build-Zeit-Disjunktheit statt Laufzeit-Priorität

**Status:** accepted · **Betrifft:** Kapitel 12.5–12.8

### Context

Die Payload-Auswahl trifft Fabrics SAT-Solver. Sein Optimierungsziel („möglichst viele, möglichst neue Mods“)
ist kein spezifizierter Prioritätsmechanismus. Wären zwei Payloads gleichzeitig erfüllbar, wäre nicht definiert,
welches gewinnt — und ein Laufzeit-Tiebreak durch FabricMultiLoader ist unmöglich, weil die Auswahl vor jedem
Modcode passiert.

### Decision

Die Constraint-Domänen aller Payloads (`minecraft × java × environment`) müssen zur Build-Zeit paarweise
**disjunkt** sein; das wird bewiesen, nicht angenommen (`OMNI-1010`). Der `priority`-Mechanismus wirkt
ausschließlich zur Build-Zeit: Ein `DomainDisjunctifier` subtrahiert die Domänen höher priorisierter Payloads von
niedrigeren (exakte Intervall-/Mengenalgebra) und schreibt die resultierenden, disjunkten Bereiche in die
generierten `depends`. Constraints, die nur filtern können (Fremdmods, Fabric API), zählen nicht zur Domäne; zwei
Payloads, die sich nur darin unterscheiden, sind ein Build-Fehler (`OMNI-1012`). Zusätzlich erzwingen
`provides`-Alias und wechselseitige `breaks` Exklusivität, und die Runtime verifiziert „genau eins“
(`OMNI-2003/2004`).

### Alternatives

* Laufzeit-Priorität: unmöglich, weil die Auswahl vor Modcode erfolgt.
* Überlappungen erlauben und auf Solver-Verhalten hoffen: nichtdeterministisch, damit nicht reproduzierbar und
  nicht supportfähig.
* Überlappungen verbieten ohne Subtraktion: würde das legitime Muster „catch-all + Spezialfall“ unmöglich
  machen.

### Consequences

* Die Auswahl ist deterministisch und **beweisbar** — ein Build, der durch den Validator geht, kann kein
  Auswahlproblem beim Spieler haben.
* `priority` bleibt als komfortables Ausdrucksmittel erhalten (catch-all + Spezialfall) und ist trotzdem
  laufzeitfrei.
* Der Mengenalgebra-Code ist der komplexeste Teil von `format` (Intervall-Subtraktion mit
  Prerelease-Grenzen) — mit 30 Testfällen und differenziellen Tests gegen die Loader-Predicate-Implementierung
  abgesichert.

---

Weiter mit [Kapitel 44 — Implementierungsplan](part-12-implementation-plan.md).
