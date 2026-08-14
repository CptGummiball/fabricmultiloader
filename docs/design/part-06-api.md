# 18. Common API

## 18.1 Design principles

| Principle | Consequence |
|---|---|
| **P1 — No Minecraft types in the common API** | Every signature uses only JDK types, `format` types and our own opaque reference types (`PlayerRef`, `WorldRef`, `ItemStackRef`). That is the only way the container can stay binary-compatible across all MC versions. |
| **P2 — Declarative instead of imperative** | Content is described as a *specification* (`ItemSpec`), not built as an MC object. The adapter translates the specification into the version-specific construction. |
| **P3 — Handles instead of objects** | Registration returns an `ItemHandle`, not an `Item`. The handle is stable across versions and offers `unwrap(Class)` as an escape hatch. |
| **P4 — Escape hatch instead of total abstraction** | What cannot be abstracted is not abstracted badly; it is moved cleanly into the version layer via `Services` (typed, user-defined interfaces) and `Capabilities` (feature gates). |
| **P5 — Java 8, no records, no `sealed`** | Binary compatibility from MC 1.16.5 onwards; builder pattern instead of records. |
| **P6 — Additive evolution** | Interfaces implemented by mod authors only ever gain `default` methods. Interfaces implemented by the framework may grow. Marked separately with `@ImplementedByFramework` / `@ImplementedByMod`. |
| **P7 — No DI framework** | A service locator (`ServiceRegistry`) with four methods replaces Guice/Dagger. Rationale: no reflection scan, no startup overhead, no extra dependency in the container, trivial debuggability. |

## 18.2 Package overview

| Package | Content |
|---|---|
| `dev.fabricmultiloader.api` | Entrypoint interfaces, `ModContext`, `Id`, `ModLogger`, `Capability`, `Capabilities`, `ServiceRegistry`, `FabricMultiLoader` (static access) |
| `dev.fabricmultiloader.format` | `Side` — the physical side. Lives in `format`, not `api`, because the payload matcher needs it and the matcher is shared between runtime and validator. Mod authors see it through `ModContext#side()`; it is visible transitively, since `api` depends on `format` as an `api` dependency. Two identically named enums in two packages of one project is a reliable source of wrong imports, and the "single source in format" rule already governs exactly this case. |
| `dev.fabricmultiloader.api.platform` | `Platform`, `PlatformFactory`, `PlatformInfo`, `PreLaunchContext`, `CrashContext` |
| `dev.fabricmultiloader.api.registry` | `Registries`, `ItemSpec`, `BlockSpec`, `SoundSpec`, `ItemGroupSpec`, `*Handle`, `Rarity`, `ToolTier` |
| `dev.fabricmultiloader.api.net` | `Networking`, `ChannelSpec`, `PayloadCodec`, `ByteSink`, `ByteSource`, `ChannelHandle`, `C2SReceiver`, `S2CReceiver` |
| `dev.fabricmultiloader.api.command` | `Commands`, `CommandSpec`, `Arg`, `CommandInvocation`, `CommandSender`, `Permission` |
| `dev.fabricmultiloader.api.event` | `Events`, `Subscription`, `LifecyclePhase`, event interfaces |
| `dev.fabricmultiloader.api.ref` | `PlayerRef`, `WorldRef`, `ItemStackRef`, `BlockPosRef`, `Unwrappable` |
| `dev.fabricmultiloader.api.config` | `ConfigHandle`, `ConfigCodec` (JSON via the `format` parser) |
| `dev.fabricmultiloader.api.resource` | `ResourceReloadListener`, `PackHandle` |

## 18.3 Entrypoint interfaces

```java
package dev.fabricmultiloader.api;

/** Shared entry point. Runs in Fabric's 'main' phase, on both client AND server. */
@ImplementedByMod
public interface UniversalMod {
    void onInitialize(ModContext ctx);
}

/** Physical client only. Runs in Fabric's 'client' phase, after onInitialize. */
@ImplementedByMod
public interface UniversalClientMod {
    void onInitializeClient(ModContext ctx);
}

/** Dedicated server only. Runs in Fabric's 'server' phase, after onInitialize. */
@ImplementedByMod
public interface UniversalServerMod {
    void onInitializeServer(ModContext ctx);
}

/**
 * Optional: runs in Fabric's 'preLaunch' phase, BEFORE Minecraft classes are loaded.
 * Must not make registry, event or networking calls (IllegalStateException).
 * Intended for loading config and early diagnostics.
 */
@ImplementedByMod
public interface UniversalPreLaunch {
    void onPreLaunch(PreLaunchContext ctx);
}
```

