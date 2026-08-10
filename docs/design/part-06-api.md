# 18. Common API

## 18.1 Designprinzipien

| Prinzip | Konsequenz |
|---|---|
| **P1 — Keine Minecraft-Typen in der Common-API** | Jede Signatur benutzt nur JDK-Typen, `format`-Typen und eigene, opake Referenztypen (`PlayerRef`, `WorldRef`, `ItemStackRef`). Nur so ist der Container binärkompatibel über alle MC-Versionen. |
| **P2 — Deklarativ statt imperativ** | Inhalte werden als *Spezifikation* (`ItemSpec`) beschrieben, nicht als MC-Objekt gebaut. Der Adapter übersetzt die Spezifikation in die versionsspezifische Konstruktion. |
| **P3 — Handles statt Objekte** | Registrierung liefert einen `ItemHandle`, nicht ein `Item`. Der Handle ist über Versionen stabil und bietet `unwrap(Class)` als Escape Hatch. |
| **P4 — Escape Hatch statt Vollabstraktion** | Was nicht abstrahierbar ist, wird nicht schlecht abstrahiert, sondern über `Services` (typisierte, eigene Schnittstellen) und `Capabilities` (Feature-Gates) sauber in den Version-Layer verschoben. |
| **P5 — Java 8, keine Records, keine `sealed`** | Binärkompatibilität ab MC 1.16.5; Builder-Pattern statt Records. |
| **P6 — Additive Evolution** | Interfaces, die Modautoren implementieren, erhalten ausschließlich `default`-Methoden hinzu. Interfaces, die das Framework implementiert, dürfen wachsen. Getrennt markiert durch `@ImplementedByFramework` / `@ImplementedByMod`. |
| **P7 — Kein DI-Framework** | Ein Service-Locator (`ServiceRegistry`) mit 4 Methoden ersetzt Guice/Dagger. Begründung: kein Reflection-Scan, kein Startzeit-Overhead, keine zusätzliche Abhängigkeit im Container, triviale Debuggability. |

## 18.2 Paketübersicht

| Package | Inhalt |
|---|---|
| `dev.fabricmultiloader.api` | Entrypoint-Interfaces, `ModContext`, `Id`, `Side`, `ModLogger`, `Capability`, `Capabilities`, `ServiceRegistry`, `FabricMultiLoader` (statischer Zugang) |
| `dev.fabricmultiloader.api.platform` | `Platform`, `PlatformFactory`, `PlatformInfo`, `PreLaunchContext`, `CrashContext` |
| `dev.fabricmultiloader.api.registry` | `Registries`, `ItemSpec`, `BlockSpec`, `SoundSpec`, `ItemGroupSpec`, `*Handle`, `Rarity`, `ToolTier` |
| `dev.fabricmultiloader.api.net` | `Networking`, `ChannelSpec`, `PayloadCodec`, `ByteSink`, `ByteSource`, `ChannelHandle`, `C2SReceiver`, `S2CReceiver` |
| `dev.fabricmultiloader.api.command` | `Commands`, `CommandSpec`, `Arg`, `CommandInvocation`, `CommandSender`, `Permission` |
| `dev.fabricmultiloader.api.event` | `Events`, `Subscription`, `LifecyclePhase`, Event-Interfaces |
| `dev.fabricmultiloader.api.ref` | `PlayerRef`, `WorldRef`, `ItemStackRef`, `BlockPosRef`, `Unwrappable` |
| `dev.fabricmultiloader.api.config` | `ConfigHandle`, `ConfigCodec` (JSON über `format`-Parser) |
| `dev.fabricmultiloader.api.resource` | `ResourceReloadListener`, `PackHandle` |

## 18.3 Entrypoint-Interfaces

```java
package dev.fabricmultiloader.api;

/** Gemeinsamer Einstiegspunkt. Läuft in der Fabric-'main'-Phase, auf Client UND Server. */
@ImplementedByMod
public interface UniversalMod {
    void onInitialize(ModContext ctx);
}

/** Nur physischer Client. Läuft in der Fabric-'client'-Phase, nach onInitialize. */
@ImplementedByMod
public interface UniversalClientMod {
    void onInitializeClient(ModContext ctx);
}

/** Nur dedizierter Server. Läuft in der Fabric-'server'-Phase, nach onInitialize. */
@ImplementedByMod
public interface UniversalServerMod {
    void onInitializeServer(ModContext ctx);
}

/**
 * Optional: läuft in der Fabric-'preLaunch'-Phase, VOR dem Laden von Minecraft-Klassen.
 * Darf keine Registry-, Event- oder Networking-Aufrufe machen (IllegalStateException).
 * Gedacht für Config-Laden und frühe Diagnose.
 */
@ImplementedByMod
public interface UniversalPreLaunch {
    void onPreLaunch(PreLaunchContext ctx);
}
```

Registrierung erfolgt **nicht** über `fabric.mod.json`, sondern über die Omni-Manifest-Entrypoints, die entweder
in der Gradle-DSL deklariert oder vom Annotation Processor aus `@UniversalEntrypoint` abgeleitet werden
(Kapitel 19.7). Damit ist die Registrierung versionsunabhängig und für alle Payloads identisch.

## 18.4 `ModContext` — vollständige Definition

```java
package dev.fabricmultiloader.api;

import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.format.version.SemVer;

import java.nio.file.Path;
import java.util.Optional;

@ImplementedByFramework
public interface ModContext {

    // ---- Identität -------------------------------------------------------
    String modId();
    SemVer modVersion();
    String displayName();

    // ---- Umgebung --------------------------------------------------------
    PlatformInfo platform();
    Side side();                                  // CLIENT | SERVER (physisch)
    boolean isDevelopment();

    // ---- Infrastruktur ---------------------------------------------------
    ModLogger log();
    Path gameDir();
    Path configDir();                             // <gameDir>/config
    Path modConfigDir();                          // <gameDir>/config/<modId>, wird angelegt

    // ---- Subsysteme ------------------------------------------------------
    Registries registries();
    Networking networking();
    Commands commands();
    Events events();
    ServiceRegistry services();

    // ---- Fähigkeiten und Fremdmods --------------------------------------
    <T> Optional<T> capability(Capability<T> capability);
    boolean has(Capability<?> capability);
    boolean isModLoaded(String modId);
    Optional<SemVer> modVersionOf(String modId);

    // ---- Lifecycle-Zustand ----------------------------------------------
    LifecyclePhase phase();
}
```

`ModContext` ist ein Framework-Interface (P6): Es darf zwischen Minor-Versionen wachsen. Modautoren
implementieren es nie.

## 18.5 `PlatformInfo`

