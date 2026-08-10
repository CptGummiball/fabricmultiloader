# 6. Evaluierte Architekturvarianten

Bewertungsskala: **++** sehr gut, **+** gut, **o** neutral/aufwändig, **–** problematisch, **– –** disqualifizierend.

## 6.1 Ansatz A — Bootstrap-Library lädt versionsspezifische Klassen aus derselben JAR

**Idee:** Alle Versionsimplementierungen liegen als Klassen in der Universal-JAR (unterschiedliche Packages);
ein Bootstrap im `preLaunch`-Entrypoint erkennt die Umgebung und instanziiert die passende Klasse über
`Class.forName`. Ein einziger `fabric.mod.json`.

| Kriterium | Bewertung | Begründung |
|---|---|---|
| Fabric-Kompatibilität | + | Ganz normale Mod, ein Entrypoint. |
| Mixin | **– –** | Die `mixins`-Liste im einzigen `fabric.mod.json` wird komplett registriert. Alle Mixin-Configs aller Versionen werden geparst, alle Mixin-Klassen per ASM gelesen, alle `targets` aufgelöst. Ein 1.20.1-Mixin auf eine in 1.21.4 entfernte Methode ⇒ harter Startcrash. `IMixinConfigPlugin` kann das nicht verhindern (5.3.2). |
| Access Widener | **– –** | Nur ein `accessWidener`-Pfad pro Mod; er müsste für alle Versionen gleichzeitig gültig sein. Mappingkorrekt nicht herstellbar. |
| Java | – | Alle Klassen liegen im selben Classpath-Eintrag. Solange nicht definiert, unkritisch — aber der Validator kann nicht mehr garantieren, dass niemand versehentlich eine Java-21-Klasse aus Common referenziert. Ein einziger Fehlgriff ⇒ `UnsupportedClassVersionError` beim Spieler. |
| Mappings | – | Alle Versions-Kompilate müssten in einer JAR mit **einem** Refmap-Satz liegen; Refmaps sind pro Config referenzierbar, also machbar, aber Loom müsste N-mal in dasselbe Artefakt kompilieren — nicht vorgesehen, erfordert eigene Loom-Umgehung. |
| Performance | ++ | Keine Extraktion. |
| Wartbarkeit | o | Ein Modul, viele Packages; Package-Disziplin nur konventionell erzwingbar. |
| Aufwand | + | Gering, solange Mixins/AW ignoriert werden — was nicht geht. |
| Fehleranfälligkeit | – – | Ein falscher Import in Common ⇒ Crash auf allen anderen Versionen. |
| Debugging | + | Ein Classpath, klare Stacktraces. |
| IDE | – | Ein Modul kann nicht gleichzeitig gegen 1.20.1 und 1.21.4 kompilieren. Erfordert doch Multi-Module ⇒ Ansatz kollabiert. |
| Mod-Kompatibilität | + | Eine Mod-ID. |
| Zukunft | – | Jede neue Version vergrößert die Menge eagerly validierter Mixins. |

**Verworfen** wegen Mixin und Access Widener. Der Bootstrap-Gedanke selbst wird jedoch übernommen — als
Lifecycle-Orchestrator, nicht als Classloading-Mechanismus.

## 6.2 Ansatz B — Remappte Payload-JARs als *Ressourcen* (nicht als Fabric-Nested-Jars)

**Idee:** `payloads/1.20.1.jar` liegen als reine ZIP-Ressourcen in der Universal-JAR; die Runtime entpackt die
passende und hängt sie über Reflection in den `KnotClassLoader` (`addUrl`) ein.

| Kriterium | Bewertung | Begründung |
|---|---|---|
| Fabric-Kompatibilität | – – | `KnotClassLoader#addURL` ist nicht öffentliche API; die Signatur wechselt zwischen Loader-Versionen (`addUrlFwd`, `KnotClassDelegate#setAllowedPrefixes`). Reflection auf Loader-Interna verstößt gegen G3 und bricht bei jedem Loader-Update. |
| Mixin | – – | Zum Zeitpunkt `preLaunch` sind Mixin-Configs bereits registriert (Phase 2.4 < 2.5). Ein nachträglich eingehängtes Payload kann seine Mixin-Configs nicht mehr anmelden. `Mixins.addConfiguration` *nach* `MixinBootstrap` funktioniert nur, solange die Ziel-Klassen noch nicht geladen sind — bei MC-Klassen zufällig meist erfüllt, aber `MixinEnvironment` ist dann schon in Phase `PREINIT`, und Fabric registriert die Config nicht mit den nötigen Metadaten (Refmap-Remapper, Mod-Zuordnung). Fragil und nicht spezifiziert. |
| Access Widener | – – | Der `AccessWidenerClassTransformer` ist zu diesem Zeitpunkt bereits gebaut. Nachträgliche AW-Einträge sind nicht vorgesehen. |
| Java | + | Nicht ausgewählte Payloads werden nicht gelesen. |
| Mappings | + | Payloads sind vorab remappt. |
| Performance | o | Eigene Extraktion + eigener Cache nötig (NF-04 verletzt). |
| Wartbarkeit | – – | Bindung an Loader-Interna. |
| Debugging | – | Klassen aus einem nachträglich eingehängten Pfad; IDE-Sourcen-Zuordnung schwierig. |
| Mod-Kompatibilität | o | Payload-Klassen sind für andere Mods sichtbar, aber der Loader kennt das Payload nicht als Mod ⇒ keine Entrypoints, keine `depends`-Prüfung, kein ModMenu-Eintrag. |

