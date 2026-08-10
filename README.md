# FabricMultiLoader

**Eine Mod-JAR. Viele Minecraft-Versionen. Ein Download für den Spieler.**

FabricMultiLoader ist eine Runtime-Library plus Gradle-Toolchain für Minecraft Fabric, mit der Modentwickler
**eine einzige universelle JAR** veröffentlichen können, die auf mehreren Minecraft-Versionen läuft — statt pro
Version eine eigene Datei.

```
examplemod-2.0.0-universal.jar
        ├── läuft auf Minecraft 1.20.1  (Java 17)
        ├── läuft auf Minecraft 1.21 – 1.21.1  (Java 21)
        ├── läuft auf Minecraft 1.21.4  (Java 21)
        └── läuft auf Minecraft 26.1+  (Java 25)
```

> **Status: Konzeptphase.** Das vollständige technische Design ist fertig und implementierungsbereit
> (46 Kapitel, ~8.600 Zeilen). Code existiert noch nicht — die Implementierung folgt dem
> [Implementierungsplan](docs/design/part-12-implementation-plan.md).

---

## Das Problem

Eine Mod, die 1.20.1, 1.21.1 und 1.21.4 unterstützt, bedeutet heute: drei Builds, drei Uploads, drei
Downloadeinträge — und Spieler, die die falsche Datei erwischen. Der naheliegende Ausweg („eine JAR, die zur
Laufzeit erkennt, wo sie läuft") scheitert an vier harten Eigenschaften von Fabric und der JVM:

| Hindernis | Warum es den naiven Ansatz bricht |
|---|---|
| **Mixins** | Fabric registriert alle Mixin-Configs, bevor Modcode läuft. Sponge Mixin löst dabei jede Mixin-Klasse und ihre Targets eager per ASM auf — ein 1.20.1-Mixin crasht unter 1.21.4 beim Registrieren, egal welcher Dispatcher später aufwacht. |
| **Access Widener** | Fabric akzeptiert genau *eine* AW-Datei pro Mod, mappinggebunden. Eine versionsübergreifende Datei ist nicht mappingkorrekt herstellbar. |
| **Bytecode-Deskriptoren** | `new Identifier(a,b)` → `Identifier.of(a,b)`, `PacketByteBuf` → `RegistryByteBuf`: Bytecode referenziert Methoden über Name **und Deskriptor**. Ein einzelnes Kompilat kann nicht beide auflösen. |
| **Java-Versionen** | 1.20.1 braucht Java 17, 1.21.x Java 21, 26.1+ Java 25. Drei Classfile-Versionen (61/65/69) in einer Datei — auf der ältesten JVM geöffnet. |

## Die Lösung

Die Universal-JAR ist **kein exotisches Containerformat mit eigenem ClassLoader**, sondern eine ganz normale
Fabric-Mod, die per Jar-in-Jar mehrere vollständige Fabric-Mods enthält:

```
examplemod-2.0.0-universal.jar          ← Container-Mod, mod id "examplemod"
├─ fabric.mod.json                      ← depends.minecraft = Union aller Payload-Ranges
├─ META-INF/omni-container.json         ← Omni-Manifest (Wahrheitsquelle für Runtime + Tooling)
├─ com/example/common/**.class          ← plattformneutraler Common-Code, keine MC-Referenzen
└─ META-INF/jars/
   ├─ fabricmultiloader-runtime-1.0.0.jar   ← die Library selbst, eigene Fabric-Mod, Java 8
   ├─ examplemod-mc1201.jar             ← depends { minecraft "1.20.1",     java ">=17" }
   ├─ examplemod-mc1211.jar             ← depends { minecraft ">=1.21 <1.21.2", java ">=21" }
   └─ examplemod-mc1214.jar             ← depends { minecraft ">=1.21.4 <1.21.5", java ">=21" }
```

**Die Auswahl trifft der Fabric Loader selbst** — sein SAT-Solver, bevor irgendeine Klasse geladen, ein Mixin
registriert oder ein Access Widener gemergt wird. Nicht ausgewählte Payloads werden **nie extrahiert, nie
geöffnet, nie dem Classpath hinzugefügt und nie von der JVM verifiziert**.

Damit lösen sich alle vier Hindernisse, ohne dass FabricMultiLoader sie selbst lösen muss:

| Problem | Lösung |
|---|---|
| Mixins fremder Versionen | Config steht in der `fabric.mod.json` des Payloads → nicht geladen = nie registriert = nie gelesen |
| Access Widener | Payload *ist* eine eigene Mod → hat seinen eigenen AW, von Loom korrekt remappt |
| Classfile-Versionen | Java-21-Payload wird auf Java 17 vom Solver verworfen → kein `UnsupportedClassVersionError` |
| Refmaps / Mappings | Ein Loom-Build pro Payload, eigenes Refmap, eigene Yarn-Version |

**Kein eigener ClassLoader. Keine Laufzeit-Bytecode-Transformation. Kein Reflection auf Loader-Interna.**

## Was der Entwickler schreibt

```java
// common/ — genau einmal kompiliert, kein Minecraft-Import
@UniversalEntrypoint
public final class ExampleMod implements UniversalMod {
    @Override public void onInitialize(ModContext ctx) {
        ItemHandle ruby = ctx.registries().item(Id.of("examplemod", "ruby"),
                ItemSpec.builder().maxCount(64).rarity(Rarity.UNCOMMON).build());

        ctx.events().playerJoin(p -> p.sendMessage("Willkommen!"));
        ctx.capability(Capabilities.COMPONENTS).ifPresent(c -> /* nur ab 1.20.5 */ …);
    }
}
```

```java
// versions/mc-1.21.4/ — der versionsspezifische Anteil, ~20 Klassen
public final class Platform1214 extends AbstractPlatform {
    @Override public void onInitialize(ModContext ctx) {
        ctx.services().register(OreGenService.class, new OreGenService1214());
    }
}
```

Erfahrungswert aus der Referenz-Beispielmod: **142 gemeinsame Klassen gegenüber 18–22 Klassen pro Version** —
also 85–89 % des Codes versionsneutral, einmal kompiliert und ohne Minecraft in Millisekunden testbar.

## Der Workflow

```bash
git clone https://github.com/CptGummiball/fabricmultiloader-template my-mod
cd my-mod && ./bootstrap.sh          # Mod-ID, Name, Package setzen
./gradlew runClient1214              # normaler Loom-Dev-Loop, eine MC-Version
./gradlew test                       # Common-Logik, ohne Minecraft, ohne Loom
./gradlew buildUniversalJar          # -> build/libs/my-mod-1.0.0-universal.jar
./gradlew validateUniversalJar       # 34 Prüfregeln, Classfile-Scan, Disjunktheitsbeweis
./gradlew integrationTest            # dieselbe Datei auf 1.20.1 / 1.21.1 / 1.21.4 gebootet
./gradlew addMinecraftVersion --mc=26.1 --java=25 --copy-from=mc1214
```

Eine neue Minecraft-Version kostet: ein TOML-Block, ein Verzeichnis, ein 4-zeiliges `build.gradle.kts` — und
danach nur noch die tatsächlichen API-Anpassungen.

## Wenn eine Version nicht unterstützt wird

Kein `NoClassDefFoundError`, kein Mixin-Stacktrace:

```
OMNI-2003  FabricMultiLoader could not start Universal Example Mod

  Detected environment
    Minecraft      1.21.4        Fabric API  0.110.0   ← too old
    Fabric Loader  0.16.9        Java        21

    payload  mc1214   Minecraft >=1.21.4 <1.21.5    ok
                      fabric-api >=0.114.0          — REJECTED: 0.110.0 installed

  Fix:
    · update Fabric API to 0.114.0 or newer for Minecraft 1.21.4
      https://modrinth.com/mod/fabric-api/versions?g=1.21.4
```

---

## Dokumentation

Der Einstieg ist **[DESIGN.md](DESIGN.md)** — Executive Summary, Ziele, Nicht-Ziele, Anforderungen und der
Navigationsindex über alle Teildokumente.

| Kapitel | Dokument |
|---|---|
| 1–4 Summary, Ziele, Nicht-Ziele, Anforderungen | [DESIGN.md](DESIGN.md) |
| 5 Fabric/JVM-Machbarkeitsanalyse | [part-01-feasibility.md](docs/design/part-01-feasibility.md) |
| 6–9 Architekturvarianten, Entscheidung, Runtime, Bootstrap | [part-02-architecture.md](docs/design/part-02-architecture.md) |
| 10–12 Containerformat, Metadata-Schema, Version Resolver | [part-03-container-format.md](docs/design/part-03-container-format.md) |
| 13–15 Classloading, Java-Kompatibilität, Mappings | [part-04-classloading.md](docs/design/part-04-classloading.md) |
| 16–17 Mixin-Architektur, Access Widener | [part-05-mixins-aw.md](docs/design/part-05-mixins-aw.md) |
| 18–19, 26–28 Common API, Adapter, Networking, Registries | [part-06-api.md](docs/design/part-06-api.md) |
| 20–25 Gradle-Plugin, DSL, Struktur, Pipeline, Ressourcen | [part-07-gradle.md](docs/design/part-07-gradle.md) |
| 29–33 Fehler, Diagnose, Validierung, Tests, CI/CD | [part-08-quality.md](docs/design/part-08-quality.md) |
| 34–38 Distribution, Beispielmod, Migration, Doku | [part-09-project.md](docs/design/part-09-project.md) |
| 39–42 Security, Performance, Grenzen, Versionierung | [part-10-nfr.md](docs/design/part-10-nfr.md) |
| 43 Architecture Decision Records (11 ADRs) | [part-11-adrs.md](docs/design/part-11-adrs.md) |
| 44 Implementierungsplan (21 Schritte, ~87 PT) | [part-12-implementation-plan.md](docs/design/part-12-implementation-plan.md) |
| 25 harte technische Fragen, beantwortet | [part-13-hard-questions.md](docs/design/part-13-hard-questions.md) |
| 45–46 Reality Check, finale Zusammenfassung | [part-14-reality-check.md](docs/design/part-14-reality-check.md) |

## Geplante Module

| Modul | Java | Aufgabe |
|---|---|---|
| `fabricmultiloader-format` | 8 | Manifest-Modell, JSON-Parser, Versionsalgebra, Resolver, Fehlercodes — geteilt zwischen Runtime und Build |
| `fabricmultiloader-api` | 8 | Entwickler-SPI: `ModContext`, `Platform`, `Registries`, `Networking`, `Commands`, `Events`, `Services`, `Capabilities` |
| `fabricmultiloader-runtime` | 8 | Eigene Fabric-Mod: Bootstrap, Lifecycle, Diagnose, versionsstabile Adapter |
| `fabricmultiloader-processor` | 8 | Annotation Processor für `@UniversalEntrypoint` |
| `fabricmultiloader-gradle` | 17 | Vier Gradle-Plugins: `settings`, `common`, `version`, `universal` |
| `fabricmultiloader-testing` | 17 | `FakeModContext`, JAR-Fixtures, Loader-Conformance-Harness, Server-Harness |
| `example` | — | `UniversalExampleMod` für 1.20.1 / 1.21.1 / 1.21.4 |

## Roadmap

| Meilenstein | Inhalt | Aufwand |
|---|---|---|
| M0 | Repository-Gerüst, Konventions-Plugins, CI-Skelett | 1 T |
| M1 | `format`: JSON, Versionsalgebra, Manifest, Resolver | 11 T |
| M2 | `api`: vollständige Entwickler-SPI | 4 T |
| M3 | `runtime`: Bootstrap, Context, Lifecycle, Mixin-Plugin | 12 T |
| **M4** | **`testing` + Loader-Conformance-Gate** | **7 T** |
| M5 | Gradle-Plugin: Matrix, Pipeline, Assembler, Validator | 30 T |
| M6 | Beispielmod, drei Versionen, Abnahme | 11 T |
| M7 | Dokumentation, Template, Release 1.0.0 | 11 T |

## Die tragende Annahme — offen benannt

Die gesamte Architektur ruht auf **einer** nicht formal spezifizierten Fabric-Loader-Eigenschaft:

> Ein genesteter Mod-Kandidat mit unerfüllbaren `depends`, auf den kein geladener Mod hart angewiesen ist, wird
> vom `ModSolver` **nicht ausgewählt** — statt einen harten Resolutionsfehler zu erzeugen.

Das ist das Verhalten der Loader-Reihen 0.14.x–0.17.x und der Grund, warum JiJ-Bibliotheken mit engen
MC-Bereichen im Ökosystem funktionieren. Weil das Fundament darauf ruht:

* wird sie in [Kapitel 5](docs/design/part-01-feasibility.md) aus der Loader-Startsequenz hergeleitet,
* durch einen **nächtlichen Conformance-Test über fünf Loader-Versionen** abgesichert, der bei Fehlschlag
  automatisch ein Issue öffnet und Releases blockiert,
* steht das Conformance-Gate im Implementierungsplan **vor** dem 30-Tage-Gradle-Plugin, nicht danach,
* und es gibt zwei vorbereitete Rückfallpfade ([Kapitel 41](docs/design/part-10-nfr.md)).

Eine zweite Annahme dieser Tragweite existiert im Entwurf nicht.

---

## Lizenz

**Kein Open Source.** Der Code und die Dokumentation sind öffentlich lesbar, aber urheberrechtlich geschützt:
Kopieren, Weiterverbreiten, Forken zur Veröffentlichung und Weiterverwendung in anderen Projekten sind ohne
schriftliche Genehmigung **nicht** erlaubt. Details: [LICENSE](LICENSE).

> **Hinweis zur Zielsetzung:** Diese Lizenz ist eine bewusste Übergangslösung für die Entwicklungsphase. Sie ist
> mit dem Endzweck des Projekts unvereinbar — die Runtime wird per Jar-in-Jar in *fremde* Mod-JARs eingebettet,
> und das *ist* Weiterverbreitung. Vor der ersten produktiven Nutzung durch Dritte muss deshalb auf eine
> permissive Lizenz umgestellt werden (Absicht: Apache-2.0, siehe [LICENSE](LICENSE) Abschnitt 4).

Minecraft ist eine Marke von Mojang Synergies AB. Fabric, Fabric Loader, Fabric API, Yarn und Fabric Loom sind
Projekte der FabricMC-Organisation. Dieses Projekt steht in keiner Verbindung zu ihnen.

## Kontakt

Fragen, Feedback, Genehmigungsanfragen: [Issues](https://github.com/CptGummiball/fabricmultiloader/issues) ·
treeman1992@outlook.de