```java
package dev.fabricmultiloader.api.platform;

public interface PlatformInfo {
    SemVer minecraft();          // 1.21.4
    SemVer fabricLoader();       // 0.16.9
    Optional<SemVer> fabricApi();
    int javaMajor();             // 17 | 21 | 25 | …
    String payloadId();          // "mc1214"
    String mappingNamespace();   // "intermediary" | "named" (Dev)

    /** true, wenn die laufende MC-Version im angegebenen Fabric-Predicate-Bereich liegt. */
    boolean minecraftIn(String... predicates);

    /** Kompaktform für Vergleiche in Common-Code: 1.21.4 -> 12104, 26.1 -> 260100. */
    int minecraftOrdinal();
}
```

`minecraftIn(">=1.21")` und `minecraftOrdinal()` erlauben Common-Code, *Verhalten* zu variieren
(z. B. abweichende Default-Config-Werte), ohne MC-Typen zu berühren. Sie erlauben **nicht**, unterschiedliche
MC-API aufzurufen — das bleibt Sache des Adapters. Diese Grenze wird in `docs/common-code.md` mit einem
Anti-Pattern-Beispiel deutlich gemacht.

## 18.6 Opake Referenzen und Escape Hatch

```java
package dev.fabricmultiloader.api.ref;

public interface Unwrappable {
    /**
     * Liefert das zugrundeliegende Minecraft-Objekt.
     * NUR aus Version-Modulen aufrufen — Common-Code kann den Typ nicht referenzieren,
     * ohne seine Versionsneutralität zu verlieren.
     *
     * @throws ClassCastException wenn der Typ nicht passt (mit erklärender Message)
     */
    <T> T unwrap(Class<T> type);
}

public interface PlayerRef extends Unwrappable {
    java.util.UUID uuid();
    String name();
    Side side();
    WorldRef world();
    double x(); double y(); double z();
    boolean hasPermission(int level);
    void sendMessage(String plainText);
    void sendMessage(dev.fabricmultiloader.api.text.Text text);   // eigenes, minimales Text-Modell
}

public interface WorldRef extends Unwrappable {
    Id dimension();               // "minecraft:overworld"
    boolean isClient();
    long time();
}

public interface ItemStackRef extends Unwrappable {
    Id item();
    int count();
    ItemStackRef withCount(int count);
    boolean isEmpty();
}
```

`unwrap` ist der bewusst eingebaute, dokumentierte Ausweg. Er ist **im Common-Code nicht sinnvoll benutzbar**
(der Typ fehlt dort), aber im Version-Modul die Brücke zurück zur vollen MC-API:

```java
// im Payload 1.21.4
ServerPlayerEntity mcPlayer = playerRef.unwrap(ServerPlayerEntity.class);
```

Das eigene `Text`-Modell (`dev.fabricmultiloader.api.text`) umfasst `Text.literal`, `Text.translatable`,
`Text.of(...).color(...).bold()` und wird vom Adapter in `net.minecraft.text.Text` übersetzt. Es ist bewusst
minimal (Literal, Translatable, Farbe, Stil, Klick-/Hover-Aktion) und deckt die Fälle ab, die Common-Code
realistisch braucht.

## 18.7 Services — der typisierte Escape Hatch

```java
package dev.fabricmultiloader.api;

@ImplementedByFramework
public interface ServiceRegistry {
    <T> T get(Class<T> type);                       // wirft OmniException OMNI-4010, wenn fehlt
    <T> java.util.Optional<T> find(Class<T> type);
    <T> void register(Class<T> type, T impl);        // nur während Platform#onInitialize erlaubt
    java.util.Set<Class<?>> registered();
}
```

Verwendung: Der Modentwickler definiert im **Common**-Modul ein Interface ohne MC-Typen, implementiert es im
**Version**-Modul und ruft es aus Common auf.

```java
// common/src/main/java/com/example/common/service/OreGenService.java
package com.example.common.service;

public interface OreGenService {
    /** Fügt die Erz-Platzierung in die Weltgenerierung ein. */
    void installRubyOre(int veinSize, int perChunk, int minY, int maxY);
}
```

```java
// versions/mc-1.21.4/src/main/java/com/example/mc1214/service/OreGenService1214.java
package com.example.mc1214.service;

public final class OreGenService1214 implements OreGenService {
    @Override public void installRubyOre(int veinSize, int perChunk, int minY, int maxY) {
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of("examplemod", "ruby_ore")));
        // versionsspezifische Registrierung von ConfiguredFeature/PlacedFeature …
    }
}
```

```java
// common: Aufruf ohne jede MC-Referenz
ctx.services().get(OreGenService.class).installRubyOre(6, 12, -32, 48);
```

Damit ist **jede** nicht abstrahierte MC-Funktionalität erreichbar, ohne dass die Common-API sie kennen muss.
Der Preis ist ein Interface pro Bedarf — dafür ist er typsicher, refactoringfähig, testbar (Mock im Unit-Test)
und ohne Reflection.

## 18.8 Capabilities — Feature-Gates statt Versionsvergleiche

```java
package dev.fabricmultiloader.api;

public final class Capability<T> {
    private final String id; private final Class<T> type;
    public static <T> Capability<T> of(String id, Class<T> type);
    public String id(); public Class<T> type();
    // equals/hashCode über id
}

public final class Capabilities {
    /** Datenkomponenten statt NBT — erst ab MC 1.20.5. */
    public static final Capability<ComponentApi> COMPONENTS =
            Capability.of("components", ComponentApi.class);

    /** Typisierte Netzwerk-Payloads (CustomPayload) — erst ab MC 1.20.5. */
    public static final Capability<TypedPayloadApi> TYPED_PAYLOADS =
            Capability.of("networking.typed", TypedPayloadApi.class);

    /** Registrierungs-Sets/Tags mit RegistryEntryLookup — ab 1.19.3. */
    public static final Capability<TagApi> TAGS = Capability.of("tags", TagApi.class);

    /** Client-Gametest-Unterstützung — nur wo Fabric API sie anbietet. */
    public static final Capability<ClientGametestApi> CLIENT_GAMETEST =
            Capability.of("gametest.client", ClientGametestApi.class);
}
```

Verwendung im Common-Code:

```java
ctx.capability(Capabilities.COMPONENTS).ifPresent(components ->
        components.attach(rubyHandle, "examplemod:charge", 0));
```

Vorteil gegenüber `if (ctx.platform().minecraftOrdinal() >= 12005)`: Die Bedingung ist **semantisch** („gibt es
Komponenten?“) statt **numerisch**, sie ist im Payload deklariert (`payload.capabilities` im Manifest, damit im
Validator und im Diagnosebericht sichtbar), und ein Backport oder eine Vorabimplementierung ändert nur eine
Deklaration statt Common-Code.

## 18.9 `ModLogger`

```java
public interface ModLogger {
    void trace(String msg, Object... args);       // {} -Platzhalter, SLF4J-Stil
    void debug(String msg, Object... args);
    void info (String msg, Object... args);
    void warn (String msg, Object... args);
    void error(String msg, Object... args);
    void error(String msg, Throwable t, Object... args);
    boolean isDebugEnabled();
    ModLogger sub(String name);                   // "examplemod/net"
}
```

