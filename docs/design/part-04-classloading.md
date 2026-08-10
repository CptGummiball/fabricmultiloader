# 13. Classloading Strategy

## 13.1 Grundsatz

> **FabricMultiLoader erzeugt keinen ClassLoader, verändert keinen ClassLoader und greift auf keine
> ClassLoader-Interna zu. Es gibt genau einen relevanten ClassLoader: `KnotClassLoader`.**

Das ist eine harte Architekturgrenze (Invariante I1, ADR-002). Jeder Pull Request, der einen `ClassLoader`
instanziiert, `URLClassLoader` benutzt, `addURL` reflektiert oder `Thread#setContextClassLoader` verändert, wird
abgelehnt. Der Build erzwingt es: Der Validator scannt die Runtime-Klassen auf Referenzen auf
`java/lang/ClassLoader`-Konstruktoren, `java/net/URLClassLoader` und `net/fabricmc/loader/impl/**`
(Regel `OMNI-1036`).

## 13.2 Wer definiert welche Klasse

| Klassen | Definierender Loader | Transformiert |
|---|---|---|
| `java.**`, `jdk.**`, `sun.**` | Bootstrap/Platform-Loader | nein |
| `net.fabricmc.loader.**`, `org.spongepowered.asm.**`, Sat4j, Tiny-Remapper | System-ClassLoader (App-Classpath) | nein |
| `net.minecraft.**`, `com.mojang.**` | `KnotClassLoader` | ja (AW → Mixin) |
| `net.fabricmc.fabric.api.**` (Fabric API als Mod) | `KnotClassLoader` | ja |
| `dev.fabricmultiloader.**` (Runtime-Mod) | `KnotClassLoader` | ja (technisch; faktisch kein Mixin zielt darauf) |
| `com.example.common.**` (Container) | `KnotClassLoader` | ja (technisch) |
| `com.example.mc1214.**` (aktives Payload) | `KnotClassLoader` | ja — hier greifen die Payload-Mixins und der Access Widener |
| Klassen nicht ausgewählter Payloads | **niemand** | — |

Konsequenz: Es gibt genau eine `com.example.common.ExampleModApi`, genau eine `net.minecraft.item.Item`, genau
eine `dev.fabricmultiloader.api.Platform`. **Class-Identity-Probleme sind strukturell ausgeschlossen**, weil
kein Typ zweimal definiert wird.

## 13.3 Warum Payload-Klassen Container-Klassen sehen (und umgekehrt)

Der Fabric Loader fügt **alle** ausgewählten Mod-JARs demselben `KnotClassLoader` als Classpath-Einträge hinzu
(Phase 2.3f). Es existiert keine Per-Mod-Isolation und kein Modul-System zwischen Mods. Daraus folgt direkt:

* `com.example.mc1214.Platform1214` (Payload) kann `com.example.common.ExampleMod` (Container) referenzieren,
  implementieren und instanziieren — es ist derselbe Namensraum.
* `com.example.common.ExampleMod` (Container) kann `dev.fabricmultiloader.api.ModContext` (Runtime-Mod)
  referenzieren.
* Payload-Klassen können Minecraft- und Fabric-API-Klassen referenzieren — normal, wie in jeder Mod.
* Container- und Runtime-Klassen dürfen Minecraft **nicht** referenzieren — nicht weil es technisch scheitern
  würde, sondern weil sie auf allen unterstützten Versionen laden müssen und Minecraft-Signaturen dort
  differieren (I3, Validator `OMNI-1042`).

**Ladereihenfolge ist irrelevant für die Sichtbarkeit**, nur für die Ausführungsreihenfolge. Klassen werden
lazy beim ersten aktiven Gebrauch definiert; ob das Payload-JAR vor oder nach dem Container-JAR im
Classpath steht, ändert nichts, weil die FQCN-Räume disjunkt sind (Validator `OMNI-1044`: Payload-Packages und
Common-Packages dürfen sich nicht überlappen).

