# 34. Distribution

## 34.1 Grundsatz

Für Spieler existiert **eine** Datei. Auf Modrinth und CurseForge wird sie als **ein** File-Upload mit **mehreren**
Game-Version-Tags veröffentlicht. Beide Plattformen unterstützen das nativ (mehrere `game_versions` pro File);
kein Trick, keine Sonderbehandlung.

## 34.2 Dateinamen

| Artefakt | Name | Zweck |
|---|---|---|
| Universal-JAR | `examplemod-2.0.0-universal.jar` | der Download für Spieler |
| Checksumme | `examplemod-2.0.0-universal.jar.sha256` | Verifikation, Modpack-Tools |
| Slim-JAR (optional) | `examplemod-2.0.0+mc1.21.4.jar` | Einzelversion, falls jemand sie braucht |
| API-Artefakt | `examplemod-api-2.0.0.jar` | Maven, für Drittmods |
| Sources (optional) | `examplemod-2.0.0-sources.jar` | pro Version-Modul, nur Maven |

Der Classifier `-universal` ist Default und über `container { archiveClassifier }` änderbar. Bewusst **kein** `+`
im Hauptdateinamen: Einige Launcher, Mod-Manager und Webserver behandeln `+` in URLs inkonsistent.

## 34.3 Modrinth

`publishModrinth` (Implementierung: eigener Publisher im Plugin, HTTP-API v2, keine Fremdplugin-Abhängigkeit;
`minotaur` kann alternativ genutzt werden):

```
POST /v2/version
{
  "project_id":     "AbCdEfGh",
  "version_number": "2.0.0",
  "name":           "Universal Example Mod 2.0.0",
  "version_type":   "release",
  "loaders":        ["fabric"],
  "game_versions":  ["1.20.1","1.21","1.21.1","1.21.4"],
  "featured":       true,
  "dependencies": [
    { "project_id": "P7dR8mSH", "dependency_type": "required" },        // Fabric API
    { "project_id": "9s6osm5g", "dependency_type": "optional" }         // Cloth Config
  ],
  "changelog":      "<Inhalt von CHANGELOG.md, Abschnitt 2.0.0>",
  "file_parts":     ["file"],
  "primary_file":   "file"
}
```

**Ableitung von `game_versions` aus der Matrix** — der Punkt, an dem Automatisierung wirklich zählt: Aus den
effektiven MC-Ranges wird die Liste der **konkret existierenden** MC-Versionen berechnet, indem der Bereich gegen
Modrinths Versionsindex (`GET /v2/tag/game_version`) geschnitten wird. Aus `>=1.21 <1.21.2` werden damit
automatisch `1.21` und `1.21.1`, aus `>=1.21.4 <1.21.5` wird `1.21.4`. Snapshots werden nur aufgenommen, wenn
`snapshots = true` in der Matrix steht. Das Ergebnis wird vor dem Upload ausgegeben und ist bei
`--dry-run` prüfbar.

## 34.4 CurseForge

Upload über die Upload-API (`POST /api/projects/<id>/upload-file`) mit `gameVersions` als Liste von
CurseForge-Version-IDs. Die IDs werden über `GET /api/game/versions` aufgelöst und auf dieselbe Weise aus der
Matrix abgeleitet. Zusätzlich werden die Tags `Fabric` (modloader) und `Java 17`/`Java 21`/`Java 25`
(Java-Version-Tags, sofern das Projekt sie nutzt) gesetzt — mit dem Hinweis in der Doku, dass CurseForge nur
**eine** Java-Angabe pro File erlaubt und deshalb die **niedrigste** gesetzt wird.

`relations`: Fabric API als `requiredDependency`, optionale Mods als `optionalDependency`.

## 34.5 Changelog

`CHANGELOG.md` im Keep-a-Changelog-Format; der Publisher extrahiert den Abschnitt der aktuellen Version und
ergänzt automatisch einen generierten Block:

```markdown
### Supported versions

| Minecraft | Java | Fabric Loader | Fabric API |
|---|---|---|---|
| 1.20.1 | 17+ | 0.14.21+ | 0.92.2+ |
| 1.21 – 1.21.1 | 21+ | 0.15.11+ | 0.102.0+ |
| 1.21.4 | 21+ | 0.16.9+ | 0.114.0+ |

This is a single universal file — the same download works on every version listed above.
SHA-256: 7c9a1f…e2
```

Der Block stammt aus `omniReport` und ist damit immer korrekt.

## 34.6 GitHub Release

Tag `v2.0.0`, Titel `Universal Example Mod 2.0.0`, Body = Changelog-Abschnitt + Versionstabelle, Assets:
Universal-JAR, `SHA256SUMS.txt`, `validation.txt`. Die Anhänge des Validierungsberichts sind bewusst öffentlich:
Sie belegen, dass das Artefakt geprüft ist, und helfen bei Supportanfragen.

