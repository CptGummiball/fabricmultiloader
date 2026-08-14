package dev.fabricmultiloader.api.event;

/**
 * Identifies a payload-specific event and the type it carries.
 *
 * <p>Same shape and rationale as {@code Capability}: an identifier that a payload can offer and
 * common code can ask for, without either naming a type the other cannot see. Identity is the id
 * alone.
 *
 * @param <T> the event payload type
 */
public final class EventKey<T> {

    private final String id;
    private final Class<T> type;

    private EventKey(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Defines an event key.
     *
     * @param id a namespaced identifier, e.g. {@code examplemod:ruby_charged}
     * @param type the payload type
     * @param <T> the payload type
     * @return the key
     */
    public static <T> EventKey<T> of(String id, Class<T> type) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("event key id must not be empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("event key type must not be null");
        }
        return new EventKey<T>(id, type);
    }

    /** The identifier. */
    public String id() {
        return id;
    }

    /** The payload type. */
    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EventKey && ((EventKey<?>) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
