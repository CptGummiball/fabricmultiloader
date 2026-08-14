package dev.fabricmultiloader.api.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A minimal, version-neutral text model.
 *
 * <p>Minecraft's {@code Text} hierarchy has been rebuilt more than once — {@code TextComponent} to
 * {@code Text}, mutable to immutable, {@code Style} reworked, serialisation moved to codecs — so
 * common code cannot name it. This model covers what mod code realistically produces: literals,
 * translation keys with arguments, colour, the five vanilla styles, click and hover actions, and
 * nesting. Anything beyond that belongs in the payload, where the real type is available.
 *
 * <p>Immutable: every fluent method returns a new instance, so a {@code Text} constant can be
 * shared without a defensive copy.
 *
 * <pre>
 * Text.translatable("examplemod.charge", 42)
 *     .color(TextColor.GOLD)
 *     .bold()
 *     .append(Text.literal(" (click)").clickRunCommand("/ruby info"));
 * </pre>
 */
public final class Text {

    /** What kind of content a text node carries. */
    public enum Kind {
        /** Literal text. */
        LITERAL,
        /** A translation key resolved against the active language. */
        TRANSLATABLE,
        /** No content of its own; a container for children. */
        EMPTY
    }

    /** The vanilla text styles. */
    public enum Style {
        /** Bold. */
        BOLD,
        /** Italic. */
        ITALIC,
        /** Underlined. */
        UNDERLINED,
        /** Struck through. */
        STRIKETHROUGH,
        /** Obfuscated. */
        OBFUSCATED
    }

    /** What a click does. */
    public enum ClickAction {
        /** Runs a command as the clicking player. */
        RUN_COMMAND,
        /** Puts a command into the player's chat box without sending it. */
        SUGGEST_COMMAND,
        /** Opens a URL, after the client's confirmation prompt. */
        OPEN_URL,
        /** Copies text to the clipboard. */
        COPY_TO_CLIPBOARD
    }

    /** A click action with its argument. */
    public static final class ClickEvent {

        private final ClickAction action;
        private final String value;

        ClickEvent(ClickAction action, String value) {
            this.action = action;
            this.value = value;
        }

        /** What clicking does. */
        public ClickAction action() {
            return action;
        }

