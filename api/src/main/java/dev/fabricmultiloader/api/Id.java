package dev.fabricmultiloader.api;

import java.util.Locale;

/**
 * A namespaced identifier, {@code namespace:path} — the neutral stand-in for Minecraft's
 * {@code Identifier}.
 *
 * <p>Common code cannot reference {@code net.minecraft.util.Identifier}: its construction changed
 * from {@code new Identifier(ns, path)} to {@code Identifier.of(ns, path)} between 1.20.1 and 1.21,
 * which is exactly the kind of descriptor change that makes one compilation for all versions
 * impossible. The adapter converts an {@code Id} into whatever the running version expects.
 *
 * <p>Character rules follow Minecraft's own: namespaces allow {@code [a-z0-9_.-]}, paths
 * additionally allow {@code /}. Validating here rather than at registration time means a typo
 * surfaces as a clear message at mod initialisation instead of a Minecraft exception several
 * frames deep.
 */
public final class Id {

    /** The namespace used when none is given. */
    public static final String MINECRAFT_NAMESPACE = "minecraft";

    private final String namespace;
    private final String path;

    private Id(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /** Creates an identifier from its two parts. */
    public static Id of(String namespace, String path) {
        requireValid(namespace, "namespace", false);
        requireValid(path, "path", true);
        return new Id(namespace, path);
    }

    /** Creates an identifier in the {@code minecraft} namespace. */
    public static Id minecraft(String path) {
        return of(MINECRAFT_NAMESPACE, path);
    }

    /**
     * Parses {@code "namespace:path"}; a value without a colon is taken as a
     * {@code minecraft}-namespaced path, matching Minecraft's own behaviour.
     */
    public static Id parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return of(MINECRAFT_NAMESPACE, value);
        }
        if (value.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException(
                    "identifier must contain at most one ':' — got \"" + value + "\"");
        }
        return of(value.substring(0, colon), value.substring(colon + 1));
    }

    /** The namespace, typically the mod id. */
    public String namespace() {
        return namespace;
    }

    /** The path within the namespace. */
    public String path() {
        return path;
    }

    /** A new identifier with the same namespace and a different path. */
    public Id withPath(String newPath) {
        return of(namespace, newPath);
    }

    /** A new identifier with a suffix appended to the path, e.g. {@code ruby} to {@code ruby_block}. */
    public Id suffixed(String suffix) {
        return of(namespace, path + suffix);
    }

    private static void requireValid(String value, String part, boolean allowSlash) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("identifier " + part + " must not be empty");
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "identifier " + part + " must be lower case — got \"" + value + "\"");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-'
                    || (allowSlash && c == '/');
            if (!allowed) {
                throw new IllegalArgumentException("invalid character '" + c + "' in identifier "
                        + part + " \"" + value + "\" — allowed: a-z 0-9 _ . -"
                        + (allowSlash ? " /" : ""));
            }
        }
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Id)) {
            return false;
        }
        Id that = (Id) other;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() * 31 + path.hashCode();
    }
}