## 34.7 Modpack- und Launcher-Verträglichkeit

| Werkzeug | Verhalten | Hinweis |
|---|---|---|
| Prism / MultiMC | Behandelt die JAR als eine Mod; erkennt `fabric.mod.json` und zeigt Name/Version | Die `depends.minecraft`-Union führt dazu, dass Prisms „passt nicht zur Instanz“-Warnung korrekt ausbleibt |
| Modrinth App | zeigt eine Mod, ein Update | `game_versions` müssen korrekt sein, sonst filtert die App die Datei aus |
| Packwiz | `.pw.toml` mit einer Datei und Hash | funktioniert unverändert |
| CurseForge App | eine Datei | s. Java-Tag-Einschränkung in 34.4 |
| Server-Hoster mit Auto-Update | eine Datei | `.sha256`-Sidecar erlaubt Integritätsprüfung |
| Modpacks, die JARs rekomprimieren (selten) | Payload-Hashes könnten brechen | `verifyIntegrity` liefert `OMNI-2013` mit dem Hinweis auf `-Dfabricmultiloader.verify=false`; Kapitel 39.4 |

## 34.8 Wann Slim-Jars sinnvoll sind

`buildSlimJars` erzeugt pro Payload ein eigenständiges Jar (Payload + Common + Container-Metadaten, auf dieses
Payload reduziert). Anwendungsfälle, für die es dokumentiert ist:

* Größenkritische Verteilung (z. B. serverseitige Auto-Downloads mit Bandbreitenlimit).
* Plattformen, die keine Mehrfach-Game-Version-Tags erlauben.
* Rückfallpfad, falls die tragende Loader-Annahme in einer künftigen Loader-Version brechen sollte
  (Kapitel 41.2) — dann wäre eine Slim-Veröffentlichung ohne Codeänderung möglich.

Standardmäßig deaktiviert, damit das Versprechen „eine Datei“ nicht verwässert wird.

---

# 35. Example Mod — `UniversalExampleMod`

## 35.1 Umfang

Unterstützt Minecraft **1.20.1**, **1.21 – 1.21.1**, **1.21.4**. Enthält: gemeinsamen Entrypoint, ein Item mit
Verhalten, einen Block mit BlockItem, einen Command, Networking in beide Richtungen, einen Common-Event-Handler,
drei Version-Adapter, je einen versionsspezifischen Mixin, gemeinsame und versionsspezifische Ressourcen,
Datagen, Unit-Tests.

## 35.2 Vollständige Projektstruktur

```
example/                                     (im Framework-Repo; im Template Root-Projekt)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── fabricmultiloader.toml               ← Matrix aus Kapitel 20.3
│   └── libs.versions.toml
│
├── common/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/common/
│       │   ├── ExampleMod.java                       @UniversalEntrypoint
│       │   ├── ExampleModClient.java                 @UniversalEntrypoint(CLIENT)
│       │   ├── ExampleCommands.java
│       │   ├── ExampleEvents.java
│       │   ├── RubyLogic.java                        reine Fachlogik, unit-getestet
│       │   ├── RubyBehavior.java                     ItemBehavior
│       │   ├── RubyContent.java                      Rezept-/Loot-Spezifikationen (Datagen + Runtime)
│       │   ├── api/ExampleModApi.java                öffentliche API für Drittmods
│       │   ├── api/RubyView.java
│       │   ├── config/ExampleConfig.java             JSON über format-Parser
│       │   ├── hook/ItemRenderHooks.java             von Mixins aufgerufen
│       │   ├── hook/ItemRenderContext.java
│       │   ├── net/ExampleNetworking.java
│       │   ├── net/ChargeRequest.java
│       │   ├── service/OreGenService.java
│       │   └── service/HudRenderService.java
│       ├── main/resources/
│       │   ├── assets/examplemod/lang/en_us.json
│       │   ├── assets/examplemod/lang/de_de.json
│       │   ├── assets/examplemod/textures/item/ruby.png
│       │   ├── assets/examplemod/textures/block/ruby_block.png
│       │   ├── assets/examplemod/models/item/ruby.json
│       │   └── data/examplemod/tags/blocks/ruby_related.json
│       ├── main/accesswidener/shared.accesswidener
│       ├── main/omni/icon.png
│       └── test/java/com/example/common/
│           ├── RubyLogicTest.java
│           ├── ExampleModInitTest.java               nutzt FakeModContext
│           └── ExampleConfigTest.java
│
├── versions/
│   ├── mc-1.20.1/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/com/example/mc1201/
│   │       │   ├── Platform1201.java
│   │       │   ├── Platform1201Factory.java
│   │       │   ├── registry/Registries1201.java
│   │       │   ├── registry/BehaviorItem1201.java
│   │       │   ├── registry/Mapping1201.java
│   │       │   ├── net/Networking1201.java
│   │       │   ├── net/ByteSink1201.java
│   │       │   ├── net/ByteSource1201.java
│   │       │   ├── net/ClientNet1201.java            (client-only)
│   │       │   ├── ref/PlayerRef1201.java
│   │       │   ├── service/OreGenService1201.java
│   │       │   ├── client/HudRenderService1201.java  (client-only)
│   │       │   ├── client/mixin/ItemRendererMixin.java
│   │       │   ├── mixin/MinecraftServerMixin.java
│   │       │   └── datagen/ExampleDataGen1201.java
│   │       ├── resources/
│   │       │   ├── examplemod-mc1201.mixins.json
│   │       │   ├── examplemod-mc1201.client.mixins.json
│   │       │   ├── examplemod-mc1201.accesswidener
│   │       │   └── assets/examplemod/models/item/ruby.json     ← Override (1.20.1-Modellformat)
│   │       └── generated/                                       ← Datagen-Output (eingecheckt)
│   ├── mc-1.21.1/   (analog, com.example.mc1211)
│   └── mc-1.21.4/   (analog, com.example.mc1214)
│
├── run/                                     (gitignored)
└── .github/workflows/build.yml
```

