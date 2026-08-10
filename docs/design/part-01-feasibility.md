# 5. Fabric/JVM-Machbarkeitsanalyse

Dieses Kapitel legt die technischen Rahmenbedingungen offen, aus denen die Architektur zwingend folgt. Alle
Aussagen beziehen sich auf Fabric Loader 0.14.0 – 0.17.x (Stand 2026-08) und Sponge Mixin 0.8.5 – 0.8.7 in der
Fabric-Variante. Jede Aussage, die architektonisch tragend ist, ist mit **[T]** markiert und in
[Kapitel 32](part-08-quality.md) durch einen Conformance-Test abgesichert.

---

## 5.1 Startsequenz des Fabric Loaders — die entscheidende Zeitachse

Die Reihenfolge der Ladephasen bestimmt, welche Freiheitsgrade FabricMultiLoader überhaupt hat. Sie ist:

```
1.  JVM startet net.fabricmc.loader.impl.launch.knot.KnotClient/KnotServer   (System-ClassLoader)
2.  Knot#init
    2.1  GameProvider-Erkennung (Minecraft-JAR finden, MC-Version bestimmen)
    2.2  KnotClassLoader wird erzeugt (+ KnotClassDelegate, Transformer-Kette)
    2.3  FabricLoaderImpl#setup
         a) ModDiscoverer: Verzeichnis mods/ scannen
            - jede *.jar öffnen, NUR fabric.mod.json lesen  (kein Bytecode!)
            - "jars"-Array auswerten -> genestete Kandidaten rekursiv,
              erneut NUR deren fabric.mod.json lesen
         b) Built-in-Kandidaten registrieren: minecraft, java, fabricloader
         c) ModResolver/ModSolver: SAT-Lösung über alle Kandidaten
            - Root-Kandidaten (Dateien in mods/) = MANDATORY
            - genestete Kandidaten            = OPTIONAL
            - depends/breaks/conflicts/provides -> harte Klauseln
         d) ausgewählte genestete Kandidaten werden nach
            <gameDir>/.fabric/processedMods/ extrahiert (hash-benannt)
         e) (nur Dev) RuntimeModRemapper: intermediary -> named
         f) alle ausgewählten Mod-JARs werden dem KnotClassLoader als
            Classpath-Einträge hinzugefügt
         g) Access Widener aller ausgewählten Mods werden eingelesen und zu
            EINEM AccessWidener zusammengeführt -> AccessWidenerClassTransformer
    2.4  FabricMixinBootstrap#init
         - MixinBootstrap.init()
         - Mixins.addConfiguration(cfg) für JEDE "mixins"-Deklaration
           JEDER ausgewählten Mod
         - MixinIntermediaryDevRemapper (nur Dev)
    2.5  EntrypointUtils.invoke("preLaunch", PreLaunchEntrypoint)
         -> HIER läuft FabricMultiLoader zum ersten Mal eigener Code
3.  Knot lädt die Minecraft-Hauptklasse über den KnotClassLoader
    -> ab jetzt greifen Mixin-Transformation + Access Widener bei jedem Class-Load
4.  Minecraft-Bootstrap; irgendwann:
    EntrypointUtils.invoke("main"/"client"/"server", ModInitializer...)
5.  Spielstart
```

**Die vier architektonisch entscheidenden Beobachtungen:**

1. **[T] In Phase 2.3a wird ausschließlich `fabric.mod.json` gelesen — kein Class-File.** Der `ModDiscoverer`
   öffnet die JAR als ZIP (in neueren Loadern über ein `ZipFileSystem`), liest den einen JSON-Eintrag, berechnet
   optional einen Hash über die Datei und schließt sie wieder. Es findet keinerlei Bytecode-Inspektion, kein
   ASM-Parsing, keine Annotation-Suche und keine Classfile-Versionsprüfung statt. **Daraus folgt: Payloads mit
   Java-21-Bytecode können in einer JAR liegen, die auf einer Java-17-JVM verarbeitet wird, solange sie nicht
   ausgewählt werden.** Das ist die Antwort auf die gesamte „Classfile-Version“-Problemfamilie.
2. **[T] Genestete Kandidaten sind für den Solver optional.** Root-Mods (Dateien direkt in `mods/`) sind
   verpflichtend zu laden; scheitert deren Auflösung, bricht der Loader mit einer Fehlermeldung ab. Genestete
   Mods sind hingegen als optionale Variablen modelliert: Der Solver maximiert die Anzahl geladener Mods, aber
   harte Klauseln (`depends`, `breaks`, „höchstens ein Kandidat pro Mod-ID“) dominieren. Ein genestetes Mod mit
   unerfüllbarem `depends`, auf das kein *geladenes* Mod hart angewiesen ist, wird einfach nicht ausgewählt.
   Genau deshalb funktionieren im Ökosystem JiJ-Bibliotheken, die `depends: { minecraft: "1.20.x" }` deklarieren.