Implementiert über SLF4J, falls vorhanden, sonst `System.err` (Kapitel 9.8). `{}`-Formatierung wird selbst
implementiert (8 Zeilen), damit sie ohne SLF4J identisch funktioniert.

---

# 19. Version Adapter API

## 19.1 `Platform` und `PlatformFactory`

```java
package dev.fabricmultiloader.api.platform;

@ImplementedByMod          // aber nur in Version-Modulen!
public interface PlatformFactory {
    Platform create(ModContext ctx);
}

@ImplementedByMod
public interface Platform {

    PlatformInfo info();

    // ---- Subsystem-Implementierungen (Pflicht) ---------------------------
    Registries registries();
    Networking networking();
    Commands   commands();
    Events     events();

    // ---- Lifecycle-Hooks (alle default, also optional) -------------------
    default void onPreLaunch(PreLaunchContext ctx) { }
    default void onInitialize(ModContext ctx) { }
    default void onInitializeClient(ModContext ctx) { }
    default void onInitializeServer(ModContext ctx) { }

    /** Capability-Auflösung. Default: keine. */
    default <T> java.util.Optional<T> capability(Capability<T> capability) {
        return java.util.Optional.empty();
    }

    /** Ergänzt Crash-Reports um Kontext (Kapitel 30.3). Default: nichts. */
    default void installCrashContext(CrashContext ctx) { }
}
```

Damit ein Version-Modul minimal bleibt, liefert die Runtime eine abstrakte Basisklasse mit sinnvollen Defaults:

```java
package dev.fabricmultiloader.api.platform;

public abstract class AbstractPlatform implements Platform {
    private final PlatformInfo info;
    private final ServiceRegistry services;

    protected AbstractPlatform(ModContext ctx) { this.info = ctx.platform(); this.services = ctx.services(); }

    @Override public final PlatformInfo info() { return info; }
    protected final ServiceRegistry services() { return services; }
}
```

## 19.2 Vollständiger Adapter für Minecraft 1.21.4

```java
package com.example.mc1214;

import com.example.common.service.OreGenService;
import com.example.mc1214.net.Networking1214;
import com.example.mc1214.registry.Registries1214;
import com.example.mc1214.service.OreGenService1214;
import dev.fabricmultiloader.api.Capabilities;
import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.AbstractPlatform;
import dev.fabricmultiloader.api.platform.CrashContext;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.runtime.adapter.CommandsImpl;      // von der Runtime bereitgestellt
import dev.fabricmultiloader.runtime.adapter.EventsImpl;

import java.util.Optional;

public final class Platform1214 extends AbstractPlatform {

    private final Registries1214 registries;
    private final Networking1214 networking;
    private final CommandsImpl   commands;
    private final EventsImpl     events;

    Platform1214(ModContext ctx) {
        super(ctx);
        this.registries = new Registries1214(ctx);
        this.networking = new Networking1214(ctx);
        this.commands   = new CommandsImpl(ctx);
        this.events     = new EventsImpl(ctx);
    }

    @Override public Registries registries() { return registries; }
    @Override public Networking networking() { return networking; }
    @Override public Commands   commands()   { return commands;   }
    @Override public Events     events()     { return events;     }

    @Override
    public void onInitialize(ModContext ctx) {
        ctx.services().register(OreGenService.class, new OreGenService1214());
        registries.flush();        // führt aufgeschobene Registrierungen aus, s. 19.4
    }

    @Override
    public <T> Optional<T> capability(Capability<T> capability) {
        if (Capabilities.COMPONENTS.equals(capability))      return Optional.of(capability.type().cast(new Components1214()));
        if (Capabilities.TYPED_PAYLOADS.equals(capability))  return Optional.of(capability.type().cast(networking));
        if (Capabilities.TAGS.equals(capability))            return Optional.of(capability.type().cast(new Tags1214()));
        return Optional.empty();
    }

    @Override
    public void installCrashContext(CrashContext ctx) {
        ctx.add("Active payload", "mc1214 (Minecraft 1.21.4, Java 21, Yarn 1.21.4+build.8)");
    }
}
```

```java
package com.example.mc1214;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PlatformFactory;

/** Vom Manifest referenziert: payload.platformFactory */
public final class Platform1214Factory implements PlatformFactory {
    @Override public Platform create(ModContext ctx) { return new Platform1214(ctx); }
}
```

Der Adapter ist damit ~40 Zeilen Klebecode. `CommandsImpl` und `EventsImpl` liefert die Runtime, weil Commands
und Fabric-API-Events über 1.20.1–26.1 stabil genug sind, um **eine** Implementierung zu erlauben
(Kapitel 28.4) — sie sind aber überschreibbar, falls eine Version abweicht.

## 19.3 Lifecycle im Detail

```
Phase                       Container            Payload                       Modcode
─────────────────────────── ─────────────────── ───────────────────────────── ──────────────────────────
preLaunch (Fabric)          ContainerPreLaunch  PayloadPreLaunch
  · Manifest + Resolve      ✓
  · Platform erzeugen                            PlatformFactory#create
  · Platform#onPreLaunch                         ✓
  · UniversalPreLaunch                                                         onPreLaunch(PreLaunchContext)
main (Fabric)                                    PayloadMain
  · Platform#onInitialize                        ✓  (Services, Registries)
  · UniversalMod                                                               onInitialize(ModContext)
  · Registries#flush                             ✓  (nach Modcode!)
client (Fabric)                                  PayloadClient
  · Platform#onInitializeClient                  ✓
  · UniversalClientMod                                                         onInitializeClient(ModContext)
server (Fabric)                                  PayloadServer
  · Platform#onInitializeServer                  ✓
  · UniversalServerMod                                                         onInitializeServer(ModContext)
Laufzeit                                                                       Events, Commands, Networking
```

Wichtig: `Platform#onInitialize` läuft **vor** dem Modcode (damit Services verfügbar sind),
`Registries#flush()` **nach** dem Modcode (damit alle deklarierten Inhalte gesammelt sind). Diese Reihenfolge ist
normativ und wird von `LifecycleStateMachine` erzwungen; ein Registry-Aufruf nach `flush` wirft `OMNI-4002`
mit Hinweis auf die korrekte Phase.

## 19.4 Registries — deklarativ mit aufgeschobener Ausführung

```java
package dev.fabricmultiloader.api.registry;

@ImplementedByMod            // im Version-Modul
public interface Registries {
    ItemHandle      item(Id id, ItemSpec spec);
    BlockHandle     block(Id id, BlockSpec spec);
    /** Registriert Block + zugehöriges BlockItem in einem Schritt. */
    BlockHandle     blockWithItem(Id id, BlockSpec spec, ItemSpec itemSpec);
    SoundHandle     sound(Id id);
    ItemGroupHandle itemGroup(Id id, ItemGroupSpec spec);
    void addToItemGroup(Id groupId, ItemHandle... items);
}
```

