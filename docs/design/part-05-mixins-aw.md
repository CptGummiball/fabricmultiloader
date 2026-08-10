# 16. Mixin Architecture

## 16.1 Das Prinzip

> **Mixins werden nicht „gefiltert“, sie werden „nicht ausgeliefert“.**
> Ein Mixin-Set gehört zu genau einem Payload. Wird das Payload nicht geladen, existiert seine Mixin-Config nicht
> im Registrierungsvorgang, und seine Mixin-Klassen liegen nicht auf dem Classpath.

Das löst die schwierigste Anforderung des Auftrags („Eine 1.20.1-Mixin-Klasse darf unter 1.21.4 nicht
versehentlich geladen oder validiert werden“) nicht durch Filterlogik, sondern durch Nichtexistenz — die einzige
Form von Isolation, die gegen Sponge Mixins eager arbeitende `ClassInfo`-Auflösung immun ist (Kapitel 5.3.2).

## 16.2 Verzeichnis- und Namenskonvention

```
versions/mc-1.21.4/src/main/
├── java/com/example/mc1214/
│   ├── Platform1214.java
│   ├── Platform1214Factory.java
│   ├── mixin/                                  ← common-side Mixins
│   │   ├── MinecraftServerMixin.java
│   │   └── ItemStackMixin.java
│   └── client/mixin/                           ← client-only Mixins
│       ├── TitleScreenMixin.java
│       └── ItemRendererMixin.java
└── resources/
    ├── examplemod-mc1214.mixins.json           ← generiert? NEIN: handgeschrieben (s. 16.3)
    ├── examplemod-mc1214.client.mixins.json
    └── examplemod-mc1214.accesswidener
```

| Element | Konvention | Erzwungen durch |
|---|---|---|
| Mixin-Package | `<basePackage>.<payloadId>.mixin` bzw. `….<payloadId>.client.mixin` | Validator `OMNI-1034` |
| Config-Dateiname | `<modId>-<payloadId>.mixins.json`, `<modId>-<payloadId>.client.mixins.json`, `<modId>-<payloadId>.server.mixins.json` | Plugin-Default + Validator `OMNI-1030` |
| Refmap-Name | `<modId>-<payloadId>-refmap.json` | Loom-Property, vom Plugin gesetzt |
| Access-Widener-Name | `<modId>-<payloadId>.accesswidener` | Plugin-Default |

Die Namensschemata enthalten immer die `payloadId`. Damit sind Config-, Refmap- und AW-Namen über die gesamte
Universal-JAR eindeutig — nicht weil es technisch nötig wäre (es lädt nie mehr als ein Payload), sondern damit
Stacktraces, Slim-Jars, Crash-Reports und manuelles Debugging eindeutig sind.

## 16.3 Mixin-Configs: handgeschrieben, aber validiert

Mixin-Configs werden **nicht** generiert. Begründung: Sie enthalten fachliche Entscheidungen
(`compatibilityLevel`, `mixins`-Auswahl, `injectors.defaultRequire`, Plugin-Klasse), die der Entwickler bewusst
treffen muss; ein Generator würde entweder alles über Konventionen erraten (fragil) oder eine zweite DSL
erfordern (redundant). Stattdessen prüft der Validator sie streng, und der `omniPayload`-Task trägt sie
automatisch in die generierte `fabric.mod.json` und in das Manifest ein — der Entwickler pflegt sie also an genau
einer Stelle.

`versions/mc-1.21.4/src/main/resources/examplemod-mc1214.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "injectors": { "defaultRequire": 1 },
  "mixins": [
    "MinecraftServerMixin",
    "ItemStackMixin"
  ]
}
```

`versions/mc-1.21.4/src/main/resources/examplemod-mc1214.client.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.client.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "injectors": { "defaultRequire": 1 },
  "client": [
    "TitleScreenMixin",
    "ItemRendererMixin"
  ]
}
```

`versions/mc-26.1/src/main/resources/examplemod-mc261.mixins.json` unterscheidet sich nur in
`compatibilityLevel: "JAVA_25"` und dem Package — ein weiterer Grund, Configs pro Payload zu halten: Der
Kompatibilitätslevel ist versionsgebunden und darf nicht höher sein als die Ziel-JVM.

**Validator-Regeln für Mixin-Configs:**

