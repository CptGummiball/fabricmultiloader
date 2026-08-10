# Antworten auf die 25 harten technischen Fragen

Jede Antwort ist eine Festlegung, keine Option. Verweise zeigen auf die normative Stelle im Dokument.

---

### 1. Wie kann eine einzige JAR von Fabric Loader auf mehreren Minecraft-Versionen akzeptiert werden?

Indem der Container selbst minecraftunabhängig ist und in seiner `fabric.mod.json` die **Union** aller
unterstützten MC-Bereiche deklariert:

```json
"depends": { "minecraft": [">=1.20.1 <1.20.2", ">=1.21 <1.21.2", ">=1.21.4 <1.21.5"] }
```

Fabric-Version-Predicates in einem Array sind OR-verknüpft. Der Container enthält keinen Minecraft-berührenden
Code, keine Mixins und keinen Access Widener — er ist daher auf jeder dieser Versionen ladbar. Der
versionsabhängige Teil steckt in genesteten Payload-Mods (`META-INF/jars/*.jar`), von denen der Loader-eigene
SAT-Solver genau eine auswählt. (Kapitel 7.1, 11.8, 12.8)

---

### 2. Welche Klasse wird als allererste FabricMultiLoader-Klasse geladen?

`dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch`, geladen vom `KnotClassLoader`, wenn Fabric die
`preLaunch`-Entrypoints aufruft. Ihr statischer Initializer ist leer; `onPreLaunch()` ruft als erstes
`RuntimeBootstrap.get()` auf.

Einzige Ausnahme: Nutzt ein Payload das optionale `ConditionalMixinPlugin`, wird
`dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin` schon in der Mixin-`select()`-Phase geladen, also
vor `preLaunch`. Diese Klasse ist deshalb bewusst auf JDK-, `format`- und `FabricLoader`-API beschränkt und stößt
`RuntimeBootstrap` nicht an (validiert durch `OMNI-1035`). (Kapitel 9.2)

---

### 3. Gegen welche Minecraft-/Fabric-Version wird diese Bootstrap-Klasse kompiliert?

Gegen **keine** Minecraft-Version. Compile-Abhängigkeiten sind ausschließlich:

* JDK 8 API (`--release 8`, Classfile-Major 52),
* `dev.fabricmultiloader.format` und `dev.fabricmultiloader.api`,
* `net.fabricmc:fabric-loader:0.14.0` als `compileOnly` — die **niedrigste** unterstützte Loader-Version,
  damit versehentliche Nutzung neuerer Loader-API zur Compile-Zeit auffällt.

Verwendete Loader-API ist auf 12 stabile Methoden begrenzt (`FabricLoader.getInstance`, `getModContainer`,
`getAllMods`, `isModLoaded`, `getEnvironmentType`, `isDevelopmentEnvironment`, `getGameDir`, `getConfigDir`,
`getObjectShare`, `ModContainer#getMetadata/findPath`, `ModMetadata`-Getter). Verboten sind
`net.fabricmc.loader.impl.**`, `net.minecraft.**`, `com.mojang.**`, `net.fabricmc.fabric.api.**`,
`org.spongepowered.**`. (Kapitel 9.3, 14.2)

---

### 4. Wie verhindert das System, dass inkompatible versionsspezifische Klassen frühzeitig geladen werden?

Nicht durch Filterung, sondern durch **Nichtvorhandensein**: Die Klassen inaktiver Payloads liegen als Bytes in
einem ZIP-Eintrag innerhalb des Containers. Der Loader extrahiert und dem Classpath hinzufügt **nur** die
Payloads, die er ausgewählt hat. Ein JAR innerhalb eines JARs ist kein Classpath-Eintrag; die JVM sieht diese
Klassen nie. Zusätzlich sind die Packages pro Payload disjunkt (`OMNI-1044`), sodass auch kein FQCN-Zufallstreffer
möglich ist. (Kapitel 5.1, 13.2, 14.2)

---

### 5. Wie werden unterschiedliche Java-Classfile-Versionen behandelt?