## 35.3 Erzeugtes Artefakt

```
build/libs/universal-example-mod-1.0.0-universal.jar        ~4.8 MiB
├── fabric.mod.json                          id=examplemod, depends.minecraft = 3 Ranges
├── META-INF/omni-container.json             3 Payloads, Hashes, Constraints
├── META-INF/MANIFEST.MF                     Omni-Container-Format: omni/1
├── com/example/common/…                     142 Klassen, Classfile 61
├── omni/icon.png
├── omni/entrypoints.json
├── LICENSE
└── META-INF/jars/
    ├── fabricmultiloader-runtime-1.0.0.jar  ~62 KiB
    ├── examplemod-mc1201.jar                ~1.42 MiB  (Classfile 61)
    ├── examplemod-mc1211.jar                ~1.51 MiB  (Classfile 65)
    └── examplemod-mc1214.jar                ~1.54 MiB  (Classfile 65)
```

## 35.4 Ausgewählte Dateien im Zusammenhang

Die zentralen Quelldateien sind in Kapitel 27/28 vollständig gezeigt (`ExampleMod`, `ExampleModClient`,
`ExampleNetworking`, `ExampleCommands`, `ExampleEvents`, `Registries1201`, `Registries1214`, `Networking1201`,
`Networking1214`, `Platform1214`, `ItemRendererMixin` in beiden Varianten). Ergänzend:

`common/src/main/java/com/example/common/RubyLogic.java` — reine Fachlogik, ohne jede Abhängigkeit außer der API:

```java
package com.example.common;

import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.ref.PlayerRef;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RubyLogic {

    private static final Map<UUID, Integer> CHARGE = new HashMap<UUID, Integer>();
    private static int maxCharge = 100;

    public static void configure(ExampleConfig config) { maxCharge = config.maxCharge(); }

    public static int chargeOf(PlayerRef player) { Integer v = CHARGE.get(player.uuid()); return v == null ? 0 : v; }
    public static int chargeOf(ItemStackRef stack) { return stack.count() * 2; }

    public static int charge(PlayerRef player, int amount) {
        int next = Math.min(maxCharge, chargeOf(player) + Math.max(0, amount));
        CHARGE.put(player.uuid(), next);
        return next;
    }

    public static void tick(long tickCount) {
        if (tickCount % 200 != 0) return;
        for (Map.Entry<UUID, Integer> e : CHARGE.entrySet()) {
            if (e.getValue() > 0) e.setValue(e.getValue() - 1);
        }
    }

    public static void onRubyBlockBroken(PlayerRef player) { charge(player, 5); }
}
```

`common/src/test/java/com/example/common/ExampleModInitTest.java`:

```java
package com.example.common;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.Side;
import dev.fabricmultiloader.testing.FakeModContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleModInitTest {

    @Test
    void registersContentOnEveryTargetVersion() {
        for (String mc : new String[] { "1.20.1", "1.21.1", "1.21.4" }) {
            FakeModContext ctx = FakeModContext.builder()
                    .modId("examplemod").modVersion("1.0.0")
                    .minecraft(mc).java(mc.startsWith("1.20") ? 17 : 21)
                    .fabricApi("0.114.0").side(Side.SERVER)
                    .build();

            new ExampleMod().onInitialize(ctx);

            assertThat(ctx.recordedItems().keySet())
                    .containsExactly(Id.of("examplemod", "ruby"), Id.of("examplemod", "ruby_block"));
            assertThat(ctx.recordedChannels())
                    .containsExactly(Id.of("examplemod", "ruby_sync"), Id.of("examplemod", "charge_request"));
            assertThat(ctx.recordedCommands()).containsExactly("ruby");
            assertThat(ctx.recordedEvents()).contains("playerJoin", "serverTick", "blockBroken");
        }
    }
}
```