| Code | Prüfung |
|---|---|
| `OMNI-1100` | `package` beginnt mit dem für dieses Payload deklarierten Präfix. |
| `OMNI-1101` | Jede in `mixins`/`client`/`server` genannte Klasse existiert im Payload unter `package`. |
| `OMNI-1102` | Jede Mixin-Klasse im Payload ist in genau einer Config genannt (fängt „vergessene“ Mixins, die stumm nichts tun). |
| `OMNI-1103` | `refmap` existiert im Payload, ist valides JSON und enthält nur Klassen dieses Payloads. |
| `OMNI-1104` | `compatibilityLevel` ≤ `JAVA_<payload.requires.java-Minimum>`. |
| `OMNI-1105` | Client-Mixin-Klassen (Package `*.client.mixin`) sind ausschließlich in einer Config mit `environment: "client"` registriert. |
| `OMNI-1106` | Keine Mixin-Klasse referenziert `net/minecraft/client/**`, wenn sie in einer nicht-client Config steht. |
| `OMNI-1107` | `required: true` ist gesetzt (sonst verschluckt Mixin Fehler stillschweigend). |
| `OMNI-1108` | Kein Container-Eintrag deklariert Mixins (`OMNI-1024`-Gegenstück: Container = mixinfrei). |

## 16.4 Environment-Zuordnung in `fabric.mod.json`

Der `omniPayload`-Task ordnet Configs automatisch zu, anhand des Dateinamens:

| Dateiname endet auf | Erzeugter Eintrag |
|---|---|
| `.client.mixins.json` | `{ "config": "…", "environment": "client" }` |
| `.server.mixins.json` | `{ "config": "…", "environment": "server" }` |
| `.mixins.json` | `"…"` (also `environment: "*"`) |

Der `environment`-Filter in `fabric.mod.json` ist der **wirksamere** der beiden möglichen Mechanismen: Er
verhindert die Registrierung der Config auf einem dedizierten Server komplett, sodass Mixin die Klassen nie
liest. Die configinterne `"client": [...]`-Liste wird zusätzlich verwendet (Doppelsicherung), ist aber allein
nicht ausreichend, weil Mixin die Klassen der Config trotzdem parst.

## 16.5 Versionsspezifische Mixins — konkretes Beispiel

Die Signaturdivergenz von `ItemRenderer#renderItem` zwischen 1.20.1 und 1.21.4 ist ein realer Fall.

`versions/mc-1.20.1/src/main/java/com/example/mc1201/client/mixin/ItemRendererMixin.java`:

```java
package com.example.mc1201.client.mixin;

import com.example.common.hook.ItemRenderHooks;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    // 1.20.1: renderItem(LivingEntity, ItemStack, ModelTransformationMode, boolean,
    //                    MatrixStack, VertexConsumerProvider, World, int, int, int)
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;"
                   + "Lnet/minecraft/item/ItemStack;"
                   + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
                   + "ZLnet/minecraft/client/util/math/MatrixStack;"
                   + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                   + "Lnet/minecraft/world/World;III)V",
            at = @At("HEAD"))
    private void examplemod$beforeRenderItem(LivingEntity entity, ItemStack stack,
                                             ModelTransformationMode mode, boolean leftHanded,
                                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             World world, int light, int overlay, int seed,
                                             CallbackInfo ci) {
        ItemRenderHooks.beforeRender(new Mc1201ItemRenderContext(stack, mode.name(), leftHanded));
    }
}
```

`versions/mc-1.21.4/src/main/java/com/example/mc1214/client/mixin/ItemRendererMixin.java`:

```java
package com.example.mc1214.client.mixin;

import com.example.common.hook.ItemRenderHooks;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemDisplayContext;      // in 1.21.4 anderer Typ als 1.20.1
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void examplemod$beforeRenderItem(ItemStack stack, ItemDisplayContext context,
                                             boolean leftHanded, CallbackInfo ci) {
        ItemRenderHooks.beforeRender(new Mc1214ItemRenderContext(stack, context.name(), leftHanded));
    }
}
```

Beide rufen **denselben** Common-Hook `ItemRenderHooks.beforeRender(ItemRenderContext)` auf. `ItemRenderContext`
ist ein Common-Interface ohne Minecraft-Typen:

