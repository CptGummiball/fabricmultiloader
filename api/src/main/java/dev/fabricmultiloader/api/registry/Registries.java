package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ImplementedByMod;

/**
 * Declares content, version-neutrally.
 *
 * <p>Calls here do not register anything immediately — they record a declaration and return a
 * handle. The adapter performs the real registration after mod initialisation, which solves three
 * problems at once: it decides the correct timing per Minecraft version, it lets common code store
 * a handle in a field before the object exists, and it makes registration order deterministic,
 * which matters because registry order leaks into network protocols and data packs.
 *
 * <p>Implemented per payload; the interface is what common code sees.
 */
@ImplementedByMod
public interface Registries {

    /**
     * Declares an item.
     *
     * @param id the identifier, normally in the mod's own namespace
     * @param spec what the item is like
     * @return a handle, unbound until after initialisation
     */
    ItemHandle item(Id id, ItemSpec spec);

    /**
     * Declares a block without an item form.
     *
     * @param id the identifier
     * @param spec what the block is like
     * @return a handle
     */
    BlockHandle block(Id id, BlockSpec spec);

    /**
     * Declares a block together with its item form, under the same identifier.
     *
     * <p>Almost always what is wanted: a block with no item cannot be obtained in survival or
     * placed from the creative inventory.
     *
     * @param id the shared identifier
     * @param blockSpec what the block is like
     * @param itemSpec what its item form is like
     * @return a handle whose {@link BlockHandle#item()} is set
     */
    BlockHandle blockWithItem(Id id, BlockSpec blockSpec, ItemSpec itemSpec);

    /**
     * Declares a sound event.
     *
     * @param id the identifier, matching an entry in {@code sounds.json}
     * @return a handle
     */
    RegistryHandle sound(Id id);

    /**
     * Declares a creative-mode item group.
     *
     * @param id the identifier
     * @param icon the item shown on the tab
     * @param displayNameKey the translation key of the tab's title
     * @return a handle
     */
    RegistryHandle itemGroup(Id id, ItemHandle icon, String displayNameKey);

    /**
     * Adds items to a creative-mode item group, vanilla ones included.
     *
     * @param groupId the group, e.g. {@code minecraft:building_blocks} or one of the mod's own
     * @param items the items to add, in order
     */
    void addToItemGroup(Id groupId, ItemHandle... items);

    /**
     * Performs the deferred registrations.
     *
     * <p>Called by the runtime once, immediately after the mod's {@code onInitialize} has returned —
     * the first moment at which everything the mod declares has actually been declared. Mod code
     * never calls this; an adapter that registers eagerly does not implement it either, which is
     * why it has an empty default rather than being abstract.
     *
     * <p>Deferring is nonetheless the recommended shape for an adapter, because the correct moment
     * to touch a Minecraft registry differs across the supported versions and this is the one place
     * that difference has to be expressed.
     */
    default void flush() {
    }
}