Jedes Payload wird auf das Java-Level seiner MC-Version kompiliert (61 für 1.20.1, 65 für 1.21.x, **69 für 26.1**)
und deklariert `depends: {"java": ">=17"}` bzw. `">=21"` bzw. `">=25"`. Der Loader wertet den synthetischen
Mod-Kandidaten `java` als harte Solver-Klausel aus. Der Container selbst wird auf dem **Minimum** der Matrix
kompiliert (im Beispiel 61) und deklariert `depends.java >=17`.

Der Validator liest den Classfile-Header (Bytes 4–7) **jeder** Klasse und prüft: Container ≤ Baseline
(`OMNI-1040`), Payload == deklariertem Major (`OMNI-1041`), Major ↔ `requires.java` konsistent (`OMNI-1046`),
Baseline == Minimum (`OMNI-1047`). Multi-Release-Strukturen sind verboten (`OMNI-1049`). (Kapitel 14)

---

### 6. Wie funktionieren Mixins, obwohl Fabric Mixins bereits sehr früh lädt?

Genau **weil** Fabric früh lädt, gehören Mixins in die Payload-Mod: Fabric registriert in Phase 2.4 die
`mixins`-Configs **aller ausgewählten Mods**. Ein nicht ausgewähltes Payload hat keine registrierte Config;
Sponge Mixin liest seine Mixin-Klassen daher nie, löst keine `ClassInfo` auf und validiert keine Targets. Für das
aktive Payload läuft alles exakt wie bei einer normalen Mod — inklusive korrektem `compatibilityLevel` pro
Java-Version.

Ein Laufzeit-„Mixin-Dispatcher“ existiert bewusst nicht: Nach Phase 2.4 kann keine Config mehr sinnvoll
nachgereicht werden, und `IMixinConfigPlugin#shouldApplyMixin` verhindert nur die *Anwendung*, nicht das *Laden
und Validieren*. (Kapitel 5.3, 16.1, 16.8)

---

### 7. Wie funktionieren unterschiedliche Refmaps?

Ein Refmap pro Payload, erzeugt vom Mixin-Annotation-Processor während des jeweiligen Loom-Compiles, mit dem
eindeutigen Namen `<modId>-<payloadId>-refmap.json` (vom Plugin über
`loom.mixin.defaultRefmapName` gesetzt) und nur von den Mixin-Configs dieses Payloads referenziert. Refmaps
werden **niemals** gemergt — dasselbe Named-Symbol kann in verschiedenen Versionen auf verschiedene
Intermediary-Namen und Deskriptoren zeigen. Der Validator prüft Existenz, Gültigkeit, Namenseindeutigkeit über
alle Payloads und dass alle Refmap-Keys Klassen dieses Payloads sind (`OMNI-1030–1033`). Im Dev-Runtime übernimmt
Fabrics `MixinIntermediaryDevRemapper` die Umkehrung. (Kapitel 5.3.3, 15.5)

---

### 8. Wie werden Access Widener behandelt?

Ein Access Widener **pro Payload**, weil ein Payload eine eigene Fabric-Mod ist und Fabrics Regel „eine
AW-Datei pro Mod“ damit pro Payload gilt. Loom remappt die Datei beim `remapJar` in den
`intermediary`-Namespace. Der Loader merged die AW-Dateien aller geladenen Mods — da nur ein Payload geladen ist,
ist genau ein mod-eigener AW aktiv.

Gemeinsame Einträge dürfen in `common/src/main/accesswidener/shared.accesswidener` (Namespace `named`) stehen und
werden zur Build-Zeit **vor** dem Remap mit der payloadspezifischen Datei gemergt (dedupliziert, sortiert, mit
Quellenkommentar). Der Container deklariert keinen AW (`OMNI-1024`). Empfohlene Alternative für Einzelfälle:
Mixin-`@Accessor`/`@Invoker`. (Kapitel 5.4, 17)

---

### 9. Wie werden unterschiedliche Yarn-/Intermediary-Mappings behandelt?

