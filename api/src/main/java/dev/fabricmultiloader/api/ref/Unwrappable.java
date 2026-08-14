package dev.fabricmultiloader.api.ref;

/**
 * A handle to a Minecraft object that common code can hold without naming its type.
 *
 * <p>{@link #unwrap} is the deliberate, documented way out of the abstraction. It is not useful in
 * common code — the type it returns cannot be referenced there — but inside a version module it is
 * the bridge back to the full Minecraft API:
 *
 * <pre>
 * ServerPlayerEntity player = playerRef.unwrap(ServerPlayerEntity.class);
 * </pre>
 *
 * <p>Having this escape hatch is what allows the abstraction above it to stay small and honest.
 * Without one, every gap would have to be filled by growing the common API until it became a second
 * Minecraft API permanently one version behind.
 */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface Unwrappable {

    /**
     * Returns the underlying Minecraft object.
     *
     * @param type the expected Minecraft type
     * @param <T> the expected type
     * @return the underlying object
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4012} if the
     *     object is not of that type; the message names both types, since the usual cause is code
     *     copied between two Minecraft versions whose class names differ
     */
    <T> T unwrap(Class<T> type);

    /** Whether {@link #unwrap} would succeed for this type. */
    boolean is(Class<?> type);
}