        /** The command, URL or text. */
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return action + "(" + value + ")";
        }
    }

    private static final Text EMPTY_TEXT = new Text(
            Kind.EMPTY, "", Collections.emptyList(), Collections.<Text>emptyList(),
            null, EnumSet.noneOf(Style.class), null, null);

    private final Kind kind;
    private final String content;
    private final List<Object> arguments;
    private final List<Text> children;
    private final TextColor color;
    private final Set<Style> styles;
    private final ClickEvent click;
    private final Text hover;

    private Text(Kind kind, String content, List<Object> arguments, List<Text> children,
            TextColor color, Set<Style> styles, ClickEvent click, Text hover) {
        this.kind = kind;
        this.content = content;
        this.arguments = arguments;
        this.children = children;
        this.color = color;
        this.styles = styles;
        this.click = click;
        this.hover = hover;
    }

    // ------------------------------------------------------------------ factories

    /** Literal text. */
    public static Text literal(String value) {
        return new Text(Kind.LITERAL, value == null ? "" : value, Collections.emptyList(),
                Collections.<Text>emptyList(), null, EnumSet.noneOf(Style.class), null, null);
    }

    /**
     * A translation key, resolved against the player's language.
     *
     * @param key the key, e.g. {@code item.examplemod.ruby.tooltip}
     * @param arguments substitution arguments; nested {@link Text} values are allowed
     */
    public static Text translatable(String key, Object... arguments) {
        return new Text(Kind.TRANSLATABLE, key == null ? "" : key,
                Collections.unmodifiableList(new ArrayList<Object>(Arrays.asList(arguments))),
                Collections.<Text>emptyList(), null, EnumSet.noneOf(Style.class), null, null);
    }

    /** An empty node, useful as a container for children. */
    public static Text empty() {
        return EMPTY_TEXT;
    }

    // ------------------------------------------------------------------ fluent modifiers

    /** A copy with the given colour. */
    public Text color(TextColor value) {
        return copy(children, value, styles, click, hover);
    }

    /** A copy with an additional style. */
    public Text style(Style value) {
        Set<Style> combined = toEnumSet(styles);
        combined.add(value);
        return copy(children, color, combined, click, hover);
    }

    /**
     * {@code EnumSet.copyOf(Collection)} throws on an empty collection that is not already an
     * {@code EnumSet} — and the sets held here are unmodifiable wrappers, so the common case of
     * styling an unstyled text hit exactly that.
     */
    private static Set<Style> toEnumSet(Set<Style> source) {
        Set<Style> target = EnumSet.noneOf(Style.class);
        target.addAll(source);
        return target;
    }

    /** A copy in bold. */
    public Text bold() {
        return style(Style.BOLD);
    }

    /** A copy in italics. */
    public Text italic() {
        return style(Style.ITALIC);
    }

    /** A copy underlined. */
    public Text underlined() {
        return style(Style.UNDERLINED);
    }

    /** A copy struck through. */
    public Text strikethrough() {
        return style(Style.STRIKETHROUGH);
    }

    /** A copy obfuscated. */
    public Text obfuscated() {
        return style(Style.OBFUSCATED);
    }

    /** A copy with an appended child. */
    public Text append(Text child) {
        List<Text> combined = new ArrayList<Text>(children);
        combined.add(child);
        return copy(Collections.unmodifiableList(combined), color, styles, click, hover);
    }

    /** A copy with an appended literal child. */
    public Text append(String literal) {
        return append(literal(literal));
    }

    /** A copy that runs a command when clicked. */
    public Text clickRunCommand(String command) {
        return copy(children, color, styles, new ClickEvent(ClickAction.RUN_COMMAND, command), hover);
    }

    /** A copy that pre-fills the chat box when clicked. */
    public Text clickSuggestCommand(String command) {
        return copy(children, color, styles,
                new ClickEvent(ClickAction.SUGGEST_COMMAND, command), hover);
    }

    /** A copy that opens a URL when clicked. */
    public Text clickOpenUrl(String url) {
        return copy(children, color, styles, new ClickEvent(ClickAction.OPEN_URL, url), hover);
    }

    /** A copy that copies text to the clipboard when clicked. */
    public Text clickCopyToClipboard(String value) {
        return copy(children, color, styles,
                new ClickEvent(ClickAction.COPY_TO_CLIPBOARD, value), hover);
    }

    /** A copy showing a tooltip on hover. */
    public Text hover(Text tooltip) {
        return copy(children, color, styles, click, tooltip);
    }

    private Text copy(List<Text> newChildren, TextColor newColor, Set<Style> newStyles,
            ClickEvent newClick, Text newHover) {
        return new Text(kind, content, arguments, newChildren, newColor,
                Collections.unmodifiableSet(toEnumSet(newStyles)), newClick, newHover);
    }

    // ------------------------------------------------------------------ accessors for adapters

    /** What kind of content this node carries. */
    public Kind kind() {
        return kind;
    }

    /** The literal text or the translation key, depending on {@link #kind()}. */
    public String content() {
        return content;
    }

    /** Translation arguments; empty unless {@link #kind()} is {@link Kind#TRANSLATABLE}. */
    public List<Object> arguments() {
        return arguments;
    }

    /** Appended children, in order. */
    public List<Text> children() {
        return children;
    }

    /** The colour, or {@code null} to inherit. */
    public TextColor color() {
        return color;
    }

    /** The applied styles. */
    public Set<Style> styles() {
        return styles;
    }

    /** The click action, or {@code null}. */
    public ClickEvent click() {
        return click;
    }

    /** The hover tooltip, or {@code null}. */
    public Text hoverText() {
        return hover;
    }

    /**
     * A plain-text rendering with no formatting, for logs and for the fallback path when no
     * adapter is available. Translation keys render as the key itself.
     */
    public String toPlainString() {
        StringBuilder out = new StringBuilder(content);
        for (Text child : children) {
            out.append(child.toPlainString());
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Text)) {
            return false;
        }
        Text that = (Text) other;
        return kind == that.kind
                && content.equals(that.content)
                && arguments.equals(that.arguments)
                && children.equals(that.children)
                && styles.equals(that.styles)
                && (color == null ? that.color == null : color.equals(that.color));
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + content.hashCode();
        result = 31 * result + children.hashCode();
        result = 31 * result + styles.hashCode();
        return 31 * result + (color == null ? 0 : color.hashCode());
    }
}