Jedes Payload ist ein eigenständiger Loom-Build mit eigener MC-Version und eigenen Mappings; Payloads teilen
keinen Bytecode. Der Mapping-Provider ist pro Payload frei wählbar (`yarn:<build>`, `mojang`, `layered:…`,
`parchment:…`) — auch gemischt. Publiziert wird jedes Payload im `intermediary`-Namespace. Der Container ist
namespace-neutral, weil er keine Minecraft-Referenz enthält (`OMNI-1042`).

Wichtig: Intermediary garantiert **Namens-**, nicht **Signaturstabilität**. Genau deshalb ist „ein Kompilat für
alle Versionen“ prinzipiell unmöglich und die Payload-Trennung notwendig. (Kapitel 15)

---

### 10. Werden Payloads bereits remapped in die Universal-JAR eingebettet?

Ja. Reihenfolge: `compileJava` (named) → `remapJar` (named → intermediary, inklusive AW und Refmap-Zielen) →
`omniPayload` (Metadaten + gemergte Ressourcen einfügen, keine Bytecode-Änderung) → `assembleUniversalJar`
(Einbettung als **STORED**-ZIP-Eintrag). Zur Laufzeit findet in Produktion **kein** weiterer Remap statt. Nur im
Loom-Dev-Run remappt Fabrics `RuntimeModRemapper` intermediary → named — Standardverhalten für jede externe Mod.
(Kapitel 15.2, 23.1)

---

### 11. Liegen Payload-Klassen auf dem normalen Knot-Classpath?

Ja — die des **aktiven** Payloads. Der Loader extrahiert das ausgewählte genestete JAR nach
`<gameDir>/.fabric/processedMods/` und fügt es dem `KnotClassLoader` als Classpath-Eintrag hinzu. Damit greifen
Access Widener und Mixin-Transformation regulär, und Payload-Klassen sehen Minecraft, Fabric API, die Runtime und
den Container-Common ohne Vermittlung. Klassen inaktiver Payloads liegen auf **keinem** Classpath. (Kapitel 13.2,
13.3)

---

### 12. Wird ein eigener ClassLoader benötigt?

**Nein.** FabricMultiLoader erzeugt an keiner Stelle einen ClassLoader — das ist eine harte Invariante (I1,
ADR-002), erzwungen durch Validator-Regel 32 (`OMNI-1036`, verbietet Referenzen auf ClassLoader-Konstruktoren,
`URLClassLoader` und `net.fabricmc.loader.impl.**`). Ein eigener ClassLoader würde die Knot-Transformerkette
umgehen, wodurch Mixins und Access Widener wirkungslos wären, und Class-Identity-Brüche an jeder
Minecraft-Typgrenze erzeugen.

Einzige Ausnahme im gesamten Projekt: der Loader-Conformance-Test-Harness lädt verschiedene
Fabric-Loader-Versionen in isolierte `URLClassLoader` — reiner Testcode, nicht ausgeliefert. (Kapitel 6.4, 13.1)

---

### 13. Falls ja: Wie greifen diese Klassen korrekt auf Minecraft- und Fabric-Klassen des Knot-ClassLoaders zu?

Die Frage entfällt, weil kein eigener ClassLoader existiert. Payload-Klassen werden vom `KnotClassLoader` selbst
definiert und greifen deshalb direkt und ohne Delegationsregeln auf Minecraft- und Fabric-Klassen zu — identisch
zu jeder normalen Fabric-Mod. (Kapitel 13.2)

---

### 14. Wie verhindert man ClassIdentity-Probleme?

Strukturell: Es gibt genau einen ClassLoader, der Minecraft, Fabric API, Runtime, Container-Common und das aktive
Payload definiert. Jeder Typ existiert damit genau einmal; `ClassCastException` zwischen „zwei Versionen derselben
Klasse“ ist unmöglich.

