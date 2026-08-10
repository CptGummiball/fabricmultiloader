# 45. Reality Check

Vollständige, ungefilterte Einordnung aller Anforderungen. Kategorien:

* **A — Sicher machbar**: Nutzt ausschließlich dokumentierte Fabric-/JVM-Mechanismen, verifizierbar.
* **B — Machbar mit definierter Speziallösung**: Braucht einen Mechanismus, der in diesem Dokument festgelegt ist.
* **C — Mit Einschränkungen machbar**: Funktioniert, hat aber eine benannte Restriktion oder einen Preis.
* **D — Nicht sinnvoll/nicht möglich**: Wegen Fabric-/JVM-Architektur ausgeschlossen; Ersatz benannt.

## 45.1 Kategorie A — Sicher machbar

| Anforderung | Warum sicher |
|---|---|
| Eine Datei, mehrere MC-Versionen im `mods`-Ordner | Container mit Union-`depends.minecraft` + JiJ-Payloads; ausschließlich dokumentierte Loader-Features |
| Versionsspezifische Mixin-Sets ohne Kreuzvalidierung | Config lebt in der Payload-Mod; nicht geladen ⇒ nie registriert ⇒ nie gelesen |
| Versionsspezifische Access Widener | „eine AW-Datei pro Mod“ gilt pro Payload |
| Versionsspezifische Refmaps | Ein Loom-Compile pro Payload, eindeutige Namen |
| Verschiedene Java-Hauptversionen (17/21/25) in einer Datei | `depends.java` + inaktive Payloads werden nie definiert |
| Verschiedene Fabric-API-Mindestversionen | Pro Payload deklariert |
| Verschiedene Mapping-Provider pro Version | Payloads teilen keinen Bytecode |
| Client-only-/Server-only-Payloads | `environment` in der Payload-Metadatei |
| Eine sichtbare Mod-ID und Version für Fabric und Dritte | Container trägt die primäre ID; Payloads sind ModMenu-Kinder |
| `FabricLoader.isModLoaded("examplemod")` funktioniert | Container ist eine normale Mod |
| Kein eigener ClassLoader, keine Laufzeit-Bytecode-Transformation | Invariante I1, Validator-geprüft |
| Keine Class-Identity-Probleme | Genau ein definierender ClassLoader |
| Versionsspezifische Bibliotheken pro Payload | Rekursives JiJ im Payload |
| Deterministische Payload-Auswahl | Build-Zeit-Disjunktheitsbeweis + `provides` + `breaks` + Runtime-Assertion |
| Reproduzierbare Builds | Feste Zeitstempel/Reihenfolge/Kompression, in CI verifiziert |
| Kontrollierte Fehlermeldung bei nicht unterstützter MC-Version | Loader-eigene Meldung mit Bereichsliste **oder** unser Bericht |
| Gradle-Multi-Project mit IntelliJ-Run-Configs pro Version | Standard-Loom-Setup pro Modul |
| Datagen pro Version | Loom-Standard |
| Unit-Tests des Common-Codes ohne Minecraft | Folge von „keine MC-Typen in der Common-API“ |
| Integrationstests derselben Datei auf mehreren echten Servern | Fabric-Installer + Probe-Mod |
| Modrinth/CurseForge als **eine** Datei mit mehreren Game-Version-Tags | Von beiden Plattformen nativ unterstützt |

## 45.2 Kategorie B — Machbar mit definierter Speziallösung

