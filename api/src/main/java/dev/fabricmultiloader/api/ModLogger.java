package dev.fabricmultiloader.api;

/**
 * Logging that works on every supported Minecraft version.
 *
 * <p>SLF4J is present from Minecraft 1.17 onwards but not on 1.16.5, so a hard compile dependency
 * on it would turn the oldest supported environment into a {@code NoClassDefFoundError} inside the
 * bootstrap — precisely the failure this framework exists to prevent. The runtime binds to SLF4J
 * reflectively when available and falls back to standard error otherwise; mod code never has to
 * know which happened.
 *
 * <p>Placeholders are SLF4J-style {@code {}}, implemented in house so the behaviour is identical
 * with and without SLF4J on the classpath.
 */
@ImplementedByFramework
public interface ModLogger {

    /** Logs at trace level. */
    void trace(String message, Object... arguments);

    /** Logs at debug level. */
    void debug(String message, Object... arguments);

    /** Logs at info level. */
    void info(String message, Object... arguments);

    /** Logs at warning level. */
    void warn(String message, Object... arguments);

    /** Logs at error level. */
    void error(String message, Object... arguments);

    /** Logs at error level with a stack trace. */
    void error(String message, Throwable cause, Object... arguments);

    /** Whether debug logging is enabled — worth checking before building an expensive message. */
    boolean isDebugEnabled();

    /**
     * A child logger with a suffixed name, e.g. {@code examplemod/net}.
     *
     * @param name the suffix
     * @return the child logger
     */
    ModLogger sub(String name);
}