Registration happens **not** through `fabric.mod.json` but through the Omni manifest entrypoints, which are either
declared in the Gradle DSL or derived by the annotation processor from `@UniversalEntrypoint` (chapter 19.7).
Registration is therefore version-independent and identical for all payloads.

## 18.4 `ModContext` — complete definition

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

    // ---- identity ---------------------------------------------------------
    String modId();
    SemVer modVersion();
    String displayName();

    // ---- environment ------------------------------------------------------
    PlatformInfo platform();
    Side side();                                  // CLIENT | SERVER (physical)
    boolean isDevelopment();

    // ---- infrastructure ---------------------------------------------------
    ModLogger log();
    Path gameDir();
    Path configDir();                             // <gameDir>/config
    Path modConfigDir();                          // <gameDir>/config/<modId>, created on demand

    // ---- subsystems -------------------------------------------------------
    Registries registries();
    Networking networking();
    Commands commands();
    Events events();
    ServiceRegistry services();

    // ---- capabilities and foreign mods ------------------------------------
    <T> Optional<T> capability(Capability<T> capability);
    boolean has(Capability<?> capability);
    boolean isModLoaded(String modId);
    Optional<SemVer> modVersionOf(String modId);

    // ---- lifecycle state --------------------------------------------------
    LifecyclePhase phase();
}
```

`ModContext` is a framework interface (P6): it may grow between minor versions. Mod authors never implement it.

## 18.5 `PlatformInfo`

```java
package dev.fabricmultiloader.api.platform;

public interface PlatformInfo {
    SemVer minecraft();          // 1.21.4
    SemVer fabricLoader();       // 0.16.9
    Optional<SemVer> fabricApi();
    int javaMajor();             // 17 | 21 | 25 | …
    String payloadId();          // "mc1214"
    String mappingNamespace();   // "intermediary" | "named" (dev)

    /** true when the running MC version falls inside the given Fabric predicate range. */
    boolean minecraftIn(String... predicates);

    /** Compact form for comparisons in common code: 1.21.4 -> 12104, 26.1 -> 260100. */
    int minecraftOrdinal();
}
```

`minecraftIn(">=1.21")` and `minecraftOrdinal()` let common code vary *behaviour* (e.g. different default config
values) without touching MC types. They do **not** allow calling different MC APIs — that remains the adapter's
job. This boundary is made explicit in `docs/common-code.md` with an anti-pattern example.

## 18.6 Opaque references and the escape hatch

```java
package dev.fabricmultiloader.api.ref;

public interface Unwrappable {
    /**
     * Returns the underlying Minecraft object.
     * Call ONLY from version modules — common code cannot reference the type
     * without losing its version neutrality.
     *
     * @throws ClassCastException when the type does not match (with an explanatory message)
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
    void sendMessage(dev.fabricmultiloader.api.text.Text text);   // our own minimal text model
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

`unwrap` is the deliberately built-in, documented way out. It is **not usefully callable from common code** (the
type is not available there) but is the bridge back to the full MC API inside a version module:

```java
// inside the 1.21.4 payload
ServerPlayerEntity mcPlayer = playerRef.unwrap(ServerPlayerEntity.class);
```

The own text model (`dev.fabricmultiloader.api.text`) comprises `Text.literal`, `Text.translatable`,
`Text.of(...).color(...).bold()` and is translated by the adapter into `net.minecraft.text.Text`. It is
deliberately minimal (literal, translatable, colour, style, click/hover action) and covers the cases common code
realistically needs.

## 18.7 Services — the typed escape hatch

```java
package dev.fabricmultiloader.api;

@ImplementedByFramework
public interface ServiceRegistry {
    <T> T get(Class<T> type);                       // throws OmniException OMNI-4010 when absent
    <T> java.util.Optional<T> find(Class<T> type);
    <T> void register(Class<T> type, T impl);        // permitted only during Platform#onInitialize
    java.util.Set<Class<?>> registered();
}
```

Usage: the mod developer defines an interface without MC types in the **common** module, implements it in the
**version** module, and calls it from common code.

```java
// common/src/main/java/com/example/common/service/OreGenService.java
package com.example.common.service;

public interface OreGenService {
    /** Adds the ore placement into world generation. */
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
        // version-specific registration of ConfiguredFeature/PlacedFeature …
    }
}
```

```java
// common: call without any MC reference
ctx.services().get(OreGenService.class).installRubyOre(6, 12, -32, 48);
```

This makes **every** non-abstracted MC feature reachable without the common API having to know about it. The price
is one interface per need — in exchange it is type-safe, refactorable, testable (mockable in unit tests) and free
of reflection.

## 18.8 Capabilities — feature gates instead of version comparisons

```java
package dev.fabricmultiloader.api;

