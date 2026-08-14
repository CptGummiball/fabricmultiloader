package dev.fabricmultiloader.runtime.log;

/**
 * Formats SLF4J-style {@code {}} placeholders.
 *
 * <p>Implemented here rather than delegated so that a log line reads identically whether SLF4J is
 * present or not. Minecraft ships SLF4J from 1.17 onwards but not on 1.16.5, and a mod author
 * comparing logs across versions should not have to wonder whether the formatting differs too.
 */
public final class MessageFormatter {

    private static final String PLACEHOLDER = "{}";

    /**
     * Substitutes arguments into a pattern.
     *
     * <p>Surplus arguments are appended in brackets rather than dropped, and surplus placeholders
     * are left as they are. Both are mistakes worth seeing in the log rather than losing quietly —
     * a swallowed argument is often the very value somebody was trying to debug.
     *
     * @param pattern the message, with {@code {}} placeholders
     * @param arguments the values
     * @return the formatted message
     */
    public static String format(String pattern, Object... arguments) {
        if (pattern == null) {
            return "";
        }
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        StringBuilder out = new StringBuilder(pattern.length() + arguments.length * 16);
        int cursor = 0;
        int used = 0;
        while (used < arguments.length) {
            int placeholder = pattern.indexOf(PLACEHOLDER, cursor);
            if (placeholder < 0) {
                break;
            }
            out.append(pattern, cursor, placeholder).append(stringify(arguments[used]));
            cursor = placeholder + PLACEHOLDER.length();
            used++;
        }
        out.append(pattern, cursor, pattern.length());
        for (int i = used; i < arguments.length; i++) {
            out.append(" [").append(stringify(arguments[i])).append(']');
        }
        return out.toString();
    }

    /**
     * The last argument if it is a {@link Throwable} and the pattern has no placeholder for it —
     * the SLF4J convention, so {@code log.error("failed for {}", id, exception)} works as expected.
     */
    public static Throwable extractTrailingThrowable(String pattern, Object... arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }
        Object last = arguments[arguments.length - 1];
        if (!(last instanceof Throwable)) {
            return null;
        }
        return countPlaceholders(pattern) < arguments.length ? (Throwable) last : null;
    }

    /** Arguments minus a trailing throwable that {@link #extractTrailingThrowable} claimed. */
    public static Object[] withoutTrailingThrowable(String pattern, Object... arguments) {
        if (extractTrailingThrowable(pattern, arguments) == null) {
            return arguments == null ? new Object[0] : arguments;
        }
        Object[] trimmed = new Object[arguments.length - 1];
        System.arraycopy(arguments, 0, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private static int countPlaceholders(String pattern) {
        if (pattern == null) {
            return 0;
        }
        int count = 0;
        int cursor = 0;
        while (true) {
            int placeholder = pattern.indexOf(PLACEHOLDER, cursor);
            if (placeholder < 0) {
                return count;
            }
            count++;
            cursor = placeholder + PLACEHOLDER.length();
        }
    }

    /**
     * Renders an argument, surviving a {@code toString} that throws.
     *
     * <p>A logger that can itself crash is worse than useless during a failed bootstrap, which is
     * precisely when it is needed most.
     */
    private static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            if (value instanceof Object[]) {
                return java.util.Arrays.deepToString((Object[]) value);
            }
            return String.valueOf(value);
        } catch (RuntimeException e) {
            return "<" + value.getClass().getName() + ".toString() threw " + e.getClass().getName() + ">";
        }
    }

    private MessageFormatter() {
        throw new AssertionError("no instances");
    }
}