Der einzige real verbleibende Kollisionsfall ist „zwei Mods liefern Klassen mit gleichem FQCN“ (Classpath-
First-Wins). Er wird beseitigt, indem die Library als eigene genestete Mod `fabricmultiloader` ausgeliefert wird
und der Loader nach Mod-ID auf die höchste kompatible Version dedupliziert — deterministisch statt
classpathabhängig. Ein Major-Wechsel erhält eine neue Mod-ID und ein neues Package
(`fabricmultiloader2` / `dev.fabricmultiloader.v2`), sodass 1.x und 2.x koexistieren. Zusätzlich warnt die
Runtime (`OMNI-2050`), wenn ihre eigenen Klassen aus einer unerwarteten JAR stammen. (Kapitel 13.4, 39.7, 42.3,
ADR-008)

---

### 15. Wie funktioniert Inter-Mod-Kommunikation?

Auf drei Wegen, alle über die **Container**-Mod-ID (Payload-IDs sind Implementierungsdetail und dürfen von
Dritten nicht referenziert werden):

1. **`FabricLoader.isModLoaded("examplemod")`** — funktioniert unverändert, weil der Container die primäre
   Mod-ID trägt.
2. **Öffentliche Mod-API im Container**: `com.example.common.api.*` liegt im Container, ist also über alle
   MC-Versionen **dasselbe Kompilat**. Drittmods kompilieren einmal gegen `com.example:examplemod-api:2.0.0`
   (`compileOnly`) und funktionieren auf jeder MC-Version. Das ist ein Vorteil, den eine klassische
   Ein-JAR-pro-Version-Veröffentlichung nicht bietet.
3. **`FabricLoader.getObjectShare()`**: Der Container veröffentlicht `"examplemod:api"` (Implementierung) und
   `"examplemod:omni"` (`ContainerHandle` für Diagnose) — nutzbar ohne Compile-Abhängigkeit.

Zusätzlich stehen Fabric-API-Events, Registries, Networking und Commands unverändert zur Verfügung; für andere
Mods ist eine Universal-Mod von einer normalen nicht unterscheidbar. (Kapitel 19.9, 24.7, 30.5)

---

### 16. Wie sieht `FabricLoader.isModLoaded()` für andere Mods aus?

| Abfrage | Ergebnis |
|---|---|
| `isModLoaded("examplemod")` | `true`, sobald der Container geladen ist — also auf jeder MC-Version innerhalb der deklarierten Union |
| `isModLoaded("examplemod-mc1214")` | `true` nur auf 1.21.4; **nicht** von Dritten zu verwenden |
| `isModLoaded("examplemod-impl")` (Alias) | `true`, wenn irgendein Payload aktiv ist; intern |
| `isModLoaded("fabricmultiloader")` | `true`, sobald mindestens eine Universal-Mod installiert ist |

Ehrliche Einschränkung: Wenn die MC-Version in der Union liegt, aber eine Nebenbedingung scheitert (z. B. Fabric
API zu alt), lädt der Container und `isModLoaded("examplemod")` ist `true`, obwohl keine Funktionalität aktiv
ist. Im Standardmodus (`strict = true`) ist dieser Zustand nicht beobachtbar, weil `preLaunch` das Spiel mit
`OMNI-2003` abbricht. Nur im ausdrücklich gewählten `strict = false`-Modus bleibt er bestehen; dafür existiert
`FabricMultiLoader.isActive("examplemod")` als präzise Abfrage, und die Doku empfiehlt sie Integratoren.
(Kapitel 18.1, 29.5, 30.5)

---

### 17. Wie werden unterschiedliche Fabric-API-Versionen gehandhabt?

Pro Payload. Jedes Payload deklariert seine eigene Mindestversion in seiner `fabric.mod.json`
(`"fabric-api": ">=0.114.0"`) und in `requires.mods` seines Deskriptors. Der Container deklariert Fabric API
lediglich als `recommends: "*"` — nicht als `depends`, weil die konkrete Anforderung payloadabhängig ist.

Ist Fabric API zu alt, wird das Payload vom Solver verworfen, der Container lädt, und unsere `preLaunch`-Diagnose
nennt exakt „fabric-api >=0.114.0 — REJECTED: 0.110.0 installed“ samt Downloadlink. Für Mods, die nur einzelne
Fabric-API-Module brauchen, gibt es `fabricApiMode = MODULES`: Dann werden Modul-IDs
(`fabric-networking-api-v1` …) statt der Aggregat-ID deklariert. Compile-seitig wird pro Version-Modul die in der
Matrix hinterlegte Fabric-API-Version verwendet. (Kapitel 12.4, 20.3, 24.2, 29.2)