| Anforderung | Speziallösung | Ort |
|---|---|---|
| Gute Fehlermeldung, wenn MC passt, aber Fabric API/Java/Fremdmod nicht | Container deklariert **keine** harte `depends` auf den Payload-Alias; `preLaunch` wertet die Constraints selbst aus und erzeugt den Bericht | 11.8, 29.2 |
| „Genau ein Payload“ garantieren | Vierfach: Disjunktheitsbeweis (Build), `provides`-Alias (Solver), wechselseitige `breaks` (Solver), Runtime-Assertion `OMNI-2003/2004` | 12.5, 9.6 |
| Prioritäten (`catch-all` + Spezialfall) trotz nicht steuerbarem Solver | `DomainDisjunctifier`: exakte Mengensubtraktion zur Build-Zeit, Ergebnis geht in die generierten `depends` | 12.7 |
| Keine doppelten Resource-Packs derselben Mod | Container ohne `assets/`/`data/`; Ressourcen werden in **jedes** Payload gemergt; Icon unter `omni/` | 25.1, ADR-009 |
| Library-Version-Kollision zwischen mehreren Universal-Mods | Runtime als eigene genestete Mod + Loader-Deduplizierung; Major-Wechsel mit neuer Mod-ID und neuem Package | 13.4, 42.3, ADR-008 |
| Gemeinsame Access-Widener-Einträge | Merge im `named`-Namespace **vor** dem Loom-Remap | 17.3 |
| Dev-Loop ohne Container (`runClient1214`) | Payload ist autark; `omni/payload.json` enthält eine Kopie der Container-Identität und -Entrypoints ⇒ Dev-Fallback mit identischem Lifecycle | 9.7 |
| Konditionale Mixins innerhalb eines Payloads | Deklaratives `ConditionalMixinPlugin` mit `omni.conditions`-Block, isolationsgeprüft | 16.6 |
| Boilerplate-Freiheit bei Entrypoints | Annotation Processor erzeugt `omni/entrypoints.json`, das in das Manifest einfließt | 19.7 |
| Versionsabhängige Features im Common-Code ohne Versionsvergleiche | `Capability<T>`-System, im Manifest deklariert und validiert | 18.8 |
| Zugriff auf nicht abstrahierte MC-API aus Common-Code | `ServiceRegistry` mit modeigenen, minecraftfreien Interfaces | 18.7 |
| Netzwerkprotokoll-Unterschiede (1.20.1 rohes `PacketByteBuf` vs. 1.21.x `CustomPayload`) | `ChannelSpec` + `PayloadCodec` + `ByteSink`/`ByteSource`; Adapter normalisiert auch das Threading | 27 |
| Integritätsprüfung ausgelieferter Payloads | SHA-256 im Manifest, Streaming-Prüfung des aktiven Payloads beim Start | 39.3 |
| Aussagekräftige Crash-Reports | `Platform#installCrashContext` (versionsspezifische API im Payload) | 30.3 |
| Zukunftssicherung gegen Loader-Mod-Isolation | `commonPackaging = EMBEDDED`, implementiert und in CI mitgetestet | 41.3 |
| Zukunftssicherung gegen Bruch der tragenden Annahme | Nächtlicher Conformance-Test über 5 Loader-Versionen mit automatischem Issue; Rückfallpfad `buildSlimJars` | 32.4, 41.2 |

## 45.3 Kategorie C — Mit Einschränkungen machbar