3. **Mixin-Configs werden pro *ausgewählter* Mod registriert (2.4), also nach der Auflösung.** Eine
   Mixin-Config eines nicht ausgewählten Payloads wird nie an `Mixins.addConfiguration` übergeben. Sponge Mixin
   liest die Mixin-Klassen einer Config erst beim ersten `select`/`prepare`-Durchlauf; nicht registrierte Configs
   existieren für Mixin nicht.
4. **Access Widener werden pro ausgewählter Mod eingelesen und *zusammengeführt* (2.3g).** Ein Mod deklariert
   genau *einen* AW-Pfad (`"accessWidener": "…"`). Mehrere Mods bringen mehrere AW-Dateien; alle werden in einen
   gemeinsamen `AccessWidener` gemergt. Das heißt: **Pro Mod eine Datei — aber unser Payload *ist* eine eigene
   Mod.** Damit ist das AW-Problem strukturell gelöst, ohne eigene Transformer.

---

## 5.2 Fabric Loader im Detail

### 5.2.1 Mod Discovery

* Gescannt werden: `<gameDir>/mods/*.jar`, `<gameDir>/mods/<mcVersion>/*.jar` (versionierte Unterordner,
  Loader ≥ 0.15), Classpath-Einträge mit `fabric.mod.json` (Dev), sowie rekursiv alle in `jars[]` deklarierten
  Pfade innerhalb bereits gefundener JARs.
* Ein Kandidat ohne parsbares `fabric.mod.json` führt zu einem harten Fehler mit Dateinamen (gut für uns:
  Beschädigte Container werden früh und mit Dateibezug gemeldet).
* Für die Deduplizierung genesteter Bibliotheken gilt: Bei mehreren Kandidaten derselben Mod-ID gewinnt die
  höchste Version, die alle Constraints erfüllt. **Das ist der Mechanismus, über den `fabricmultiloader-runtime`
  aus vielen Universal-JARs auf genau eine Instanz dedupliziert wird.**
* Die Extraktion ausgewählter genesteter JARs erfolgt nach `<gameDir>/.fabric/processedMods/`, mit
  hash-/namensbasiertem Cache. Beim zweiten Start entfällt die Extraktion, wenn Hash und Größe passen.
  FabricMultiLoader implementiert **keinen eigenen Cache** und keine eigene Extraktion (NF-04).

### 5.2.2 `fabric.mod.json` — relevante Felder und ihre Semantik

| Feld | Relevanz für FabricMultiLoader |
|---|---|
| `schemaVersion` | Immer `1`. Vom Generator geschrieben. |
| `id` | `^[a-z][a-z0-9-_]{1,63}$`. Container = primäre Mod-ID; Payloads = `<id>-mc<compact>`. |
| `version` | Container = Modversion. Payload = `<modVersion>+mc<mcVersion>` (Build-Metadata nach `+` ist für SemVer-Vergleiche irrelevant, aber in Logs/ModMenu sichtbar). |
| `provides` | Alias-IDs. Zwei geladene Mods dürfen **nicht** dieselbe ID bereitstellen ⇒ nutzbar als „höchstens ein Payload“-Garantie. |
| `environment` | `*`/`client`/`server`. Wird vom Loader **vor** dem Classloading ausgewertet: ein `client`-Mod wird auf einem dedizierten Server gar nicht geladen. Für Client-only-Payloads verwendbar. |
| `entrypoints` | Map von Phase → Liste von Klassennamen (optional mit `adapter`). Klassen dürfen in **einer anderen Mod** liegen — alle Mod-Klassen teilen einen ClassLoader. |
| `jars` | `[{"file": "META-INF/jars/x.jar"}]`. Rekursiv. Kernmechanismus dieser Architektur. |
| `mixins` | Liste von Config-Dateinamen oder Objekten `{config, environment}`. Pro Mod. Wird in 2.4 registriert. |
| `accessWidener` | Genau ein Pfad pro Mod. |
| `depends` | Map ID → Version-Predicate **oder Array von Predicates (OR-Semantik)**. Eingebaute IDs: `minecraft`, `java` (Version = Java-Major, z. B. `17.0.0`), `fabricloader`. |
| `breaks` / `conflicts` | Harte bzw. weiche Negativbeziehung. `breaks` = SAT-Klausel „nicht beide“. |
| `recommends` / `suggests` | Nur Log-Hinweise, keine Klauseln. |
| `custom` | Beliebige Objekte; `custom.modmenu.parent`, `custom.modmenu.badges` werden von ModMenu ausgewertet. |