```java
package dev.fabricmultiloader.api.registry;

public final class ItemSpec {
    private final int maxCount; private final Rarity rarity; private final boolean fireproof;
    private final Integer maxDamage; private final Id craftingRemainder;
    private final ItemBehavior behavior;   // kann null sein
    private final java.util.List<String> tooltipTranslationKeys;

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        public Builder maxCount(int c);
        public Builder rarity(Rarity r);
        public Builder fireproof();
        public Builder maxDamage(int d);
        public Builder craftingRemainder(Id id);
        public Builder behavior(ItemBehavior b);
        public Builder tooltip(String translationKey);
        public ItemSpec build();
    }
}

/** Verhaltens-Callbacks ohne Minecraft-Typen. */
public interface ItemBehavior {
    default UseResult onUse(UseContext ctx) { return UseResult.PASS; }
    default UseResult onUseOnBlock(BlockUseContext ctx) { return UseResult.PASS; }
    default void onInventoryTick(ItemStackRef stack, PlayerRef holder, boolean selected) { }
}

public interface UseContext {
    PlayerRef player(); WorldRef world(); ItemStackRef stack(); Hand hand();
}

public enum UseResult { SUCCESS, CONSUME, PASS, FAIL }
```

Implementierung für 1.20.1 und 1.21.4 — die Divergenz ist real und wird hier vollständig gezeigt:

```java
// versions/mc-1.20.1/src/main/java/com/example/mc1201/registry/Registries1201.java
package com.example.mc1201.registry;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries as McRegistries;   // (Illustration: in Java per Import-Alias nicht möglich,
                                                            //  real: net.minecraft.registry.Registries direkt genutzt)
import net.minecraft.util.Identifier;

public final class Registries1201 implements dev.fabricmultiloader.api.registry.Registries {

    private final java.util.List<Runnable> deferred = new java.util.ArrayList<>();
    private final ModContext ctx;

    public Registries1201(ModContext ctx) { this.ctx = ctx; }

    @Override
    public ItemHandle item(Id id, ItemSpec spec) {
        Identifier mcId = new Identifier(id.namespace(), id.path());     // 1.20.1: Konstruktor
        ItemHandleImpl handle = new ItemHandleImpl(id);
        deferred.add(() -> {
            FabricItemSettings settings = new FabricItemSettings().maxCount(spec.maxCount());
            if (spec.fireproof()) settings.fireproof();
            if (spec.maxDamage() != null) settings.maxDamage(spec.maxDamage());
            settings.rarity(Mapping1201.rarity(spec.rarity()));
            Item item = spec.behavior() == null
                    ? new Item(settings)
                    : new BehaviorItem1201(settings, spec.behavior(), ctx);
            handle.bind(Registry.register(net.minecraft.registry.Registries.ITEM, mcId, item));
        });
        return handle;
    }

    public void flush() { for (Runnable r : deferred) r.run(); deferred.clear(); }
}
```

```java
// versions/mc-1.21.4/src/main/java/com/example/mc1214/registry/Registries1214.java
package com.example.mc1214.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class Registries1214 implements dev.fabricmultiloader.api.registry.Registries {

    private final java.util.List<Runnable> deferred = new java.util.ArrayList<>();
    private final ModContext ctx;

    public Registries1214(ModContext ctx) { this.ctx = ctx; }

    @Override
    public ItemHandle item(Id id, ItemSpec spec) {
        Identifier mcId = Identifier.of(id.namespace(), id.path());        // 1.21+: statische Factory
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, mcId);  // ab 1.21.2 Pflicht
        ItemHandleImpl handle = new ItemHandleImpl(id);
        deferred.add(() -> {
            Item.Settings settings = new Item.Settings().registryKey(key).maxCount(spec.maxCount());
            if (spec.fireproof()) settings.fireproof();
            if (spec.maxDamage() != null) settings.maxDamage(spec.maxDamage());
            settings.rarity(Mapping1214.rarity(spec.rarity()));
            Item item = spec.behavior() == null
                    ? new Item(settings)
                    : new BehaviorItem1214(settings, spec.behavior(), ctx);
            handle.bind(Registry.register(Registries.ITEM, key, item));
        });
        return handle;
    }

    public void flush() { for (Runnable r : deferred) r.run(); deferred.clear(); }
}
```

Die Unterschiede — `new Identifier(...)` vs. `Identifier.of(...)`, `FabricItemSettings` vs. `Item.Settings`,
`registryKey` als neue Pflicht, `Registry.register(registry, Identifier, T)` vs.
`Registry.register(registry, RegistryKey, T)` — sind exakt die Art von Bruch, die ein einzelnes Kompilat
unmöglich macht und die hier auf **jeweils 15 Zeilen Adaptercode** eingedämmt wird.

`ItemHandle`:

```java
package dev.fabricmultiloader.api.registry;

public interface ItemHandle extends Unwrappable {
    Id id();
    boolean isBound();                       // false vor flush()
    ItemStackRef stack(int count);
    dev.fabricmultiloader.api.text.Text name();
}
```

## 19.5 Warum aufgeschobene Registrierung

Common-Code deklariert Inhalte in `onInitialize`. Zu diesem Zeitpunkt ist die MC-Registry in einigen Versionen
schon, in anderen noch nicht schreibbar; ab 1.21.2 verlangt `Item.Settings` einen `RegistryKey`, der aus der ID
gebildet werden muss. Die Aufschiebung löst drei Probleme gleichzeitig:

1. Der Adapter bestimmt den **Zeitpunkt** der echten Registrierung pro Version (in `flush()`, aufgerufen aus
   `PayloadMain` nach dem Modcode).
2. Common-Code erhält sofort einen `ItemHandle` und kann ihn in Feldern speichern, obwohl das MC-Objekt noch
   nicht existiert (`isBound() == false`).
3. Die Reihenfolge der Registrierung ist deterministisch (Deklarationsreihenfolge), was für Registry-IDs in
   Netzwerkprotokollen und Datapacks relevant ist.

## 19.6 Capability-Deklaration und -Validierung

Ein Payload deklariert im Build (Kapitel 21.4) `capabilities = ["registries", "commands", "networking.v1",
"events.lifecycle", "components"]`. Der Validator prüft (`OMNI-1130`), dass für jede deklarierte Capability
`Platform#capability` einen nichtleeren Wert liefert — geprüft durch einen **Reflection-freien
Bytecode-Check**: Die Adapter-Klasse muss eine `capability`-Methode überschreiben und die entsprechende
`Capabilities`-Konstante im Konstantenpool referenzieren. Das ist eine Heuristik mit klarer Fehlermeldung und
`@SuppressCapabilityCheck`-Ausweg; sie fängt in der Praxis den häufigsten Fehler (Capability deklariert, aber
`capability()` nicht implementiert).

Der Diagnosebericht listet Capabilities pro Payload, sodass ein Nutzer sofort sieht, warum ein Feature auf
1.20.1 fehlt.

## 19.7 Annotation Processor — Boilerplate-Eliminierung