**Verworfen.** Der Ansatz verliert genau die Loader-Dienste, die man braucht.

## 6.3 Ansatz C — Alle Versionsklassen in unterschiedlichen Packages derselben JAR

Identisch zu A in allen relevanten Punkten (Mixin/AW/Loom), plus dem Nachteil, dass ein einziges Gradle-Modul
nicht gegen mehrere MC-Versionen kompilieren kann. **Verworfen.** Die Package-Trennung selbst wird als
*Konvention innerhalb* der gewählten Architektur übernommen (Kapitel 22.4), weil sie Stacktraces und
Slim-Jars eindeutig macht.

## 6.4 Ansatz D — Eigener ClassLoader für Payloads

| Kriterium | Bewertung | Begründung |
|---|---|---|
| Mixin | – – | Mixin transformiert ausschließlich Klassen, die durch die Knot-Transformerkette laufen. Ein Child-Loader umgeht sie vollständig ⇒ Payload-Mixins wirken nie. |
| Access Widener | – – | Ebenso: kein AW-Transformer im eigenen Loader. |
| Class-Identity | – – | Minecraft-Typen müssten an den Parent delegiert werden (sonst zwei `Item`-Klassen ⇒ `ClassCastException` an jeder Grenze). Delegation an Knot ist möglich (Parent-First für `net.minecraft.**`), aber dann liegen die Payload-Klassen in einem Loader, dessen Klassen von Mixin-transformierten MC-Klassen **nicht** gesehen werden können, sobald Minecraft z. B. per `Class.forName` in seinem Kontext auflöst. Registry-Callbacks, Codecs mit `Class`-Literalen und Datafixer-Reflection brechen unvorhersehbar. |
| Fabric-Dienste | – – | `FabricLoader#getEntrypointContainers`, `getModContainer`, Resource-Pack-Registrierung: alles an Mod-Kandidaten gebunden, die der Loader kennt. |
| Debugging | – – | Doppelte Klassennamen in Stacktraces, IDE-Breakpoints unzuverlässig. |

**Verworfen als Hauptmechanismus.** Kein Teilaspekt wird übernommen. Ein eigener ClassLoader kommt in dieser
Architektur an **keiner** Stelle vor — das ist eine bewusste, harte Designgrenze (ADR-002).

## 6.5 Ansatz E — Fabric-Nested-JAR-System (JiJ)

| Kriterium | Bewertung | Begründung |
|---|---|---|
| Fabric-Kompatibilität | ++ | Ausschließlich dokumentierte Features: `jars`, `depends`, `provides`, `breaks`, `environment`. |
| Mixin | ++ | Config pro Payload-Mod; nicht geladen ⇒ nicht registriert ⇒ nie validiert. |
| Access Widener | ++ | Ein AW pro Payload-Mod, von Loom korrekt remappt. |
| Java | ++ | `depends.java` wird vom Solver ausgewertet; nicht gewählte Payloads werden nie definiert. |
| Mappings | ++ | Ein Loom-Build pro Payload. |
| Performance | + | Loader-eigene Extraktion mit Hash-Cache; kein eigener Mechanismus. Kaltstart einmalig ~20–60 ms pro Payload-JAR, danach Cache-Hit. |
| Wartbarkeit | ++ | Keine Loader-Interna, kein Bytecode-Engineering. |
| Aufwand | o | Der Aufwand liegt vollständig in der Build-Toolchain (Metadaten-Generierung, Assembler, Validator) — also an der Stelle, an der Fehler zur Build-Zeit auffallen. |
| Fehleranfälligkeit | + | Fehlerquelle ist der Generator, nicht der Spielerrechner. |
| Debugging | ++ | Ein ClassLoader, normale Stacktraces, `.fabric/processedMods` enthält die echten JARs zum Inspizieren. |
| IDE | ++ | Ein Gradle-Modul pro MC-Version = Standard-Loom-Setup, das IntelliJ nativ versteht. |
| Mod-Kompatibilität | + | Container ist eine normale Mod. Payloads erscheinen als genestete Mods (ModMenu-Kinder). |
| Inter-Mod-Kommunikation | + | Common-API im Container ist über alle Versionen binärstabil ⇒ Drittmods kompilieren einmal dagegen. |
| Zukunft | ++ | Neue MC-Version = neues Payload; nichts Bestehendes wird angefasst. |
| Risiko | o | Eine tragende Annahme (5.1/2). Mit Conformance-Test und Rückfallpfad beherrschbar. |

**Stärkster Ansatz.**