## 13.4 Der einzige verbleibende Kollisionsfall — und seine Lösung

Zwei verschiedene Universal-Mods (`examplemod`, `othermod`) enthalten beide FabricMultiLoader-Klassen. Bei einem
klassischen „Fat-Jar mit eingebetteter Library“ würde der `KnotClassLoader` die erste gefundene
`dev.fabricmultiloader.runtime.Bootstrap` gewinnen lassen (First-Wins über Classpath-Reihenfolge) — die Version
wäre nichtdeterministisch, und eine ältere Library müsste ein neueres Manifest interpretieren.

**Lösung:** Die Library wird als **eigene genestete Fabric-Mod** ausgeliefert (`fabricmultiloader`, Kapitel 8.1).
Der Loader dedupliziert Mods nach ID und wählt die höchste Version, die alle Constraints erfüllt (5.2.1). Damit:

* existiert prozessweit genau **eine** Runtime, und zwar die neueste aller installierten Universal-Mods;
* ist die Auswahl **deterministisch** (höchste SemVer) statt classpath-abhängig;
* erzwingt jeder Container per `depends: {"fabricmultiloader": ">=1.0.0 <2.0.0"}`, dass die gewählte Runtime
  kompatibel ist. Eine zu neue Major-Version führt zu einer klaren Loader-Fehlermeldung statt zu
  `NoSuchMethodError`.

Für den hypothetischen Major-Wechsel gilt die Regel aus Kapitel 42.3: Major 2 erhält Mod-ID
`fabricmultiloader2` und Package `dev.fabricmultiloader.v2`, sodass 1.x und 2.x koexistieren können und keine
Mod zum Zwangsupdate gedrängt wird.

**Bewusst nicht gewählte Alternative:** Relocation (jarjar/shadow) pro Mod. Sie würde die Kollision auch lösen,
aber (a) jede Universal-JAR um eine eigene Kopie vergrößern, (b) die *öffentliche* API der Mod unbrauchbar
machen (`com.example.common.api.Handle` würde `dev.example.shadow.fabricmultiloader.api.ModContext`
referenzieren — Drittmods könnten nicht dagegen kompilieren), und (c) Debugging und Stacktraces verrauschen.

## 13.5 Ressourcen-Lookup

* Mod-Ressourcen werden über `ModContainer#findPath(String)` gelesen, nie über
  `Class#getResourceAsStream` oder `ClassLoader#getResource`. Begründung: Bei mehreren Universal-Mods im Spiel
  würde `getResource("META-INF/omni-container.json")` das erste beliebige Manifest liefern. `findPath` ist
  mod-gebunden und damit eindeutig.
* `findPath` liefert einen `Path` innerhalb eines vom Loader verwalteten `ZipFileSystem` (Produktion) oder
  eines Verzeichnisses (Dev). Der Pfad wird ausschließlich lesend und ausschließlich mit
  `Files.readAllBytes`/`Files.newInputStream` verwendet; es wird nie ein `FileSystem` selbst geöffnet oder
  geschlossen (das würde den Loader-eigenen zerstören).
* Minecraft-Ressourcen (`assets/`, `data/`) werden **nicht** von FabricMultiLoader gelesen; sie werden vom
  Fabric Resource Loader als Resource-Pack des Payloads registriert (Kapitel 25.2).

## 13.6 Instanziierung der Payload-Klasse