| Anforderung | Einschränkung | Bewertung / Gegenmaßnahme |
|---|---|---|
| Größe der Universal-JAR | ≈ Summe der Payloads inkl. N-facher Ressourcenkopie; Beispielmod 4,82 MiB statt 1,63 MiB | Vom Auftraggeber ausdrücklich akzeptiert. Für einen Nutzer mit drei Instanzen ist es netto gleich viel. `buildSlimJars` als Ausweg. |
| Payload-Extraktion beim ersten Start | Der Loader extrahiert das ausgewählte Payload nach `.fabric/processedMods/` (~11 ms bei 1,5 MiB, STORED) | Nicht vermeidbar ohne eigenen ClassLoader (den wir ausschließen). Es ist derselbe Mechanismus wie bei jeder JiJ-Bibliothek; wir fügen keinen eigenen Cache hinzu. |
| `isModLoaded("examplemod")` bei aktivem `strict = false` | `true`, obwohl keine Funktionalität aktiv ist | Im Standardmodus nicht beobachtbar (Start bricht ab). `FabricMultiLoader.isActive()` als präzise Abfrage, in der Doku für Integratoren empfohlen. |
| Payloads erscheinen in der Modliste | Drei zusätzliche Einträge (`examplemod-mc1201` …) | Über `custom.modmenu.parent` + `badges: ["library"]` als Kinder der Hauptmod dargestellt; in ModMenu ein Eintrag mit Unterpunkten. Andere Modlisten-UIs zeigen sie flach. |
| Duplizierter Adaptercode zwischen Payloads | Ein inhaltsgleicher Mixin/Adapter existiert N-mal | Durch Handle-/Spec-Design klein gehalten (18–22 Klassen pro Payload vs. 142 Common-Klassen). Optionales `shared`-Sourceset für benachbarte Versionen. |
| Kotlin | Kotlin-Runtime darf nicht in den Container; `fabric-language-kotlin` ist MC-versionsgebunden | Kotlin im Payload, Java im Common; `fabric-language-kotlin` pro Payload als `omniMod`. Warnung `OMNI-1184`. |
| Java-8-Beschränkung von `format`/`api`/`runtime` | Keine Records, kein `var`, kein `sealed`, kein `List.of` in Framework-Modulen | Preis für 1.16.5-Reichweite. Erzwungen durch `--release 8`, kompensiert durch Builder-Pattern. Modcode ist davon nicht betroffen. |
| Modpacks, die JARs rekomprimieren | Payload-Hashes brechen ⇒ `OMNI-2013` | Meldung nennt `-Dfabricmultiloader.verify=false`. Bewusst kein stiller Fallback. |
| CurseForge-Java-Tag | Nur **ein** Java-Tag pro File möglich | Es wird die niedrigste Java-Version gesetzt; in der Beschreibung steht die vollständige Tabelle. |
| Client-Smoke-Tests in CI | GPU-lose Client-Starts sind historisch instabil | Server-Tests sind blockierend, Client-Smoke nicht; Xvfb + llvmpipe, dokumentierter Retry. |
| Quilt Loader | Eigener Resolver mit abweichender Behandlung optionaler genesteter Mods | Nicht getestet, nicht garantiert. Der Conformance-Harness kann Quilt später als weitere „Loader-Version“ aufnehmen. |
| Offene obere MC-Grenzen (`>=1.21.4`) | Bricht irgendwann zwangsläufig | Erlaubt, aber `OMNI-1050` warnt; Template nutzt geschlossene Bereiche. |
| AW-Einträge auf Fremdmod-Klassen | Fragile Kopplung an Fremdmod-Interna | Erlaubt nur mit expliziter Freigabe `allowForeignAccessWidener(...)`, sonst `OMNI-1122`. |
| Entfernen einer alten MC-Version | Kann `baselineJava` anheben ⇒ Common wird mit höherem Bytecode kompiliert | `removeMinecraftVersion` weist darauf hin und schlägt einen Changelog-Eintrag vor. |

## 45.4 Kategorie D — Nicht sinnvoll oder nicht möglich