```java
package com.example.common.hook;

public interface ItemRenderContext {
    String itemId();          // "minecraft:diamond_sword"
    int count();
    String displayMode();     // "GUI", "FIRST_PERSON_RIGHT_HAND", …
    boolean leftHanded();
}
```

Das ist das Muster für **jeden** versionsspezifischen Mixin: *Der Mixin lebt im Payload, die Fachlogik lebt im
Common-Code, die Grenze ist ein minecraftfreies Interface.* Damit bleibt der versionsspezifische Anteil auf
wenige Zeilen Adapterlogik begrenzt.

Wäre `ItemRendererMixin` unter 1.21.4 aus dem 1.20.1-Payload geladen worden, wäre das Ergebnis ein harter
`InvalidInjectionException` beim ersten Rendern — der Fehler, den diese Architektur unmöglich macht: Die
1.20.1-Klasse existiert unter 1.21.4 nicht auf dem Classpath.

## 16.6 Conditional Mixins *innerhalb* eines Payloads

Innerhalb eines Payloads bleibt ein legitimer Bedarf an Bedingungen: Integrations-Mixins, die nur greifen
sollen, wenn eine optionale Fremdmod geladen ist, oder die per Config abschaltbar sein sollen.
FabricMultiLoader liefert dafür ein deklaratives Config-Plugin.

`examplemod-mc1214.integration.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8.5",
  "package": "com.example.mc1214.integration.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod-mc1214-refmap.json",
  "plugin": "dev.fabricmultiloader.runtime.mixin.ConditionalMixinPlugin",
  "mixins": ["ClothConfigScreenMixin", "JeiPluginMixin"],
  "injectors": { "defaultRequire": 1 },
  "omni": {
    "conditions": {
      "ClothConfigScreenMixin": { "requireMod": "cloth-config", "version": ">=15.0.0" },
      "JeiPluginMixin":         { "requireMod": "jei" }
    },
    "defaultDecision": "apply"
  }
}
```

Implementierung (vollständig, ohne Auslassungen der relevanten Logik):

```java
package dev.fabricmultiloader.runtime.mixin;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deklaratives Mixin-Config-Plugin. Liest den "omni"-Block der eigenen Mixin-Config
 * und entscheidet je Mixin-Klasse, ob sie angewendet wird.
 *
 * WICHTIG: Diese Klasse laeuft VOR der preLaunch-Phase (Mixin select()).
 * Sie darf ausschliesslich JDK-, format- und FabricLoader-API benutzen und
 * insbesondere NICHT RuntimeBootstrap anstossen. Validator-Regel OMNI-1035.
 */
public final class ConditionalMixinPlugin implements IMixinConfigPlugin {

    private final Map<String, Condition> conditions = new HashMap<String, Condition>();
    private boolean defaultApply = true;
    private String configName = "<unknown>";

    @Override
    public void onLoad(String mixinPackage) {
        // Der Config-Name ist ueber getRefMapperConfig() nicht verfuegbar; Mixin uebergibt
        // in onLoad nur das Package. Wir leiten die Config-Datei ueber das Package ab,
        // indem wir alle Mixin-Configs des Classpath scannen, die dieses Package deklarieren.
        for (String candidate : ConfigLocator.configsForPackage(mixinPackage)) {
            configName = candidate;
            parse(candidate);
        }
    }

    private void parse(String resource) {
        try (InputStream in = ConditionalMixinPlugin.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) return;
            JsonObject root = Json.parse(readAll(in)).asObject();
            JsonObject omni = root.optObject("omni");
            if (omni == null) return;
            defaultApply = !"skip".equals(omni.optString("defaultDecision", "apply"));
            JsonObject cond = omni.optObject("conditions");
            if (cond == null) return;
            for (String simpleName : cond.keys()) {
                conditions.put(simpleName, Condition.parse(cond.getObject(simpleName)));
            }
        } catch (Exception e) {
            // Ein defektes Plugin darf den Start nicht sprengen: fail-open mit Warnung.
            PluginLog.warn("OMNI-2200 could not read conditional mixin config '" + resource
                    + "': " + e + " — all mixins in this config will be applied.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        Condition c = conditions.get(simple);
        if (c == null) return defaultApply;
        boolean apply = c.evaluate();
        PluginLog.debug("OMNI-2201 " + configName + ": " + simple
                + (apply ? " applied" : " skipped (" + c.describe() + ")"));
        return apply;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return Collections.emptyList(); }
    @Override public void preApply (String t, ClassNode n, String m, IMixinInfo i) { }
    @Override public void postApply(String t, ClassNode n, String m, IMixinInfo i) { }

    /** requireMod + optionale Version, requireProperty, requireEnv. */
    static final class Condition {
        String requireMod; VersionRange version; String requireProperty; String requireEnv;

        boolean evaluate() {
            FabricLoader loader = FabricLoader.getInstance();
            if (requireMod != null) {
                if (!loader.isModLoaded(requireMod)) return false;
                if (version != null) {
                    SemVer v = SemVer.parseLenient(loader.getModContainer(requireMod).get()
                            .getMetadata().getVersion().getFriendlyString());
                    if (!version.test(v)) return false;
                }
            }
            if (requireProperty != null && !Boolean.parseBoolean(System.getProperty(requireProperty, "false")))
                return false;
            if (requireEnv != null && !requireEnv.equalsIgnoreCase(loader.getEnvironmentType().name()))
                return false;
            return true;
        }
        String describe() { … }
        static Condition parse(JsonObject o) { … }
    }
}
```