```java
package dev.fabricmultiloader.runtime.payload;

final class PlatformLoader {

    static Platform create(PayloadDescriptor payload, ModContext ctx) {
        String fqcn = payload.platformFactory();
        Class<?> raw;
        try {
            // Absichtlich der ClassLoader DIESER Klasse: es ist der KnotClassLoader,
            // der auch alle Payload-Klassen definiert. Kein TCCL, keine eigene Suche.
            raw = Class.forName(fqcn, false, PlatformLoader.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new OmniException(ErrorCode.OMNI_2020, Messages.platformFactoryMissing(payload, fqcn), e);
        }
        if (!PlatformFactory.class.isAssignableFrom(raw)) {
            throw new OmniException(ErrorCode.OMNI_2022, Messages.platformFactoryWrongType(payload, raw));
        }
        try {
            PlatformFactory factory = (PlatformFactory) raw.getDeclaredConstructor().newInstance();
            Platform platform = factory.create(ctx);
            if (platform == null) {
                throw new OmniException(ErrorCode.OMNI_2023, Messages.platformFactoryReturnedNull(payload));
            }
            return platform;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new OmniException(ErrorCode.OMNI_2021, Messages.platformFactoryFailed(payload, fqcn), e);
        }
    }
}
```

`Class.forName(fqcn, false, …)` mit `initialize = false` ist bewusst gewählt: Der statische Initializer der
Factory läuft erst bei der Instanziierung, sodass ein Typfehler (`OMNI-2022`) gemeldet wird, **bevor** fremder
Code läuft.

## 13.7 Was passiert im Dedicated-Server-Fall mit Client-Klassen

* Ein Payload mit `environment: "client"` wird auf einem dedizierten Server **gar nicht geladen** (Loader wertet
  `environment` vor dem Classloading aus). Wenn *alle* Payloads client-only sind, führt das auf dem Server dazu,
  dass der Container zwar lädt, aber kein Payload — die Runtime erkennt das und meldet `OMNI-2003` mit dem
  spezifischen Text „diese Mod ist eine Client-Mod“ statt einer generischen Meldung.
* Innerhalb eines universalen Payloads liegen Client-Klassen in einem eigenen Package
  (`com.example.mc1214.client.**`) und werden ausschließlich aus dem `client`-Entrypoint-Pfad referenziert.
  Klassenweise Trennung ist Pflicht: eine Klasse, die im Feldtyp `MinecraftClient` verwendet, darf auf dem
  Server nie geladen werden. Der Validator prüft das statisch (Regel `OMNI-1045`): Alle Klassen, die
  `net/minecraft/client/**` referenzieren, müssen unter einem als `clientOnly` deklarierten Package liegen, und
  keine Nicht-Client-Klasse darf sie referenzieren.

---

# 14. Java Compatibility

## 14.1 Das Problem in einem Satz

Eine Universal-JAR muss Bytecode für Java 17 (MC 1.18–1.20.4), Java 21 (MC 1.20.5–1.21.x) und Java 25
(MC 26.1+) gleichzeitig enthalten, während sie auf der jeweils **ältesten** dieser JVMs geöffnet und teilweise
ausgeführt wird.

## 14.2 Die Lösung

| Schicht | Ziel-Classfile | Wird geladen auf | Mechanismus |
|---|---|---|---|
| `fabricmultiloader-format/api/runtime/processor` | **52** (Java 8) | jeder unterstützten JVM | `--release 8` |
| Container-Common des Mods | `baselineJavaMajor` = Minimum der Matrix (Beispiel: **61**/Java 17) | jeder JVM, auf der die Mod startet | `--release <baseline>` |
| Payload 1.20.1 | 61 (Java 17) | nur Java ≥ 17 | `depends.java >=17` |
| Payload 1.21.1 | 65 (Java 21) | nur Java ≥ 21 | `depends.java >=21` |
| Payload 26.1 | **69 (Java 25)** | nur Java ≥ 25 | `depends.java >=25` |

Die JVM prüft die Classfile-Version in `defineClass`. Ein nicht ausgewähltes Payload wird nie extrahiert, nie
dem Classpath hinzugefügt und nie definiert — sein Bytecode ist für die JVM reiner ZIP-Inhalt. Damit gilt:

> **Ein Java-25-Payload in einer JAR, die auf einer Java-17-JVM läuft, kann keinen
> `UnsupportedClassVersionError` auslösen, weil keine seiner Klassen definiert wird.**

Das ist die vollständige Antwort auf Fragen 5, 21, 22 und 23.