| Ursprüngliche Vorstellung | Warum nicht | Was stattdessen gebaut wird |
|---|---|---|
| **Ein Runtime-Dispatcher wählt versionsspezifische Klassen aus einer gemeinsamen Klassenmenge** | Mixin-Configs und Access Widener werden vom Loader **vor** jedem Modcode registriert bzw. gemergt. Sponge Mixin löst Mixin-Klassen und ihre Targets eagerly auf. Ein Dispatcher, der später läuft, kann daran nichts ändern; ein 1.20.1-Mixin würde unter 1.21.4 beim Registrieren scheitern. | Die Auswahl passiert **früher** als in der ursprünglichen Idee — im Loader-Solver, vor jeder Klassenberührung. Der „Bootstrap“ bleibt als Lifecycle-Orchestrator und Diagnoseinstanz erhalten, nicht als Classloading-Mechanismus. |
| **Ein einzelnes Kompilat für alle MC-Versionen** | Bytecode referenziert Methoden über Name **und Deskriptor**. Intermediary garantiert Namensstabilität, nicht Signaturstabilität. `new Identifier(a,b)` → `Identifier.of(a,b)`, `PacketByteBuf` → `RegistryByteBuf`: solche Änderungen sind in einem Kompilat nicht auflösbar. | N Kompilate, eine Datei. Der gemeinsame Anteil (85–89 %) wird genau einmal kompiliert — im Container, minecraftfrei. |
| **Eigener ClassLoader für Payloads** | Umgeht die Knot-Transformerkette: Mixins und Access Widener wirken nicht. Minecraft-Typen müssten an den Parent delegiert werden, wodurch Registry-, Codec- und Reflection-Pfade von Minecraft die Payload-Klassen nicht finden. Doppelte Klassennamen in Stacktraces, unzuverlässige Breakpoints. | Genau ein ClassLoader (Knot). Isolation durch Nichtvorhandensein statt durch Loader-Grenzen. |
| **Payloads als Ressourcen nachträglich in den Classpath hängen** | `KnotClassLoader#addURL` ist keine öffentliche API und wechselt zwischen Loader-Versionen. Zum Zeitpunkt `preLaunch` sind Mixin-Configs registriert und der AW-Transformer gebaut — Nachreichen ist nicht vorgesehen. | Fabric-JiJ: Der Loader macht Extraktion und Classpath-Erweiterung selbst, zum richtigen Zeitpunkt. |
| **Multi-Release-JAR als Selektionsmechanismus** | Selektiert nach Java-, nicht nach Minecraft-Version (1.21.1 und 1.21.4 sind beide Java 21). Metadaten (`fabric.mod.json`, Mixin-Configs, Refmaps) sind nicht MR-fähig. Knots Ressourcen-Delegate garantiert keine MR-Semantik. | `depends.java` für die Java-Achse, `depends.minecraft` für die MC-Achse — zwei getrennte, jeweils korrekte Mechanismen. |
| **Eine gemeinsame Access-Widener-Datei für alle Versionen** | Loader akzeptiert genau eine AW-Datei pro Mod; sie ist mappinggebunden; Loom kann nur gegen eine Mappings-Version remappen; Member-Namen können differieren. | Ein AW pro Payload; gemeinsame Einträge werden vor dem Remap gemergt. |
| **Eine gemeinsame Mixin-Config mit Laufzeitfilterung** | `IMixinConfigPlugin#shouldApplyMixin` verhindert nur die Anwendung; `getMixins()` kann Einträge nicht zurückziehen; die Target-Auflösung passiert vorher. | Ein Mixin-Set pro Payload; `ConditionalMixinPlugin` nur für Feinsteuerung innerhalb einer Version. |
| **Vollständige, versionsneutrale Minecraft-API** | Rendering, Weltgenerierung, Codecs, Komponenten, DataFixer und Registry-Timing ändern sich zu tief und zu oft. Eine „vollständige“ Abstraktion wäre ein Dauerprojekt mit ständigem Rückstand. | Abstrahiert wird, was stabil ist (Lifecycle, einfache Registrierung, Commands, Networking-Nutzdaten, stabile Events, Ressourcen, Config, Diagnose). Für alles andere: `Services`, `Capabilities`, `unwrap`. Die Grenze ist dokumentiert, nicht verschwiegen. |
| **Core-Transformationen jenseits von Mixin** | Fabric hat keine öffentliche Transformer-API; Knots Kette ist nicht erweiterbar. | Mixin. Wo Mixin nicht reicht, ist die Mod auch ohne FabricMultiLoader blockiert. |
| **Mods laden, bevor der Loader Mixins bootstrapped** | Es existiert keine Fabric-Phase vor 2.4 für Modcode. | Kein Bedarf: Payload-Mixins werden in 2.4 registriert — genauso früh wie bei jeder normalen Mod. |
| **Kryptografische Signatur des Containers** | Ohne Schlüsselverteilung, Vertrauensanker und Widerruf ist eine Signatur semantisch wertlos (der Angreifer signiert selbst). | SHA-256 im Manifest + veröffentlichte Sidecar-Summen; `container.signatures` ist als Feld reserviert und für ein künftiges `omni/2` vorbereitet. |
| **Zwei-Datei-Lösung mit Nachladen aus dem Netz** | Verstößt gegen das Kernziel und gegen die Regeln von Modrinth/CurseForge; erhebliches Sicherheitsproblem. | Alles liegt in der einen Datei. |
| **Source-Preprocessor als Pflichtbestandteil** | Zweite, nicht typgeprüfte Sprache; schlechtere IDE-, Review- und Refactoring-Erfahrung; löst weder Mixin- noch AW- noch Verpackungsfragen. | `:common` + optionales `shared`-Sourceset + Adapter. Externer Preprocessor bleibt kombinierbar. |
| **Payload-Auswahl über eine eigene Prioritätsregel zur Laufzeit** | Die Auswahl trifft der Solver, bevor Modcode läuft. Das Solver-Optimierungsziel ist kein spezifizierter Tiebreak. | Build-Zeit-Disjunktheit (bewiesen) + Range-Subtraktion für Prioritäten. |