**Grenzen, die dokumentiert werden müssen** (`docs/mixins.md`):

* `shouldApplyMixin` verhindert die *Anwendung*, nicht das *Laden und Validieren* der Mixin-Klasse. Eine
  Integrations-Mixin-Klasse darf daher **nur** Typen referenzieren, die in dieser MC-Version existieren; für
  Fremdmod-Typen bedeutet das: Der Mixin darf `cloth-config`-Klassen im Rumpf verwenden (lazy aufgelöst), aber
  **nicht** in `@Mixin(Target.class)` — dort ist ein `targets = "…"`-String mit voll qualifiziertem Namen nötig,
  weil sonst die Zielklasse eager aufgelöst wird und ohne die Fremdmod fehlt.
* Deshalb gilt für optionale Fremdmod-Integrationen zusätzlich: Mixin-Config nur registrieren, wenn die Mod
  vorhanden ist, geht **nicht** deklarativ (die `fabric.mod.json` ist statisch). Der robuste Weg für harte
  Fremdmod-Abhängigkeiten ist ein **eigenes Payload** mit `requires.mods` — oder Verzicht auf Mixin und
  Verwendung der offiziellen API der Fremdmod.

## 16.7 Fehlerbehandlung bei Mixin-Problemen

| Situation | Wer meldet | Verbesserung durch FabricMultiLoader |
|---|---|---|
| Mixin-Target existiert nicht (falsches Payload für diese MC-Version — durch Fehlkonfiguration der Matrix) | Mixin `InvalidInjectionException` beim Class-Load | Die Runtime installiert in `PayloadPreLaunch` einen `Thread.UncaughtExceptionHandler`-unabhängigen **Crash-Report-Anhang** über den Payload-Adapter (`Platform#installCrashContext`), der in jedem Crash-Report den Block „FabricMultiLoader: examplemod 2.0.0, payload mc1214, mc 1.21.4, java 21“ ergänzt. Damit ist im Bugreport sofort sichtbar, welches Payload aktiv war. |
| Mixin-Config im Payload, aber nicht in `fabric.mod.json` | niemand (Mixin greift stumm nicht) | Validator `OMNI-1109`: Jede `*.mixins.json` im Payload muss in der `fabric.mod.json` registriert sein. |
| Mixin-Config in `fabric.mod.json`, Datei fehlt | Loader: harter Startfehler | Validator `OMNI-1110` fängt es zur Build-Zeit. |
| Zwei Payloads mit identischem Config-Namen | niemand (nur eines lädt) | Validator `OMNI-1030` erzwingt Eindeutigkeit. |
| `compatibilityLevel` höher als JVM | Mixin `IllegalArgumentException` beim Start | Validator `OMNI-1104`. |

## 16.8 Warum es keinen „Mixin-Dispatcher“ gibt

Ein zentraler „Mixin Dispatcher“, der zur Laufzeit entscheidet, welche Mixin-Sets aktiv sind, war in der
ursprünglichen Projektidee vorgesehen. Er ist in dieser Architektur **nicht vorhanden und nicht nötig** — und
das ist eine Verbesserung, nicht ein Weglassen:

* Ein Dispatcher könnte Mixins erst nach `MixinBootstrap` registrieren (Phase 2.4 ist vorbei, wenn Modcode
  läuft). Nachträgliche `Mixins.addConfiguration`-Aufrufe sind nicht spezifiziert und in Fabric nicht
  unterstützt.
* Ein Dispatcher könnte die eagere `ClassInfo`-Auflösung nicht verhindern.
* Der Loader macht die Auswahl bereits — deterministisch, vor jeder Klassenberührung, mit vollständiger
  Fehlerdiagnose.

Die Rolle, die der Dispatcher übernehmen sollte (Sicherstellen, dass nur die richtigen Mixins greifen),
übernimmt damit vollständig die Payload-Auswahl des Loaders plus die Build-Zeit-Validierung.

---

# 17. Access Widener Architecture

## 17.1 Ausgangsproblem

Fabric Loader akzeptiert genau **einen** `accessWidener`-Pfad pro Mod, die Datei ist mappingabhängig
(Namespace-Header wird geprüft), und Mitglieder-Namen können zwischen MC-Versionen differieren. Eine einzige
versionsübergreifende AW-Datei ist damit nicht mappingkorrekt herstellbar (Kapitel 5.4.2).

## 17.2 Lösung

**Ein Access Widener pro Payload.** Da ein Payload eine eigene Fabric-Mod ist, gilt die „eine Datei pro
Mod“-Regel pro Payload — nicht pro Universal-JAR. Der Loader merged die AW-Dateien aller *geladenen* Mods; da nur
ein Payload geladen ist, ist genau ein mod-eigener AW aktiv.

```
common/src/main/accesswidener/shared.accesswidener            (Namespace named, versionsneutral gemeint)
versions/mc-1.20.1/src/main/resources/examplemod-mc1201.accesswidener   (Namespace named, versionsspezifisch)
versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener
versions/mc-26.1/src/main/resources/examplemod-mc261.accesswidener
        │
        │  mergeAccessWidener<Payload>   (Build-Zeit, VOR Loom-Remap, im Namespace named)
        ▼
versions/mc-X/build/omni/accesswidener/examplemod-mcX.accesswidener
        │  Loom remapJar  (named → intermediary)
        ▼
Payload:  examplemod-mcX.accesswidener   (Namespace intermediary)
```

## 17.3 Merge-Semantik

`MergeAccessWidenerTask` (Gradle, deklarierte In-/Outputs, cachebar):

1. Liest die gemeinsame Datei `common/src/main/accesswidener/shared.accesswidener`, falls vorhanden.
2. Liest die payload-spezifische Datei, falls vorhanden.
3. Prüft: Beide müssen `accessWidener v2 named` als Header haben (`OMNI-1120`, sonst Fehler mit Zeilenangabe).
4. Schreibt einen Header `accessWidener v2 named` und danach die Vereinigung der Einträge:
   * Zeilenweise Normalisierung: Kommentare (`#`) entfernt, Whitespace kollabiert, leere Zeilen entfernt.
   * Deduplizierung über den normalisierten Text.
   * Sortierung: nach `(typ, klasse, member)` lexikographisch — für Reproduzierbarkeit.
   * Bei widersprüchlichen Einträgen für dasselbe Ziel (`accessible` vs. `extendable` vs. `mutable`) werden
     **alle** behalten (AW ist additiv, kein Konflikt).
5. Voranstellen eines generierten Kommentarblocks mit Quelle jeder Zeile — für Debugging:

```
accessWidener v2 named
# generated by fabricmultiloader-gradle 1.0.0 for payload mc1214 — do not edit
# sources: common/src/main/accesswidener/shared.accesswidener (3 entries)
#          versions/mc-1.21.4/src/main/resources/examplemod-mc1214.accesswidener (2 entries)
accessible	field	net/minecraft/client/MinecraftClient	itemUseCooldown	I
accessible	method	net/minecraft/entity/player/PlayerEntity	getAttackCooldownProgress	(F)F
extendable	class	net/minecraft/block/AbstractBlock
mutable	field	net/minecraft/entity/LivingEntity	activeItemStack	Lnet/minecraft/item/ItemStack;
```