## 6.6 Ansatz F — Build-Time-Codegenerierung + Runtime-Dispatcher

**Idee:** Ein Generator erzeugt aus Annotationen eine Dispatcher-Klasse (`switch` über MC-Version), die die
richtige Adapter-Klasse instanziiert.

Das löst kein einziges der harten Probleme (Mixin, AW, Deskriptoren, Classfile-Versionen), ist aber als
*Komfortschicht* wertvoll: Es eliminiert Boilerplate und macht Payload-Metadaten aus Code ableitbar.
**Wird als Teilkomponente übernommen** (`fabricmultiloader-processor`, Kapitel 19.7, 23.5).

## 6.7 Ansatz G — Kombination

Die reale Lösung ist eine Kombination aus **E** (Kern: Isolation und Auswahl), **F** (Komfort: Metadaten- und
Entrypoint-Generierung) und dem *Bootstrap-Gedanken* aus **A** (Komfort: Lifecycle, Diagnose, Determinismus).
Ansätze B, C, D werden nicht verwendet.

## 6.8 Ansatz H — Geprüfte Alternativen, die ebenfalls verworfen wurden

| Alternative | Verworfen weil |
|---|---|
| **Zwei-Datei-Lösung** (Bootstrap-Mod lädt versionsspezifische Mods aus dem Internet nach) | Verstößt gegen G1 (eine Datei) und gegen Plattform-Richtlinien (Modrinth/CurseForge verbieten Nachladen von Code). Zusätzlich Security-Desaster. |
| **Loader-Plugin/Custom `GameProvider`** | Fabric hat keine öffentliche Plugin-Schnittstelle vor dem `ModDiscoverer`. Ein eigener `GameProvider` würde die Startsequenz ersetzen und wäre mit jedem anderen Werkzeug (Prism, ServerPacks, Loader-Updates) inkompatibel. |
| **Source-Preprocessor als Pflicht** (Stonecutter-/ReplayMod-Stil, `//#if MC>=12100`) | Löst Quellcode-Duplikate elegant, ändert aber nichts an Verpackung, Mixin-Isolation oder AW. Als *optionale* Ergänzung kompatibel (Kapitel 24.8), nicht als Pflichtbestandteil: Ein Preprocessor macht Quellcode für IDEs und Contributors schlechter lesbar und ist damit ein G2-Risiko. |
| **Ein Payload pro MC-Version als separate Root-JAR im Unterordner `mods/<mcversion>/`** | Funktioniert (Loader ≥ 0.15 unterstützt versionierte Mod-Ordner), erfordert aber, dass der Spieler mehrere Dateien in die richtigen Unterordner legt ⇒ G1 verletzt. |
| **Bytecode-Rewriting zur Laufzeit zur API-Angleichung** | Nicht-Ziel N4. |

---

# 7. Finale Architekturentscheidung

## 7.1 Die Entscheidung

> **FabricMultiLoader implementiert Ansatz G mit Ansatz E als Kern:**
> Die Universal-JAR ist eine gewöhnliche Fabric-Mod („**Container**“), die pro unterstützter
> Minecraft-Versionsspanne eine vollständige, separat gebaute und remappte Fabric-Mod („**Payload**“) per
> Jar-in-Jar enthält. Die Auswahl des Payloads trifft der **Fabric-Loader-eigene Dependency-Solver** anhand
> generierter, zur Build-Zeit als disjunkt bewiesener `depends`-Constraints. FabricMultiLoader selbst liefert
> die Runtime-Library (Lifecycle, Diagnose, Common-API), das Containerformat und die Build-Toolchain — aber
> **keinen** eigenen ClassLoader, **keine** Laufzeit-Bytecode-Transformation und **keine** Reflection auf
> Loader-Interna.

## 7.2 Die fünf Invarianten

Diese Invarianten sind normativ. Jede Implementierungsentscheidung muss sie erhalten; der Validator prüft sie.

* **I1 — Ein ClassLoader.** Alle Klassen des Containers, aller Payloads, der Runtime und von Minecraft werden
  vom `KnotClassLoader` definiert. FabricMultiLoader erzeugt niemals einen ClassLoader.
* **I2 — Genau ein aktives Payload.** Zur Laufzeit ist für einen Container genau ein Payload geladen. Garantiert
  durch: Build-Zeit-Disjunktheitsbeweis, `provides`-Alias-Exklusivität, wechselseitige `breaks`-Deklarationen,
  Runtime-Assertion mit Fehlercode `OMNI-2003`.
* **I3 — Der Container berührt Minecraft nicht.** Keine Referenz auf `net/minecraft/**`, `com/mojang/**`,
  `net/fabricmc/fabric/api/**` in Container-Klassen. Keine Mixins, kein Access Widener, keine `assets/`- oder
  `data/`-Einträge im Container.
* **I4 — Payloads sind vollständig und autark.** Jedes Payload enthält alle für seine MC-Version nötigen
  Klassen, Mixin-Configs, Refmaps, Access Widener, Ressourcen und versionsspezifischen Bibliotheken. Ein Payload
  ist ohne Container in einem Dev-Run funktionsfähig (Dev-Fallback, 9.7) und als Slim-Jar publizierbar.