---

### 18. Wie funktionieren Client-/Server-spezifische Klassen?

Auf drei Ebenen, die kombiniert werden:

1. **Payload-Ebene**: `"environment": "client"` bzw. `"server"` in der Payload-`fabric.mod.json`. Ein
   Client-Payload wird auf einem dedizierten Server gar nicht geladen — inklusive Mixins, AW und Ressourcen.
2. **Mixin-Config-Ebene**: `{"config": "…client.mixins.json", "environment": "client"}` verhindert die
   Registrierung auf dem Server, sodass Mixin die Klassen nie liest.
3. **Code-Ebene**: getrennte Entrypoints (`UniversalClientMod`/`UniversalServerMod`) und getrennte Packages
   (`…<payloadId>.client.**`, in `clientOnlyPackages` deklariert). Der Validator prüft statisch, dass keine vom
   `common`-Entrypoint erreichbare Klasse ein Client-Package referenziert (`OMNI-1150`) und dass
   `net/minecraft/client/**`-Referenzen nur in Client-Packages vorkommen (`OMNI-1045`).

Die physische Seite ist zur Laufzeit über `ctx.side()` abfragbar. (Kapitel 26)

---

### 19. Was passiert bei einer nicht unterstützten Minecraft-Version?

Zwei klar getrennte Fälle:

1. **MC außerhalb der Union**: Fabrics eigener Resolver lehnt den Container ab und zeigt seine lokalisierte
   Fehler-GUI (Client) bzw. eine formatierte Konsolenmeldung (Server) mit den erlaubten Bereichen. Kein
   FabricMultiLoader-Code läuft; es kann per Konstruktion keinen Mixin- oder JVM-Fehler geben.
2. **MC in der Union, aber kein Payload wählbar** (Fabric API zu alt, Java zu alt, Fremdmod fehlt,
   Client-Payload auf Server): Der Container lädt, und `ContainerPreLaunch` erzeugt den Bericht `OMNI-2003` mit
   erkannter Umgebung, jedem Payload, jedem Constraint und dessen Auswertung, der Liste unterstützter
   MC-Versionen und konkreten Handlungsanweisungen mit Links (Beispiel in Kapitel 29.2). Im Standardmodus bricht
   der Start ab; mit `-Dfabricmultiloader.strict=false` läuft das Spiel weiter und die Mod bleibt deaktiviert
   (`OMNI-2101`).

In keinem Fall entsteht ein nackter `NoClassDefFoundError` oder ein Mixin-Stacktrace. Ein Integrationstest
(`itest unsupported`) prüft das automatisiert und lässt den Build fehlschlagen, wenn im Log ein
`NoClassDefFoundError` erscheint. (Kapitel 9.9, 29.2, 32.5)

---

### 20. Wie groß ist der technische Mindestumfang einer Universal-JAR?

| Bestandteil | Größe |
|---|---|
| `fabric.mod.json` (generiert) | ~1,2 KiB |
| `META-INF/omni-container.json` (1 Payload) | ~1,8 KiB |
| `META-INF/MANIFEST.MF` | ~0,3 KiB |
| `fabricmultiloader-runtime-1.0.0.jar` (enthält `format` + `api` + `runtime`) | ~62 KiB |
| **Overhead ohne Modinhalt** | **≈ 66 KiB** (NF-03: < 80 KiB erfüllt) |
| plus Payload-Minimum (eine Adapter-Klasse, eine Factory, generierte Metadaten) | ~6 KiB |
| **Kleinstmögliche funktionsfähige Universal-JAR** | **≈ 72 KiB** |

Der Runtime-Anteil wird über die Loader-Deduplizierung nur **einmal pro Spiel** geladen, auch wenn 40
Universal-Mods installiert sind. (Kapitel 40.4)

---