public final class Capability<T> {
    private final String id; private final Class<T> type;
    public static <T> Capability<T> of(String id, Class<T> type);
    public String id(); public Class<T> type();
    // equals/hashCode over id
}

public final class Capabilities {
    /** Data components instead of NBT — only from MC 1.20.5. */
    public static final Capability<ComponentApi> COMPONENTS =
            Capability.of("components", ComponentApi.class);

    /** Typed network payloads (CustomPayload) — only from MC 1.20.5. */
    public static final Capability<TypedPayloadApi> TYPED_PAYLOADS =
            Capability.of("networking.typed", TypedPayloadApi.class);

    /** Registry sets/tags with RegistryEntryLookup — from 1.19.3. */
    public static final Capability<TagApi> TAGS = Capability.of("tags", TagApi.class);

    /** Client gametest support — only where Fabric API offers it. */
    public static final Capability<ClientGametestApi> CLIENT_GAMETEST =
            Capability.of("gametest.client", ClientGametestApi.class);
}
```

Usage in common code:

```java
ctx.capability(Capabilities.COMPONENTS).ifPresent(components ->
        components.attach(rubyHandle, "examplemod:charge", 0));
```

Advantage over `if (ctx.platform().minecraftOrdinal() >= 12005)`: the condition is **semantic** (“are components
available?”) instead of **numeric**, it is declared in the payload (`payload.capabilities` in the manifest, hence
visible to the validator and in the diagnostic report), and a backport or an early implementation changes only a
declaration instead of common code.

## 18.9 `ModLogger`

```java
public interface ModLogger {
    void trace(String msg, Object... args);       // {} placeholders, SLF4J style
    void debug(String msg, Object... args);
    void info (String msg, Object... args);
    void warn (String msg, Object... args);
    void error(String msg, Object... args);
    void error(String msg, Throwable t, Object... args);
    boolean isDebugEnabled();
    ModLogger sub(String name);                   // "examplemod/net"
}
```

Implemented over SLF4J when available, otherwise `System.err` (chapter 9.8). `{}` formatting is implemented in
house (8 lines) so it behaves identically without SLF4J.

---

# 19. Version Adapter API

## 19.1 `Platform` and `PlatformFactory`

```java
package dev.fabricmultiloader.api.platform;

@ImplementedByMod          // but only in version modules!
public interface PlatformFactory {
    Platform create(ModContext ctx);
}

@ImplementedByMod
public interface Platform {

    PlatformInfo info();

    // ---- subsystem implementations (mandatory) ---------------------------
    Registries registries();
    Networking networking();
    Commands   commands();
    Events     events();

    // ---- lifecycle hooks (all default, hence optional) -------------------
    default void onPreLaunch(PreLaunchContext ctx) { }
    default void onInitialize(ModContext ctx) { }
    default void onInitializeClient(ModContext ctx) { }
    default void onInitializeServer(ModContext ctx) { }

    /** Capability resolution. Default: none. */
    default <T> java.util.Optional<T> capability(Capability<T> capability) {
        return java.util.Optional.empty();
    }

    /** Adds context to crash reports (chapter 30.3). Default: nothing. */
    default void installCrashContext(CrashContext ctx) { }
}
```

To keep a version module minimal, the runtime provides an abstract base class with sensible defaults:

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

## 19.2 A complete adapter for Minecraft 1.21.4

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
import dev.fabricmultiloader.runtime.adapter.CommandsImpl;      // provided by the runtime
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
        // No flush() here: the runtime calls Registries#flush after the mod's own onInitialize,
        // which is the first moment everything the mod declares has been declared (19.4).
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

/** Referenced by the manifest: payload.platformFactory */
public final class Platform1214Factory implements PlatformFactory {
    @Override public Platform create(ModContext ctx) { return new Platform1214(ctx); }
}
```