* **I5 — Alle Metadaten sind generiert.** `fabric.mod.json` (Container und Payloads), `META-INF/omni-container.json`
  und `omni/payload.json` werden ausschließlich vom Gradle-Plugin erzeugt. Handgeschriebene Varianten sind ein
  Build-Fehler (`OMNI-1021`).

## 7.3 Warum diese Entscheidung die Zielvorgabe erfüllt

| Ursprüngliche Zielvorgabe | Erfüllung |
|---|---|
| „Eine JAR, viele MC-Versionen“ | Ja, buchstäblich eine Datei. |
| „Erkennt beim Start die Umgebung“ | Ja — die Erkennung passiert in Phase 2.3c des Loaders (Solver) und wird in `preLaunch` von der Runtime verifiziert und berichtet. Die Erkennung ist damit *früher* als in der ursprünglichen Idee, was sie erst korrekt macht. |
| „Nutzt ausschließlich die passende Implementierung“ | Ja, stärker als gefordert: die übrigen Implementierungen sind nicht einmal auf dem Classpath. |
| „Mixins, Ressourcen, Integrationen versionsspezifisch“ | Ja, jeweils nativ über die Payload-Mod-Metadaten. |
| „Größere JAR akzeptabel“ | Genutzt: keine Deduplizierung, dafür vollständige Isolation. |

Die einzige Abweichung von der ursprünglichen Vorstellung: **Der Dispatcher wählt nicht selbst Klassen aus,
sondern der Loader wählt Mods aus.** Das ist keine Einschränkung, sondern die Voraussetzung dafür, dass Mixins
und Access Widener überhaupt funktionieren (Kapitel 5.3.2, 5.4.2).

---

# 8. Runtime Architecture

## 8.1 Komponenten zur Laufzeit

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  JVM  ·  System-ClassLoader                                                   │
│  ├── net.fabricmc.loader.**            (Fabric Loader, Knot, ModSolver)       │
│  └── org.spongepowered.asm.**          (Mixin)                                │
└───────────────────────────────────────────────────────────────────────────────┘
                                    │  erzeugt
                                    ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│  KnotClassLoader   (Transformer: AccessWidener → Mixin)                       │
│                                                                               │
│  ┌─ Mod: minecraft ───────────────┐  ┌─ Mod: fabric-api (+ Module) ─────────┐ │
│  │  net.minecraft.**              │  │  net.fabricmc.fabric.api.**          │ │
│  └────────────────────────────────┘  └──────────────────────────────────────┘ │
│                                                                               │
│  ┌─ Mod: fabricmultiloader  (genestet, dedupliziert, Java 8) ───────────────┐ │
│  │  dev.fabricmultiloader.format.**     Manifest, SemVer, Predicates        │ │
│  │  dev.fabricmultiloader.api.**        Common-API (SPI für Modautoren)     │ │
│  │  dev.fabricmultiloader.runtime.**    Bootstrap, Resolver, Diagnostics    │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ Mod: examplemod  (Container = die Universal-JAR) ───────────────────────┐ │
│  │  com.example.common.**   plattformneutraler Modcode + öffentliche ModAPI │ │
│  │  META-INF/omni-container.json                                           │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ Mod: examplemod-mc1214  (genestet, GENAU EINES aktiv) ─────────────────┐ │
│  │  com.example.mc1214.**            Adapter, Mixins, Registrierung        │ │
│  │  examplemod-mc1214.mixins.json / .client.mixins.json                    │ │
│  │  examplemod-mc1214-refmap.json                                          │ │
│  │  examplemod-mc1214.accesswidener                                        │ │
│  │  assets/examplemod/**  data/examplemod/**   (common ⊕ version, gemergt)  │ │
│  │  omni/payload.json                                                      │ │
│  │  META-INF/jars/cloth-config-15.0.140.jar   (versionsspez. Bibliothek)   │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─ NICHT geladen: examplemod-mc1201.jar, examplemod-mc1211.jar ───────────┐ │
│  │  liegen unangetastet als ZIP-Einträge im Container. Nie entpackt, nie   │ │
│  │  geöffnet, nie verifiziert, nicht auf dem Classpath.                    │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────┘
```

## 8.2 Abhängigkeitsrichtungen (Compile- und Runtime)

```
fabricmultiloader-format   ──────────────┐            (Java 8, keine Abhängigkeiten)
        ▲                                │
        │                                ▼
fabricmultiloader-api ◄────── fabricmultiloader-runtime ──compileOnly──► fabric-loader
        ▲                                ▲
        │                                │ liest
   mod:common                     META-INF/omni-container.json
        ▲                                │
        │ compile+dev-runtime            │
   mod:versions/mc-X ──────► Loom(MC, yarn, fabric-api) │
        │                                              │
        └────────── erzeugt Payload ───────────────────►┘
```

* `format` hat **keine** Abhängigkeiten (auch nicht Gson) — eigener minimaler JSON-Parser (Kapitel 11.7),
  damit es sowohl im Gradle-Build als auch in der Runtime ohne Shading nutzbar ist.
* `api` hängt nur von `format` ab (für `MinecraftVersion`, `VersionRange`).
* `runtime` hängt von `format` + `api` ab und deklariert `fabric-loader` als `compileOnly`.
* Modseitig: `common` sieht nur `api` (+ `format` transitiv). `versions/mc-X` sieht `api`, `common`, sowie über
  Loom Minecraft und Fabric API.

## 8.3 Objektmodell zur Laufzeit

```
RuntimeRegistry (Singleton, in fabricmultiloader)
 ├─ Map<String /*containerModId*/, ContainerRuntime>
 │
 └─ ContainerRuntime
     ├─ ContainerManifest      (aus META-INF/omni-container.json, immutable)
     ├─ Environment            (MC-, Loader-, API-, Java-Version, EnvType, dev?)
     ├─ ResolutionReport       (pro Payload: matched / rejected + Grund)
     ├─ PayloadDescriptor      (der aktive Payload-Eintrag)
     ├─ Platform               (Instanz aus platformFactory des Payloads)
     ├─ ModContextImpl         (an den Modcode übergeben)
     └─ LifecycleState         (DISCOVERED → RESOLVED → PLATFORM_READY →
                                COMMON_INIT → SIDE_INIT → RUNNING | FAILED)
