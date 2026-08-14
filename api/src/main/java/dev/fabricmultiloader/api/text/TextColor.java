package dev.fabricmultiloader.api.text;

/**
 * A text colour: one of Minecraft's sixteen named colours, or an arbitrary RGB value.
 *
 * <p>Named colours are kept distinct from their RGB equivalents rather than normalised away,
 * because Minecraft treats them differently — a named colour survives into contexts where a custom
 * one is dropped, and a scoreboard team colour must be named. Collapsing them would lose that at
 * the point where it is impossible to recover.
 */
public final class TextColor {

    /** Black. */
    public static final TextColor BLACK = named("black", 0x000000);
    /** Dark blue. */
    public static final TextColor DARK_BLUE = named("dark_blue", 0x0000AA);
    /** Dark green. */
    public static final TextColor DARK_GREEN = named("dark_green", 0x00AA00);
    /** Dark aqua. */
    public static final TextColor DARK_AQUA = named("dark_aqua", 0x00AAAA);
    /** Dark red. */
    public static final TextColor DARK_RED = named("dark_red", 0xAA0000);
    /** Dark purple. */
    public static final TextColor DARK_PURPLE = named("dark_purple", 0xAA00AA);
    /** Gold. */
    public static final TextColor GOLD = named("gold", 0xFFAA00);
    /** Gray. */
    public static final TextColor GRAY = named("gray", 0xAAAAAA);
    /** Dark gray. */
    public static final TextColor DARK_GRAY = named("dark_gray", 0x555555);
    /** Blue. */
    public static final TextColor BLUE = named("blue", 0x5555FF);
    /** Green. */
    public static final TextColor GREEN = named("green", 0x55FF55);
    /** Aqua. */
    public static final TextColor AQUA = named("aqua", 0x55FFFF);
    /** Red. */
    public static final TextColor RED = named("red", 0xFF5555);
    /** Light purple. */
    public static final TextColor LIGHT_PURPLE = named("light_purple", 0xFF55FF);
    /** Yellow. */
    public static final TextColor YELLOW = named("yellow", 0xFFFF55);
    /** White. */
    public static final TextColor WHITE = named("white", 0xFFFFFF);

    private final String name;
    private final int rgb;

    private TextColor(String name, int rgb) {
        this.name = name;
        this.rgb = rgb;
    }

    private static TextColor named(String name, int rgb) {
        return new TextColor(name, rgb);
    }

    /**
     * An arbitrary RGB colour.
     *
     * @param rgb {@code 0xRRGGBB}
     * @return the colour
     */
    public static TextColor of(int rgb) {
        return new TextColor(null, rgb & 0xFFFFFF);
    }

    /** The vanilla colour name, or {@code null} for a custom RGB colour. */
    public String name() {
        return name;
    }

    /** The colour as {@code 0xRRGGBB}. */
    public int rgb() {
        return rgb;
    }

    /** Whether this is one of the sixteen named colours. */
    public boolean isNamed() {
        return name != null;
    }

    @Override
    public String toString() {
        return name != null ? name : String.format("#%06x", rgb);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TextColor)) {
            return false;
        }
        TextColor that = (TextColor) other;
        return rgb == that.rgb && (name == null ? that.name == null : name.equals(that.name));
    }

    @Override
    public int hashCode() {
        return rgb * 31 + (name == null ? 0 : name.hashCode());
    }
}