## 14.3 Java-Version-Erkennung und `depends.java`

Fabric Loader stellt einen synthetischen Mod-Kandidaten `java` bereit, dessen Version die JVM-Version ist
(Major aus `Runtime.version().feature()` bzw. der `java.specification.version`-Property). Damit ist
`depends: {"java": ">=25"}` eine vom Loader ausgewertete, harte Solver-Klausel — genau wie `minecraft`.

Die Runtime-eigene Erkennung (für Diagnose) ist Java-8-kompatibel und reflektionsfrei:

```java
package dev.fabricmultiloader.format.version;

public final class JavaVersions {
    public static int currentMajor() {
        String v = System.getProperty("java.specification.version", "");
        if (v.startsWith("1.")) {                     // 1.8 → 8
            return parseIntSafe(v.substring(2), 8);
        }
        int dot = v.indexOf('.');                     // "25" oder "25.0.1"
        return parseIntSafe(dot < 0 ? v : v.substring(0, dot), 8);
    }

    /** Classfile-Major für eine Java-Hauptversion: 8→52, 17→61, 21→65, 25→69. */
    public static int classfileMajor(int javaMajor) { return javaMajor + 44; }

    /** Umkehrung; wirft für Werte < 45. */
    public static int javaMajorOf(int classfileMajor) { … }
}
```

Die Formel `classfileMajor = javaMajor + 44` gilt ab Java 1.1 (45) durchgehend und braucht keine Tabelle;
sie ist mit Testfällen für 8, 11, 17, 21, 25, 30 abgesichert und macht künftige Java-Versionen
konfigurationsfrei.

## 14.4 Classfile-Scan im Validator

`ValidateUniversalJarTask` liest von **jeder** `.class`-Datei in Container und Payloads die Bytes 4–7
(Minor/Major aus dem Classfile-Header) — kein ASM, keine Klassendefinition, ~200 MB/s.

| Regel | Prüfung | Reaktion |
|---|---|---|
| `OMNI-1040` | Jede Container-Klasse hat `major ≤ container.baselineJavaMajor` | Fehler, listet die ersten 20 Verstöße mit Pfad und Major |
| `OMNI-1041` | Jede Payload-Klasse hat `major == payload.classfileMajor` | Fehler |
| `OMNI-1046` | `payload.classfileMajor` passt zur Untergrenze von `requires.java` (`javaMajorOf(major) ≤ min(requires.java)`) | Fehler — verhindert genau den Fall „Java-25-Bytecode mit `depends.java >=21`“ |
| `OMNI-1047` | `container.baselineJavaMajor` == Minimum aller `min(requires.java)` | Fehler |
| `OMNI-1048` | Genestete Bibliotheken eines Payloads haben `major ≤ payload.classfileMajor` | Warnung (Bibliotheken sind oft konservativer, umgekehrt wäre es ein Fehler) |

Damit ist der häufigste denkbare Fehler eines Modentwicklers — „ich habe im Common-Modul versehentlich
`--release 21` stehen lassen, und jetzt startet meine Mod auf 1.20.1 nicht“ — ein Build-Fehler mit exakter
Dateiangabe statt eines Spielerabsturzes.

## 14.5 Toolchains im Build

```kotlin
// gradle/fabricmultiloader.toml steuert die Werte; hier die resultierende Konfiguration
// :common
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }   // Compiler-JDK
tasks.withType<JavaCompile> { options.release = 17 }                  // Ziel-Bytecode = baseline

// :versions:mc-1.20.1
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile> { options.release = 17 }

// :versions:mc-1.21.1
tasks.withType<JavaCompile> { options.release = 21 }

// :versions:mc-26.1
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
tasks.withType<JavaCompile> { options.release = 25 }
```

Regeln, die das Plugin erzwingt:

1. **`options.release` statt `sourceCompatibility`/`targetCompatibility`.** `--release` prüft zusätzlich die
   verwendete API gegen den Ziel-JDK-Stand und verhindert damit, dass Common-Code versehentlich
   `List.of(...)` (Java 9) oder `String.formatted` (Java 15) benutzt, obwohl `baselineJavaMajor = 8` gilt.
