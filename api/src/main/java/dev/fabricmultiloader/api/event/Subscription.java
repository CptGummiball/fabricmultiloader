package dev.fabricmultiloader.api.event;

import dev.fabricmultiloader.api.ImplementedByFramework;

/**
 * A registered event handler that can be removed again.
 *
 * <p>Most handlers live for the process lifetime, but the ones that do not — a temporary listener
 * during a minigame, a handler bound to a config option — otherwise leak, and a leaked tick handler
 * costs performance every tick for the rest of the session. Implements {@link AutoCloseable} so it
 * works with try-with-resources where that reads naturally.
 */
@ImplementedByFramework
public interface Subscription extends AutoCloseable {

    /** Removes the handler. Calling it twice is harmless. */
    void unsubscribe();

    /** Whether the handler is still registered. */
    boolean isActive();

    @Override
    default void close() {
        unsubscribe();
    }
}