Dieser Test läuft in ~40 ms, braucht kein Minecraft, kein Loom und keinen Gradle-Sync — und deckt trotzdem den
größten Teil der Modlogik ab. Das ist der praktische Nutzen von Prinzip P1.

`versions/mc-1.20.1/src/main/java/com/example/mc1201/mixin/MinecraftServerMixin.java`:

```java
package com.example.mc1201.mixin;

import com.example.common.RubyLogic;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Demonstriert einen Mixin, der auf allen Zielversionen identisch aussieht, aber
 * trotzdem pro Payload existiert — weil Refmap und Intermediary-Bindung pro Version
 * unterschiedlich sind.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void examplemod$afterTick(CallbackInfo ci) {
        RubyLogic.tick(((MinecraftServer) (Object) this).getTicks());
    }
}
```

## 35.5 Versionsspezifische Ressource als Demonstration

`assets/examplemod/models/item/ruby.json` existiert zweimal:

* `common/src/main/resources/…` — Modell im ab 1.21.4 gültigen Format,
* `versions/mc-1.20.1/src/main/resources/…` — Variante für das ältere Format.

In `build.gradle.kts`:

```kotlin
resources {
    strictOverrides.set(true)
    allowOverride("assets/examplemod/models/item/ruby.json")
}
```

Der Merge-Report weist den Override aus; ohne `allowOverride` schlägt der Build mit `OMNI-1200` fehl. Damit ist
jede Ressourcenabweichung zwischen Versionen im Code-Review sichtbar — eine der häufigsten Fehlerquellen in
Multi-Version-Projekten.

---

# 36. Migration Guide — bestehende Fabric-Mod → FabricMultiLoader

Ausgangslage: `ExampleMod` für Minecraft 1.21.4, klassisches Loom-Single-Project.

```
examplemod/
├── build.gradle.kts             (fabric-loom, MC 1.21.4)
├── gradle.properties
└── src/main/
    ├── java/com/example/examplemod/**
    └── resources/{fabric.mod.json, examplemod.mixins.json, examplemod.accesswidener, assets/, data/}
```

## 36.1 Schritt 1 — Struktur anlegen (mechanisch, 10 Minuten)

```bash
mkdir -p common/src/main/{java,resources,accesswidener,omni} common/src/test/java
mkdir -p versions/mc-1.21.4/src/main/{java,resources}
git mv src/main/java/com/example/examplemod versions/mc-1.21.4/src/main/java/com/example/mc1214
git mv src/main/resources/assets    common/src/main/resources/assets
git mv src/main/resources/data      common/src/main/resources/data
git mv src/main/resources/examplemod.mixins.json \
       versions/mc-1.21.4/src/main/resources/examplemod-mc1214.mixins.json
git mv src/main/resources/examplemod.accesswidener \
       versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener
git rm src/main/resources/fabric.mod.json          # wird ab jetzt generiert
```

Der gesamte bestehende Code landet zunächst **im Version-Modul**. Das ist Absicht: Nach diesem Schritt ist die
Mod bereits eine funktionierende FabricMultiLoader-Mod mit *einem* Payload. Alles Weitere ist Verbesserung, nicht
Voraussetzung.

## 36.2 Schritt 2 — Matrix und Buildfiles (15 Minuten)

`gradle/fabricmultiloader.toml` mit `[mod]`, `[container] baselineJava = 21`, `[framework]` und einem Block
`[versions.mc1214]` (Werte aus dem alten `gradle.properties`).

`settings.gradle.kts`, Root-`build.gradle.kts`, `common/build.gradle.kts`,
`versions/mc-1.21.4/build.gradle.kts` gemäß Kapitel 21 — jeweils 5–40 Zeilen. Das alte `build.gradle.kts` wird
gelöscht; seine `dependencies` wandern nach `versions/mc-1.21.4/build.gradle.kts`, wobei
`modImplementation("me.shedaniel.cloth:…")` zu `omniMod("me.shedaniel.cloth:cloth-config-fabric", key = "clothConfig")`
wird.

## 36.3 Schritt 3 — Entrypoint umstellen (20 Minuten)

Vorher:

```java
public final class ExampleMod implements ModInitializer {
    @Override public void onInitialize() { … }
}
```

Nachher — zwei Klassen, weil sich die Verantwortung teilt:

```java
// versions/mc-1.21.4/src/main/java/com/example/mc1214/Platform1214.java
public final class Platform1214 extends AbstractPlatform {
    @Override public void onInitialize(ModContext ctx) {
        // alles, was Minecraft-Typen braucht — zunächst der komplette alte Code
        ExampleModContent.registerAll();
        ExampleModNetworking.register();
    }
    // registries()/networking()/commands()/events() zunächst über die
    // Runtime-Defaults bzw. UnsupportedPlatformParts, solange kein Common-Code sie nutzt
}
```

```java
// common/src/main/java/com/example/common/ExampleMod.java
@UniversalEntrypoint
public final class ExampleMod implements UniversalMod {
    @Override public void onInitialize(ModContext ctx) {
        ctx.log().info("ExampleMod {} on Minecraft {}", ctx.modVersion(), ctx.platform().minecraft());
    }
}
```

Zu diesem Zeitpunkt ist `common` fast leer und die Mod funktioniert vollständig.
`./gradlew buildUniversalJar validateUniversalJar runClient1214` muss grün sein — **das ist der Meilenstein**,
an dem die Migration technisch abgeschlossen ist.

## 36.4 Schritt 4 — Zweite Version hinzufügen (der eigentliche Nutzen)

```bash
./gradlew addMinecraftVersion --mc=1.21.1 --range=">=1.21 <1.21.2" \
    --yarn=1.21.1+build.3 --loader=0.15.11 --fabric-api=0.102.0+1.21.1 --java=21
```

Das Scaffolding erzeugt Verzeichnis, `build.gradle.kts`, Matrixeintrag, `Platform1211`/`Platform1211Factory`,
leere Mixin-Configs und einen AW-Stub. Danach: `cp -r` des 1.21.4-Codes nach `mc1211`, Package-Rename,
Kompilieren, Fehler abarbeiten. Jeder Compilerfehler ist eine echte API-Abweichung — genau die Arbeit, die
niemand automatisieren kann.

## 36.5 Schritt 5 — Duplikate nach `common` ziehen (iterativ, optional)

Jetzt zahlt sich Abstraktion aus. Reihenfolge nach Nutzen:

| Priorität | Was | Wie |
|---|---|---|
| 1 | Reine Fachlogik (Berechnungen, Zustand, Config) | 1:1 nach `common`, keine Anpassung nötig |
| 2 | Registrierungslisten | `Registries`-SPI verwenden (Kapitel 19.4) |
| 3 | Networking | `ChannelSpec` + `PayloadCodec` (Kapitel 27) |
| 4 | Commands | `CommandSpec` (Kapitel 28.2) |
| 5 | Events | `Events` (Kapitel 28.3) |
| 6 | Datagen-Eingaben | neutrale Specs in `common`, Provider im Payload |
| — | Rendering, Weltgenerierung, Mixins | bleiben im Payload; Brücke über Hooks/Services |

Nach diesem Schritt liegen typischerweise 60–85 % des Codes in `common`. Erfahrungswert aus der Beispielmod:
142 Common-Klassen gegenüber 18–22 Klassen pro Payload.

## 36.6 Schritt 6 — Dritte Version, Ressourcen, Release

1.20.1 hinzufügen (analog Schritt 4). Ressourcenabweichungen mit `allowOverride` deklarieren. Validator laufen
lassen, Integrationstests aktivieren, `publishUniversal` konfigurieren. Auf Modrinth/CurseForge die alten
Einzeldateien **behalten** (sie funktionieren weiter) und ab jetzt nur noch die Universal-Datei
veröffentlichen — mit einem Changelog-Hinweis, dass eine Datei nun alle Versionen abdeckt.

## 36.7 Migrationsfallen

| Falle | Erkennung | Lösung |
|---|---|---|
| Statische Felder mit MC-Typen in `common` | `OMNI-1042` | Handle-Pattern (`ItemHandle`) verwenden |
| `Registry.register` direkt aus `common` | `OMNI-1042` | über `ctx.registries()` |
| Alte `fabric.mod.json` noch in Ressourcen | `OMNI-1021` | löschen; alles kommt aus der Matrix/DSL |
| Mixin-Config-Name ohne `payloadId` | `OMNI-1030` beim zweiten Payload | umbenennen |
| Assets im Container statt im Payload | `OMNI-1023` | Assets nach `common/src/main/resources` (werden in Payloads gemergt) |
| Java-21-Bytecode im Common bei 1.20.1-Support | `OMNI-1040` | `[container] baselineJava = 17` |
| Kotlin-Runtime im Container | `OMNI-1184` | `fabric-language-kotlin` pro Payload als `omniMod` |
| Mod-ID-Änderung befürchtet | — | Die Mod-ID bleibt **identisch**; nur zusätzliche Payload-IDs erscheinen (als ModMenu-Kinder) |

---

# 37. Adding New Minecraft Versions

## 37.1 Der Workflow in einem Befehl

