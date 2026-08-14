package com.example.mc1214;

import dev.fabricmultiloader.api.Capabilities;
import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.capability.ComponentApi;
import dev.fabricmultiloader.api.command.CommandSpec;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.EventKey;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.event.ServerRef;
import dev.fabricmultiloader.api.event.Subscription;
import dev.fabricmultiloader.api.net.ChannelHandle;
import dev.fabricmultiloader.api.net.ChannelSpec;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.AbstractPlatform;
import dev.fabricmultiloader.api.platform.CrashContext;
import dev.fabricmultiloader.api.platform.PreLaunchContext;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import dev.fabricmultiloader.api.registry.BlockHandle;
import dev.fabricmultiloader.api.registry.BlockSpec;
import dev.fabricmultiloader.api.registry.ItemHandle;
import dev.fabricmultiloader.api.registry.ItemSpec;
import dev.fabricmultiloader.api.registry.Registries;
import dev.fabricmultiloader.api.registry.RegistryHandle;
import dev.fabricmultiloader.runtime.fixture.Recorder;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The payload adapter a real mod would write, minus everything that needs Minecraft.
 *
 * <p>Deliberately placed in {@code com.example.mc1214}, the package the reference manifest fixture
 * declares for its 1.21.4 payload. That makes the fixture executable end to end and means the
 * package containment check the runtime performs is exercised with a name that legitimately passes,
 * not one written to make the test go green.
 */
public final class Platform1214 extends AbstractPlatform {

    private final FakeRegistries registries = new FakeRegistries();
    private final FakeNetworking networking = new FakeNetworking();
    private final FakeCommands commands = new FakeCommands();
    private final FakeEvents events = new FakeEvents();

    Platform1214(ModContext ctx) {
        super(ctx);
        Recorder.record("platform:constructed");
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
    public void onPreLaunch(PreLaunchContext ctx) {
        Recorder.record("platform:onPreLaunch:" + ctx.modId());
    }

    @Override
    public void onInitialize(ModContext ctx) {
        Recorder.record("platform:onInitialize");
        ctx.services().register(Greeting.class, new Greeting() {
            @Override
            public String greet() {
                return "hello from 1.21.4";
            }
        });
    }

    @Override
    public void onInitializeClient(ModContext ctx) {
        Recorder.record("platform:onInitializeClient");
    }

    @Override
    public void onInitializeServer(ModContext ctx) {
        Recorder.record("platform:onInitializeServer");
    }

    @Override
    public <T> Optional<T> capability(Capability<T> capability) {
        if (Capabilities.COMPONENTS.equals(capability)) {
            return Optional.of(capability.type().cast(new FakeComponents()));
        }
        return Optional.empty();
    }

    @Override
    public void installCrashContext(CrashContext ctx) {
        Recorder.record("platform:installCrashContext");
        ctx.add("Adapter", "Platform1214");
    }

    /** A mod-defined service, implemented per version — the escape hatch in miniature. */
    public interface Greeting {
        /** Returns a version-specific greeting. */
        String greet();
    }

    /** Records the flush so the "after mod code" ordering can be asserted. */
    static final class FakeRegistries implements Registries {

        @Override
        public ItemHandle item(Id id, ItemSpec spec) {
            Recorder.record("registries:item:" + id);
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public BlockHandle block(Id id, BlockSpec spec) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public BlockHandle blockWithItem(Id id, BlockSpec blockSpec, ItemSpec itemSpec) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public RegistryHandle sound(Id id) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public RegistryHandle itemGroup(Id id, ItemHandle icon, String displayNameKey) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public void addToItemGroup(Id groupId, ItemHandle... items) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }

        @Override
        public void flush() {
            Recorder.record("registries:flush");
        }
    }

    static final class FakeNetworking implements Networking {
        @Override
        public <T> ChannelHandle<T> register(ChannelSpec<T> spec) {
            throw new UnsupportedOperationException("no Minecraft in this test");
        }
    }

    static final class FakeCommands implements Commands {
        @Override
        public void register(CommandSpec spec) {
            Recorder.record("commands:register:" + spec.literal());
        }
    }

    static final class FakeEvents implements Events {
        @Override
        public Subscription serverStarted(Consumer<ServerRef> handler) {
            return inactive();
        }

        @Override
        public Subscription serverStopping(Consumer<ServerRef> handler) {
            return inactive();
        }

        @Override
        public Subscription serverTick(Consumer<ServerRef> handler) {
            return inactive();
        }

        @Override
        public Subscription clientTick(Consumer<ModContext> handler) {
            return inactive();
        }

        @Override
        public Subscription playerJoin(Consumer<PlayerRef> handler) {
            return inactive();
        }

        @Override
        public Subscription playerLeave(Consumer<PlayerRef> handler) {
            return inactive();
        }

        @Override
        public Subscription worldLoad(Consumer<WorldRef> handler) {
            return inactive();
        }

        @Override
        public Subscription dataReload(Consumer<ModContext> handler) {
            return inactive();
        }

        @Override
        public Subscription blockBroken(BlockBreakHandler handler) {
            return inactive();
        }

        @Override
        public <T> Subscription custom(EventKey<T> key, Consumer<T> handler) {
            return inactive();
        }

        private static Subscription inactive() {
            return new Subscription() {
                @Override
                public void unsubscribe() {
                }

                @Override
                public boolean isActive() {
                    return false;
                }
            };
        }
    }

    static final class FakeComponents implements ComponentApi {
        @Override
        public Optional<Integer> getInt(ItemStackRef stack, Id component) {
            return Optional.empty();
        }

        @Override
        public Optional<String> getString(ItemStackRef stack, Id component) {
            return Optional.empty();
        }

        @Override
        public Optional<Boolean> getBoolean(ItemStackRef stack, Id component) {
            return Optional.empty();
        }

        @Override
        public ItemStackRef setInt(ItemStackRef stack, Id component, int value) {
            return stack;
        }

        @Override
        public ItemStackRef setString(ItemStackRef stack, Id component, String value) {
            return stack;
        }

        @Override
        public ItemStackRef setBoolean(ItemStackRef stack, Id component, boolean value) {
            return stack;
        }

        @Override
        public ItemStackRef remove(ItemStackRef stack, Id component) {
            return stack;
        }

        @Override
        public boolean has(ItemStackRef stack, Id component) {
            return false;
        }
    }
}
