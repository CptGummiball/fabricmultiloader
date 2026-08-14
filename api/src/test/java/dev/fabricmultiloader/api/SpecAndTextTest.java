package dev.fabricmultiloader.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fabricmultiloader.api.command.Arg;
import dev.fabricmultiloader.api.command.CommandSpec;
import dev.fabricmultiloader.api.net.ChannelSpec;
import dev.fabricmultiloader.api.net.PayloadCodec;
import dev.fabricmultiloader.api.registry.BlockSpec;
import dev.fabricmultiloader.api.registry.ItemSpec;
import dev.fabricmultiloader.api.registry.Rarity;
import dev.fabricmultiloader.api.registry.UseResult;
import dev.fabricmultiloader.api.text.Text;
import dev.fabricmultiloader.api.text.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpecAndTextTest {

    @Nested
    @DisplayName("item and block specifications")
    class Specs {

        @Test
        void itemDefaultsMatchVanilla() {
            ItemSpec spec = ItemSpec.simple();
            assertThat(spec.maxCount()).isEqualTo(64);
            assertThat(spec.rarity()).isEqualTo(Rarity.COMMON);
            assertThat(spec.fireproof()).isFalse();
            assertThat(spec.isDamageable()).isFalse();
            assertThat(spec.behavior()).isNull();
        }

        @Test
        @DisplayName("a damageable item is forced to stack size 1, which Minecraft requires")
        void damageableItemsCannotStack() {
            ItemSpec spec = ItemSpec.builder().maxCount(64).maxDamage(250).build();
            assertThat(spec.maxCount()).isEqualTo(1);
            assertThat(spec.isDamageable()).isTrue();
        }

        @Test
        @DisplayName("out-of-range values are refused here rather than misbehaving in game")
        void validatesBounds() {
            assertThatThrownBy(() -> ItemSpec.builder().maxCount(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 99");
            assertThatThrownBy(() -> ItemSpec.builder().maxCount(100))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ItemSpec.builder().maxDamage(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> BlockSpec.builder().luminance(16))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 15");
        }

        @Test
        void tooltipKeysKeepTheirOrderAndSkipBlanks() {
            ItemSpec spec = ItemSpec.builder()
                    .tooltip("a").tooltip(null).tooltip("").tooltip("b").build();
            assertThat(spec.tooltipKeys()).containsExactly("a", "b");
        }

        @Test
        void blockStrengthSetsBothHardnessAndResistance() {
            BlockSpec spec = BlockSpec.builder().strength(5.0f).requiresTool().build();
            assertThat(spec.hardness()).isEqualTo(5.0f);
            assertThat(spec.resistance()).isEqualTo(5.0f);
            assertThat(spec.requiresTool()).isTrue();
        }

        @Test
        void useResultsDistinguishHandledFromPass() {
            assertThat(UseResult.SUCCESS.isHandled()).isTrue();
            assertThat(UseResult.CONSUME.isHandled()).isTrue();
            assertThat(UseResult.FAIL.isHandled()).isTrue();
            assertThat(UseResult.PASS.isHandled()).isFalse();
        }
    }

    @Nested
    @DisplayName("text model")
    class TextModel {

        @Test
        void literalsAndTranslationsCarryTheirContent() {
            assertThat(Text.literal("hello").kind()).isEqualTo(Text.Kind.LITERAL);
            assertThat(Text.literal("hello").content()).isEqualTo("hello");

            Text translated = Text.translatable("examplemod.charge", 42);
            assertThat(translated.kind()).isEqualTo(Text.Kind.TRANSLATABLE);
            assertThat(translated.content()).isEqualTo("examplemod.charge");
            assertThat(translated.arguments()).containsExactly(Integer.valueOf(42));
        }

        @Test
        @DisplayName("every modifier returns a copy, so a shared constant cannot be mutated")
        void isImmutable() {
            Text base = Text.literal("hello");
            Text styled = base.color(TextColor.GOLD).bold();

            assertThat(base.color()).isNull();
            assertThat(base.styles()).isEmpty();
            assertThat(styled.color()).isEqualTo(TextColor.GOLD);
            assertThat(styled.styles()).containsExactly(Text.Style.BOLD);
        }

        @Test
        void stylesAccumulate() {
            Text text = Text.literal("x").bold().italic().underlined();
            assertThat(text.styles()).containsExactlyInAnyOrder(
                    Text.Style.BOLD, Text.Style.ITALIC, Text.Style.UNDERLINED);
        }

        @Test
        void childrenNestAndFlattenForLogging() {
            Text text = Text.literal("a").append(Text.literal("b")).append("c");
            assertThat(text.children()).hasSize(2);
            assertThat(text.toPlainString()).isEqualTo("abc");
        }

        @Test
        void clickAndHoverAreRecorded() {
            Text text = Text.literal("run")
                    .clickRunCommand("/ruby info")
                    .hover(Text.literal("tooltip"));

            assertThat(text.click().action()).isEqualTo(Text.ClickAction.RUN_COMMAND);
            assertThat(text.click().value()).isEqualTo("/ruby info");
            assertThat(text.hoverText().content()).isEqualTo("tooltip");
        }

        @Test
        @DisplayName("named colours stay distinguishable from equivalent RGB values")
        void namedColoursAreNotNormalisedAway() {
            assertThat(TextColor.GOLD.isNamed()).isTrue();
            assertThat(TextColor.GOLD.name()).isEqualTo("gold");
            assertThat(TextColor.of(0xFFAA00).isNamed()).isFalse();
            assertThat(TextColor.GOLD).isNotEqualTo(TextColor.of(0xFFAA00));
            assertThat(TextColor.GOLD.rgb()).isEqualTo(TextColor.of(0xFFAA00).rgb());
        }
    }

    @Nested
    @DisplayName("channel and command specifications")
    class OtherSpecs {

        private final PayloadCodec<String> codec = new PayloadCodec<String>() {
            @Override
            public void write(dev.fabricmultiloader.api.net.ByteSink out, String value) {
                out.writeString(value);
            }

            @Override
            public String read(dev.fabricmultiloader.api.net.ByteSource in) {
                return in.readString();
            }
        };

        @Test
        void channelDirectionsGateTraffic() {
            ChannelSpec<String> c2s = ChannelSpec.c2s(Id.of("examplemod", "a"), codec);
            assertThat(c2s.direction().allowsC2S()).isTrue();
            assertThat(c2s.direction().allowsS2C()).isFalse();

            ChannelSpec<String> both = ChannelSpec.both(Id.of("examplemod", "b"), codec);
            assertThat(both.direction().allowsC2S()).isTrue();
            assertThat(both.direction().allowsS2C()).isTrue();
        }

        @Test
        void channelRequiresAnIdAndACodec() {
            assertThatThrownBy(() -> ChannelSpec.c2s(null, codec))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ChannelSpec.c2s(Id.of("a", "b"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void commandTreesKeepArgumentOrder() {
            CommandSpec spec = CommandSpec.named("ruby")
                    .sub(CommandSpec.named("charge")
                            .arg("amount", Arg.integer(1, 100))
                            .arg("target", Arg.player())
                            .permissionLevel(2)
                            .executes(invocation -> Integer.valueOf(1))
                            .build())
                    .build();

            assertThat(spec.literal()).isEqualTo("ruby");
            assertThat(spec.children()).hasSize(1);

            CommandSpec charge = spec.children().get(0);
            assertThat(charge.arguments().keySet()).containsExactly("amount", "target");
            assertThat(charge.permissionLevel()).isEqualTo(2);
            assertThat(charge.arguments().get("amount").type()).isEqualTo(Integer.class);
        }

        @Test
        @DisplayName("a command that does nothing and groups nothing is a mistake, not a no-op")
        void commandNeedsAnActionOrChildren() {
            assertThatThrownBy(() -> CommandSpec.named("empty").build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("neither an action nor sub-commands");
        }

        @Test
        void commandValidatesNamesAndLevels() {
            assertThatThrownBy(() -> CommandSpec.named(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CommandSpec.named("x").permissionLevel(5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CommandSpec.named("x")
                    .arg("a", Arg.word()).arg("a", Arg.word()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duplicate argument");
        }

        @Test
        void capabilityIdentityIsTheIdAlone() {
            Capability<String> first = Capability.of("x", String.class);
            Capability<String> second = Capability.of("x", String.class);
            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
            assertThat(Capabilities.all()).contains(Capabilities.COMPONENTS, Capabilities.TAGS);
            assertThat(Capabilities.COMPONENTS.id()).isEqualTo("components");
        }
    }
}