### 21. Kann die Universal-JAR Minecraft-Versionen mit unterschiedlichen erforderlichen Java-Hauptversionen enthalten?

Ja, uneingeschränkt. Die Referenzmatrix enthält bewusst drei: Java 17 (1.20.1), Java 21 (1.21.x) und **Java 25
(26.1)** — Classfile-Majors 61, 65, 69 in einer Datei. Die Auswahl trifft der Loader über `depends.java`; der
Container wird auf dem Minimum kompiliert und deklariert `depends.java >=17`, damit er auf der ältesten Umgebung
lädt. Der Validator erzwingt die Konsistenz von Bytecode-Level, `classfileMajor`-Angabe und `requires.java`
(`OMNI-1041/1046/1047`). Ein neuer Java-Sprung kostet den Modautor genau einen Matrix-Eintrag. (Kapitel 14,
37.4)

---

### 22. Falls eine alte JVM die JAR öffnet: Wie verhindert man `UnsupportedClassVersionError` durch Payloads für neuere Java-Versionen?

Der Fehler entsteht ausschließlich in `ClassLoader#defineClass`, also beim **Definieren** einer Klasse — nicht
beim Öffnen einer JAR und nicht beim Lesen eines ZIP-Eintrags. Die Kette ist lückenlos:

1. Fabrics `ModDiscoverer` liest von genesteten JARs **nur** `fabric.mod.json` — eine Textdatei. Keine
   Bytecode-Inspektion, kein ASM, keine Classfile-Versionsprüfung.
2. Der Solver verwirft das Java-25-Payload auf einer Java-17-JVM, weil `depends: {"java": ">=25"}` unerfüllbar
   ist.
3. Verworfene Payloads werden nicht extrahiert und dem Classpath nicht hinzugefügt.
4. Folglich wird keine ihrer Klassen je definiert.

Zusätzliche Absicherung zur Build-Zeit: Der Validator prüft, dass kein Payload einen Classfile-Major hat, der
höher ist, als seine `requires.java`-Untergrenze erlaubt (`OMNI-1046`) — der einzige Weg, wie dieser Fehler
entstehen könnte, ist damit ausgeschlossen, bevor die Datei existiert. Und der Container, dessen Klassen
tatsächlich auf der ältesten JVM definiert werden, wird auf Classfile-Ebene vollständig gescannt
(`OMNI-1040`). (Kapitel 5.5.1, 14.2, 14.4)

---

### 23. Welche Teile müssen zwingend auf dem kleinsten gemeinsamen Java-Level kompiliert werden?

| Artefakt | Ziel | Grund |
|---|---|---|
| `fabricmultiloader-format` | Classfile 52 (Java 8) | wird von Runtime **und** Gradle-Plugin genutzt und muss auf jeder unterstützten JVM laden |
| `fabricmultiloader-api` | 52 | von Common-Code und allen Payloads referenziert |
| `fabricmultiloader-runtime` | 52 | Bootstrap läuft auf der ältesten JVM |
| `fabricmultiloader-processor` | 52 | Annotation Processor |
| Container-Common des Mods | `baselineJava` = Minimum der Matrix (im Beispiel 61/Java 17) | wird auf der ältesten unterstützten MC-Version geladen |
| Payloads | jeweils ihr eigenes Level (61/65/69) | werden nur auf passenden JVMs geladen |

Durchgesetzt mit `--release` (nicht `targetCompatibility`), damit auch die verwendete **API** gegen das Ziel-JDK
geprüft wird, und zusätzlich durch den Classfile-Scan des Validators. Praktische Folge: In `format`/`api`/
`runtime` sind Records, `var`, `sealed`, Switch-Expressions und `List.of` verboten; kompensiert durch
Builder-Pattern. (Kapitel 5.5.2, 14.2, 14.7)

---

### 24. Wie wird eine neue Minecraft-Version hinzugefügt?

Ein Befehl, dann die echte Anpassungsarbeit:

```bash
./gradlew addMinecraftVersion --id=mc261 --mc=26.1 --range=">=26.1 <26.2" \
    --yarn=26.1+build.1 --loader=0.17.0 --fabric-api=0.130.0+26.1 --java=25 --copy-from=mc1214
```