The adapter is therefore ~40 lines of glue code. `CommandsImpl` and `EventsImpl` are provided by the runtime,
because commands and Fabric API events are stable enough across 1.20.1–26.1 to allow **one** implementation
(chapter 28.4) — but they remain overridable should a version diverge.

## 19.3 The lifecycle in detail

```
Phase                       Container            Payload                       Mod code
─────────────────────────── ─────────────────── ───────────────────────────── ──────────────────────────
preLaunch (Fabric)          ContainerPreLaunch  PayloadPreLaunch
  · manifest + resolve      ✓
  · create the platform                          PlatformFactory#create
  · Platform#onPreLaunch                         ✓
  · UniversalPreLaunch                                                         onPreLaunch(PreLaunchContext)
main (Fabric)                                    PayloadMain
  · Platform#onInitialize                        ✓  (services, registries)
  · UniversalMod                                                               onInitialize(ModContext)
  · Registries#flush                             ✓  (after the mod code!)
client (Fabric)                                  PayloadClient
  · Platform#onInitializeClient                  ✓
  · UniversalClientMod                                                         onInitializeClient(ModContext)
server (Fabric)                                  PayloadServer
  · Platform#onInitializeServer                  ✓
  · UniversalServerMod                                                         onInitializeServer(ModContext)
runtime                                                                        events, commands, networking
```

Important: `Platform#onInitialize` runs **before** the mod code (so services are available),
`Registries#flush()` runs **after** the mod code (so all declared content has been collected). This order is
normative and enforced by `LifecycleStateMachine`; a registry call after `flush` throws `OMNI-4002` with a hint at
the correct phase.

## 19.4 Registries — declarative with deferred execution

```java
package dev.fabricmultiloader.api.registry;

@ImplementedByMod            // in the version module
public interface Registries {
    ItemHandle      item(Id id, ItemSpec spec);
    BlockHandle     block(Id id, BlockSpec spec);
    /** Registers a block and its BlockItem in one step. */
    BlockHandle     blockWithItem(Id id, BlockSpec spec, ItemSpec itemSpec);
    SoundHandle     sound(Id id);
    ItemGroupHandle itemGroup(Id id, ItemGroupSpec spec);
    void addToItemGroup(Id groupId, ItemHandle... items);

    /**
     * Performs the deferred registrations. Called by the runtime after the mod's onInitialize;
     * mod code never calls it. Default-empty, so an adapter registering eagerly ignores it.
     */
    default void flush() { }
}
```