```java
// common/src/main/java/com/example/common/ExampleMod.java
package com.example.common;

import dev.fabricmultiloader.api.*;

@UniversalEntrypoint                       // → entrypoints.common
public final class ExampleMod implements UniversalMod {
    @Override public void onInitialize(ModContext ctx) { … }
}
```

```java
@UniversalEntrypoint(Side.CLIENT)          // → entrypoints.client
public final class ExampleModClient implements UniversalClientMod { … }
```

`fabricmultiloader-processor` (`javax.annotation.processing.Processor`, `-Aomni.modId=<id>`):

* validiert, dass die annotierte Klasse das passende Interface implementiert, `public`, nicht abstrakt ist und
  einen öffentlichen parameterlosen Konstruktor hat — sonst `error` mit `Element`-Bezug, also direkt in der IDE
  sichtbar;
* validiert, dass die Klasse in einem als `commonPackages` deklarierten Package liegt;
* schreibt `omni/entrypoints.json` in die Ressourcen von `:common`;
* der `generateOmniManifest`-Task liest diese Datei und mischt sie mit den DSL-Angaben; Duplikate sind ein
  Fehler (`OMNI-1140`), fehlende Entrypoints ebenfalls (`OMNI-1141`: mindestens ein `common`-Entrypoint).

Der Processor ist **optional**: `fabricMultiLoader { mod { entrypoint("com.example.common.ExampleMod") } }`
funktioniert ohne ihn. Das Template aktiviert ihn.

## 19.8 Adapter für versionsspezifische Erweiterung durch Modautoren

Ein Version-Modul darf die von der Runtime gelieferten Implementierungen ersetzen:

```java
public final class Platform1201 extends AbstractPlatform {
    private final Commands commands;
    Platform1201(ModContext ctx) {
        super(ctx);
        // 1.20.1 hat keine ‚registryAccess'-Variante eines Befehls, den wir brauchen:
        this.commands = new Commands1201(ctx);        // eigene Implementierung
    }
    @Override public Commands commands() { return commands; }
}
```

Damit ist die Runtime-Implementierung ein Default, keine Zwangsjacke — wichtig für Langlebigkeit: Wenn MC 27
die Command-Registrierung umbaut, braucht nur das neue Payload eine eigene `Commands`-Implementierung; die
Runtime muss nicht aktualisiert werden.

## 19.9 Öffentliche Mod-API für Drittmods

```java
// common/src/main/java/com/example/common/api/ExampleModApi.java
package com.example.common.api;

public interface ExampleModApi {

    static ExampleModApi get() {
        Object o = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getObjectShare().get("examplemod:api");
        if (o == null) throw new IllegalStateException(
                "ExampleMod API requested before ExampleMod initialised, or ExampleMod is not active. "
              + "Check FabricLoader.isModLoaded(\"examplemod\") and listen for the objectShare key.");
        return (ExampleModApi) o;
    }

    int rubyCharge(ItemStackRef stack);
    void registerRubyRecipe(Id id, java.util.List<Id> ingredients);
}
```

Der Container veröffentlicht die Implementierung in `getObjectShare()` am Ende von `onInitialize`. Weil dieses
Interface im **Container** liegt (nicht im Payload) und der Container über alle MC-Versionen dasselbe Kompilat
ist, gilt: **Eine Drittmod kompiliert genau einmal gegen `examplemod-api` und funktioniert mit jeder
MC-Version.** Das ist ein Vorteil, den eine klassische Multi-Jar-Veröffentlichung nicht hat, und ein zentrales
Verkaufsargument des Frameworks.

Publikationsseitig wird dafür ein eigenes Maven-Artefakt erzeugt: `com.example:examplemod-api:2.0.0` = der
`:common`-Jar, gefiltert auf `com.example.common.api.**` + `dev.fabricmultiloader.api.**` als `api`-Dependency
(Kapitel 24.7).

---

# 26. Client/Server Handling

## 26.1 Drei Ebenen der Seitenunterscheidung

| Ebene | Mechanismus | Wirkung |
|---|---|---|
| **Payload-Ebene** | `environment: "client"` in der Payload-`fabric.mod.json` | Payload wird auf dedizierten Servern gar nicht geladen — inklusive Mixins, AW und Ressourcen. Für reine Client-Mods. |
| **Mixin-Config-Ebene** | `{"config": "…client.mixins.json", "environment": "client"}` | Client-Mixins werden auf dem Server nicht registriert. |
| **Code-Ebene** | Getrennte Entrypoints (`UniversalClientMod`), getrennte Packages (`*.client.**`) | Client-Klassen werden auf dem Server nie geladen. |

## 26.2 Regeln für Common-Code

* Common-Code darf `ctx.side()` abfragen, aber **niemals** clientseitige Funktionalität direkt aufrufen.
* Clientspezifische Fachlogik gehört in eine Klasse, die nur aus `UniversalClientMod` erreichbar ist.
* Der Validator prüft (`OMNI-1150`), dass keine Klasse, die von einem `common`-Entrypoint aus erreichbar ist,
  eine Klasse aus einem als `clientOnly` deklarierten Package referenziert. Die Erreichbarkeitsanalyse ist ein
  einfacher, transitiver Konstantenpool-Scan über den Container — schnell und ohne Bytecode-Interpretation.

## 26.3 Server-only Payloads

Analog zu Client-only: `environment: "server"`. Praktisch relevant für Mods, die auf dedizierten Servern
zusätzliche Payloads brauchen (z. B. weil eine Server-Software wie Paper-artige Fabric-Forks eigene Anpassungen
verlangt). Das Framework unterstützt es, das Template nutzt es nicht.

## 26.4 Beispiel: Client-Initialisierung

```java
// common/src/main/java/com/example/common/ExampleModClient.java
package com.example.common;

import dev.fabricmultiloader.api.*;
import dev.fabricmultiloader.api.event.Events;

@UniversalEntrypoint(Side.CLIENT)
public final class ExampleModClient implements UniversalClientMod {

    @Override
    public void onInitializeClient(ModContext ctx) {
        ctx.log().info("Client init on Minecraft {}", ctx.platform().minecraft());

        // Netzwerk-Empfänger für die S2C-Nachricht (Kapitel 27)
        ExampleNetworking.RUBY_SYNC.receiveOnClient((payload, client) ->
                client.log().debug("charge={} for {}", payload.charge(), payload.item()));

        // Client-Tick-Event über die neutrale Event-API
        ctx.events().clientTick(client -> RubyHud.tick());

        // Versions-/Fähigkeitsabhängige Client-Erweiterung
        ctx.capability(Capabilities.COMPONENTS).ifPresent(c -> RubyHud.enableComponentDisplay());
    }
}
```

`RubyHud` liegt im Common-Modul und arbeitet ausschließlich mit neutralen Typen; das eigentliche Rendern
geschieht über einen Service (`HudRenderService`), den jedes Payload implementiert — weil
`HudRenderCallback`/`DrawContext` sich zwischen 1.20.1 und 1.21.4 mehrfach geändert haben.

---

# 27. Networking Example — das Referenzbeispiel für Adaptierung

## 27.1 Die Common-API

