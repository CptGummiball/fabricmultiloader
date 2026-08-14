package dev.fabricmultiloader.api;

import dev.fabricmultiloader.api.capability.ComponentApi;
import dev.fabricmultiloader.api.capability.TagApi;
import dev.fabricmultiloader.api.capability.TypedPayloadApi;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The capabilities the framework itself defines.
 *
 * <p>Mods may define their own the same way — {@code Capability.of(id, Interface.class)} — for
 * anything a subset of their payloads provides. The ids here appear in each payload's
 * {@code capabilities} list in the manifest, so the validator can check that a payload declaring
 * one actually implements it, and a diagnostic report can tell a user which features their
 * Minecraft version does not get.
 */
public final class Capabilities {

    /** Data components on item stacks. Minecraft 1.20.5 and later. */
    public static final Capability<ComponentApi> COMPONENTS =
            Capability.of("components", ComponentApi.class);

    /** Typed, negotiated network payloads. Minecraft 1.20.5 and later. */
    public static final Capability<TypedPayloadApi> TYPED_PAYLOADS =
            Capability.of("networking.typed", TypedPayloadApi.class);

    /** Item and block tag lookup. Available on every supported version, once data packs have loaded. */
    public static final Capability<TagApi> TAGS = Capability.of("tags", TagApi.class);

    /** Every capability the framework defines, in declaration order. */
    public static List<Capability<?>> all() {
        return Collections.unmodifiableList(
                Arrays.<Capability<?>>asList(COMPONENTS, TYPED_PAYLOADS, TAGS));
    }

    private Capabilities() {
        throw new AssertionError("no instances");
    }
}
