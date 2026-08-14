package dev.fabricmultiloader.api;

/**
 * A named, optional capability a payload may or may not provide.
 *
 * <p>The alternative — {@code if (ctx.platform().minecraftOrdinal() >= 12005)} — encodes a
 * historical accident as a permanent condition. A capability asks the question that is actually
 * meant ("are data components available?"), is declared per payload in the manifest so the
 * validator and the diagnostic report can both see it, and survives a backport or an early
 * implementation without a single edit to common code.
 *
 * <p>Identity is the id alone, so a capability constant is safe to compare with {@code equals}
 * across class loaders and across framework versions.
 *
 * @param <T> the interface a payload supplies when it provides this capability
 */
public final class Capability<T> {

    private final String id;
    private final Class<T> type;

    private Capability(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Defines a capability.
     *
     * @param id the manifest identifier, e.g. {@code "networking.typed"}
     * @param type the interface payloads implement for it
     * @param <T> the capability interface
     * @return the capability constant
     */
    public static <T> Capability<T> of(String id, Class<T> type) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("capability id must not be empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("capability type must not be null");
        }
        return new Capability<T>(id, type);
    }

    /** The manifest identifier. */
    public String id() {
        return id;
    }

    /** The interface a payload supplies. */
    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Capability && ((Capability<?>) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