## 45.5 Das eine echte Restrisiko — explizit benannt

Die gesamte Architektur ruht auf **einer** nicht formal spezifizierten Loader-Eigenschaft:

> Ein genesteter Mod-Kandidat mit unerfüllbaren `depends`, auf den kein geladener Mod hart angewiesen ist, wird
> vom `ModSolver` nicht ausgewählt — statt einen harten Resolutionsfehler zu erzeugen.

Bewertung:

* **Wahrscheinlichkeit einer Änderung:** gering. Die Eigenschaft folgt direkt aus der Modellierung genesteter
  Kandidaten als optionale SAT-Variablen und ist die Grundlage dafür, dass JiJ-Bibliotheken mit engen
  MC-Bereichen im Ökosystem überhaupt funktionieren. Eine Änderung würde viele bestehende Mods brechen.
* **Erkennung:** nächtlicher Conformance-Test über fünf Loader-Versionen, mit automatischer Issue-Erstellung und
  Release-Blocker (`conformance.yml`). Ein neuer Loader wird getestet, bevor Nutzer betroffen sind.
* **Rückfallpfad 1 (ohne Codeänderung):** `buildSlimJars` + Veröffentlichung eines Files pro MC-Version. Das
  verliert G1 („eine Datei“), erhält aber alles andere — Common-Code, Adapter-Architektur, Toolchain,
  Validierung, Tests bleiben unverändert.
* **Rückfallpfad 2:** `commonPackaging = EMBEDDED` + Payloads als Root-Mods in `mods/<mcversion>/`-Unterordnern
  (Loader ≥ 0.15). Eine Datei pro Version, aber vom Nutzer korrekt einzuordnen.
* **Dokumentation:** `docs/internals/loader-assumption.md` ist die zentrale Seite für künftige Maintainer und
  enthält Herleitung, Testliste, Bruchszenarien und Rückfallpfade.

Es gibt in diesem Entwurf **keine zweite** Annahme dieser Tragweite. Alle übrigen Mechanismen (JiJ,
`depends`-Auswertung, `provides`-Exklusivität, `environment`, Mixin-Registrierung pro Mod, AW pro Mod,
Ein-ClassLoader-Modell, `findPath`) sind dokumentierte, breit genutzte Fabric-Features.

---

# 46. Final Architecture Summary

## 46.1 Das System in zwölf Sätzen

1. Eine Universal-JAR ist eine gewöhnliche Fabric-Mod („Container“) mit der echten Mod-ID des Entwicklers.
2. Der Container enthält keinen Minecraft-berührenden Code, keine Mixins, keinen Access Widener und keine
   `assets/`/`data/`-Einträge — er ist auf jeder unterstützten Version ladbar.
3. Er trägt den plattformneutralen Common-Code des Mods (inklusive der öffentlichen Mod-API für Drittmods),
   kompiliert auf das kleinste Java-Level der Matrix.
