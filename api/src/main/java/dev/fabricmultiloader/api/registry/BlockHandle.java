package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.text.Text;

/** A reference to a registered block. */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface BlockHandle extends RegistryHandle {

    /**
     * The block's item form, or {@code null} if the block was registered without one.
     *
     * <p>Registered through {@code Registries#blockWithItem}, because a block and its item are two
     * registry entries that must share an identifier — getting that wrong yields a block that
     * cannot be obtained, which is easy to do and awkward to notice.
     */
    ItemHandle item();

    /** The block's display name as a translatable text. */
    Text name();

    /** The translation key of the block's name, e.g. {@code block.examplemod.ruby_block}. */
    String translationKey();
}
