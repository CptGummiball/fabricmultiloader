package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.ServiceRegistry;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.ChannelHandle;
import dev.fabricmultiloader.api.net.ChannelSpec;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import dev.fabricmultiloader.api.registry.BlockHandle;
import dev.fabricmultiloader.api.registry.BlockSpec;
import dev.fabricmultiloader.api.registry.ItemHandle;
import dev.fabricmultiloader.api.registry.ItemSpec;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.api.registry.RegistryHandle;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.runtime.adapter.CommandRegistry;
import dev.fabricmultiloader.runtime.adapter.EventBus;
import dev.fabricmultiloader.runtime.context.ServiceRegistryImpl;
import dev.fabricmultiloader.runtime.log.Log;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link ModContext} with no Minecraft behind it, which records everything the mod declares.
 *
 * <pre>
 * ModContext ctx = FakeModContext.builder()
 *         .modId("examplemod").modVersion("2.0.0")
 *         .minecraft("1.21.4").fabricApi("0.114.0").java(21).side(Side.SERVER)
 *         .capability(Capabilities.COMPONENTS, new FakeComponents())
 *         .service(OreGenService.class, new NoOpOreGen())
 *         .build();
 *
 * new ExampleMod().onInitialize(ctx);
 *
 * assertThat(ctx.recorded().items()).containsKey(Id.of("examplemod", "ruby"));
 * </pre>
 *
 * <p>The bulk of a mod's logic is testable this way — in the reference example mod, 85 to 89 percent
 * of the classes — because the common API names no Minecraft type. That is not a testing trick
 * bolted on afterwards; it falls out of design principle P1, and it is the single biggest day-to-day
 * benefit of this architecture.
 *
 * <p>Commands and events are <b>not</b> faked. They delegate to the real
 * {@link CommandRegistry} and {@link EventBus} the runtime uses in the game, so a test exercises the
 * actual side filtering, permission accumulation, conflict detection, dispatch ordering and failure
 * containment rather than a lookalike that might disagree with them. {@link #fireServerStarted} and
 * its siblings then let a test drive the mod's own handlers.
 */
public final class FakeModContext implements ModContext {

    private final String modId;
    private final SemVer modVersion;
    private final String displayName;
    private final PlatformInfo platform;
    private final Side side;
    private final boolean development;
    private final Path gameDir;
    private final ModLogger log;
    private final Map<String, SemVer> loadedMods;
    private final Map<String, Object> capabilities;
    private final ServiceRegistryImpl services;
    private final RecordedRegistrations recorded = new RecordedRegistrations();
    private final Map<Id, FakeChannel<?>> channels = new LinkedHashMap<Id, FakeChannel<?>>();
    private final FakeRegistries registries = new FakeRegistries();
    private final FakeNetworking networking = new FakeNetworking();
    private final CommandRegistry commands;
    private final EventBus events;

    private LifecyclePhase phase = LifecyclePhase.COMMON_INIT;

    private FakeModContext(Builder builder) {
        this.modId = builder.modId;
        this.modVersion = builder.modVersion;
        this.displayName = builder.displayName == null ? builder.modId : builder.displayName;
        this.side = builder.side;
        this.development = builder.development;
        this.gameDir = builder.gameDir;
        this.log = Log.named(builder.modId);
        this.loadedMods = new LinkedHashMap<String, SemVer>(builder.loadedMods);
        this.capabilities = new LinkedHashMap<String, Object>(builder.capabilities);
        this.platform = new FakePlatformInfo(builder);
        this.commands = new CommandRegistry(builder.modId, builder.side, log);
        this.events = new EventBus(builder.modId, log);

        this.services = new ServiceRegistryImpl(builder.modId);
        this.services.openRegistration();
        for (Map.Entry<Class<?>, Object> service : builder.services.entrySet()) {
            register(service.getKey(), service.getValue());
        }
        // Left open: a test may register a service from the payload's perspective at any point,
        // and enforcing the window here would test the harness rather than the mod.
    }

    @SuppressWarnings("unchecked")
    private <T> void register(Class<T> type, Object implementation) {
        services.register(type, (T) implementation);
    }

    /** Starts a builder with sensible defaults: Minecraft 1.21.4, Java 21, server side. */
    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------------ assertions

    /** Everything the mod declared. */
    public RecordedRegistrations recorded() {
        return recorded;
    }

    /** The real command registry the mod registered into. */
    public CommandRegistry commandRegistry() {
        return commands;
    }

    /** The real event bus the mod subscribed to. */
    public EventBus eventBus() {
        return events;
    }

    /** Sets the phase a call will observe, for testing phase-dependent code. */
    public FakeModContext phase(LifecyclePhase value) {
        this.phase = value;
        return this;
    }

    // ------------------------------------------------------------------ driving the mod

    /** Fires {@code serverStarted} at the mod's handlers. */
    public void fireServerStarted(dev.fabricmultiloader.api.event.ServerRef server) {
        events.fireServerStarted(server);
    }

    /** Fires {@code playerJoin} at the mod's handlers. */
    public void firePlayerJoin(PlayerRef player) {
        events.firePlayerJoin(player);
    }

    /** Fires {@code playerLeave} at the mod's handlers. */
    public void firePlayerLeave(PlayerRef player) {
        events.firePlayerLeave(player);
    }

    /** Fires {@code worldLoad} at the mod's handlers. */
    public void fireWorldLoad(WorldRef world) {
        events.fireWorldLoad(world);
    }

    /** Fires {@code serverTick} the given number of times. */
    public void tick(dev.fabricmultiloader.api.event.ServerRef server, int times) {
        for (int i = 0; i < times; i++) {
            events.fireServerTick(server);
        }
    }

    /**
     * Runs the deferred registration flush, as the runtime does after {@code onInitialize}.
     *
     * <p>Only interesting when a test wants to assert that it happened; the recording itself is
     * immediate.
     */
    public void flush() {
        registries.flush();
    }

    // ------------------------------------------------------------------ ModContext

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public SemVer modVersion() {
        return modVersion;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public PlatformInfo platform() {
        return platform;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean isDevelopment() {
        return development;
    }

    @Override
    public LifecyclePhase phase() {
        return phase;
    }

    @Override
    public ModLogger log() {
        return log;
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public Path configDir() {
        return gameDir.resolve("config");
    }

    @Override
    public Path modConfigDir() {
        return configDir().resolve(modId);
    }

    @Override
    public Registries registries() {
        return registries;
    }

    @Override
    public Networking networking() {
        return networking;
    }

    @Override
    public Commands commands() {
        return commands;
    }

    @Override
    public Events events() {
        return events;
    }

    @Override
    public ServiceRegistry services() {
        return services;
    }

    @Override
    public <T> Optional<T> capability(Capability<T> capability) {
        if (capability == null) {
            return Optional.empty();
        }
        Object value = capabilities.get(capability.id());
        return value == null ? Optional.<T>empty() : Optional.of(capability.type().cast(value));
    }

    @Override
    public boolean has(Capability<?> capability) {
        return capability != null && capabilities.containsKey(capability.id());
    }

    @Override
    public boolean isModLoaded(String otherModId) {
        return otherModId != null && loadedMods.containsKey(otherModId);
    }

    @Override
    public Optional<SemVer> modVersionOf(String otherModId) {
        return otherModId == null ? Optional.<SemVer>empty()
                : Optional.ofNullable(loadedMods.get(otherModId));
    }

    @Override
    public String toString() {
        return modId + " " + modVersion + " on " + platform + " (" + recorded + ")";
    }

    // ------------------------------------------------------------------ subsystems

    private final class FakeRegistries implements Registries {

        @Override
        public ItemHandle item(Id id, ItemSpec spec) {
            recorded.recordItem(id, spec);
            return FakeHandles.item(id);
        }

        @Override
        public BlockHandle block(Id id, BlockSpec spec) {
            recorded.recordBlock(id, spec);
            return FakeHandles.block(id);
        }

        @Override
        public BlockHandle blockWithItem(Id id, BlockSpec blockSpec, ItemSpec itemSpec) {
            recorded.recordBlock(id, blockSpec);
            recorded.recordBlockItem(id, itemSpec);
            return FakeHandles.blockWithItem(id);
        }

        @Override
        public RegistryHandle sound(Id id) {
            recorded.recordSound(id);
            return FakeHandles.plain(id);
        }

        @Override
        public RegistryHandle itemGroup(Id id, ItemHandle icon, String displayNameKey) {
            recorded.recordItemGroup(id, displayNameKey);
            return FakeHandles.plain(id);
        }

        @Override
        public void addToItemGroup(Id groupId, ItemHandle... items) {
            for (ItemHandle item : items) {
                recorded.recordItemGroupContent(groupId, item.id());
            }
        }

        @Override
        public void flush() {
            recorded.recordFlush();
        }
    }

    private final class FakeNetworking implements Networking {

        @Override
        public <T> ChannelHandle<T> register(ChannelSpec<T> spec) {
            recorded.recordChannel(spec);
            FakeChannel<T> channel = new FakeChannel<T>(spec);
            channels.put(spec.id(), channel);
            return channel;
        }
    }

    /**
     * A channel that records what was sent and delivers to the mod's own receiver.
     *
     * <p>Payloads are recorded as objects rather than as bytes. A test asserting "the mod sent a
     * charge update for slot 3" wants the object; a test asserting that the codec round-trips wants
     * {@link PacketBuffer}, which is a separate concern and a separate tool.
     */
    private final class FakeChannel<T> implements ChannelHandle<T> {

        private final ChannelSpec<T> spec;
        private C2SReceiver<T> serverReceiver;
        private S2CReceiver<T> clientReceiver;

        FakeChannel(ChannelSpec<T> spec) {
            this.spec = spec;
        }

        @Override
        public Id id() {
            return spec.id();
        }

        @Override
        public void receiveOnServer(C2SReceiver<T> receiver) {
            this.serverReceiver = receiver;
        }

        @Override
        public void receiveOnClient(S2CReceiver<T> receiver) {
            this.clientReceiver = receiver;
        }

        @Override
        public void sendToServer(T payload) {
            recorded.recordSend(spec.id(), "server", payload);
        }

        @Override
        public void sendTo(PlayerRef player, T payload) {
            recorded.recordSend(spec.id(),
                    player == null ? "player:?" : "player:" + player.name(), payload);
        }

        @Override
        public void sendToAllIn(WorldRef world, T payload) {
            recorded.recordSend(spec.id(),
                    world == null ? "world:?" : "world:" + world.dimension(), payload);
        }

        @Override
        public void sendToAll(T payload) {
            recorded.recordSend(spec.id(), "all", payload);
        }

        @Override
        public boolean canReceive(PlayerRef player) {
            return true;
        }

        /** Delivers a payload to the mod's server-side receiver, as if a client had sent it. */
        void deliverToServer(T payload, PlayerRef sender) {
            if (serverReceiver != null) {
                serverReceiver.accept(payload, sender, FakeModContext.this);
            }
        }

        /** Delivers a payload to the mod's client-side receiver. */
        void deliverToClient(T payload) {
            if (clientReceiver != null) {
                clientReceiver.accept(payload, FakeModContext.this);
            }
        }
    }

    /**
     * Delivers a payload to the mod's receiver on the given channel, as if it had arrived over the
     * network.
     *
     * @param channelId the channel
     * @param payload the payload
     * @param sender the sending player, or {@code null} to deliver client-side
     * @param <T> the payload type
     * @throws IllegalArgumentException if the mod never registered that channel
     */
    @SuppressWarnings("unchecked")
    public <T> void deliver(Id channelId, T payload, PlayerRef sender) {
        FakeChannel<T> channel = (FakeChannel<T>) channels.get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("the mod registered no channel " + channelId
                    + "; it registered " + recorded.channels().keySet());
        }
        if (sender == null) {
            channel.deliverToClient(payload);
        } else {
            channel.deliverToServer(payload, sender);
        }
    }

    private static final class FakePlatformInfo implements PlatformInfo {

        private final SemVer minecraft;
        private final SemVer fabricLoader;
        private final SemVer fabricApi;
        private final int javaMajor;
        private final String payloadId;
        private final String mappingNamespace;

        FakePlatformInfo(Builder builder) {
            this.minecraft = builder.minecraft;
            this.fabricLoader = builder.fabricLoader;
            this.fabricApi = builder.fabricApi;
            this.javaMajor = builder.javaMajor;
            this.payloadId = builder.payloadId;
            this.mappingNamespace = builder.development
                    ? dev.fabricmultiloader.format.manifest.MappingsInfo.NAMED
                    : dev.fabricmultiloader.format.manifest.MappingsInfo.INTERMEDIARY;
        }

        @Override
        public SemVer minecraft() {
            return minecraft;
        }

        @Override
        public SemVer fabricLoader() {
            return fabricLoader;
        }

        @Override
        public Optional<SemVer> fabricApi() {
            return Optional.ofNullable(fabricApi);
        }

        @Override
        public int javaMajor() {
            return javaMajor;
        }

        @Override
        public String payloadId() {
            return payloadId;
        }

        @Override
        public String mappingNamespace() {
            return mappingNamespace;
        }

        @Override
        public boolean minecraftIn(String... predicates) {
            if (predicates == null || predicates.length == 0) {
                return false;
            }
            return dev.fabricmultiloader.format.version.VersionRange.parse(predicates)
                    .test(minecraft);
        }

        @Override
        public int minecraftOrdinal() {
            return dev.fabricmultiloader.format.version.MinecraftVersions.ordinal(minecraft);
        }

        @Override
        public String toString() {
            return "mc=" + minecraft + " payload=" + payloadId + " java=" + javaMajor;
        }
    }

    /** Builds a fake context. */
    public static final class Builder {

        private String modId = "testmod";
        private SemVer modVersion = SemVer.of(1, 0, 0);
        private String displayName;
        private SemVer minecraft = SemVer.parse("1.21.4");
        private SemVer fabricLoader = SemVer.parse("0.16.9");
        private SemVer fabricApi = SemVer.parse("0.114.0");
        private int javaMajor = 21;
        private String payloadId = "mc1214";
        private Side side = Side.SERVER;
        private boolean development;
        private Path gameDir = Paths.get(".");
        private final Map<String, SemVer> loadedMods = new LinkedHashMap<String, SemVer>();
        private final Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        private final Map<Class<?>, Object> services = new LinkedHashMap<Class<?>, Object>();

        /** Sets the mod id. */
        public Builder modId(String value) {
            this.modId = value;
            return this;
        }

        /** Sets the mod version. */
        public Builder modVersion(String value) {
            this.modVersion = SemVer.parseLenient(value);
            return this;
        }

        /** Sets the display name. */
        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        /** Sets the simulated Minecraft version, and registers it as a loaded mod. */
        public Builder minecraft(String value) {
            this.minecraft = SemVer.parseLenient(value);
            this.loadedMods.put("minecraft", this.minecraft);
            return this;
        }

        /** Sets the simulated Fabric Loader version. */
        public Builder fabricLoader(String value) {
            this.fabricLoader = SemVer.parseLenient(value);
            this.loadedMods.put("fabricloader", this.fabricLoader);
            return this;
        }

        /** Sets the simulated Fabric API version, or {@code null} for "not installed". */
        public Builder fabricApi(String value) {
            this.fabricApi = value == null ? null : SemVer.parseLenient(value);
            if (value != null) {
                this.loadedMods.put("fabric-api", this.fabricApi);
            } else {
                this.loadedMods.remove("fabric-api");
            }
            return this;
        }

        /** Sets the simulated Java feature version. */
        public Builder java(int value) {
            this.javaMajor = value;
            return this;
        }

        /** Sets the payload id reported by {@code platform().payloadId()}. */
        public Builder payloadId(String value) {
            this.payloadId = value;
            return this;
        }

        /** Sets the physical side. */
        public Builder side(Side value) {
            this.side = value;
            return this;
        }

        /** Marks the context as a development runtime. */
        public Builder inDevelopment() {
            this.development = true;
            return this;
        }

        /** Sets the game directory, normally a JUnit temporary directory. */
        public Builder gameDir(Path value) {
            this.gameDir = value;
            return this;
        }

        /** Adds another loaded mod. */
        public Builder mod(String otherModId, String version) {
            this.loadedMods.put(otherModId, SemVer.parseLenient(version));
            return this;
        }

        /**
         * Provides a capability.
         *
         * @param capability the capability
         * @param implementation what {@code ctx.capability(...)} returns
         * @param <T> the capability interface
         */
        public <T> Builder capability(Capability<T> capability, T implementation) {
            this.capabilities.put(capability.id(), implementation);
            return this;
        }

        /**
         * Registers a service, as a payload's {@code Platform#onInitialize} would.
         *
         * @param type the service interface
         * @param implementation the implementation
         * @param <T> the service type
         */
        public <T> Builder service(Class<T> type, T implementation) {
            this.services.put(type, implementation);
            return this;
        }

        /** Builds the context. */
        public FakeModContext build() {
            return new FakeModContext(this);
        }
    }
}