4. Er enthält per Jar-in-Jar die Library-Mod `fabricmultiloader` und **je unterstützter MC-Versionsspanne eine
   vollständige, separat gebaute und remappte Fabric-Mod** („Payload“).
5. Jedes Payload deklariert in seiner eigenen `fabric.mod.json` seine Constraints
   (`minecraft`, `java`, `fabricloader`, `fabric-api`, Fremdmods, `environment`), seine Mixin-Configs, sein
   Refmap und seinen Access Widener.
6. Der Fabric-Loader-eigene SAT-Solver wählt daraus **genau eines** aus — vor jedem Class-Load, vor der
   Mixin-Registrierung, vor dem Access-Widener-Merge.
7. Nicht ausgewählte Payloads werden nie extrahiert, nie geöffnet, nie dem Classpath hinzugefügt und nie von der
   JVM verifiziert; damit sind Mixin-, Refmap-, AW- und Classfile-Version-Isolation vollständig.
8. Die Auswahl ist deterministisch, weil die Constraint-Domänen zur **Build-Zeit** als paarweise disjunkt bewiesen
   werden (Range-Subtraktion für Prioritäten) und weil `provides`-Alias und wechselseitige `breaks` Exklusivität
   erzwingen.
9. `fabricmultiloader` verifiziert in `preLaunch`, dass genau ein Payload aktiv ist, prüft dessen SHA-256, führt
   die Lifecycle-Kette Container → Payload → Common aus und erzeugt andernfalls einen vollständigen
   Diagnosebericht mit Ist-Zustand, Constraint-Auswertung und Handlungsanweisung.
10. Modcode wird gegen eine minecraftfreie Common-API geschrieben (`ModContext`, `Registries`, `Networking`,
    `Commands`, `Events`, `Services`, `Capabilities`); versionsspezifische Divergenz lebt in schlanken
    Payload-Adaptern, mit `unwrap` und `Services` als typisierten Escape Hatches.
11. Die Gradle-Toolchain (vier Plugins, eine TOML-Matrix als Wahrheitsquelle) generiert alle Metadaten, merged
    Ressourcen und Access Widener deterministisch, assembliert reproduzierbar und prüft das fertige Artefakt gegen
    34 Regeln.
12. Es gibt keinen eigenen ClassLoader, keine Laufzeit-Bytecode-Transformation und keinen Zugriff auf
    Loader-Interna — der riskante Teil des Problems wird nicht gelöst, sondern **vermieden**.

## 46.2 Das Zielprojekt, wie es am Ende funktioniert

```
UniversalExampleMod                                   ./gradlew buildUniversalJar
├── common/                    → com/example/common/**            im Container, Java 17, 0 MC-Referenzen
├── versions/mc-1.20.1/        → examplemod-mc1201.jar   MC 1.20.1  Java 17  Classfile 61
├── versions/mc-1.21.1/        → examplemod-mc1211.jar   MC 1.21.x  Java 21  Classfile 65
├── versions/mc-1.21.4/        → examplemod-mc1214.jar   MC 1.21.4  Java 21  Classfile 65
└── (künftig) versions/mc-26.1 → examplemod-mc261.jar    MC 26.1    Java 25  Classfile 69
                                        │
                                        ▼
                    build/libs/universal-example-mod-1.0.0-universal.jar
```

Der Nutzer legt **dieselbe Datei** in seinen `mods`-Ordner:

| Umgebung | Ergebnis |
|---|---|
| Minecraft 1.20.1 + Fabric 0.14.21 + Java 17 | Payload `mc1201` aktiv; `mc1211`, `mc1214`, `mc261` nie berührt |
| Minecraft 1.21.1 + Fabric 0.15.11 + Java 21 | Payload `mc1211` aktiv |
| Minecraft 1.21.4 + Fabric 0.16.9 + Java 21 | Payload `mc1214` aktiv |
| Minecraft 26.1 + Fabric 0.17.0 + Java 25 | Payload `mc261` aktiv — auf einer JVM, die die Java-17-Payloads ebenso ignoriert |
| Minecraft 1.19.2 | Fabric zeigt die erlaubten Bereiche; kein `NoClassDefFoundError`, kein Mixin-Stacktrace |
| Minecraft 1.21.4 mit zu alter Fabric API | `OMNI-2003` mit `fabric-api >=0.114.0 — REJECTED: 0.110.0 installed` und Downloadlink |

