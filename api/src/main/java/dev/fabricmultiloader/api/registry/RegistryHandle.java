package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.Unwrappable;

/**
 * A stable reference to something the mod registered.
 *
 * <p>Registration returns a handle rather than the Minecraft object because the object does not
 * exist yet: content is declared during {@code onInitialize} and actually created afterwards, when
 * the adapter knows the right constructor for the running version. A handle can be stored in a
 * static field immediately and resolves once {@link #isBound()} turns true.
 *
 * <p>Used directly for registrations that carry no extra behaviour — sounds and item groups.
 */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface RegistryHandle extends Unwrappable {

    /** The identifier this was registered under. */
    Id id();

    /**
     * Whether the underlying Minecraft object exists yet.
     *
     * <p>False between declaration and the flush that follows mod initialisation. Calling
     * {@link Unwrappable#unwrap} before then raises {@code OMNI-4002} naming the phase, rather than
     * returning null for something to trip over later.
     */
    boolean isBound();
}
