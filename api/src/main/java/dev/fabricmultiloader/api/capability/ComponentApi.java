package dev.fabricmultiloader.api.capability;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import java.util.Optional;

/**
 * Data components on item stacks — available from Minecraft 1.20.5 onwards.
 *
 * <p>1.20.5 replaced arbitrary NBT tags on stacks with typed, registered components. The two models
 * are different enough that no shared abstraction is honest: NBT is schemaless and stored per stack,
 * components are declared and validated. Rather than offering an accessor that silently does
 * nothing on 1.20.1, this exists only where the feature does, and common code asks first:
 *
 * <pre>
 * ctx.capability(Capabilities.COMPONENTS)
 *    .ifPresent(components -&gt; components.setInt(stack, Id.of("examplemod", "charge"), 5));
 * </pre>
 *
 * <p>A mod needing per-stack data on older versions keeps its own storage there — which is work,
 * but visible work, rather than a silent behavioural difference.
 */
@dev.fabricmultiloader.api.ImplementedByMod
public interface ComponentApi {

    /** Reads an integer component. */
    Optional<Integer> getInt(ItemStackRef stack, Id component);

    /** Reads a string component. */
    Optional<String> getString(ItemStackRef stack, Id component);

    /** Reads a boolean component. */
    Optional<Boolean> getBoolean(ItemStackRef stack, Id component);

    /** Sets an integer component, returning the modified stack. */
    ItemStackRef setInt(ItemStackRef stack, Id component, int value);

    /** Sets a string component, returning the modified stack. */
    ItemStackRef setString(ItemStackRef stack, Id component, String value);

    /** Sets a boolean component, returning the modified stack. */
    ItemStackRef setBoolean(ItemStackRef stack, Id component, boolean value);

    /** Removes a component, returning the modified stack. */
    ItemStackRef remove(ItemStackRef stack, Id component);

    /** Whether the stack carries the component. */
    boolean has(ItemStackRef stack, Id component);
}