```java
package dev.fabricmultiloader.api.registry;

public final class ItemSpec {
    private final int maxCount; private final Rarity rarity; private final boolean fireproof;
    private final Integer maxDamage; private final Id craftingRemainder;
    private final ItemBehavior behavior;   // may be null
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

/** Behaviour callbacks without Minecraft types. */
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

Implementations for 1.20.1 and 1.21.4 — the divergence is real and shown here in full:

```java
// versions/mc-1.20.1/src/main/java/com/example/mc1201/registry/Registries1201.java
package com.example.mc1201.registry;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class Registries1201 implements dev.fabricmultiloader.api.registry.Registries {

    private final java.util.List<Runnable> deferred = new java.util.ArrayList<>();
    private final ModContext ctx;

    public Registries1201(ModContext ctx) { this.ctx = ctx; }

    @Override
    public ItemHandle item(Id id, ItemSpec spec) {
        Identifier mcId = new Identifier(id.namespace(), id.path());     // 1.20.1: constructor
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
        Identifier mcId = Identifier.of(id.namespace(), id.path());        // 1.21+: static factory
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, mcId);  // mandatory from 1.21.2
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

The differences — `new Identifier(...)` vs. `Identifier.of(...)`, `FabricItemSettings` vs. `Item.Settings`,
`registryKey` as a new requirement, `Registry.register(registry, Identifier, T)` vs.
`Registry.register(registry, RegistryKey, T)` — are exactly the kind of break that makes a single compilation
impossible, and here they are contained in **15 lines of adapter code each**.

`ItemHandle`:

```java
package dev.fabricmultiloader.api.registry;

public interface ItemHandle extends Unwrappable {
    Id id();
    boolean isBound();                       // false before flush()
    ItemStackRef stack(int count);
    dev.fabricmultiloader.api.text.Text name();
}
```

## 19.5 Why deferred registration

Common code declares content in `onInitialize`. At that moment the MC registry is already writable in some versions
and not yet in others; from 1.21.2 onwards `Item.Settings` requires a `RegistryKey` that has to be derived from the
ID. Deferral solves three problems at once:

1. The adapter decides the **timing** of the real registration per version (in `flush()`, called from `PayloadMain`
   after the mod code).
2. Common code immediately receives an `ItemHandle` and can store it in fields even though the MC object does not
   exist yet (`isBound() == false`).
3. Registration order is deterministic (declaration order), which matters for registry IDs in network protocols and
   data packs.

## 19.6 Capability declaration and validation

A payload declares in the build (chapter 21.4) `capabilities = ["registries", "commands", "networking.v1",
"events.lifecycle", "components"]`. The validator checks (`OMNI-1130`) that `Platform#capability` returns a
non-empty value for every declared capability — verified by a **reflection-free bytecode check**: the adapter class
must override a `capability` method and reference the corresponding `Capabilities` constant in its constant pool.
That is a heuristic with a clear error message and a `@SuppressCapabilityCheck` opt-out; in practice it catches the
most common mistake (a capability declared but `capability()` not implemented).

The diagnostic report lists capabilities per payload, so a user immediately sees why a feature is missing on
1.20.1.

## 19.7 Annotation processor — eliminating boilerplate

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

* validates that the annotated class implements the matching interface, is `public`, is not abstract and has a
  public no-argument constructor — otherwise an `error` bound to the `Element`, i.e. visible directly in the IDE;
* validates that the class resides in a package declared in `commonPackages`;
* writes `omni/entrypoints.json` into the resources of `:common`;
* the `generateOmniManifest` task reads that file and merges it with the DSL entries; duplicates are an error
  (`OMNI-1140`), missing entrypoints likewise (`OMNI-1141`: at least one `common` entrypoint).

The processor is **optional**: `fabricMultiLoader { mod { entrypoint("com.example.common.ExampleMod") } }` works
without it. The template enables it.

## 19.8 Version-specific extension by mod authors

A version module may replace the implementations supplied by the runtime:

```java
public final class Platform1201 extends AbstractPlatform {
    private final Commands commands;
    Platform1201(ModContext ctx) {
        super(ctx);
        // 1.20.1 has no 'registryAccess' variant of a command we need:
        this.commands = new Commands1201(ctx);        // our own implementation
    }
    @Override public Commands commands() { return commands; }
}
```

The runtime implementation is therefore a default, not a straitjacket — which matters for longevity: if MC 27
overhauls command registration, only the new payload needs its own `Commands` implementation; the runtime does not
have to be updated.

## 19.9 The public mod API for third-party mods

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

The container publishes the implementation into `getObjectShare()` at the end of `onInitialize`. Because this
interface lives in the **container** (not in the payload) and the container is the same compilation across all MC
versions: **a third-party mod compiles exactly once against `examplemod-api` and works with every MC version.**
That is an advantage a classic multi-JAR release does not have, and a central selling point of the framework.

On the publishing side a separate Maven artifact is produced for it: `com.example:examplemod-api:2.0.0` = the
`:common` JAR filtered to `com.example.common.api.**` plus `dev.fabricmultiloader.api.**` as an `api` dependency
(chapter 24.7).

---

# 26. Client/Server Handling

## 26.1 Three levels of side separation

| Level | Mechanism | Effect |
|---|---|---|
| **Payload level** | `environment: "client"` in the payload `fabric.mod.json` | The payload is not loaded at all on dedicated servers — including its mixins, AW and resources. For pure client mods. |
| **Mixin config level** | `{"config": "…client.mixins.json", "environment": "client"}` | Client mixins are not registered on the server. |
| **Code level** | Separate entrypoints (`UniversalClientMod`), separate packages (`*.client.**`) | Client classes are never loaded on the server. |

## 26.2 Rules for common code

* Common code may query `ctx.side()` but must **never** call client-side functionality directly.
* Client-specific business logic belongs in a class reachable only from `UniversalClientMod`.
* The validator checks (`OMNI-1150`) that no class reachable from a `common` entrypoint references a class in a
  package declared `clientOnly`. The reachability analysis is a simple transitive constant-pool scan over the
  container — fast and without bytecode interpretation.

## 26.3 Server-only payloads

Analogous to client-only: `environment: "server"`. Practically relevant for mods that need additional payloads on
dedicated servers (e.g. because a server software such as a Paper-like Fabric fork requires its own adjustments).
The framework supports it; the template does not use it.

## 26.4 Example: client initialisation

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

        // network receiver for the S2C message (chapter 27)
        ExampleNetworking.RUBY_SYNC.receiveOnClient((payload, client) ->
                client.log().debug("charge={} for {}", payload.charge(), payload.item()));

        // client tick event through the neutral event API
        ctx.events().clientTick(client -> RubyHud.tick());

        // version-/capability-dependent client extension
        ctx.capability(Capabilities.COMPONENTS).ifPresent(c -> RubyHud.enableComponentDisplay());
    }
}
```

`RubyHud` lives in the common module and works exclusively with neutral types; the actual rendering happens through
a service (`HudRenderService`) implemented by each payload — because `HudRenderCallback`/`DrawContext` changed
several times between 1.20.1 and 1.21.4.

---

# 27. Networking Example — the reference case for adaptation

## 27.1 The common API

```java
package dev.fabricmultiloader.api.net;