2. **Ein Toolchain-JDK ≥ dem höchsten Ziel** wird per `foojay-resolver` automatisch bereitgestellt; für
   `release = 25` ist mindestens JDK 25 nötig, weil `--release 25` von älteren Compilern nicht unterstützt wird.
   Fehlt es, gibt das Plugin die Meldung `OMNI-1090` mit dem konkreten `gradle/fabricmultiloader.toml`-Eintrag
   und dem Download-Hinweis aus.
3. **Loom-Run-Tasks** erhalten pro Version explizit `javaLauncher` aus der passenden Toolchain, damit
   `runClient1201` mit Java 17 und `runClient261` mit Java 25 startet — auch wenn Gradle selbst auf einem
   anderen JDK läuft.

## 14.6 Multi-Release-JARs

Verworfen (Begründung in Kapitel 5.5.3). Der Assembler schreibt **kein** `Multi-Release: true` und legt
**keine** `META-INF/versions/`-Einträge an; der Validator lehnt beides ab (`OMNI-1049`), weil es die
Auswahl-Semantik doppeln und mit der Payload-Auswahl in Konflikt geraten könnte.

## 14.7 Sprachfeatures und Bibliotheks-API

| Modul | Erlaubte Sprachversion | Begründung |
|---|---|---|
| `format`, `api`, `runtime`, `processor` | Java 8: keine `var`, keine Records, keine Switch-Expressions, keine sealed classes, keine `List.of` | müssen auf 1.16.5-JVMs laufen; Records würden zusätzlich die Binärkompatibilität der API an Java 16+ binden |
| Container-Common des Mods | `baselineJavaMajor` der Matrix — im Beispiel Java 17: Records, `var`, Switch-Expressions, Text-Blöcke, `sealed` erlaubt | vom Modentwickler frei wählbar; `--release` erzwingt Korrektheit |
| Payload `mc-26.1` | Java 25 | volle Freiheit |

Die Java-8-Beschränkung der Framework-Module ist der Preis dafür, dass FabricMultiLoader auch für
1.16.5-Mods brauchbar bleibt. Sie wird durch Codestil-Konventionen (Kapitel 40 des Contributor Guides) und
`--release 8` durchgesetzt, nicht durch Disziplin allein. Im API-Design wird sie kompensiert durch
Builder-Pattern statt Records und `Optional` statt `sealed`-Hierarchien.

## 14.8 Verhalten bei zu alter JVM

| Szenario | Ergebnis |
|---|---|
| JVM ist zu alt für **Minecraft** | Minecraft/Loader bricht selbst ab, bevor Mods geladen werden. Nicht unser Zuständigkeitsbereich. |
| JVM ≥ MC-Anforderung, aber zu alt für alle Payloads (unmöglich bei korrekter Matrix, möglich bei manueller Matrixpflege) | Container lädt (`depends.java` = Minimum), kein Payload wählbar ⇒ `OMNI-2003` mit Zeile „Java: 17 erkannt — Payload 'mc261' benötigt ≥ 25“. |
| JVM ist zu alt für den **Container** | `depends.java` des Containers scheitert ⇒ Loader-Fehler-GUI mit „requires Java 17 or later“. |
| Unbekannt neue JVM (z. B. Java 30) | Alle `>=`-Constraints erfüllt; das neueste Payload wird gewählt. Offene Obergrenzen bei `java` sind Absicht: eine neuere JVM ist praktisch immer abwärtskompatibel. |

---

# 15. Mapping Strategy

## 15.1 Grundsatz

Jedes Payload ist ein **eigenständiger Loom-Build** mit eigener Minecraft-Version, eigenen Mappings, eigenem
Refmap und eigenem Access-Widener-Remap. Payloads teilen **keinen Bytecode**. Daher gibt es kein
versionsübergreifendes Mapping-Problem — es gibt N unabhängige, jeweils in sich korrekte Mapping-Kontexte.

