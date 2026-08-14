package dev.fabricmultiloader.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.api.Capabilities;
import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.UniversalMod;
import dev.fabricmultiloader.api.command.Arg;
import dev.fabricmultiloader.api.command.CommandSpec;
import dev.fabricmultiloader.api.net.ByteSink;
import dev.fabricmultiloader.api.net.ByteSource;
import dev.fabricmultiloader.api.net.ChannelSpec;
import dev.fabricmultiloader.api.net.PayloadCodec;
import dev.fabricmultiloader.api.registry.BlockSpec;
import dev.fabricmultiloader.api.registry.ItemHandle;
import dev.fabricmultiloader.api.registry.ItemSpec;
import dev.fabricmultiloader.api.registry.Rarity;
import dev.fabricmultiloader.format.Side;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The claim this module exists to make good on: a mod's common half is testable without Minecraft,
 * without Loom, in milliseconds.
 *
 * <p>{@link ExampleCommonMod} below is written exactly as a real mod's common code would be — it
 * imports nothing from Minecraft, because it cannot.
 */
class FakeModContextTest {

    private static final Id RUBY = Id.of("examplemod", "ruby");
    private static final Id RUBY_BLOCK = Id.of("examplemod", "ruby_block");
    private static final Id SYNC = Id.of("examplemod", "ruby_sync");

    /** A mod, as one is actually written. */
    static final class ExampleCommonMod implements UniversalMod {

        static final PayloadCodec<Integer> CHARGE = new PayloadCodec<Integer>() {
            @Override
            public void write(ByteSink out, Integer value) {
                out.writeVarInt(value.intValue());
            }

            @Override
            public Integer read(ByteSource in) {
                return Integer.valueOf(in.readVarInt());
            }
        };

        final List<String> log = new ArrayList<String>();

        @Override
        public void onInitialize(ModContext ctx) {
            ItemHandle ruby = ctx.registries().item(RUBY,
                    ItemSpec.builder().maxCount(64).rarity(Rarity.UNCOMMON).build());
            ctx.registries().blockWithItem(RUBY_BLOCK,
                    BlockSpec.builder().strength(5.0f).requiresTool().build(),
                    ItemSpec.simple());
            ctx.registries().addToItemGroup(Id.minecraft("building_blocks"), ruby);

            ctx.networking().register(ChannelSpec.both(SYNC, CHARGE));

            ctx.commands().register(CommandSpec.named("ruby")
                    .permissionLevel(2)
                    .sub(CommandSpec.named("give")
                            .arg("amount", Arg.integer(1, 64))
                            .executes(inv -> 1)
                            .build())
                    .build());

            ctx.events().playerJoin(player -> log.add("joined"));

            // The version-dependent branch every universal mod has, expressed as a capability
            // rather than as a version number.
            if (ctx.has(Capabilities.COMPONENTS)) {
                log.add("components available");
            } else {
                log.add("components unavailable");
            }
        }
    }

    @Nested
    @DisplayName("recording")
    class Recording {

        @Test
        @DisplayName("a mod's declarations are recorded verbatim")
        void recordsRegistrations() {
            FakeModContext ctx = FakeModContext.builder()
                    .modId("examplemod").modVersion("2.0.0")
                    .minecraft("1.21.4").fabricApi("0.114.0").java(21).side(Side.SERVER)
                    .capability(Capabilities.COMPONENTS, new FakeComponents())
                    .build();

            new ExampleCommonMod().onInitialize(ctx);

            assertThat(ctx.recorded().items()).containsOnlyKeys(RUBY);
            // Verbatim: the fake records the specification the mod built, so a test can assert on
            // the exact values the adapter would have used.
            assertThat(ctx.recorded().items().get(RUBY).maxCount()).isEqualTo(64);
            assertThat(ctx.recorded().items().get(RUBY).rarity()).isEqualTo(Rarity.UNCOMMON);
            assertThat(ctx.recorded().blocks()).containsOnlyKeys(RUBY_BLOCK);
            assertThat(ctx.recorded().blocks().get(RUBY_BLOCK).requiresTool()).isTrue();
            assertThat(ctx.recorded().blockItems()).containsOnlyKeys(RUBY_BLOCK);
            assertThat(ctx.recorded().itemGroupContents())
                    .containsEntry(Id.minecraft("building_blocks"),
                            java.util.Collections.singletonList(RUBY));
            assertThat(ctx.recorded().hasChannel(SYNC)).isTrue();
        }

