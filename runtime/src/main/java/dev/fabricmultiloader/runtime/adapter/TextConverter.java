package dev.fabricmultiloader.runtime.adapter;

import dev.fabricmultiloader.api.text.Text;
import dev.fabricmultiloader.api.text.TextColor;
import java.util.List;
import java.util.Set;

/**
 * Folds the version-neutral text model into whatever type a payload builds, and renders it for
 * places that only take a string.
 *
 * <p>The tree walk, the style inheritance and the argument handling are identical on every
 * Minecraft version; only the four constructors at the leaves differ. Splitting it that way turns
 * each payload's text adapter into five short methods and keeps the part that is easy to get subtly
 * wrong — nesting order, an empty root with children, a hover text that is itself styled — in one
 * place with tests.
 */
public final class TextConverter {

    /**
     * What a payload supplies: how to build its own text type.
     *
     * @param <T> the payload's text type, {@code net.minecraft.text.Text} in practice
     */
    public interface Visitor<T> {

        /** Builds literal text. */
        T literal(String text);

        /**
         * Builds translatable text.
         *
         * @param key the translation key
         * @param arguments substitution arguments; any {@link Text} among them has already been
         *     converted, so the list contains the payload's own type for those entries
         */
        T translatable(String key, List<Object> arguments);

        /** Builds an empty node. */
        T empty();

        /**
         * Applies formatting to a node.
         *
         * @param node the node built by one of the other methods
         * @param color the colour, or {@code null} to inherit
         * @param styles the styles to apply, possibly empty
         * @param click the click action, or {@code null}
         * @param hover the already-converted hover text, or {@code null}
         * @return the styled node
         */
        T styled(T node, TextColor color, Set<Text.Style> styles, Text.ClickEvent click, T hover);

        /**
         * Appends a child.
         *
         * @param parent the node to append to
         * @param child the child
         * @return the combined node
         */
        T append(T parent, T child);
    }

    /**
     * Converts a text tree.
     *
     * @param text the source, may be {@code null}
     * @param visitor the payload's builder
     * @param <T> the payload's text type
     * @return the converted text, or {@code null} if {@code text} was {@code null}
     */
    public static <T> T convert(Text text, Visitor<T> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor must not be null");
        }
        if (text == null) {
            return null;
        }

        T node;
        if (text.kind() == Text.Kind.LITERAL) {
            node = visitor.literal(text.content());
        } else if (text.kind() == Text.Kind.TRANSLATABLE) {
            node = visitor.translatable(text.content(), convertArguments(text, visitor));
        } else {
            node = visitor.empty();
        }

        // Styling happens before children are appended: in Minecraft's model a child inherits the
        // parent's style, and appending first would apply the parent's formatting to text that
        // already carries its own.
        T hover = convert(text.hoverText(), visitor);
        if (text.color() != null || !text.styles().isEmpty()
                || text.click() != null || hover != null) {
            node = visitor.styled(node, text.color(), text.styles(), text.click(), hover);
        }

        for (Text child : text.children()) {
            node = visitor.append(node, convert(child, visitor));
        }
        return node;
    }

    private static <T> List<Object> convertArguments(Text text, Visitor<T> visitor) {
        List<Object> arguments = text.arguments();
        java.util.List<Object> converted = new java.util.ArrayList<Object>(arguments.size());
        for (Object argument : arguments) {
            converted.add(argument instanceof Text
                    ? convert((Text) argument, visitor)
                    : argument);
        }
        return java.util.Collections.unmodifiableList(converted);
    }

    /**
     * Renders text with the legacy section-sign codes.
     *
     * <p>For server consoles and log files, which take a string and understand {@code §} but not a
     * component tree. Translation keys render as the key, since no language file is loaded at the
     * point most of this output is produced.
     *
     * @param text the source, may be {@code null}
     * @return the rendered string, never {@code null}
     */
    public static String toLegacyString(Text text) {
        StringBuilder out = new StringBuilder();
        render(text, out);
        return out.toString();
    }

    private static void render(Text text, StringBuilder out) {
        if (text == null) {
            return;
        }
        boolean formatted = false;
        if (text.color() != null) {
            out.append('§').append(legacyCode(text.color()));
            formatted = true;
        }
        for (Text.Style style : text.styles()) {
            out.append('§').append(styleCode(style));
            formatted = true;
        }

        out.append(text.content());
        for (Text child : text.children()) {
            render(child, out);
        }
        if (formatted) {
            out.append('§').append('r');
        }
    }

    /**
     * The legacy code for a colour.
     *
     * <p>An arbitrary RGB colour has no legacy code, so the nearest vanilla one is used rather than
     * dropping the colour entirely — a console line in approximately the right colour is more use
     * than an uncoloured one, and this path only ever feeds human-readable output.
     */
    static char legacyCode(TextColor color) {
        TextColor nearest = color.isNamed() ? color : nearestNamed(color.rgb());
        String name = nearest.name();
        for (int i = 0; i < NAMED_ORDER.length; i++) {
            if (NAMED_ORDER[i].name().equals(name)) {
                return "0123456789abcdef".charAt(i);
            }
        }
        return 'f';
    }

    private static TextColor nearestNamed(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        TextColor best = TextColor.WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (TextColor candidate : NAMED_ORDER) {
            int cr = (candidate.rgb() >> 16) & 0xFF;
            int cg = (candidate.rgb() >> 8) & 0xFF;
            int cb = candidate.rgb() & 0xFF;
            long distance = square(red - cr) + square(green - cg) + square(blue - cb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static long square(int value) {
        return (long) value * value;
    }

    private static char styleCode(Text.Style style) {
        switch (style) {
            case OBFUSCATED:
                return 'k';
            case BOLD:
                return 'l';
            case STRIKETHROUGH:
                return 'm';
            case UNDERLINED:
                return 'n';
            case ITALIC:
                return 'o';
            default:
                return 'r';
        }
    }

    /** The sixteen vanilla colours in legacy code order, so the index is the code. */
    private static final TextColor[] NAMED_ORDER = {
        TextColor.BLACK, TextColor.DARK_BLUE, TextColor.DARK_GREEN, TextColor.DARK_AQUA,
        TextColor.DARK_RED, TextColor.DARK_PURPLE, TextColor.GOLD, TextColor.GRAY,
        TextColor.DARK_GRAY, TextColor.BLUE, TextColor.GREEN, TextColor.AQUA,
        TextColor.RED, TextColor.LIGHT_PURPLE, TextColor.YELLOW, TextColor.WHITE,
    };

    private TextConverter() {
        throw new AssertionError("no instances");
    }
}