## 15.2 Namespace-Zustände im Lebenszyklus eines Payloads

```
Quellcode (versions/mc-1.21.4/src/main/java)
   Namespace: named (Yarn 1.21.4+build.8)
        │  javac + Mixin-Annotation-Processor
        ▼
build/classes  +  examplemod-mc1214-refmap.json (named → intermediary)
   Namespace: named
        │  Loom remapJar (tiny-remapper)
        ▼
build/libs/examplemod-mc1214.jar
   Namespace: intermediary   ← Klassen, Refmap-Ziele und AW-Datei sind remappt
        │  omniPayload-Task (Metadaten + Ressourcen-Merge, kein Remap)
        ▼
build/omni/payloads/examplemod-mc1214.jar
   Namespace: intermediary
        │  assembleUniversalJar (STORED-Einbettung)
        ▼
examplemod-2.0.0-universal.jar → META-INF/jars/examplemod-mc1214.jar
   Namespace: intermediary
        │  Produktionsstart: Loader extrahiert, kein Remap
        ▼  Dev-Start mit Universal-JAR: RuntimeModRemapper intermediary → named
Laufzeit
```

Der Container durchläuft **keinen** Remap-Schritt: Er enthält keine Minecraft-Referenzen, weshalb `remapJar` für
ihn nicht nur unnötig, sondern verboten ist (der Assembler ist ein reiner `Zip`-Task, kein Loom-Task).

## 15.3 Mapping-Provider pro Version frei wählbar

```toml
[versions.mc1201]
minecraft   = "1.20.1"
mappings    = "yarn:1.20.1+build.10"

[versions.mc1214]
minecraft   = "1.21.4"
mappings    = "yarn:1.21.4+build.8"

[versions.mc261]
minecraft   = "26.1"
mappings    = "mojang"            # Mojang Official Mappings, z. B. weil Yarn noch nicht fertig ist
```

Erlaubte Werte: `yarn:<build>`, `mojang`, `layered:<spec>` (durchgereicht an Looms
`loom.layered { … }`), `parchment:<version>` (Layer über Mojmap). Da Payloads keinen Bytecode teilen, ist eine
gemischte Matrix technisch unproblematisch. Der Validator prüft nur die *Konsistenz innerhalb* eines Payloads
(AW-Namespace, Refmap-Präsenz) — `OMNI-1080`.

Praktische Konsequenz für den Modentwickler: In `versions/mc-26.1/src/main/java` heißen Klassen dann
`net.minecraft.world.item.Item` (Mojmap) statt `net.minecraft.item.Item` (Yarn). Das ist zulässig, weil jedes
Version-Modul seinen eigenen Quellcode hat. Für den geteilten `shared`-Sourceset (Kapitel 24.8) muss der
Mapping-Provider hingegen über alle beteiligten Versionen identisch sein; der Validator erzwingt das
(`OMNI-1081`).

## 15.4 Intermediary-Stabilität — was garantiert ist und was nicht

| Garantie | Gilt | Konsequenz |
|---|---|---|
| Klassen-Intermediary-Name bleibt über Versionen stabil, solange die Klasse „dieselbe“ ist | ja | Ein Mixin-Target-Name bricht selten allein durch die Version. |
| Member-Intermediary-Name bleibt stabil | überwiegend | Neu eingeführte Member erhalten neue Nummern; umgezogene Member können neu nummeriert werden. |
| **Deskriptoren bleiben stabil** | **nein** | Der Hauptgrund für Payload-Trennung. Ein geänderter Parameter ⇒ anderer Deskriptor ⇒ Bytecode nicht auflösbar ⇒ `NoSuchMethodError`. |
| Klasse existiert in allen Versionen | nein | Neue/entfernte Klassen sind normal. |

