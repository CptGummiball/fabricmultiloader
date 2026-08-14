package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.text.Text;

/** A reference to a registered item. */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface ItemHandle extends RegistryHandle {

    /**
     * A stack of this item.
     *
     * @param count how many
     * @return the stack
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4002} if the
     *     item is not bound yet
     */
    ItemStackRef stack(int count);

    /** A stack of one. */
    ItemStackRef stack();

    /** The item's display name as a translatable text. */
    Text name();

    /** The translation key of the item's name, e.g. {@code item.examplemod.ruby}. */
    String translationKey();
}