```

`RuntimeRegistry` unterstützt mehrere Container gleichzeitig: Zwei verschiedene Universal-Mods im selben Spiel
sind unabhängige `ContainerRuntime`-Instanzen. Die Runtime-Mod selbst ist dank Loader-Deduplizierung nur einmal
vorhanden (5.2.1).

## 8.4 Thread- und Zustandsmodell

* Alle Bootstrap-Schritte laufen auf dem Loader-Thread (Main), synchron, in `preLaunch` und in den
  Initializer-Phasen. Kein eigener Thread, kein Executor.
* `RuntimeRegistry` verwendet `ConcurrentHashMap` und `computeIfAbsent`, um gegen ungewöhnliche
  Entrypoint-Reihenfolgen robust zu sein; jede Zustandsübergang-Methode ist idempotent und protokolliert
  Doppelaufrufe auf `DEBUG`.
* Zustandsübergänge sind vorwärtsgerichtet; ein Rückschritt ist ein Programmierfehler (`OMNI-4001`).

---

# 9. Bootstrap Sequence

## 9.1 Gesamtablauf

```
Fabric Loader ModDiscoverer
  liest fabric.mod.json von: Container, Runtime, allen Payloads          [nur JSON]
        │
        ▼
Fabric Loader ModSolver
  wählt: Container (mandatory) + Runtime + GENAU EIN Payload             [SAT]
        │
        ▼
Loader: ausgewählte JiJ-Mods extrahieren, Classpath erweitern,
        Access Widener mergen, Mixin-Configs registrieren
        │
        ▼
preLaunch-Phase  (topologisch: fabricmultiloader → examplemod → examplemod-mc1214)
        │
        ├─► [1] RuntimeBootstrap (aus Mod fabricmultiloader, kein Entrypoint —
        │        statisch beim ersten Zugriff initialisiert)
        │        · Environment ermitteln
        │        · alle Container entdecken (Scan geladener Mods nach Manifest)
        │
        ├─► [2] ContainerPreLaunch  (Entrypoint des Containers)
        │        · Manifest laden, validieren, Schemaversion prüfen
        │        · ResolutionReport berechnen (Selbstprüfung gegen Environment)
        │        · genau-ein-Payload-Assertion  → sonst OMNI-2003/2004/2005 + Abbruch
        │        · Integritätsprüfung SHA-256 des aktiven Payloads (optional, Default an)
        │        · Startbanner + Diagnosebericht schreiben
        │
        └─► [3] PayloadPreLaunch  (Entrypoint des Payloads)
                 · platformFactory instanziieren  → Platform
                 · Platform#onPreLaunch(PreLaunchContext)
                 · LifecycleState = PLATFORM_READY
        │
        ▼
Minecraft-Klassen werden geladen  → Mixins des aktiven Payloads greifen
        │
        ▼
main-Phase  →  PayloadMain
                 · CommonBootstrap: UniversalMod#onInitialize(ModContext)  [Common]
                 · Platform#onInitialize(ModContext)                       [Version]
                 · LifecycleState = COMMON_INIT
        │
        ▼
client-Phase → PayloadClient           server-Phase → PayloadServer
   · UniversalClientMod#onInitializeClient   · UniversalServerMod#onInitializeServer
   · Platform#onInitializeClient             · Platform#onInitializeServer
        │
        ▼