Ausgangslage: 1.20.1, 1.21.1, 1.21.4 werden unterstützt. Neu erscheint **26.1 mit Java 25**.

```bash
./gradlew addMinecraftVersion \
    --id=mc261 \
    --mc=26.1 \
    --range=">=26.1 <26.2" \
    --yarn=26.1+build.1 \
    --loader=0.17.0 \
    --fabric-api=0.130.0+26.1 \
    --java=25 \
    --copy-from=mc1214
```

## 37.2 Was der Task genau tut

| Schritt | Ergebnis |
|---|---|
| 1 | Fügt `[versions.mc261]` in `gradle/fabricmultiloader.toml` ein — an der richtigen Position (sortiert), mit allen Pflichtfeldern und den `capabilities` der Vorlage |
| 2 | Erstellt `versions/mc-26.1/build.gradle.kts` (4 Zeilen + `dependencies`-Block der Vorlage) |
| 3 | Erstellt `versions/mc-26.1/src/main/{java,resources}` |
| 4 | Kopiert bei `--copy-from` den Java-Quellcode der Vorlage und **benennt das Package um** (`com.example.mc1214` → `com.example.mc261`), inklusive aller Importe und der `package`-Zeilen, sowie die Klassennamen-Suffixe (`Platform1214` → `Platform261`) |
| 5 | Kopiert und benennt Mixin-Configs (`examplemod-mc261.mixins.json`, `.client.mixins.json`) und passt `package`, `refmap` und `compatibilityLevel` (`JAVA_25`) an |
| 6 | Kopiert die AW-Datei als `examplemod-mc261.accesswidener` |
| 7 | Prüft `[container] baselineJava` — bleibt bei 17 (Minimum), kein Eingriff nötig; hätte die neue Version das Minimum gesenkt, würde der Task es anpassen und darauf hinweisen |
| 8 | Prüft Disjunktheit der neuen Range gegen alle bestehenden und bricht mit `OMNI-1010` ab, wenn sie überlappt |
| 9 | Ergänzt die CI-Matrix in `.github/workflows/integration.yml` (per YAML-Patch, kommentiert markiert) |
| 10 | Gibt eine Checkliste der verbleibenden manuellen Schritte aus |

## 37.3 Ausgabe des Tasks

```
> Task :addMinecraftVersion

Added Minecraft 26.1 as payload 'mc261'.

  matrix       gradle/fabricmultiloader.toml            [versions.mc261]
  project      versions/mc-26.1/build.gradle.kts
  sources      versions/mc-26.1/src/main/java/com/example/mc261/   (22 files copied from mc1214)
  mixins       examplemod-mc261.mixins.json, examplemod-mc261.client.mixins.json  (compatibilityLevel JAVA_25)
  widener      examplemod-mc261.accesswidener
  ci           .github/workflows/integration.yml        (matrix entry added)

  container baseline java stays 17 (minimum of 17, 21, 21, 25)
  effective ranges after subtraction:
      mc1201  >=1.20.1 <1.20.2
      mc1211  >=1.21 <1.21.2
      mc1214  >=1.21.4 <1.21.5
      mc261   >=26.1 <26.2
  disjointness  OK

Next steps
  1. ./gradlew :versions:mc-26.1:build          — expect compile errors; each one is a real API change
  2. fix them in versions/mc-26.1/src/main/java/com/example/mc261/
  3. ./gradlew runClient261                     — verify in-game (requires JDK 25)
  4. ./gradlew runDatagen261                    — regenerate data if formats changed
  5. ./gradlew buildUniversalJar validateUniversalJar
  6. ./gradlew integrationTestMc261
  7. update capabilities in [versions.mc261] if the new version gained or lost features
  8. add 26.1 to the README support table (or run ./gradlew omniReport)
```

## 37.4 Der Java-25-Sprung im Detail

Beim Wechsel auf eine MC-Version mit höherem Java-Bedarf passiert genau das:

| Betroffen | Änderung | Automatisch? |
|---|---|---|
| `[versions.mc261].java = 25`, `javaRange = ">=25"` | neu | ja (CLI-Parameter) |
| Toolchain des Version-Moduls | JDK 25, `options.release = 25` | ja (aus der Matrix) |
| `payload.classfileMajor` | 69 | ja (gemessen beim Manifest-Bau) |
| Payload-`depends.java` | `>=25` | ja |
| Container-`depends.java` | bleibt `>=17` | ja (Minimum) |
| Container-Bytecode | bleibt Classfile 61 | ja (unverändert) |
| Mixin `compatibilityLevel` | `JAVA_25` | ja (Scaffolding) |
| CI-Job | zusätzlicher Matrixeintrag mit `java: 25` | ja |
| Entwickler-JDK | JDK 25 muss verfügbar sein | Toolchain-Resolver lädt es, sonst `OMNI-1090` |