**Version-Predicate-Syntax** (Loader `VersionPredicateParser`): `*`, `1.20.1`, `=1.20.1`, `>=1.20.1`,
`>1.20`, `<=1.21.4`, `<1.22`, `~1.20.1` (≥1.20.1 <1.21.0), `^1.20.1` (≥1.20.1 <2.0.0), Kombination mehrerer
Bedingungen durch Leerzeichen (AND: `">=1.21 <1.21.2"`), Alternativen durch Array (OR).
FabricMultiLoader nutzt ausschließlich `>=`/`<`/`=` und Arrays — die Teilmenge, die in allen Loader-Versionen
0.14+ identische Semantik hat, und die von der eigenen Implementierung in `fabricmultiloader-format`
bitgenau nachgebildet wird (Kapitel 12).

### 5.2.3 Der Solver

Der `ModSolver` baut ein Boolesches Erfüllbarkeitsproblem (Sat4j) mit einer Variable pro Kandidat:

* **Mandatory-Klausel** je Root-Mod-ID: mindestens ein Kandidat dieser ID muss `true` sein.
* **At-most-one-Klausel** je Mod-ID *und* je bereitgestellter Alias-ID (`provides`).
* **Depends-Klausel** je Kandidat: `candidate → OR(passende Kandidaten der Ziel-ID)`.
* **Breaks/Conflicts-Klausel**: `¬(a ∧ b)`.
* **Optimierungsziel**: möglichst viele und möglichst neue Mods laden.

Konsequenzen für uns:

1. Zwei Payloads mit derselben `provides`-Alias-ID können **nie** gleichzeitig geladen werden — strukturelle
   Exklusivität ohne eigene Logik.
2. Das Optimierungsziel („möglichst viele/neue“) ist **kein deterministischer Prioritätsmechanismus**, auf den
   man Payload-Auswahl stützen darf: Bei zwei gleichzeitig erfüllbaren Payloads ist nicht spezifiziert, welcher
   gewinnt. **Deshalb muss die Disjunktheit der Payload-Constraints zur *Build-Zeit* bewiesen werden**
   (Kapitel 12.6, Validator-Regel `OMNI-1010`/`OMNI-1012`). Der `priority`-Mechanismus des Frameworks wird
   deshalb nicht zur Laufzeit ausgewertet, sondern zur Build-Zeit in *disjunkte* Bereiche umgerechnet
   (Range-Subtraktion, Kapitel 12.7).
3. Unerfüllbarkeit eines *Root*-Mods erzeugt eine ausführliche, lokalisierte Loader-Fehlermeldung mit
   Fabric-GUI-Dialog. Das nutzen wir für den Fall „Minecraft-Version gar nicht unterstützt“, indem der
   Container selbst `depends.minecraft` = Union aller Payload-Ranges deklariert.

### 5.2.4 Classpath-Verhalten und Knot

* `KnotClassLoader` (bzw. `KnotCompatibilityClassLoader` bei `-Dfabric.loader.useCompatibilityClassLoader=true`)
  ist der ClassLoader für **Minecraft und alle Mods**. Er delegiert an einen internen
  `URLClassLoader`-artigen Delegate für Ressourcen und wendet auf jede geladene Klasse die Transformer-Kette
  an (Access Widener → Mixin → Fabric-eigene Transformer).
* **Es gibt keine Isolation zwischen Mods.** Alle Mod-Klassen liegen im selben Namensraum desselben
  ClassLoaders. Daraus folgt direkt: (a) Payload-Klassen können Common-Klassen des Containers sehen und
  umgekehrt; (b) es gibt keine `ClassIdentity`-Probleme; (c) gleiche FQCN aus zwei Mods kollidieren
  (first wins) — der Grund, warum die Runtime als **genestete Mod mit Loader-Deduplizierung** ausgeliefert
  wird und nicht als geschatteter Fat-Jar-Inhalt (ADR-008).
* Parent des `KnotClassLoader` ist der System-ClassLoader; dort liegen JVM-Klassen und der Loader selbst.
  Klassen unter `net.fabricmc.loader.` werden vom Parent geladen (Loader-Interna sind für Mods sichtbar,
  aber nicht transformierbar).