LifecycleState = RUNNING;  Events#gameStarted feuert beim ersten Server-/Client-Tick
```

## 9.2 Exakter Startpunkt und erste geladene Klasse

* **Erste FabricMultiLoader-Klasse überhaupt:**
  `dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch`, geladen vom `KnotClassLoader`, wenn
  `EntrypointUtils.invoke("preLaunch", …)` den Container-Entrypoint auflöst.
  Ihr statischer Initializer ist leer; sie ruft in `onPreLaunch()` als erstes
  `RuntimeBootstrap.get()` auf, was die eigentliche Initialisierung anstößt.
* Vor diesem Zeitpunkt wird **kein** FabricMultiLoader-Code ausgeführt. Alles Frühere ist deklarativ (JSON).
* Eine Ausnahme existiert nur, wenn ein Payload das optionale `ConditionalMixinPlugin` nutzt
  (Kapitel 16.6): Dann wird `dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin` bereits in
  Phase 2.4/`select()` geladen — also **vor** `preLaunch`. Diese Klasse ist deshalb bewusst so geschrieben,
  dass sie nur `format`-Klassen und `FabricLoader`-API benutzt und **niemals** `RuntimeBootstrap` anstößt.
  Der Validator prüft diese Isolation (`OMNI-1035`).

## 9.3 Kompilationsziel des Bootstraps

| Eigenschaft | Wert |
|---|---|
| Bytecode-Ziel | `--release 8` (Classfile-Major 52) |
| Erlaubte Abhängigkeiten | JDK 8 API, `dev.fabricmultiloader.format.**`, `dev.fabricmultiloader.api.**`, `net.fabricmc.loader.api.**`, `net.fabricmc.api.EnvType` |
| Verbotene Abhängigkeiten | alles unter `net.minecraft`, `com.mojang`, `net.fabricmc.fabric.api`, `org.spongepowered`, `net.fabricmc.loader.impl` |
| Fabric-Loader-Compile-Version | `net.fabricmc:fabric-loader:0.14.0` (`compileOnly`) — die niedrigste unterstützte; damit ist ausgeschlossen, dass versehentlich neuere API verwendet wird |
| Genutzte Loader-API | `FabricLoader.getInstance()`, `getModContainer(String)`, `getAllMods()`, `isModLoaded(String)`, `getEnvironmentType()`, `isDevelopmentEnvironment()`, `getGameDir()`, `getConfigDir()`, `getObjectShare()`, `ModContainer#getMetadata()`, `ModContainer#findPath(String)`, `ModMetadata#getId()/getVersion()/getName()`, `Version#getFriendlyString()` |
| Logging | `java.util.logging` ist verboten; Ausgabe über `System.out`/`System.err`? **Nein** — siehe 9.8: SLF4J-über-Reflection mit Fallback |

## 9.4 Environment Detection

```java
package dev.fabricmultiloader.runtime.env;

public final class EnvironmentDetector {

    public static Environment detect() {
        FabricLoader loader = FabricLoader.getInstance();

        SemVer minecraft = loader.getModContainer("minecraft")
                .map(c -> SemVer.parseLenient(c.getMetadata().getVersion().getFriendlyString()))
                .orElseThrow(() -> new OmniException(ErrorCode.OMNI_2010,
                        "Minecraft mod container not present — unsupported launch setup."));

        SemVer fabricLoader = loader.getModContainer("fabricloader")
                .map(c -> SemVer.parseLenient(c.getMetadata().getVersion().getFriendlyString()))
                .orElse(SemVer.UNKNOWN);

        SemVer fabricApi = firstPresent(loader, "fabric-api", "fabric");   // 'fabric' = Alias von fabric-api

        int javaMajor = JavaVersions.currentMajor();                       // 8, 17, 21, …

        Side side = loader.getEnvironmentType() == EnvType.CLIENT ? Side.CLIENT : Side.SERVER;

        return new Environment(minecraft, fabricLoader, fabricApi, javaMajor, side,
                loader.isDevelopmentEnvironment(), loadedModVersions(loader));
    }
}
```

**Erkennungsdetails:**

| Größe | Quelle | Besonderheiten |
|---|---|---|
| Minecraft-Version | Mod-Container `minecraft` | Fabric normalisiert bereits auf SemVer-Form: `1.20.1`, `1.21.4`, Snapshots als `1.21.5-alpha.24.45.a`, Pre-Releases als `1.21.4-rc.1`. `parseLenient` akzeptiert außerdem zweistellige Schemata wie `26.2` (→ `26.2.0`) für künftige Mojang-Versionierung. |
| Fabric Loader | Mod-Container `fabricloader` | Immer vorhanden. |
| Fabric API | Mod-Container `fabric-api`, alternativ Alias `fabric` | Fehlt legitim, wenn die Mod Fabric API nicht braucht ⇒ `Optional`. Achtung: Einzelmodul-Installationen (nur `fabric-networking-api-v1`) liefern kein `fabric-api`; deshalb prüft der Resolver zusätzlich pro Payload deklarierte Modul-IDs (Kapitel 12.4). |
| Java | `Runtime.version()` ab 9, sonst `System.getProperty("java.specification.version")` mit `1.8`-Sonderfall | In Java-8-Bytecode kompiliert, daher Reflection-frei über die Property. |
| Side | `loader.getEnvironmentType()` | **Physische** Seite (Client-JAR vs. Server-JAR), nicht die logische. |
| Dev | `loader.isDevelopmentEnvironment()` | Steuert Dev-Fallback (9.7) und Namespace-Erwartungen. |
| Geladene Mods | `loader.getAllMods()` | Für `requires.mods`-Prüfungen und Diagnosebericht. |

