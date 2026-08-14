package dev.fabricmultiloader.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fabricmultiloader.api.text.Text;
import dev.fabricmultiloader.api.text.TextColor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextConverterTest {

    /**
     * A visitor that records what it was asked to build, standing in for a payload's real one.
     *
     * <p>Recording the calls rather than building a lookalike type is the point: what the runtime
     * owes a payload is the right sequence of calls, and that is exactly what would otherwise be
     * reimplemented — differently — in each version module.
     */
    private static final class RecordingVisitor implements TextConverter.Visitor<String> {

        @Override
        public String literal(String text) {
            return "lit(" + text + ")";
        }

        @Override
        public String translatable(String key, List<Object> arguments) {
            return "tr(" + key + arguments + ")";
        }

        @Override
        public String empty() {
            return "empty";
        }

        @Override
        public String styled(String node, TextColor color, Set<Text.Style> styles,
                Text.ClickEvent click, String hover) {
            StringBuilder out = new StringBuilder("styled[").append(node);
            if (color != null) {
                out.append(",color=").append(color.name());
            }
            if (!styles.isEmpty()) {
                out.append(",styles=").append(new java.util.TreeSet<Text.Style>(styles));
            }
            if (click != null) {
                out.append(",click=").append(click.action());
            }
            if (hover != null) {
                out.append(",hover=").append(hover);
            }
            return out.append(']').toString();
        }

        @Override
        public String append(String parent, String child) {
            return parent + "+" + child;
        }
    }

    private static String convert(Text text) {
        return TextConverter.convert(text, new RecordingVisitor());
    }

    @Test
    @DisplayName("a plain literal needs no styling call")
    void convertsAPlainLiteral() {
        assertThat(convert(Text.literal("hello"))).isEqualTo("lit(hello)");
    }

    @Test
    @DisplayName("a translation key carries its arguments through")
    void convertsTranslatable() {
        assertThat(convert(Text.translatable("examplemod.charge", 42)))
                .isEqualTo("tr(examplemod.charge[42])");
    }

    @Test
    @DisplayName("a nested Text argument is converted before the visitor sees it")
    void convertsNestedArguments() {
        // Otherwise every payload would have to notice that a translation argument might itself be
        // a Text and recurse — which is the kind of thing that gets forgotten in one version.
        assertThat(convert(Text.translatable("examplemod.of", Text.literal("ruby"))))
                .isEqualTo("tr(examplemod.of[lit(ruby)])");
    }

    @Test
    @DisplayName("styling is applied before children are appended")
    void stylesBeforeAppending() {
        Text text = Text.literal("a").color(TextColor.GOLD).bold().append("b");

        // Minecraft's model has children inherit the parent's style. Appending first would apply
        // the parent's formatting to text that already carries its own.
        assertThat(convert(text))
                .isEqualTo("styled[lit(a),color=gold,styles=[BOLD]]+lit(b)");
    }

    @Test
    @DisplayName("click and hover reach the visitor, with the hover already converted")
    void convertsInteractions() {
        Text text = Text.literal("click me")
                .clickRunCommand("/ruby info")
                .hover(Text.literal("runs a command"));

        assertThat(convert(text)).isEqualTo(
                "styled[lit(click me),click=RUN_COMMAND,hover=lit(runs a command)]");
    }

    @Test
    @DisplayName("an empty root with children converts to a container")
    void convertsAnEmptyContainer() {
        assertThat(convert(Text.empty().append("a").append("b")))
                .isEqualTo("empty+lit(a)+lit(b)");
    }

    @Test
    @DisplayName("null converts to null rather than throwing")
    void toleratesNull() {
        assertThat(convert(null)).isNull();
    }

    @Test
    @DisplayName("legacy rendering emits colour, style and reset codes")
    void rendersLegacyCodes() {
        Text text = Text.literal("warning").color(TextColor.GOLD).bold();

        assertThat(TextConverter.toLegacyString(text)).isEqualTo("§6§lwarning§r");
    }

    @Test
    @DisplayName("an arbitrary RGB colour renders as the nearest vanilla one")
    void approximatesRgbColours() {
        // A console line in approximately the right colour beats an uncoloured one, and this path
        // only ever feeds human-readable output.
        assertThat(TextConverter.legacyCode(TextColor.of(0xFF5555))).isEqualTo('c');
        assertThat(TextConverter.legacyCode(TextColor.of(0x000010))).isEqualTo('0');
        assertThat(TextConverter.legacyCode(TextColor.of(0xFFFFFF))).isEqualTo('f');
        // Pure red is nearer vanilla's dark_red (0xAA0000) than its "red" (0xFF5555), which is
        // pink-ish. Matching by distance rather than by name is the whole point.
        assertThat(TextConverter.legacyCode(TextColor.of(0xFF0000))).isEqualTo('4');
    }

    @Test
    @DisplayName("legacy rendering walks children and leaves unformatted text alone")
    void rendersNestedText() {
        Text text = Text.literal("plain ")
                .append(Text.literal("red").color(TextColor.RED))
                .append(" plain");

        assertThat(TextConverter.toLegacyString(text)).isEqualTo("plain §cred§r plain");
    }
}