Was **nicht** passiert: Die Universal-JAR wird auf Java 17 nicht unbenutzbar. Der Java-25-Payload wird auf einer
Java-17-JVM vom Solver verworfen (`depends.java >=25`), seine Classfiles mit Major 69 werden nie gelesen. Die
Mod funktioniert auf 1.20.1/Java 17 unverändert weiter. Das ist der Kern des Nutzenversprechens — und der Grund,
warum die Architektur den Java-Sprung ohne Sonderfall verkraftet.

## 37.5 Wenn sich das Versionsschema ändert (`1.21.x` → `26.1`)

`SemVer.parseLenient("26.1")` ergibt `26.1.0`, `parseLenient("1.21.4")` ergibt `1.21.4`. Die Ordnung
`1.21.4 < 26.1.0` ist korrekt (Major-Vergleich). Alle Ranges, Unions und Subtraktionen funktionieren unverändert.
Fabric selbst normalisiert seine `minecraft`-Modversion auf dasselbe Schema, weshalb der Vergleich
Container↔Loader konsistent bleibt.

Der einzige Punkt, der Aufmerksamkeit braucht: `minecraftOrdinal()` (Kapitel 18.5) — die Kompaktform muss
weiterhin monoton sein. Definition: `major * 10000 + minor * 100 + patch` ⇒ `1.21.4` → 12104,
`26.1` → 260100. Monoton, kollisionsfrei bis Minor 99/Patch 99, dokumentiert und getestet
(`MinecraftOrdinalTest` mit Fällen 1.16.5, 1.20.1, 1.21.4, 26.1, 26.10, 27.0).

## 37.6 Version entfernen

```bash
./gradlew removeMinecraftVersion --id=mc1201 --confirm
```

Löscht Matrixeintrag, Verzeichnis und CI-Eintrag, aktualisiert `baselineJava` (steigt eventuell von 17 auf 21!)
und weist ausdrücklich darauf hin, dass damit die **Container-Baseline steigt** — was bedeutet, dass Common-Code
neu kompiliert wird und nun Java-21-Bytecode enthalten darf. Das ist ein Breaking Change für Nutzer der alten
MC-Version und wird deshalb mit einer Warnung und einem Changelog-Vorschlag begleitet.

---

# 38. Documentation Architecture

## 38.1 Struktur

```
docs/
├── index.md                         Startseite: Was, für wen, in 60 Sekunden
├── getting-started.md               Quick Start (Template → erste JAR in 10 Minuten)
├── concepts.md                      Container, Payload, Common, Adapter, Runtime — Begriffe und Bild
├── architecture.md                  Wie es funktioniert (verdichtete Fassung dieses Dokuments)
├── gradle-plugin.md                 Plugin-IDs, Tasks, Konfiguration, Troubleshooting des Builds
├── gradle-dsl.md                    generierte DSL-Referenz (alle Extensions, Properties, Defaults)
├── matrix.md                        gradle/fabricmultiloader.toml — jedes Feld, jede Regel
├── version-modules.md              Aufbau eines Version-Moduls, shared-Sourceset, Mapping-Provider
├── common-code.md                  Was darf in common, Anti-Patterns, Stabilitätstabelle der Fabric-Events
├── api-reference.md                Einstieg in die Javadoc-API-Referenz (verlinkt)
├── registries.md                   Items, Blöcke, Sounds, Item-Gruppen, deferred registration
├── networking.md                   ChannelSpec, Codecs, Threading, Versionsunterschiede
├── commands.md                     CommandSpec, Argumenttypen, Permissions
├── events.md                       Event-Katalog, Subscriptions, Lifecycle
├── mixins.md                       Payload-Mixins, Namensschema, Conditional Mixins, Fallen
├── access-wideners.md              shared vs. payload, Merge, @Accessor-Alternative
├── resources.md                    Merge-Regeln, allowOverride, Datagen, Lang-Merge
├── dependencies.md                 omniMod/omniOptionalMod/omniInclude, Fabric API, Kotlin
├── client-server.md                Seitentrennung auf drei Ebenen
├── testing.md                      FakeModContext, Unit-Tests, Integrationstests, CI
├── distribution.md                 Modrinth, CurseForge, Dateinamen, Changelog, Checksummen
├── migration.md                    Kapitel 36 als Anleitung
├── adding-a-version.md             Kapitel 37 als Anleitung
├── removing-a-version.md           inkl. Baseline-Effekt
├── errors.md                       Jeder OMNI-Code mit Ursache, Diagnose, Lösung (Anker-Ziel aller Meldungen)
├── troubleshooting.md              Symptomorientiert: „Mod erscheint nicht“, „Mixin-Crash“, „falsches Payload“
├── faq.md                          20 Fragen inkl. „Ist das nicht riskant?“, „Wie groß wird die JAR?“
├── performance.md                  Messwerte, Startzeit, Extraktion, Caching
├── security.md                     Bedrohungsmodell, Hashes, Zip-Slip, tmp-Dateien
├── compatibility.md               Garantien und Grenzen (Kapitel 41)
├── versioning.md                   SemVer, Format-, Schema-, Plugin-Versionen (Kapitel 42)
├── release-guide.md                Für Modautoren: von Tag zu veröffentlichter Datei
├── contributing.md                 Für Framework-Contributors: Setup, Codestil, Java-8-Regel, Reviewregeln
├── internals/
│   ├── loader-assumption.md        Die tragende Annahme, Beweisführung, Conformance-Tests, Rückfallplan
│   ├── boot-sequence.md            Phasen, Klassen, Zeitachse
│   ├── container-format.md         Omni v1 Spezifikation (normativ)
│   ├── manifest-schema.md          JSON-Schema, Felder, Forward-Compat
│   ├── resolver.md                 Versionsalgebra, Disjunktheit, Range-Subtraktion
│   ├── classloading.md             Ein-ClassLoader-Modell, Kollisionsfälle
│   ├── validator-rules.md          alle 34 Regeln im Detail
│   └── adr/                        ADR-001 … ADR-010 (Kapitel 43)
└── api/                            generierte Javadoc (api, format, runtime-public)
```