## 9.5 Container-Discovery

```java
for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
    Path manifest = mod.findPath("META-INF/omni-container.json").orElse(null);
    if (manifest == null) continue;
    ContainerManifest m = ManifestReader.read(manifest, mod.getMetadata().getId());
    registry.register(new ContainerRuntime(m, environment, mod));
}
```

* `findPath` ist Loader-≥0.12-API und liefert einen `Path` in einem `ZipFileSystem` oder im Verzeichnis
  (Dev). Kein manuelles ZIP-Handling, kein Zip-Slip-Risiko.
* Der Scan ist O(Anzahl Mods) mit einem Datei-Lookup pro Mod; gemessen < 3 ms bei 300 Mods (NF-01).
* Die Mod-ID aus dem Manifest muss mit der ID des tragenden `ModContainer` übereinstimmen, sonst `OMNI-2012`
  („Manifest gehört zu einer anderen Mod — JAR wurde manipuliert oder falsch zusammengebaut“).

## 9.6 Selbstprüfung: genau ein Payload

```java
List<PayloadDescriptor> loaded  = new ArrayList<>();
List<Rejection>        rejected = new ArrayList<>();

for (PayloadDescriptor p : manifest.payloads()) {
    if (FabricLoader.getInstance().isModLoaded(p.modId())) {
        loaded.add(p);
    } else {
        rejected.add(PayloadMatcher.explain(p, environment));   // liefert konkreten Grund
    }
}

switch (loaded.size()) {
    case 1  -> activate(loaded.get(0));
    case 0  -> fail(ErrorCode.OMNI_2003, DiagnosticReport.noMatchingPayload(manifest, environment, rejected));
    default -> fail(ErrorCode.OMNI_2004, DiagnosticReport.ambiguousPayloads(manifest, loaded));
}
```

`PayloadMatcher.explain` bewertet die deklarierten Constraints des Payloads **selbst** erneut gegen das
erkannte Environment und liefert damit den *fachlichen* Grund („Fabric API 0.110.0 < 0.114.0 erforderlich“),
nicht nur „Mod nicht geladen“. Das ist der Unterschied zwischen einer Loader-Meldung und einer brauchbaren
Fehlermeldung (Kapitel 29).

Der Fall `default` (mehrere Payloads geladen) ist durch Build-Zeit-Disjunktheit und `provides`-Exklusivität
ausgeschlossen; er wird trotzdem geprüft, weil eine manipulierte oder von Hand gemergte JAR ihn erzeugen kann.

## 9.7 Dev-Fallback (Payload läuft ohne Container)

Im Loom-Dev-Run eines Version-Moduls (`./gradlew :versions:mc-1.21.4:runClient`) existiert der Container nicht.
Damit der Dev-Loop funktioniert, ist jedes Payload autark (Invariante I4):

```
PayloadPreLaunch
  ├─ Container-Mod (aus omni/payload.json: containerModId) geladen?
  │    ja  → normaler Pfad, Manifest des Containers ist Autorität
  │    nein→ DEV-FALLBACK:
  │           · nur zulässig wenn FabricLoader#isDevelopmentEnvironment()
  │             ODER System-Property fabricmultiloader.slim=true (Slim-Jar-Modus)
  │           · omni/payload.json enthält eine eingebettete Kopie von
  │             container.modId/modVersion/entrypoints  → daraus wird ein
  │             synthetischer ContainerManifest gebaut (1 Payload: dieses)
  │           · Warnung OMNI-2100 auf INFO-Level: "running standalone payload"
  └─ weiter wie normal
```

Damit gilt: derselbe Code, derselbe Lifecycle, dieselbe API — im Dev-Run wie in der Universal-JAR wie im
Slim-Jar. Kein zweiter Codepfad im Modcode.

## 9.8 Logging

* Die Runtime benutzt **SLF4J**, falls verfügbar, sonst `System.err`. Ermittlung einmalig, reflektiv,
  Java-8-kompatibel:

```java
final class Log {
    private static final Object SLF4J = tryCreate("dev.fabricmultiloader");   // null wenn nicht vorhanden
    static void info (String msg) { emit("INFO",  msg, null); }
    static void warn (String msg, Throwable t) { emit("WARN", msg, t); }
    static void error(String msg, Throwable t) { emit("ERROR", msg, t); }
    // emit(): reflektiver Aufruf von org.slf4j.Logger#info/warn/error,
    //         Fallback: System.err.println("[FabricMultiLoader/LEVEL] " + msg)
}
```