```java
package dev.fabricmultiloader.api.net;

@ImplementedByMod          // im Version-Modul
public interface Networking {
    <T> ChannelHandle<T> register(ChannelSpec<T> spec);
}

public final class ChannelSpec<T> {
    private final Id id;
    private final Direction direction;
    private final PayloadCodec<T> codec;

    public static <T> ChannelSpec<T> c2s(Id id, PayloadCodec<T> codec);
    public static <T> ChannelSpec<T> s2c(Id id, PayloadCodec<T> codec);
    public static <T> ChannelSpec<T> both(Id id, PayloadCodec<T> codec);

    public enum Direction { C2S, S2C, BOTH }
}

public interface ChannelHandle<T> {
    Id id();

    /** Server-Seite: Empfänger für C2S. */
    void receiveOnServer(C2SReceiver<T> receiver);
    /** Client-Seite: Empfänger für S2C. */
    void receiveOnClient(S2CReceiver<T> receiver);

    /** Client → Server. Nur auf dem Client aufrufbar. */
    void sendToServer(T payload);
    /** Server → ein Spieler. */
    void sendTo(PlayerRef player, T payload);
    /** Server → alle Spieler in einer Welt. */
    void sendToAllIn(WorldRef world, T payload);
    /** Server → alle Spieler. */
    void sendToAll(T payload);

    /** true, wenn der Gegenüber diesen Channel angemeldet hat. */
    boolean canReceive(PlayerRef player);
}

public interface C2SReceiver<T> { void accept(T payload, PlayerRef sender, ModContext ctx); }
public interface S2CReceiver<T> { void accept(T payload, ModContext ctx); }

public interface PayloadCodec<T> {
    void write(ByteSink out, T value);
    T read(ByteSource in);
}

public interface ByteSink {
    ByteSink writeBoolean(boolean v); ByteSink writeByte(int v);   ByteSink writeShort(int v);
    ByteSink writeInt(int v);         ByteSink writeVarInt(int v); ByteSink writeLong(long v);
    ByteSink writeFloat(float v);     ByteSink writeDouble(double v);
    ByteSink writeString(String v);   ByteSink writeUuid(java.util.UUID v);
    ByteSink writeId(Id v);           ByteSink writeItemStack(ItemStackRef v);
    ByteSink writeBytes(byte[] v);
    <E> ByteSink writeList(java.util.List<E> list, java.util.function.BiConsumer<ByteSink, E> writer);
    <E> ByteSink writeOptional(java.util.Optional<E> value, java.util.function.BiConsumer<ByteSink, E> writer);
}

public interface ByteSource { /* symmetrische read*-Methoden */ }
```

`ByteSink`/`ByteSource` sind der entscheidende Trick: Sie kapseln `PacketByteBuf` bzw. `RegistryByteBuf`, sodass
der Codec im **Common**-Code lebt, obwohl die Buffer-Typen versionsspezifisch sind. `writeItemStack` ist bewusst
Teil der Schnittstelle, weil ItemStack-Serialisierung sich zwischen 1.20.1 (NBT) und 1.20.5+ (Komponenten,
registry-abhängig) fundamental geändert hat und deshalb nur der Adapter sie korrekt implementieren kann.

## 27.2 Verwendung im Common-Code

```java
// common/src/main/java/com/example/common/net/ExampleNetworking.java
package com.example.common.net;

import dev.fabricmultiloader.api.*;
import dev.fabricmultiloader.api.net.*;

public final class ExampleNetworking {

    public static final class RubySync {
        private final Id item; private final int charge;
        public RubySync(Id item, int charge) { this.item = item; this.charge = charge; }
        public Id item() { return item; }
        public int charge() { return charge; }
    }

    public static final PayloadCodec<RubySync> RUBY_SYNC_CODEC = new PayloadCodec<RubySync>() {
        @Override public void write(ByteSink out, RubySync v) { out.writeId(v.item()).writeVarInt(v.charge()); }
        @Override public RubySync read(ByteSource in)         { return new RubySync(in.readId(), in.readVarInt()); }
    };

    public static ChannelHandle<RubySync> RUBY_SYNC;         // S2C
    public static ChannelHandle<ChargeRequest> CHARGE_REQ;   // C2S

    public static void register(ModContext ctx) {
        RUBY_SYNC = ctx.networking().register(
                ChannelSpec.s2c(Id.of("examplemod", "ruby_sync"), RUBY_SYNC_CODEC));

        CHARGE_REQ = ctx.networking().register(
                ChannelSpec.c2s(Id.of("examplemod", "charge_request"), ChargeRequest.CODEC));

        CHARGE_REQ.receiveOnServer((req, sender, c) -> {
            int newCharge = RubyLogic.charge(sender, req.amount());
            RUBY_SYNC.sendTo(sender, new RubySync(Id.of("examplemod", "ruby"), newCharge));
        });
    }
}
```

Dieser Code ist **vollständig versionsneutral** und wird genau einmal kompiliert.

## 27.3 Adapter für Minecraft 1.20.1 (rohes `PacketByteBuf`, kanalbasiert)

```java
package com.example.mc1201.net;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.net.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class Networking1201 implements Networking {

    private final ModContext ctx;
    public Networking1201(ModContext ctx) { this.ctx = ctx; }

    @Override
    public <T> ChannelHandle<T> register(ChannelSpec<T> spec) {
        Identifier channel = new Identifier(spec.id().namespace(), spec.id().path());
        return new Handle1201<>(ctx, channel, spec);
    }

    private static final class Handle1201<T> implements ChannelHandle<T> {
        private final ModContext ctx; private final Identifier channel; private final ChannelSpec<T> spec;

        @Override
        public void receiveOnServer(C2SReceiver<T> receiver) {
            ServerPlayNetworking.registerGlobalReceiver(channel,
                    (server, player, handler, buf, responseSender) -> {
                        T payload = spec.codec().read(new ByteSource1201(buf));      // auf Netzwerk-Thread
                        server.execute(() ->                                         // auf Server-Thread
                                receiver.accept(payload, new PlayerRef1201(player), ctx));
                    });
        }

        @Override
        public void receiveOnClient(S2CReceiver<T> receiver) {
            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
            ClientNet1201.register(channel, spec, receiver, ctx);   // eigene Klasse: kein Client-Typ hier
        }

        @Override
        public void sendToServer(T payload) {
            PacketByteBuf buf = PacketByteBufs.create();
            spec.codec().write(new ByteSink1201(buf), payload);
            ClientNet1201.send(channel, buf);
        }

        @Override
        public void sendTo(PlayerRef player, T payload) {
            PacketByteBuf buf = PacketByteBufs.create();
            spec.codec().write(new ByteSink1201(buf), payload);
            ServerPlayNetworking.send(player.unwrap(ServerPlayerEntity.class), channel, buf);
        }

        @Override
        public boolean canReceive(PlayerRef player) {
            return ServerPlayNetworking.canSend(player.unwrap(ServerPlayerEntity.class), channel);
        }
        // sendToAllIn / sendToAll analog über PlayerLookup
    }
}
```

