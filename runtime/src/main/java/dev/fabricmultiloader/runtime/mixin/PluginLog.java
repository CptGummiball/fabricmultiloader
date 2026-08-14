package dev.fabricmultiloader.runtime.mixin;

/**
 * Logging for the one class that runs before the framework exists.
 *
 * <p>{@link dev.fabricmultiloader.runtime.log.Log} would work here — it is stateless and binds to
 * SLF4J reflectively — but it is allowed to let an {@code Error} propagate, and this code runs
 * inside Mixin's {@code select()} phase, before {@code preLaunch}, before any FabricMultiLoader
 * state exists and before Fabric has an error dialog to show anything in. An exception escaping a
 * log call there is a launch that dies with no diagnostic at all.
 *
 * <p>So this swallows everything, including {@code Error}. That is the wrong default almost
 * everywhere else and the right one here: the plugin's entire failure policy is to fail open, and a
 * logging failure must not be the exception to it.
 */
final class PluginLog {

    private static final String PREFIX = "[fabricmultiloader/mixin] ";

    /** Whether the conditional decisions are traced. */
    static boolean isDebugEnabled() {
        try {
            return Boolean.getBoolean("fabricmultiloader.debug");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Logs a warning. Never throws. */
    static void warn(String message) {
        emit(System.err, message);
    }

    /** Logs a decision, only with debugging enabled. Never throws. */
    static void debug(String message) {
        if (isDebugEnabled()) {
            emit(System.out, message);
        }
    }

    private static void emit(java.io.PrintStream stream, String message) {
        try {
            stream.println(PREFIX + message);
        } catch (Throwable ignored) {
            // Nothing left to report it with, and failing here would abort the launch over a log
            // line.
        }
    }

    private PluginLog() {
        throw new AssertionError("no instances");
    }
}