Begründung: SLF4J ist auf MC ≥ 1.17 garantiert vorhanden, auf 1.16.5 nicht. Ein harter SLF4J-Compile wäre auf
1.16.5 ein `NoClassDefFoundError` im Bootstrap — genau der Fehler, den wir vermeiden wollen. Der reflektive
Zugriff kostet einmalig ~0,3 ms.

* **Standard-Startausgabe** (Level INFO, eine Zeile pro Container):

```
[FabricMultiLoader] examplemod 2.0.0 → payload 'mc1214' (examplemod-mc1214 2.0.0+mc1.21.4)
                    mc=1.21.4 loader=0.16.9 fabric-api=0.114.0 java=21 side=CLIENT
```

* **Debug-Modus** `-Dfabricmultiloader.debug=true`: vollständiger `ResolutionReport` (alle Payloads mit
  Match/Reject-Begründung), Manifest-Dump, Timing pro Bootstrap-Phase, Pfad des extrahierten Payloads.

## 9.9 Fehlerbehandlung im Bootstrap — vollständige Fallmatrix

| Fall | Erkennung | Code | Verhalten |
|---|---|---|---|
| Minecraft-Version nicht in der Union der Payload-Ranges | Fabric-Solver (Container-`depends.minecraft`) | — | Loader zeigt eigene Fehler-GUI mit den erlaubten Bereichen. Container-Code läuft nicht. |
| MC unterstützt, aber kein Payload wählbar (Fabric API zu alt, Java zu alt, Fremdmod fehlt, Client-only-Payload auf Server) | `ContainerPreLaunch` | `OMNI-2003` | Diagnosebericht (Kapitel 29.2), Abbruch (`strict=true`) bzw. Warnung + Deaktivierung (`strict=false`). |
| Mehrere Payloads geladen | `ContainerPreLaunch` | `OMNI-2004` | Abbruch, Bericht listet die Kollision. |
| Manifest fehlt/nicht parsbar | `ManifestReader` | `OMNI-2001` | Abbruch: „Container beschädigt — bitte neu herunterladen“, mit SHA-256 der JAR. |
| Manifest-Schemaversion > unterstützt | `ManifestReader` | `OMNI-2002` | Abbruch: „FabricMultiLoader ≥ X erforderlich“ + Downloadlink. |
| Manifest verlangt neuere Runtime (`minRuntime`) | `ContainerPreLaunch` | `OMNI-2002` | wie oben. |
| Manifest-Mod-ID ≠ tragende Mod-ID | `ContainerPreLaunch` | `OMNI-2012` | Abbruch: JAR manipuliert/falsch gebaut. |
| SHA-256 des aktiven Payloads weicht ab | `IntegrityChecker` | `OMNI-2013` | Abbruch (abschaltbar über `-Dfabricmultiloader.verify=false`); Meldung nennt Soll/Ist. |
| `platformFactory`-Klasse fehlt | `PlatformLoader` | `OMNI-2020` | Abbruch mit FQCN, Payload-ID und Hinweis auf beschädigtes Payload. |
| `platformFactory` wirft | `PlatformLoader` | `OMNI-2021` | Abbruch, Ursache als `cause` durchgereicht, Bericht angehängt. |
| Common-Entrypoint-Klasse fehlt/wirft | `CommonBootstrap` | `OMNI-2030/2031` | Abbruch mit Klassenname und Phase. |
| Java-Version zu alt für den Container selbst | `depends.java` des Containers | — | Loader-Fehler-GUI. |
| Java-Version zu alt für alle Payloads, aber ausreichend für Container | `ContainerPreLaunch` | `OMNI-2003` | Diagnosebericht nennt exakt die erforderliche Java-Version pro Payload. |
| Doppelter Container derselben Mod-ID (zwei Universal-JARs im Ordner) | Loader (mandatory, at-most-one) | — | Loader-Fehler „duplicate mod“. Standardverhalten, gut verständlich. |
| Runtime-Mod fehlt (JiJ entfernt) | Container-`depends.fabricmultiloader` | — | Loader-Fehler „missing dependency fabricmultiloader“. |

## 9.10 Verhalten bei unbekannten und künftigen Versionen

* Eine MC-Version außerhalb aller Ranges ⇒ Loader-Meldung (kontrolliert, s. o.).
* Eine MC-Version *innerhalb* einer offenen Range (`>=1.21.4`) ⇒ Payload wird geladen. Offene obere Grenzen
  sind erlaubt, aber der Validator warnt (`OMNI-1050`), weil sie unvermeidlich irgendwann brechen; das Template
  verwendet standardmäßig geschlossene Ranges bis zur nächsten Minor-Version.
* Der Container schreibt bei erfolgreichem Start `<gameDir>/.fabricmultiloader/<modid>-last-launch.json` mit
  Environment und gewähltem Payload. Diese Datei ist die erste Anlaufstelle im Supportfall
  (Kapitel 30.4) und wird bei jedem Start atomar überschrieben (Temp-Datei + `ATOMIC_MOVE`).

---

Weiter mit [Kapitel 10–12 — Omni-Containerformat, Metadata-Schema, Version Resolver](part-03-container-format.md).
