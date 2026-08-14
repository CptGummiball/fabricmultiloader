package dev.fabricmultiloader.runtime.log;

import dev.fabricmultiloader.api.ModLogger;
import java.lang.reflect.Method;

/**
 * Logging that binds to SLF4J when it is there and to standard error when it is not.
 *
 * <p>Minecraft has shipped SLF4J since 1.17, but not on 1.16.5 — and a hard dependency would turn
 * the oldest supported environment into a {@code NoClassDefFoundError} inside the bootstrap, which
 * is exactly the class of failure this framework exists to replace with a readable message. The
 * binding is resolved once, reflectively, and costs about a third of a millisecond.
 *
 * <p>Reflection is confined to this class. Everywhere else logs through {@link ModLogger}.
 */
public final class Log implements ModLogger {

    private static final Binding BINDING = Binding.detect();

    private final String name;
    private final Object delegate;

    private Log(String name) {
        this.name = name;
        this.delegate = BINDING.createLogger(name);
    }

    /** A logger with the given name. */
    public static ModLogger named(String name) {
        return new Log(name == null || name.isEmpty() ? "fabricmultiloader" : name);
    }

    /** The framework's own logger. */
    public static ModLogger framework() {
        return named("fabricmultiloader");
    }

    /** Whether SLF4J was found. Exposed for diagnostics, not for behaviour. */
    public static boolean isUsingSlf4j() {
        return BINDING.available;
    }

    @Override
    public void trace(String message, Object... arguments) {
        emit(Level.TRACE, message, arguments);
    }

    @Override
    public void debug(String message, Object... arguments) {
        emit(Level.DEBUG, message, arguments);
    }

    @Override
    public void info(String message, Object... arguments) {
        emit(Level.INFO, message, arguments);
    }

    @Override
    public void warn(String message, Object... arguments) {
        emit(Level.WARN, message, arguments);
    }

    @Override
    public void error(String message, Object... arguments) {
        emit(Level.ERROR, message, arguments);
    }

    @Override
    public void error(String message, Throwable cause, Object... arguments) {
        emit(Level.ERROR, MessageFormatter.format(message, arguments), cause);
    }

    @Override
    public boolean isDebugEnabled() {
        return Boolean.getBoolean("fabricmultiloader.debug");
    }

    @Override
    public ModLogger sub(String suffix) {
        return new Log(name + "/" + suffix);
    }

    private void emit(Level level, String message, Object... arguments) {
        if (level == Level.TRACE && !isDebugEnabled()) {
            return;
        }
        if (level == Level.DEBUG && !isDebugEnabled()) {
            return;
        }
        Throwable trailing = MessageFormatter.extractTrailingThrowable(message, arguments);
        String text = MessageFormatter.format(
                message, MessageFormatter.withoutTrailingThrowable(message, arguments));
        emit(level, text, trailing);
    }

    private void emit(Level level, String text, Throwable cause) {
        if (delegate != null && BINDING.emit(delegate, level, text, cause)) {
            return;
        }
        java.io.PrintStream stream = level == Level.ERROR || level == Level.WARN
                ? System.err : System.out;
        stream.println("[" + name + "/" + level + "] " + text);
        if (cause != null) {
            cause.printStackTrace(stream);
        }
    }

    private enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    /**
     * The reflective SLF4J binding, resolved once at class initialisation.
     *
     * <p>Methods are looked up on the {@code org.slf4j.Logger} <em>interface</em> rather than on the
     * concrete logger's class. Backend implementations are frequently package-private, and invoking
     * a public method through a non-public declaring class raises {@code IllegalAccessException} —
     * a trap that only shows up with a particular logging backend installed.
     */
    private static final class Binding {

        private final boolean available;
        private final Method getLogger;
        private final Method[] byLevel;
        private final Method[] byLevelWithCause;

        private Binding(boolean available, Method getLogger, Method[] byLevel,
                Method[] byLevelWithCause) {
            this.available = available;
            this.getLogger = getLogger;
            this.byLevel = byLevel;
            this.byLevelWithCause = byLevelWithCause;
        }

        static Binding detect() {
            try {
                Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
                Class<?> logger = Class.forName("org.slf4j.Logger");
                Level[] levels = Level.values();
                Method[] plain = new Method[levels.length];
                Method[] withCause = new Method[levels.length];
                for (int i = 0; i < levels.length; i++) {
                    String name = levels[i].name().toLowerCase(java.util.Locale.ROOT);
                    plain[i] = logger.getMethod(name, String.class);
                    withCause[i] = logger.getMethod(name, String.class, Throwable.class);
                }
                return new Binding(true, factory.getMethod("getLogger", String.class),
                        plain, withCause);
            } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
                // Expected on Minecraft 1.16.5 and in plain unit tests. Deliberately silent: the
                // fallback is fully functional, and a warning here would be emitted before any
                // logging configuration exists to route it anywhere useful.
                return new Binding(false, null, null, null);
            }
        }

        Object createLogger(String name) {
            if (!available) {
                return null;
            }
            try {
                return getLogger.invoke(null, name);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        boolean emit(Object logger, Level level, String text, Throwable cause) {
            if (!available) {
                return false;
            }
            try {
                if (cause == null) {
                    byLevel[level.ordinal()].invoke(logger, text);
                } else {
                    byLevelWithCause[level.ordinal()].invoke(logger, text, cause);
                }
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