```java
// ByteSink-Adapter für 1.20.1
final class ByteSink1201 implements ByteSink {
    private final PacketByteBuf buf;
    ByteSink1201(PacketByteBuf buf) { this.buf = buf; }
    public ByteSink writeVarInt(int v)   { buf.writeVarInt(v); return this; }
    public ByteSink writeString(String v){ buf.writeString(v); return this; }
    public ByteSink writeId(Id v)        { buf.writeIdentifier(new Identifier(v.namespace(), v.path())); return this; }
    public ByteSink writeItemStack(ItemStackRef v) { buf.writeItemStack(v.unwrap(ItemStack.class)); return this; }
    // … alle weiteren Methoden
}
```

## 27.4 Adapter für Minecraft 1.21.4 (typisierte `CustomPayload`)

```java
package com.example.mc1214.net;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.net.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class Networking1214 implements Networking, TypedPayloadApi {

    private final ModContext ctx;
    public Networking1214(ModContext ctx) { this.ctx = ctx; }

    /** Generischer CustomPayload-Wrapper: trägt den bereits serialisierten Common-Payload. */
    record OmniPayload<T>(CustomPayload.Id<OmniPayload<T>> id, T value) implements CustomPayload {
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return id; }
    }

    @Override
    public <T> ChannelHandle<T> register(ChannelSpec<T> spec) {
        Identifier mcId = Identifier.of(spec.id().namespace(), spec.id().path());
        CustomPayload.Id<OmniPayload<T>> payloadId = new CustomPayload.Id<>(mcId);

        PacketCodec<RegistryByteBuf, OmniPayload<T>> codec = PacketCodec.of(
                (payload, buf) -> spec.codec().write(new ByteSink1214(buf), payload.value()),
                buf -> new OmniPayload<>(payloadId, spec.codec().read(new ByteSource1214(buf))));

        switch (spec.direction()) {
            case C2S:  PayloadTypeRegistry.playC2S().register(payloadId, codec); break;
            case S2C:  PayloadTypeRegistry.playS2C().register(payloadId, codec); break;
            case BOTH: PayloadTypeRegistry.playC2S().register(payloadId, codec);
                       PayloadTypeRegistry.playS2C().register(payloadId, codec); break;
        }
        return new Handle1214<>(ctx, payloadId, spec);
    }

    private static final class Handle1214<T> implements ChannelHandle<T> {
        @Override
        public void receiveOnServer(C2SReceiver<T> receiver) {
            ServerPlayNetworking.registerGlobalReceiver(payloadId, (payload, context) ->
                    // context.player() liefert bereits den Server-Thread-Kontext
                    receiver.accept(payload.value(), new PlayerRef1214(context.player()), ctx));
        }

        @Override
        public void sendTo(PlayerRef player, T payload) {
            ServerPlayNetworking.send(player.unwrap(ServerPlayerEntity.class),
                    new OmniPayload<>(payloadId, payload));
        }
        // sendToServer über ClientPlayNetworking.send(new OmniPayload<>(...)) in einer Client-Klasse
    }

    // TypedPayloadApi (Capability): erlaubt Common-Code, native Codecs zu nutzen, wenn verfügbar
    @Override public boolean supportsRegistrySync() { return true; }
}
```

## 27.5 Was dieses Beispiel demonstriert

| Aspekt | 1.20.1 | 1.21.4 | Lösung |
|---|---|---|---|
| Kanal-Identität | `Identifier` | `CustomPayload.Id<T>` | Adapter kapselt |
| Registrierung | implizit beim ersten Receiver | explizit über `PayloadTypeRegistry`, **vor** dem ersten Join | Adapter registriert in `register()`, also in `onInitialize` — funktioniert in beiden Versionen |
| Puffer-Typ | `PacketByteBuf` | `RegistryByteBuf` (mit Registry-Zugriff) | `ByteSink`/`ByteSource`-Abstraktion |
| Thread-Wechsel | manuell `server.execute` | vom `context` übernommen | Adapter normalisiert: Receiver läuft **immer** auf dem Spiel-Thread |
| ItemStack-Serialisierung | NBT | Komponenten-Codec, registry-abhängig | `ByteSink#writeItemStack` |
| Fabric-API-Version | ≥ 0.92.2 | ≥ 0.114.0 | pro Payload deklariert |

Der Modautor schreibt **einen** Codec und **einen** Handler und erhält korrektes Verhalten auf beiden Versionen.
Dieses Muster — neutrale Spezifikation + neutraler Datenpfad + versionsspezifische Bindung — ist das
Grundmuster für jedes weitere Subsystem.

---

# 28. Registries und Events — weitere Beispiele

## 28.1 Item-Registrierung im Common-Code

```java
// common/src/main/java/com/example/common/ExampleMod.java
package com.example.common;

import dev.fabricmultiloader.api.*;
import dev.fabricmultiloader.api.registry.*;
import com.example.common.net.ExampleNetworking;

@UniversalEntrypoint
public final class ExampleMod implements UniversalMod {

    public static final Id MOD = Id.of("examplemod", "examplemod");
    public static ItemHandle RUBY;
    public static BlockHandle RUBY_BLOCK;

    @Override
    public void onInitialize(ModContext ctx) {
        ModLogger log = ctx.log();
        log.info("ExampleMod {} on Minecraft {} (payload {})",
                ctx.modVersion(), ctx.platform().minecraft(), ctx.platform().payloadId());

        RUBY = ctx.registries().item(Id.of("examplemod", "ruby"),
                ItemSpec.builder()
                        .maxCount(64)
                        .rarity(Rarity.UNCOMMON)
                        .tooltip("item.examplemod.ruby.tooltip")
                        .behavior(new RubyBehavior())
                        .build());

        RUBY_BLOCK = ctx.registries().blockWithItem(Id.of("examplemod", "ruby_block"),
                BlockSpec.builder().hardness(5.0f).resistance(6.0f).requiresTool().build(),
                ItemSpec.builder().build());

        ctx.registries().addToItemGroup(Id.of("minecraft", "building_blocks"), RUBY_BLOCK.item());

        ExampleNetworking.register(ctx);
        ExampleCommands.register(ctx);
        ExampleEvents.register(ctx);

        ctx.services().find(com.example.common.service.OreGenService.class)
                .ifPresent(s -> s.installRubyOre(6, 12, -32, 48));
    }
}
```

```java
final class RubyBehavior implements ItemBehavior {
    @Override public UseResult onUse(UseContext ctx) {
        ctx.player().sendMessage("Charge: " + RubyLogic.chargeOf(ctx.stack()));
        return UseResult.SUCCESS;
    }
}
```

## 28.2 Commands