Deshalb ist auch die naheliegende Idee „ich schreibe meinen Modcode direkt gegen Intermediary, dann läuft ein
Kompilat überall“ nicht tragfähig: Sie löst das Namensproblem, nicht das Signaturproblem. FabricMultiLoader
verwendet Intermediary nur als **Publikations-Namespace** — genau wie jede normale Fabric-Mod.

## 15.5 Refmap-Strategie

| Regel | Umsetzung |
|---|---|
| Ein Refmap pro Payload | Ergebnis des separaten Loom-Compiles; kein Merge. |
| Eindeutiger Refmap-Name über alle Payloads | Loom-Property `loom.mixin.defaultRefmapName = "<modid>-<payloadId>-refmap.json"`, vom Plugin gesetzt. Validator `OMNI-1030`. |
| Refmap muss existieren, wenn Mixins vorhanden sind | Validator `OMNI-1031`: Für jede Mixin-Config mit `refmap`-Feld muss die Datei im Payload liegen und valides JSON sein. |
| Refmap-Einträge müssen zu den Mixin-Klassen des Payloads gehören | Validator `OMNI-1032`: Jeder Top-Level-Key des Refmaps muss eine im Payload vorhandene Klasse sein. Fängt versehentlich mitgepackte Fremd-Refmaps. |
| Kein `refmap` bei leerer Mixin-Liste | Validator `OMNI-1033`: Warnung, wenn ein Refmap ohne zugehörige Config existiert (Aufräumhinweis). |
| Dev-Runtime | `MixinIntermediaryDevRemapper` des Loaders übernimmt named↔intermediary; keine eigene Logik. |

## 15.6 Access-Widener-Remap

* Quelle: `versions/mc-X/src/main/resources/<modid>-<payloadId>.accesswidener`, Namespace-Header `named`.
* Loom-Konfiguration: `loom.accessWidenerPath = file("src/main/resources/<modid>-<payloadId>.accesswidener")`
  (vom Plugin gesetzt). `remapJar` schreibt die Datei mit Header `intermediary` in das Payload.
* Der gemeinsame AW-Anteil aus `common/src/main/accesswidener/shared.accesswidener` wird **vor** dem Remap
  konkateniert (Kapitel 17.4), ist also ebenfalls in `named` formuliert und wird korrekt mit-remappt.
* Der Validator liest den Header des AW im fertigen Payload und vergleicht ihn mit
  `payload.mappings.namespace` (`OMNI-1082`). Ein `named`-Header im Release-Artefakt wäre ein
  Loom-Konfigurationsfehler und führt zur Laufzeit zu einem harten Loader-Abbruch — deshalb ist die Prüfung
  ein Fehler, keine Warnung.

## 15.7 Umgang mit Yarn-Umbenennungen im geteilten Quellcode

Wenn `shared`-Quellcode über mehrere Versionen kompiliert wird und Yarn eine Klasse umbenennt
(z. B. `ItemStack#getName` bleibt, aber `PlayerEntity` → `Player` in einer künftigen Yarn-Generation), gibt es
genau drei zulässige Reaktionen — der Preprocessor-Weg ist bewusst nicht dabei:

1. **Typalias über die Common-API**: Die betroffene Verwendung wird hinter eine Common-Schnittstelle gezogen
   (z. B. `PlayerRef`) und in jedem Payload separat implementiert. Bevorzugter Weg.
2. **Klasse aus `shared` in die Version-Module verschieben** (Duplikat mit je eigenem Import). Pragmatisch bei
   kleinen Klassen.
3. **Mapping-Layer pinnen**: `mappings = "layered:yarn:<älterer build>+patch"` — hält alte Namen künstlich
   stabil. Nur als Übergangslösung, mit Warnung `OMNI-1083`, weil es Yarn-Updates blockiert.

Die Dokumentationsseite `docs/mappings.md` beschreibt alle drei Wege mit Beispielen und einer
Entscheidungsmatrix.

---

Weiter mit [Kapitel 16–17 — Mixin-Architektur und Access Widener](part-05-mixins-aw.md).