* Mixin-Transformation greift nur für Klassen, die **durch den KnotClassLoader** geladen werden. Ein eigener
  Child-ClassLoader umgeht die Transformer-Kette komplett — dort landende Klassen bekommen weder Mixins noch
  Access Widening, und ihre Minecraft-Typen wären, falls dort erneut geladen, inkompatibel zu denen im
  Knot-Loader. Das ist der harte Grund gegen Ansatz D.

### 5.2.5 Entrypoints

* Phasen: `preLaunch` (`PreLaunchEntrypoint`), `main` (`ModInitializer`), `client` (`ClientModInitializer`),
  `server` (`DedicatedServerModInitializer`), sowie mod-definierte Phasen beliebiger Typen über
  `FabricLoader#getEntrypointContainers`.
* Aufrufreihenfolge: Mods werden topologisch nach `depends` sortiert; innerhalb gleicher Ordnung nach ID.
  **Da jedes Payload `depends` auf den Container deklariert, laufen Container-Entrypoints garantiert vor
  Payload-Entrypoints.** Die Runtime verlässt sich nicht allein darauf (idempotente, explizit sequenzierte
  Initialisierung, Kapitel 9.6), nutzt es aber als Standardpfad.
* Eine Ausnahme aus einem Entrypoint wird von `EntrypointUtils` in eine
  `net.fabricmc.loader.impl.FormattedException` verpackt und von Knot über die Fabric-Fehler-GUI angezeigt
  (Client) bzw. formatiert nach stderr geschrieben (Server). Die **Message der geworfenen Exception erscheint
  dabei vollständig** — das ist unser Kanal für Diagnoseberichte, ohne Loader-Interna zu berühren
  (Kapitel 29.4).
* `preLaunch` läuft **vor** dem ersten Minecraft-Class-Load. Ein Abbruch dort ist sauber: keine halb
  initialisierte Registry, keine bereits angewandten Mixins.

### 5.2.6 Objekt-Austausch zwischen Mods

`FabricLoader.getInstance().getObjectShare()` (Loader ≥ 0.12) ist eine prozessweite `Map<String,Object>` mit
`put`/`get`/`whenAvailable`. FabricMultiLoader nutzt sie, um pro Container einen Handle zu veröffentlichen
(`"<modid>:omni"` → `ContainerHandle`), damit Drittmods und Debug-Werkzeuge den aktiven Payload auslesen
können, ohne Klassen der Runtime zu importieren (Kapitel 19.9).

---

## 5.3 Mixin

### 5.3.1 Zeitpunkte

| Zeitpunkt | Was passiert |
|---|---|
| Knot 2.4 | `Mixins.addConfiguration(name)` je Config-Eintrag ausgewählter Mods. Es wird nur der **Dateiname registriert**, die JSON-Datei noch nicht zwingend geparst. |
| Erste Transformation | `MixinProcessor#select` → alle registrierten Configs werden geparst, `IMixinConfigPlugin#onLoad` aufgerufen, Mixin-Klassen aufgelöst (`ClassInfo`), `targets` validiert. |
| Pro Ziel-Class-Load | `shouldApplyMixin` (Plugin) → `preApply` → Injektion → `postApply`. Fehlende Targets/Injection-Points erzeugen `InvalidInjectionException`/`MixinApplyError`. |

Wichtig: **Die Validierung einer Mixin-Klasse passiert erst, wenn ihre Config registriert ist.** Eine nicht
registrierte Config kostet exakt null — kein Datei-Read, kein ASM, kein Fehler. Das ist die Grundlage von
G4/F-04.

### 5.3.2 Versionsspezifische Targets

Die realen Bruchstellen zwischen MC-Versionen:

* **Umbenannte Klassen**: Intermediary hält Klassen stabil, aber neu eingeführte/aufgeteilte Klassen erhalten
  neue Nummern. Beispiel: Networking-Payload-Typen in 1.20.5+ existieren in 1.20.1 gar nicht.
* **Geänderte Methodensignaturen**: `ItemRenderer#renderItem` erhielt zwischen 1.20.1 und 1.21.x zusätzliche
  Parameter. Ein `@Inject` mit `method = "renderItem(...)V"` ist damit versionsgebunden.
* **Verschwundene Methoden**: Ein `@Inject` auf eine entfernte Methode ist ein harter Startfehler.
* **Geänderte Injection Points**: `@At(value="INVOKE", target="…")` referenziert exakte Deskriptoren.

Konsequenz: **Ein versionsübergreifend gültiges Mixin-Set ist im Allgemeinen unmöglich.** Jede Lösung muss
Mixin-Sets pro Version trennen. Es gibt genau drei Mechanismen dafür:

| Mechanismus | Bewertung |
|---|---|
| `IMixinConfigPlugin#shouldApplyMixin` | Verhindert *Anwendung*, aber **nicht** das Laden und Validieren der Mixin-Klasse durch `ClassInfo`. `targets`-Auflösung passiert vorher. Eine Mixin-Klasse, die eine in dieser Version nicht existierende Zielklasse referenziert, scheitert bereits in `select()`. **Unzureichend als Hauptmechanismus.** Brauchbar für Feinsteuerung *innerhalb* einer Version (z. B. „nur wenn Mod X geladen“). |
| Getrennte Mixin-Configs pro Version, alle in einer Mod deklariert, mit Config-Plugin, das `getMixins()` leer liefert | `getMixins()` kann Mixins nachreichen; um sie zu *entfernen*, müsste man sie aus der JSON weglassen. Fabric parst die Config vollständig; `mixins`-Einträge der JSON werden immer aufgelöst. Ein Plugin kann sie nicht zurückziehen. **Nicht tragfähig.** |
| Getrennte Mixin-Configs in getrennten Mods, von denen nur eine geladen wird | Die nicht geladene Config wird nie registriert; ihre Klassen liegen nicht einmal auf dem Classpath. **Vollständig sicher.** |

Der dritte Mechanismus ist genau das, was JiJ-Payloads liefern. Deshalb ist die Payload-Trennung nicht nur eine
Verpackungsentscheidung, sondern die **einzige belastbare** Mixin-Isolationsstrategie.

### 5.3.3 Refmaps

* Loom erzeugt beim Kompilieren via Mixin-Annotation-Processor eine Refmap
  (`<archivesBaseName>-refmap.json`), die Named-Namen (Yarn) → Intermediary abbildet, und referenziert sie in
  der Mixin-Config über `"refmap": "…"`.
* Die Refmap ist **strikt an die MC-Version und die Mappings gebunden**, gegen die kompiliert wurde. Ein
  Zusammenführen von Refmaps mehrerer MC-Versionen ist semantisch falsch: Derselbe Named-Name kann in
  verschiedenen Versionen auf verschiedene Intermediary-Namen zeigen (bei neu eingeführten Membern) und
  dieselbe Methode kann verschiedene Deskriptoren haben.
* **Lösung:** eine Refmap pro Payload, mit eindeutigem Namen (`examplemod-mc1201-refmap.json`), niemals
  gemergt. Das ist automatisch der Fall, weil jedes Payload ein eigener Loom-Compile ist.
* Im Dev-Runtime hebt der `MixinIntermediaryDevRemapper` die Refmap-Auflösung auf Named um; das ist
  Loader-intern und funktioniert unverändert, weil pro Lauf nur ein Payload existiert.

### 5.3.4 Client-/Server-Mixins

Zwei Ebenen:

* `fabric.mod.json`: `"mixins": [{"config": "x.client.mixins.json", "environment": "client"}]` — Config wird
  auf dedizierten Servern nicht registriert. **Bevorzugter Mechanismus.**
* In der Config: `"client": [...]`, `"server": [...]`, `"mixins": [...]` — Mixin-eigene Aufteilung nach
  `MixinEnvironment.Side`.

FabricMultiLoader generiert pro Payload bis zu drei Configs (`common`, `client`, `server`) und deklariert sie mit
korrektem `environment`. Client-Mixins referenzieren Klassen, die auf einem dedizierten Server nicht existieren
(`net.minecraft.client.**`) — die Trennung ist daher nicht optional, sondern Pflicht.

### 5.3.5 Grenzen

* Mixins können nicht *nachträglich* auf bereits geladene Klassen angewendet werden. Da unsere Payload-Auswahl
  im Solver (vor 2.4) passiert, ist das kein Problem.
* Zwei Payloads könnten theoretisch identische Mixin-Klassennamen enthalten. Da nie zwei Payloads geladen
  werden, ist das harmlos; der Validator erzwingt dennoch eindeutige **Config-Dateinamen und Refmap-Namen**
  über alle Payloads (Regel `OMNI-1030`), damit Slim-Jars und manuelles Debugging eindeutig bleiben.
* `@Mixin(targets = "…")` mit String-Klassennamen umgeht die Refmap-Prüfung und ist versionsfragil; die
  Dokumentation empfiehlt Klassenliterale.

---

## 5.4 Access Widener

### 5.4.1 Verarbeitung

* Format: Textdatei, Header `accessWidener v2 <namespace>` (Namespace `named` in Sources, `intermediary` im
  publizierten Artefakt — Loom remappt beim `remapJar`).
* Der Loader liest die AW-Dateien **aller ausgewählten Mods** in Phase 2.3g in einen gemeinsamen
  `AccessWidener` und installiert einen `AccessWidenerClassTransformer` in der Knot-Transformerkette. Die
  Erweiterung passiert beim Class-Load, vor Mixin.