Der Task erzeugt: Matrixeintrag in `gradle/fabricmultiloader.toml`, `versions/mc-26.1/build.gradle.kts`,
Quellverzeichnisse, kopierten und **umbenannten** Adaptercode (`com.example.mc1214` → `com.example.mc261`,
`Platform1214` → `Platform261`), Mixin-Configs mit angepasstem `package`/`refmap`/`compatibilityLevel`
(`JAVA_25`), AW-Stub und den CI-Matrixeintrag. Er prüft die Disjunktheit der neuen Range gegen alle bestehenden
und bricht sonst mit `OMNI-1010` ab.

Danach: `:versions:mc-26.1:build` (jeder Compilerfehler ist eine echte API-Änderung), `runClient261`,
`runDatagen261`, `buildUniversalJar`, `validateUniversalJar`, `integrationTestMc261`, `capabilities` aktualisieren.
**Bestehende Payloads werden nicht angefasst**, und die Container-Baseline bleibt beim Minimum (17), sodass die
Mod auf 1.20.1/Java 17 unverändert weiterläuft. (Kapitel 37)

---

### 25. Welche Bereiche können nicht vollständig abstrahiert werden?

Ehrliche Liste. Für diese Bereiche gibt es **keine** versionsneutrale Common-API; sie leben im Payload und werden
über `Services`, `Capabilities` und Common-Hooks angebunden:

| Bereich | Grund |
|---|---|
| **Rendering** (`DrawContext`, `MatrixStack`, `RenderLayer`, Shader-Pipeline, `HudRenderCallback`) | Signaturen und Konzepte ändern sich fast jede Version; ab 1.21.x mehrfach umgebaut |
| **Mixins** | Sind per Definition an konkrete Targets und Deskriptoren gebunden |
| **Weltgenerierung** (`ConfiguredFeature`, `PlacedFeature`, Biome-Modifikation, `Codec`-Registrierungen) | Registry- und Codec-Umbauten pro Version |
| **NBT/Datenkomponenten** | 1.20.5 hat NBT-Item-Daten durch Komponenten ersetzt — kein gemeinsames Modell möglich; über `Capabilities.COMPONENTS` gekapselt |
| **Codecs/Serialisierung** (`Codec`, `PacketCodec`, `StreamCodec`) | Typsystem und Registry-Bindung versionsabhängig |
| **DataFixerUpper / Schema-Migration** | An Mojangs Versionsschemata gebunden |
| **Paketformate und Registry-Sync** | Protokoll ändert sich pro Version; abstrahiert wird nur der *Nutzdatenpfad* (`ByteSink`/`ByteSource`), nicht das Protokoll |
| **GUI-Layout und Screen-Klassen** | Klassenhierarchie und Layoutmodell ändern sich |
| **Entity-/Block-Attribute und -Verhalten in der Tiefe** | Nur einfache Fälle sind über `ItemSpec`/`BlockSpec` deklarativ abbildbar |
| **Datagen-Provider** | `FabricDataGenerator`-API versionsabhängig; abstrahiert werden nur die *Eingabedaten* (`RecipeSpec`, `LootSpec`) |
| **Core-Transformationen jenseits von Mixin** | Fabric bietet keine öffentliche Transformer-API — auch ohne FabricMultiLoader nicht |

Abstrahiert und stabil sind hingegen: Lifecycle, Logging, Config, Pfade, Registrierung einfacher Inhalte
(Items, Blöcke, Sounds, Item-Gruppen), Commands, Networking-Nutzdaten, die stabilen Fabric-API-Events, Ressourcen
und Diagnose. Erfahrungswert aus der Beispielmod: 142 Common-Klassen gegenüber 18–22 Klassen pro Payload —
also 85–89 % des Codes versionsneutral. (Kapitel 18.1, 19.7, 28.4, 41.2)

---

Weiter mit [Kapitel 45–46 — Reality Check und finale Architekturzusammenfassung](part-14-reality-check.md).