@ImplementedByMod          // in the version module
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

    /** Server side: receiver for C2S. */
    void receiveOnServer(C2SReceiver<T> receiver);
    /** Client side: receiver for S2C. */
    void receiveOnClient(S2CReceiver<T> receiver);

    /** Client → server. Callable on the client only. */
    void sendToServer(T payload);
    /** Server → one player. */
    void sendTo(PlayerRef player, T payload);
    /** Server → all players in a world. */
    void sendToAllIn(WorldRef world, T payload);
    /** Server → all players. */
    void sendToAll(T payload);

    /** true when the peer has registered this channel. */
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

public interface ByteSource { /* symmetric read* methods */ }
```

`ByteSink`/`ByteSource` are the decisive trick: they encapsulate `PacketByteBuf` resp. `RegistryByteBuf`, so the
codec can live in **common** code even though the buffer types are version-specific. `writeItemStack` is
deliberately part of the interface, because ItemStack serialisation changed fundamentally between 1.20.1 (NBT) and
1.20.5+ (components, registry-dependent) and can therefore be implemented correctly only by the adapter.

## 27.2 Usage in common code

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

This code is **completely version-neutral** and is compiled exactly once.

## 27.3 Adapter for Minecraft 1.20.1 (raw `PacketByteBuf`, channel-based)

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
                        T payload = spec.codec().read(new ByteSource1201(buf));      // on the network thread
                        server.execute(() ->                                         // on the server thread
                                receiver.accept(payload, new PlayerRef1201(player), ctx));
                    });
        }

        @Override
        public void receiveOnClient(S2CReceiver<T> receiver) {
            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
            ClientNet1201.register(channel, spec, receiver, ctx);   // separate class: no client type here
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
        // sendToAllIn / sendToAll analogously via PlayerLookup
    }
}
```

```java
// ByteSink adapter for 1.20.1
final class ByteSink1201 implements ByteSink {
    private final PacketByteBuf buf;
    ByteSink1201(PacketByteBuf buf) { this.buf = buf; }
    public ByteSink writeVarInt(int v)   { buf.writeVarInt(v); return this; }
    public ByteSink writeString(String v){ buf.writeString(v); return this; }
    public ByteSink writeId(Id v)        { buf.writeIdentifier(new Identifier(v.namespace(), v.path())); return this; }
    public ByteSink writeItemStack(ItemStackRef v) { buf.writeItemStack(v.unwrap(ItemStack.class)); return this; }
    // … all remaining methods
}
```