Im Log steht bei erfolgreichem Start genau eine Zeile:

```
[FabricMultiLoader] examplemod 2.0.0 → payload 'mc1214' (examplemod-mc1214 2.0.0+mc1.21.4)
                    mc=1.21.4 loader=0.16.9 fabric-api=0.114.0 java=21 side=CLIENT
```

## 46.3 Warum diese Architektur langfristig trägt

| Eigenschaft | Begründung |
|---|---|
| **Der Loader macht die schwierige Arbeit** | Auswahl, Extraktion, Classpath, Mixin-Registrierung, AW-Merge, Deduplizierung — alles bestehende, breit genutzte Fabric-Mechanismen. FabricMultiLoader ergänzt Metadaten, Determinismus, Diagnose und Toolchain. |
| **Fehler entstehen zur Build-Zeit, nicht beim Spieler** | 34 Validator-Regeln, Classfile-Scan, Referenz-Scan, Disjunktheitsbeweis, Golden-File-Tests, Reproduzierbarkeitsprüfung. |
| **Additive Erweiterbarkeit** | Eine neue MC-Version ist ein neues Modul plus ein TOML-Block; bestehende Payloads werden nicht angefasst. Ein neuer Java-Sprung (21 → 25) ist ein Feld. |
| **Ehrliche Grenzen** | Nicht abstrahierbare Bereiche sind benannt (Frage 25) und haben einen definierten Weg (`Services`, `Capabilities`, `unwrap`) statt einer leckenden Abstraktion. |
| **Ein einziges Restrisiko, aktiv überwacht** | Nächtliche Conformance-Tests über fünf Loader-Versionen, automatische Issue-Erstellung, zwei vorbereitete Rückfallpfade, eigene Doku-Seite. |
| **Testbarkeit als Nebenprodukt** | Weil Common-Code keine MC-Typen kennt, ist der Großteil der Modlogik in Millisekunden ohne Minecraft testbar. |
| **Stabile Mod-API über MC-Versionen** | Die öffentliche API einer Mod liegt im Container und ist damit ein Kompilat für alle Versionen — ein Vorteil, den klassische Multi-JAR-Veröffentlichung nicht hat. |
| **Keine Wetten auf Interna** | Kein Reflection auf `net.fabricmc.loader.impl`, kein eigener ClassLoader, kein eigener Transformer, keine Loader-Patches. Loader-Updates sind damit unkritisch. |

## 46.4 Abnahmekriterium des Projekts

Das Projekt ist erfolgreich, wenn folgender Ablauf reproduzierbar funktioniert:

```bash
git clone https://github.com/fabricmultiloader/fabricmultiloader-template my-mod
cd my-mod && ./bootstrap.sh          # Mod-ID, Name, Package setzen
./gradlew runClient1214              # Dev-Loop
./gradlew test                       # Common-Logik ohne Minecraft
./gradlew buildUniversalJar          # eine Datei
./gradlew validateUniversalJar       # 34 Regeln, 0 Fehler
./gradlew integrationTest            # dieselbe Datei auf 1.20.1 / 1.21.1 / 1.21.4 gebootet
./gradlew publishUniversal           # ein File, vier Game-Version-Tags
```

und der Spieler mit dieser einen Datei auf jeder unterstützten Version spielt, ohne zu wissen, dass es Payloads
gibt — und bei einer nicht unterstützten Version eine Meldung erhält, die ihm sagt, was er tun soll.

---

**Ende des technischen Konzepts.** Zurück zum [Navigationsindex](../../DESIGN.md).