```java
package dev.fabricmultiloader.api.command;

public interface Commands {
    void register(CommandSpec spec);
}

public final class CommandSpec {
    public static Builder named(String literal) { … }

    public static final class Builder {
        public Builder permissionLevel(int level);
        public Builder arg(String name, Arg<?> type);
        public Builder sub(CommandSpec child);
        public Builder executes(java.util.function.Function<CommandInvocation, Integer> body);
        public Builder onlyOn(Side side);
        public CommandSpec build();
    }
}

public final class Arg<T> {
    public static Arg<Integer> integer(int min, int max);
    public static Arg<String>  word();
    public static Arg<String>  greedyString();
    public static Arg<PlayerRef> player();
    public static Arg<Id>      identifier();
    public static Arg<Double>  decimal(double min, double max);
}

public interface CommandInvocation {
    <T> T arg(String name, Class<T> type);
    java.util.Optional<PlayerRef> player();      // ausführender Spieler, falls vorhanden
    CommandSender sender();
    void reply(String plainText);
    void reply(dev.fabricmultiloader.api.text.Text text);
    void broadcast(dev.fabricmultiloader.api.text.Text text);
}
```

Common-Verwendung:

```java
package com.example.common;

final class ExampleCommands {
    static void register(ModContext ctx) {
        ctx.commands().register(CommandSpec.named("ruby")
                .permissionLevel(0)
                .sub(CommandSpec.named("charge")
                        .arg("amount", Arg.integer(1, 100))
                        .executes(inv -> {
                            int amount = inv.arg("amount", Integer.class);
                            inv.player().ifPresent(p -> ExampleNetworking.CHARGE_REQ.sendToServer(
                                    new ChargeRequest(amount)));
                            inv.reply("Requested charge " + amount);
                            return 1;
                        })
                        .build())
                .sub(CommandSpec.named("info")
                        .executes(inv -> {
                            inv.reply("ExampleMod " + ctx.modVersion()
                                    + " · MC " + ctx.platform().minecraft()
                                    + " · payload " + ctx.platform().payloadId());
                            return 1;
                        })
                        .build())
                .build());
    }
}
```

Der Adapter (`CommandsImpl` in der Runtime, für alle Versionen gemeinsam) übersetzt `CommandSpec` in Brigadier:

```java
package dev.fabricmultiloader.runtime.adapter;

public final class CommandsImpl implements Commands {
    // Nutzt ausschliesslich CommandRegistrationCallback + Brigadier — beides stabil
    // von 1.19 bis 26.1. Die einzige Divergenz (ServerCommandSource#sendFeedback nimmt
    // ab 1.20 einen Supplier) wird ueber einen kleinen, pro Payload gesetzten
    // FeedbackAdapter geloest, den die Runtime aus Platform#capability bezieht.
}
```

Damit ist auch belegt, dass die Runtime **teilweise** versionsübergreifende Adapter enthalten darf — überall, wo
die zugrunde liegende API tatsächlich stabil ist. Der Validator dokumentiert das über
`payload.capabilities`: Ein Payload, das `commands` nicht deklariert, muss `Platform#commands()` selbst
implementieren.

## 28.3 Events

```java
package dev.fabricmultiloader.api.event;

public interface Events {
    Subscription serverStarted (java.util.function.Consumer<ServerRef> handler);
    Subscription serverStopping(java.util.function.Consumer<ServerRef> handler);
    Subscription serverTick    (java.util.function.Consumer<ServerRef> handler);
    Subscription clientTick    (java.util.function.Consumer<ModContext> handler);
    Subscription playerJoin    (java.util.function.Consumer<PlayerRef> handler);
    Subscription playerLeave   (java.util.function.Consumer<PlayerRef> handler);
    Subscription worldLoad     (java.util.function.Consumer<WorldRef> handler);
    Subscription blockBroken   (BlockBreakHandler handler);
    Subscription itemUsed      (ItemUseHandler handler);
    Subscription dataReload    (java.util.function.Consumer<ModContext> handler);

    /** Erweiterungspunkt für Payload-spezifische Events. */
    <T> Subscription custom(EventKey<T> key, java.util.function.Consumer<T> handler);
}

public interface Subscription extends AutoCloseable {
    void unsubscribe();
    @Override default void close() { unsubscribe(); }
}
```

```java
package com.example.common;

final class ExampleEvents {
    static void register(ModContext ctx) {
        ctx.events().playerJoin(player -> {
            player.sendMessage("Welcome! ExampleMod runs on payload "
                    + ctx.platform().payloadId());
            ExampleNetworking.RUBY_SYNC.sendTo(player,
                    new ExampleNetworking.RubySync(Id.of("examplemod", "ruby"),
                            RubyLogic.chargeOf(player)));
        });

        ctx.events().serverTick(server -> RubyLogic.tick(server.tickCount()));

        ctx.events().blockBroken((world, player, pos, blockId) -> {
            if (blockId.equals(Id.of("examplemod", "ruby_block"))) RubyLogic.onRubyBlockBroken(player);
            return true;   // erlauben
        });
    }
}
```

## 28.4 Stabilitätsanalyse der Event-Quellen

| Fabric-API-Event | 1.20.1 | 1.21.x | 26.1 | Bewertung |
|---|---|---|---|---|
| `ServerLifecycleEvents.SERVER_STARTED/STOPPING` | ✓ | ✓ | ✓ | stabil ⇒ gemeinsame Runtime-Implementierung |
| `ServerTickEvents.END_SERVER_TICK` | ✓ | ✓ | ✓ | stabil |
| `ClientTickEvents.END_CLIENT_TICK` | ✓ | ✓ | ✓ | stabil (Client-Klasse) |
| `ServerPlayConnectionEvents.JOIN/DISCONNECT` | ✓ | ✓ | ✓ | stabil |
| `CommandRegistrationCallback` | ✓ (3 Parameter) | ✓ | ✓ | stabil ab 1.19 |
| `PlayerBlockBreakEvents.BEFORE` | ✓ | ✓ | ✓ | stabil |
| `ServerWorldEvents.LOAD` | ✓ | ✓ | ✓ | stabil |
| `HudRenderCallback` | ✓ (`MatrixStack`) | ✗ Signatur (`DrawContext`, dann `RenderTickCounter`) | ✗ | **instabil** ⇒ pro Payload über `HudRenderService` |
| `ItemTooltipCallback` | ✓ | Signatur erweitert (`Item.TooltipContext`) | ✗ | **instabil** ⇒ Service |
| `ResourceManagerHelper` | ✓ | ✓ | ✓ | stabil |
| `PayloadTypeRegistry` | ✗ existiert nicht | ✓ | ✓ | versionsspezifisch, über Capability |

Diese Tabelle ist Teil der Dokumentation (`docs/common-code.md`) und wird pro Framework-Release gegen die
Referenzmatrix geprüft — ein Integrationstest kompiliert eine Sonde gegen jede Matrix-Version und meldet
Signaturabweichungen (`EventStabilityProbeTest`, Kapitel 32.5).

---

Weiter mit [Kapitel 20–25 — Gradle-Plugin, DSL, Repository-Struktur, Build-Pipeline, Dependencies, Ressourcen](part-07-gradle.md).