## 27.4 Adapter for Minecraft 1.21.4 (typed `CustomPayload`)

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

    /** Generic CustomPayload wrapper: carries the already-serialised common payload. */
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
                    // context.player() already provides the server-thread context
                    receiver.accept(payload.value(), new PlayerRef1214(context.player()), ctx));
        }

        @Override
        public void sendTo(PlayerRef player, T payload) {
            ServerPlayNetworking.send(player.unwrap(ServerPlayerEntity.class),
                    new OmniPayload<>(payloadId, payload));
        }
        // sendToServer via ClientPlayNetworking.send(new OmniPayload<>(...)) in a client class
    }

    // TypedPayloadApi (capability): lets common code use native codecs when available
    @Override public boolean supportsRegistrySync() { return true; }
}
```

## 27.5 What this example demonstrates

| Aspect | 1.20.1 | 1.21.4 | Resolution |
|---|---|---|---|
| Channel identity | `Identifier` | `CustomPayload.Id<T>` | encapsulated by the adapter |
| Registration | implicit at the first receiver | explicit via `PayloadTypeRegistry`, **before** the first join | the adapter registers in `register()`, i.e. during `onInitialize` — works in both versions |
| Buffer type | `PacketByteBuf` | `RegistryByteBuf` (with registry access) | the `ByteSink`/`ByteSource` abstraction |
| Thread hand-off | manual `server.execute` | taken over by the `context` | the adapter normalises: the receiver **always** runs on the game thread |
| ItemStack serialisation | NBT | component codec, registry-dependent | `ByteSink#writeItemStack` |
| Fabric API version | ≥ 0.92.2 | ≥ 0.114.0 | declared per payload |

The mod author writes **one** codec and **one** handler and gets correct behaviour on both versions. This
pattern — a neutral specification plus a neutral data path plus a version-specific binding — is the base pattern
for every further subsystem.

---

# 28. Registries and Events — further examples

## 28.1 Item registration in common code

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
    java.util.Optional<PlayerRef> player();      // the executing player, if any
    CommandSender sender();
    void reply(String plainText);
    void reply(dev.fabricmultiloader.api.text.Text text);
    void broadcast(dev.fabricmultiloader.api.text.Text text);
}
```

Common usage:

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

The adapter (`CommandsImpl` in the runtime, shared across versions) translates `CommandSpec` into Brigadier:

```java
package dev.fabricmultiloader.runtime.adapter;

public final class CommandsImpl implements Commands {
    // Uses only CommandRegistrationCallback + Brigadier — both stable from
    // 1.19 to 26.1. The single divergence (ServerCommandSource#sendFeedback takes
    // a Supplier from 1.20 onwards) is handled by a small per-payload
    // FeedbackAdapter that the runtime obtains from Platform#capability.
}
```

This also demonstrates that the runtime **may** contain version-spanning adapters — wherever the underlying API is
genuinely stable. The validator documents that through `payload.capabilities`: a payload that does not declare
`commands` must implement `Platform#commands()` itself.

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

    /** Extension point for payload-specific events. */
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
            return true;   // allow
        });
    }
}
```

## 28.4 Stability analysis of the event sources

| Fabric API event | 1.20.1 | 1.21.x | 26.1 | Assessment |
|---|---|---|---|---|
| `ServerLifecycleEvents.SERVER_STARTED/STOPPING` | ✓ | ✓ | ✓ | stable ⇒ shared runtime implementation |
| `ServerTickEvents.END_SERVER_TICK` | ✓ | ✓ | ✓ | stable |
| `ClientTickEvents.END_CLIENT_TICK` | ✓ | ✓ | ✓ | stable (client class) |
| `ServerPlayConnectionEvents.JOIN/DISCONNECT` | ✓ | ✓ | ✓ | stable |
| `CommandRegistrationCallback` | ✓ (3 parameters) | ✓ | ✓ | stable from 1.19 |
| `PlayerBlockBreakEvents.BEFORE` | ✓ | ✓ | ✓ | stable |
| `ServerWorldEvents.LOAD` | ✓ | ✓ | ✓ | stable |
| `HudRenderCallback` | ✓ (`MatrixStack`) | ✗ signature (`DrawContext`, then `RenderTickCounter`) | ✗ | **unstable** ⇒ per payload via `HudRenderService` |
| `ItemTooltipCallback` | ✓ | signature extended (`Item.TooltipContext`) | ✗ | **unstable** ⇒ service |
| `ResourceManagerHelper` | ✓ | ✓ | ✓ | stable |
| `PayloadTypeRegistry` | ✗ does not exist | ✓ | ✓ | version-specific, via capability |

This table is part of the documentation (`docs/common-code.md`) and is checked against the reference matrix on
every framework release — an integration test compiles a probe against each matrix version and reports signature
deviations (`EventStabilityProbeTest`, chapter 32.5).

---

Continue with [chapters 20–25 — Gradle plugin, DSL, repository structure, build pipeline, dependencies, resources](part-07-gradle.md).