* Namespace-Prüfung: Der Loader verlangt, dass der Header-Namespace zum Runtime-Namespace passt
  (`intermediary` in Produktion, `named` im Dev-Run). Falscher Namespace ⇒ harter Fehler.
* Einträge, deren Klasse nie geladen wird, sind wirkungslos. Einträge auf ein **nicht existierendes Member
  einer existierenden Klasse** werden beim Transformieren nicht gefunden und stillschweigend ignoriert —
  das ist kein Fehler, aber auch keine verlässliche Grundlage: Eine versionsübergreifende AW-Datei wäre also
  „meistens harmlos“, aber sie kann nicht ausdrücken, dass ein Member in einer Version anders heißt.

### 5.4.2 Warum eine gemeinsame AW-Datei nicht reicht

1. Intermediary-Namen für **neu eingeführte** Member unterscheiden sich zwischen Versionen; eine Zeile
   `accessible field net/minecraft/class_310 field_1724 …` kann in 1.20.1 ein anderes Feld meinen als in 1.21.4,
   falls Felder umnummeriert wurden.
2. Klassen, die in einer Version nicht existieren, erzeugen keine Fehler — aber Loom kann eine AW-Datei nur
   gegen **eine** Mappings-Version remappen. Eine handgeschriebene, versionsübergreifende AW-Datei müsste
   bereits in Intermediary vorliegen und damit auf Yarn-Komfort verzichten.
3. Der Loader akzeptiert exakt **einen** `accessWidener`-Pfad pro Mod. Mehrere Dateien pro Mod sind nicht
   deklarierbar.

### 5.4.3 Konsequenz

**Ein Payload = eine Mod = ein eigener Access Widener, von Loom gegen die richtige Mappings-Version remappt.**
Das ist die vollständige Lösung; sie braucht keine Runtime-Transformationen, keine Reflection und keinen
eigenen Transformer. Der Container selbst deklariert **keinen** Access Widener (Validator-Regel `OMNI-1024`),
weil er keine Minecraft-Klassen berührt.

Für den Sonderfall „dasselbe AW-Bedürfnis in allen Versionen“ generiert das Gradle-Plugin die Payload-AW-Datei
aus einer gemeinsamen Quelle: `common/src/main/accesswidener/shared.accesswidener` wird jedem Version-Modul
als Basis untergelegt und mit `versions/mc-X/src/main/resources/<modid>.accesswidener` gemergt
(Kapitel 17.4) — der Merge findet in Named-Namespace *vor* dem Loom-Remap statt, ist also mappingkorrekt.

---

## 5.5 Java und JVM

### 5.5.1 Java-Anforderungen je Minecraft-Version

| Minecraft | erforderliche Java-Major | Classfile-Major der MC-Klassen |
|---|---|---|
| 1.16.5 | 8 | 52 |
| 1.17 – 1.17.1 | 16 | 60 |
| 1.18 – 1.20.4 | 17 | 61 |
| 1.20.5 – 1.21.x | 21 | 65 |
| **26.1 und neuer** | **25** | **69** |
| künftig | ≥ 25 | ≥ 69 |

Der Sprung 1.21.x → 26.1 hebt die erforderliche Java-Hauptversion von 21 auf **25** (Classfile-Major 69). Eine
Mod, die 1.20.1, 1.21.1 und 26.1 unterstützt, muss also Payloads mit den Classfile-Majors 61, 65 und 69 in
**einer** Datei ausliefern und auf einer Java-17-JVM (1.20.1) trotzdem startfähig bleiben. Genau das leistet
`depends.java` in Verbindung mit der Tatsache, dass nicht ausgewählte Payloads nie definiert werden.

Die JVM prüft die Classfile-Version **beim Definieren einer Klasse** (`ClassLoader#defineClass` →
`UnsupportedClassVersionError`), nicht beim Lesen der JAR. Solange eine Class-Datei nicht definiert wird, ist
ihre Version irrelevant. Der Loader definiert nur Klassen ausgewählter Mods (5.1, Beobachtung 1) ⇒ **eine
Universal-JAR darf Payloads mit Classfile-Major 61 und 65 gleichzeitig enthalten** (Antwort auf Frage 21/22).

### 5.5.2 Was zwingend auf Baseline-Level kompiliert werden muss

Alles, was auf der **ältesten** unterstützten Umgebung geladen wird:

| Artefakt | Ziel-Bytecode | Begründung |
|---|---|---|
| `fabricmultiloader-format` | 52 (Java 8) | wird auch vom Gradle-Plugin genutzt; muss auf jeder unterstützten JVM laufen |
| `fabricmultiloader-api` | 52 | vom Common-Code und allen Payloads referenziert |
| `fabricmultiloader-runtime` | 52 | Bootstrap läuft auf der ältesten JVM |
| `fabricmultiloader-processor` | 52 | Annotation Processor, läuft im Build |
| Container-Common des Mods | `baselineJava` aus der Matrix (Beispiel: 17) | wird auf der ältesten unterstützten MC-Version geladen |
| Payload `mc-1.20.1` | 61 | MC 1.20.1 → Java 17 |
| Payload `mc-1.21.4` | 65 | MC 1.21.4 → Java 21 |

Der Validator scannt jede Class-Datei des Containers und jedes Payloads und vergleicht den Major-Wert mit dem
deklarierten Soll (Regel `OMNI-1040`/`OMNI-1041`). Ein versehentliches `--release 21` im Common-Modul wird so
zur Build-Zeit gefangen, nicht beim Spieler.

### 5.5.3 Multi-Release-JARs — warum sie hier ungeeignet sind

Ein MR-JAR (`Multi-Release: true`, `META-INF/versions/<n>/…`) selektiert nach **Java-Version**, nicht nach
Minecraft-Version. Damit:

* könnte man Java-17- vs. Java-21-Bytecode trennen — aber nicht 1.21.1 von 1.21.4 (beide Java 21). Das
  eigentliche Problem wird nicht adressiert.
* wird die Selektion vom ClassLoader gemacht. `KnotClassLoader` implementiert MR-Semantik nicht garantiert
  (er liest Ressourcen über einen eigenen Delegate); Verhalten wäre loaderversionsabhängig.
* wären Mixin-Configs, Refmaps und `fabric.mod.json` nicht mit-selektierbar — sie liegen als Ressourcen im
  Root und sind nicht MR-fähig im Sinne des Loaders.

**Verworfen.** MR-JARs lösen ein anderes Problem.

### 5.5.4 Lazy Classloading als Isolationsmechanismus — und seine Grenze

Ein oft vorgeschlagener Ansatz ist: „alles in eine JAR, Klassen werden ohnehin lazy geladen“. Das trägt nur
teilweise:

* Klassen werden tatsächlich erst beim ersten aktiven Gebrauch definiert. Ein `if (mc >= 1.21) new Foo1214()`
  lädt `Foo1214` nur im `true`-Zweig — **aber** die *verifizierende* Methode, die `Foo1214` referenziert, muss
  auflösbar sein; die Auflösung ist in HotSpot lazy pro Bytecode-Instruktion, also praktisch tolerant.
* **Aber**: Mixin-Configs und Access Widener sind *keine* lazy geladenen Klassen, sondern deklarative
  Metadaten, die der Loader eagerly verarbeitet. Genau hier bricht der naive Ansatz.
* **Und**: Sponge Mixin baut für jede Mixin-Klasse einer registrierten Config ein `ClassInfo` — das ist ein
  eagerer ASM-Read der Mixin-Klasse *und* ihrer Targets.

Lazy Classloading ist also eine **notwendige, aber nicht ausreichende** Eigenschaft. Es ist der Grund, warum
Common-Code und Runtime problemlos in einer JAR neben allem anderen leben können; es ist nicht der Grund, warum
Payloads isoliert sind — das leistet die JiJ-Auswahl.

### 5.5.5 Reflection, MethodHandles, ServiceLoader

* **Reflection** wird ausschließlich an einer Stelle im kritischen Pfad verwendet:
  `Class.forName(platformFactory).getDeclaredConstructor().newInstance()`. Der Klassenname stammt aus dem
  signierten/gehashten Manifest, nicht aus einem Scan. Kosten: eine Klasse.
* **MethodHandles** werden nicht verwendet. Sie brächten keinen Vorteil, da die Aufrufe einmalig sind, und
  erhöhten die Baseline-Anforderungen (`MethodHandles.privateLookupIn` erst ab Java 9).
* **ServiceLoader** wird bewusst **nicht** für Payload-Discovery verwendet: `ServiceLoader` scannt
  `META-INF/services/**` über den gesamten Classpath, wäre also nichtdeterministisch bei mehreren Universal-Mods
  und liefert keine brauchbaren Fehlermeldungen. Stattdessen: explizite FQCN im Manifest.
  (`ServiceLoader` *funktioniert* im Knot-Loader — die Entscheidung ist eine Determinismus-Entscheidung,
  keine technische Notwendigkeit.)
