package dev.fabricmultiloader.api;

/**
 * The <em>physical</em> side a mod is running on: a client distribution or a dedicated server.
 *
 * <p>This is not the logical side. A single-player game runs an integrated server inside a
 * {@link #CLIENT} distribution; {@code side()} still reports {@code CLIENT} there, because the
 * distinction that matters for class loading is which classes exist at all.
 *
 * <p>Mirrors Fabric's {@code net.fabricmc.api.EnvType} without depending on it, so that the
 * common API stays free of loader types.
 */
public enum Side {

    /** A client distribution — {@code net.minecraft.client} classes exist. */
    CLIENT,

    /** A dedicated server — client classes are absent and must never be referenced. */
    SERVER;

    /** Returns {@code true} for {@link #CLIENT}. */
    public boolean isClient() {
        return this == CLIENT;
    }

    /** Returns {@code true} for {@link #SERVER}. */
    public boolean isServer() {
        return this == SERVER;
    }

    /**
     * Parses the value of a payload's {@code environment} constraint.
     *
     * @param value {@code "client"}, {@code "server"} or {@code "*"}, case-insensitive
     * @return the matching side, or {@code null} for {@code "*"} meaning "both"
     * @throws IllegalArgumentException if the value is none of the three
     */
    public static Side parseConstraint(String value) {
        if (value == null) {
            throw new IllegalArgumentException("environment constraint must not be null");
        }
        String normalised = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("*".equals(normalised)) {
            return null;
        }
        if ("client".equals(normalised)) {
            return CLIENT;
        }
        if ("server".equals(normalised)) {
            return SERVER;
        }
        throw new IllegalArgumentException(
                "invalid environment constraint '" + value + "', expected \"client\", \"server\" or \"*\"");
    }
}