`shared.accesswidener` ist **kein** Versprechen, dass die Einträge in allen Versionen existieren — sie ist eine
Bequemlichkeit für den häufigen Fall. Existiert ein Eintrag in einer Version nicht, ist er dort wirkungslos
(Kapitel 5.4.1); der Validator warnt (`OMNI-1121`), wenn ein Eintrag in einem Payload keine passende Klasse in
dessen Intermediary-Mappings findet — dazu liest er die von Loom bereitgestellte Tiny-Mappings-Datei des
jeweiligen Version-Moduls. Diese Prüfung ist eine **Warnung**, keine Fehlermeldung, weil AW-Einträge legitim auf
optionale Ziele zeigen können.

## 17.4 Access Widener für Fremdmod-Klassen

Access Widener können nur auf Klassen wirken, die durch den Knot-Transformer laufen — das sind Minecraft **und
alle Mods**. Ein AW-Eintrag auf eine Fremdmod-Klasse ist also technisch möglich, aber:

* Der Namespace-Header ist `named`/`intermediary` und bezieht sich auf Minecraft-Mappings; Fremdmod-Klassen sind
  nicht gemappt und müssen mit ihrem echten FQCN eingetragen werden. Loom remappt sie nicht (kein Mapping-Eintrag
  ⇒ unverändert durchgereicht), das funktioniert also.
* Der Validator warnt (`OMNI-1122`), weil es eine fragile Kopplung an Fremdmod-Interna ist, und verlangt eine
  explizite Freigabe: `omni { allowForeignAccessWidener("cloth-config") }` in der DSL des Version-Moduls.

## 17.5 Warum kein eigener Transformer, keine Reflection, kein Build-Time-Bytecode-Patch

Alternativen, die geprüft und verworfen wurden:

| Alternative | Verworfen weil |
|---|---|
| Eigener `AccessWidener` zur Laufzeit nachladen | Der `AccessWidenerClassTransformer` wird in Phase 2.3g gebaut; keine öffentliche API zum Nachreichen. Reflection auf `FabricLoaderImpl#getAccessWidener` verstößt gegen G3. |
| Reflection statt Access Widening im Modcode | Funktioniert für Feldzugriff und Methodenaufruf, aber nicht für `extendable` (Vererbung von final-Klassen) und nicht performant in Hot Paths. Zudem bricht `setAccessible` auf JDK 17+ bei JPMS-geschützten Paketen — nicht bei Minecraft (unnamed module), aber die Fehlerklasse ist unangenehm. Als *Ergänzung* für Einzelfälle dokumentiert, nicht als Ersatz. |
| Build-Time-Bytecode-Patch (Klassen im Payload public machen) | Ändert Minecraft-Bytecode, der nicht Teil unseres Artefakts ist — unmöglich, weil Minecraft nicht mitgeliefert wird. |
| Mixin `@Accessor`/`@Invoker` statt AW | Legitime, oft **bessere** Alternative: versionsspezifisch (liegt im Payload), refmap-gestützt, ohne globale Sichtbarkeitsänderung. Die Dokumentation empfiehlt `@Accessor`/`@Invoker` als Standard und AW nur für `extendable`/`mutable` und für Zugriff aus vielen Klassen. |

## 17.6 Validator-Regeln (Zusammenfassung)

| Code | Regel | Schwere |
|---|---|---|
| `OMNI-1024` | Container deklariert **keinen** `accessWidener` | Fehler |
| `OMNI-1082` | AW-Namespace im fertigen Payload == `payload.mappings.namespace` (`intermediary`) | Fehler |
| `OMNI-1120` | Quelldateien haben Header `accessWidener v2 named` | Fehler |
| `OMNI-1121` | Jeder AW-Eintrag hat ein auflösbares Ziel in den Mappings des Payloads | Warnung |
| `OMNI-1122` | AW-Einträge auf Nicht-Minecraft-Klassen sind explizit freigegeben | Warnung/Fehler bei fehlender Freigabe |
| `OMNI-1123` | `payload.accessWidener` im Manifest == `accessWidener` in der Payload-`fabric.mod.json` == existierende Datei | Fehler |
| `OMNI-1124` | AW-Datei ist deterministisch sortiert (erkennt Handbearbeitung des generierten Artefakts) | Warnung |

---

Weiter mit [Kapitel 18–19, 26–28 — Common API, Version-Adapter-API, Client/Server, Networking, Registries](part-06-api.md).