## 38.2 Inhaltsvorgaben pro Seite (Auszug der wichtigsten)

| Seite | Muss enthalten |
|---|---|
| `getting-started.md` | Voraussetzungen (JDK, Gradle), `git clone` des Templates, `runClient`, erste Codeänderung, `buildUniversalJar`, wo die Datei liegt, was als nächstes zu lesen ist. Maximal 10 Minuten Lesezeit, jeder Befehl kopierbar. |
| `concepts.md` | Das Diagramm aus Kapitel 8.1, die fünf Begriffe, die drei Isolationsebenen, ein Satz zu „warum nicht ein Kompilat“. |
| `common-code.md` | Erlaubte/verbotene Referenzen, drei Anti-Pattern mit Fehlercode, die Event-Stabilitätstabelle, Entscheidungsbaum „common vs. shared vs. payload“. |
| `mixins.md` | Namensschema, warum Payload-Isolation funktioniert, `environment`-Zuordnung, Conditional Mixins mit vollständiger Config, die drei dokumentierten Fallen (Fremdmod-Targets, `targets`-Strings, `compatibilityLevel`). |
| `errors.md` | Pro Code: Titel, Ursache, „so sieht die Meldung aus“, Diagnoseschritte, Lösung, verwandte Codes. Wird durch `ErrorCodeDocumentationTest` erzwungen. |
| `internals/loader-assumption.md` | Herleitung aus dem Loader-Quellcode mit Klassennamen, die Conformance-Testliste, was passiert, wenn die Annahme bricht, der Rückfallpfad. **Die wichtigste Seite für künftige Maintainer.** |
| `compatibility.md` | Tabelle aus Kapitel 41 mit Garantie/Grenze/Begründung/Workaround. |
| `contributing.md` | Java-8-Regel für `format`/`api`/`runtime` mit Begründung, Verbot eigener ClassLoader, Pflicht zu Fehlercode + Doku-Anker + Test bei jedem neuen Fehlerpfad, Golden-File-Workflow. |

## 38.3 Erzeugung und Prüfung

* Site: MkDocs Material, `mkdocs.yml` im Repo, Deployment über `docs.yml` nach GitHub Pages.
* Javadoc: `./gradlew javadocAll` erzeugt eine kombinierte Referenz für `api` + `format`; Doclet-Konfiguration
  mit `-Xdoclint:all,-missing` und Fehlerabbruch bei defekten Links.
* DSL-Referenz: aus KDoc der Extension-Interfaces generiert (`./gradlew generateDslReference`), damit
  Dokumentation und Code nicht divergieren.
* Prüfungen in CI: toter Link (`lychee`), fehlender Fehlercode-Abschnitt (`ErrorCodeDocumentationTest`),
  Codeblöcke in `docs/**` müssen kompilieren (`docs-snippets`-Sourceset mit den Beispielen aus
  `getting-started`, `common-code`, `networking`, `registries`).

Der letzte Punkt ist entscheidend für Langlebigkeit: **Alle Java-Beispiele der Dokumentation liegen als echte,
kompilierte Quellen im Repository** und werden bei jedem Build gegen die aktuelle API kompiliert. Ein
API-Umbau, der die Doku veraltet macht, bricht damit den Build.

---

Weiter mit [Kapitel 39–42 — Security, Performance, Kompatibilitätsgrenzen, Versionierung](part-10-nfr.md).