* **`ClassCastException`/Class-Identity**: Ausgeschlossen, weil kein zweiter ClassLoader existiert. Jede Klasse
  wird von genau einem Loader (Knot) definiert.

---

## 5.6 Mappings

### 5.6.1 Namensräume

| Namespace | Eigenschaften |
|---|---|
| `official` | Mojangs obfuszierte Namen; ändern sich pro Version vollständig. |
| `intermediary` | Von Fabric verwaltet, **über Versionen hinweg stabil, solange das Element „dasselbe“ bleibt**. Neue Elemente erhalten neue Nummern; entfernte Nummern werden nicht wiederverwendet. Runtime-Namespace in Produktion. |
| `named` (Yarn) | Menschenlesbar, pro Version eigener Build, umbenennbar zwischen Versionen. Dev-Runtime-Namespace. |
| Mojang Official Mappings | Alternativ zu Yarn nutzbar (Loom `layered { officialMojangMappings() }`); ändert nichts an der Architektur, da pro Payload frei wählbar. |

### 5.6.2 Warum Intermediary-Stabilität nicht ausreicht

Intermediary garantiert Namensstabilität, **nicht Signaturstabilität**. Wenn `method_1234(PacketByteBuf)` zu
`method_1234(RegistryByteBuf)` wird, bleibt der Name gleich, aber der Deskriptor ändert sich — und Bytecode
referenziert Name **und** Deskriptor. Ein einzelnes Kompilat kann daher nicht beide Versionen bedienen.
Genau hier liegt die harte Grenze, an der „ein Kompilat für alle Versionen“ scheitert (Nicht-Ziel N2) und
weshalb pro Version kompiliert und remappt werden muss.

### 5.6.3 Konsequenzen für die Architektur

1. **Pro Payload ein vollständiger Loom-Build** mit eigener MC-Version, eigenen Mappings, eigenem Refmap,
   eigenem AW-Remap. Payloads dürfen unterschiedliche Mapping-*Provider* nutzen (Yarn hier, Mojmap dort), weil
   sie keinen Bytecode teilen.
2. **Der Container ist namespace-neutral**, weil er keine Minecraft-Referenz enthält. Er wird von Loom nie
   remappt (Validator-Regel `OMNI-1042`: keine Referenz auf `net/minecraft/`, `com/mojang/blaze3d/`,
   `net/fabricmc/fabric/api/` in Container-Klassen).
3. **Der Dev-Runtime** remappt ausgewählte Mods intermediary→named. Das betrifft nur das Payload; der Container
   bleibt unverändert. Damit funktioniert auch das Testen der fertigen Universal-JAR im Loom-Dev-Run.

---

## 5.7 Zusammenfassung: Wo die eigentlichen Schwierigkeiten liegen

| Schwierigkeit | Härtegrad | Lösung in dieser Architektur |
|---|---|---|
| Mixin-Configs werden eagerly verarbeitet | **hart** | Config lebt in der Payload-Mod; nicht geladene Mod ⇒ keine Config. |
| Access Widener: genau eine Datei pro Mod, mappingabhängig | **hart** | Payload = eigene Mod ⇒ eigener AW. Gemeinsame AW-Quelle wird pre-remap gemergt. |
| Unterschiedliche Deskriptoren derselben MC-Methode | **hart, unlösbar in einem Kompilat** | N Kompilate, ein Container. Common-Code darf MC nicht berühren. |
| Unterschiedliche Java-Major-Versionen | mittel | `depends.java` + Solver + Validator-Classfile-Scan. |
| Deterministische Payload-Auswahl | mittel | Build-Zeit-Disjunktheitsbeweis + `provides`-Exklusivität + Runtime-Assertion. |
| Eine Mod-Identität nach außen | mittel | Container trägt die primäre Mod-ID; Payloads sind ModMenu-Kinder mit Library-Badge. |
| Versionsabhängige Fabric-API-/Mod-Abhängigkeiten | leicht | Pro Payload eigene `depends`; optional pro Payload genestete Bibliotheken. |
| Ressourcenkonflikte zwischen Common und Payload | leicht | Ressourcen werden zur Build-Zeit in das Payload gemergt; der Container trägt **keine** `assets/`- oder `data/`-Einträge. |
| Loader-Verhalten bei unerfüllbaren genesteten Mods | **tragende Annahme** | Conformance-Test über Loader-Matrix in CI; Rückfallpfad `buildSlimJars`. |
| Größe der JAR | akzeptiert | N Payloads, keine Deduplizierung. |

---

Weiter mit [Kapitel 6–9 — Architekturvarianten und finale Entscheidung](part-02-architecture.md).