        @Test
        @DisplayName("commands go through the real registry, not a lookalike")
        void usesTheRealCommandRegistry() {
            FakeModContext ctx = FakeModContext.builder().side(Side.SERVER).build();

            new ExampleCommonMod().onInitialize(ctx);

            // The same collection logic the game runs: full paths, folded arguments, and the
            // permission inherited from the gated parent.
            assertThat(ctx.commandRegistry().nodes()).hasSize(1);
            assertThat(ctx.commandRegistry().nodes().get(0).path()).isEqualTo("ruby give");
            assertThat(ctx.commandRegistry().nodes().get(0).arguments()).containsOnlyKeys("amount");
            assertThat(ctx.commandRegistry().nodes().get(0).permissionLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("events go through the real bus, so a test can drive the mod's handlers")
        void usesTheRealEventBus() {
            FakeModContext ctx = FakeModContext.builder().build();
            ExampleCommonMod mod = new ExampleCommonMod();
            mod.onInitialize(ctx);

            ctx.firePlayerJoin(null);
            ctx.firePlayerJoin(null);

            assertThat(mod.log).contains("joined");
            assertThat(java.util.Collections.frequency(mod.log, "joined")).isEqualTo(2);
            assertThat(ctx.eventBus().activeEvents()).containsExactly("playerJoin");
        }

        @Test
        @DisplayName("sent payloads are recorded as objects, not as bytes")
        void recordsSentPayloads() {
            FakeModContext ctx = FakeModContext.builder().build();
            ctx.networking().register(ChannelSpec.both(SYNC, ExampleCommonMod.CHARGE))
                    .sendToAll(Integer.valueOf(42));

            assertThat(ctx.recorded().sent()).hasSize(1);
            assertThat(ctx.recorded().sent().get(0).channel()).isEqualTo(SYNC);
            assertThat(ctx.recorded().sent().get(0).target()).isEqualTo("all");
            assertThat(ctx.recorded().sent().get(0).payload()).isEqualTo(Integer.valueOf(42));
        }

        @Test
        @DisplayName("an incoming payload reaches the mod's receiver")
        void deliversIncomingPayloads() {
            FakeModContext ctx = FakeModContext.builder().build();
            final List<Object> received = new ArrayList<Object>();
            ctx.networking().register(ChannelSpec.both(SYNC, ExampleCommonMod.CHARGE))
                    .receiveOnClient((payload, context) -> received.add(payload));

            ctx.deliver(SYNC, Integer.valueOf(7), null);

            assertThat(received).containsExactly(Integer.valueOf(7));
        }

        @Test
        @DisplayName("delivering on a channel the mod never registered says which ones it did")
        void reportsAnUnknownChannel() {
            FakeModContext ctx = FakeModContext.builder().build();
            ctx.networking().register(ChannelSpec.both(SYNC, ExampleCommonMod.CHARGE));

            assertThatThrownBy(() -> ctx.deliver(Id.of("examplemod", "other"), "x", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("examplemod:ruby_sync");
        }
    }

    @Nested
    @DisplayName("simulated environments")
    class Environments {

        @Test
        @DisplayName("the same mod code takes both capability branches, depending only on the fake")
        void simulatesThreeMinecraftVersions() {
            // This is the argument for capabilities over version comparisons, as a test: the
            // branch is chosen by what the payload provides, so both paths are reachable without
            // running three Minecraft versions.
            ExampleCommonMod modern = new ExampleCommonMod();
            modern.onInitialize(FakeModContext.builder()
                    .minecraft("1.21.4").java(21).payloadId("mc1214")
                    .capability(Capabilities.COMPONENTS, new FakeComponents())
                    .build());

            ExampleCommonMod legacy = new ExampleCommonMod();
            legacy.onInitialize(FakeModContext.builder()
                    .minecraft("1.20.1").java(17).payloadId("mc1201")
                    .fabricApi("0.92.2")
                    .build());

            assertThat(modern.log).contains("components available");
            assertThat(legacy.log).contains("components unavailable");
        }

        @Test
        @DisplayName("the platform reports what the builder was told")
        void reportsTheSimulatedPlatform() {
            FakeModContext ctx = FakeModContext.builder()
                    .minecraft("1.20.1").fabricLoader("0.14.21").fabricApi("0.92.2")
                    .java(17).payloadId("mc1201").side(Side.CLIENT)
                    .mod("cloth-config", "11.0.99")
                    .build();

            assertThat(ctx.platform().minecraft().toString()).isEqualTo("1.20.1");
            assertThat(ctx.platform().javaMajor()).isEqualTo(17);
            assertThat(ctx.platform().payloadId()).isEqualTo("mc1201");
            assertThat(ctx.platform().minecraftIn(">=1.20 <1.21")).isTrue();
            assertThat(ctx.platform().minecraftIn(">=1.21")).isFalse();
            assertThat(ctx.platform().minecraftOrdinal()).isEqualTo(12001);
            assertThat(ctx.side()).isEqualTo(Side.CLIENT);
            assertThat(ctx.isModLoaded("cloth-config")).isTrue();
            assertThat(ctx.modVersionOf("cloth-config").get().toString()).isEqualTo("11.0.99");
        }

        @Test
        @DisplayName("a client-only command is filtered on a simulated server")
        void appliesSideFiltering() {
            FakeModContext server = FakeModContext.builder().side(Side.SERVER).build();
            server.commands().register(CommandSpec.named("hud")
                    .onlyOn(Side.CLIENT).executes(inv -> 1).build());

            assertThat(server.commandRegistry().nodes()).isEmpty();
        }

        @Test
        @DisplayName("no Fabric API means none is reported")
        void simulatesAMissingFabricApi() {
            FakeModContext ctx = FakeModContext.builder().fabricApi(null).build();

            assertThat(ctx.platform().fabricApi()).isEmpty();
            assertThat(ctx.isModLoaded("fabric-api")).isFalse();
        }
    }

    @Nested
    @DisplayName("services and capabilities")
    class ServicesAndCapabilities {

        interface OreGen {
            String describe();
        }

        @Test
        @DisplayName("a service registered on the builder is readable from the context")
        void providesServices() {
            FakeModContext ctx = FakeModContext.builder()
                    .service(OreGen.class, () -> "fake ore gen")
                    .build();

            assertThat(ctx.services().get(OreGen.class).describe()).isEqualTo("fake ore gen");
            assertThat(ctx.services().registered()).containsExactly(OreGen.class);
        }

        @Test
        @DisplayName("components round-trip through the fake")
        void componentsWork() {
            FakeComponents components = new FakeComponents();
            FakeModContext ctx = FakeModContext.builder()
                    .capability(Capabilities.COMPONENTS, components)
                    .build();
            dev.fabricmultiloader.api.ref.ItemStackRef stack =
                    new FakeHandles.FakeItemStack(RUBY, 1);
            Id charge = Id.of("examplemod", "charge");

            ctx.capability(Capabilities.COMPONENTS).get().setInt(stack, charge, 5);

            assertThat(components.getInt(stack, charge)).contains(Integer.valueOf(5));
            assertThat(components.has(stack, charge)).isTrue();
            // Stack identity, not content: two stacks of the same item are different stacks.
            assertThat(components.has(new FakeHandles.FakeItemStack(RUBY, 1), charge)).isFalse();
        }

        @Test
        @DisplayName("an unprovided capability is absent")
        void missingCapabilityIsEmpty() {
            FakeModContext ctx = FakeModContext.builder().build();

            assertThat(ctx.has(Capabilities.TAGS)).isFalse();
            assertThat(ctx.capability(Capabilities.TAGS)).isEmpty();
        }
    }

    @Test
    @DisplayName("a handle is honest about not being bound")
    void handlesAreUnbound() {
        FakeModContext ctx = FakeModContext.builder().build();
        ItemHandle ruby = ctx.registries().item(RUBY, ItemSpec.simple());

        assertThat(ruby.id()).isEqualTo(RUBY);
        assertThat(ruby.isBound()).isFalse();
        assertThat(ruby.translationKey()).isEqualTo("item.examplemod.ruby");
        assertThat(ruby.stack(3).count()).isEqualTo(3);
        assertThatThrownBy(() -> ruby.unwrap(String.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("no Minecraft object");
    }

    @Test
    @DisplayName("the flush the runtime performs is observable")
    void recordsTheFlush() {
        FakeModContext ctx = FakeModContext.builder().build();
        assertThat(ctx.recorded().flushCount()).isZero();

        ctx.flush();

        assertThat(ctx.recorded().flushCount()).isEqualTo(1);
    }
}
