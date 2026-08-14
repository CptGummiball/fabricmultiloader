package dev.fabricmultiloader.api.capability;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import java.util.List;

/**
 * Queries item and block tags.
 *
 * <p>A capability rather than a core API because tag lookup requires registry access, which is only
 * available once the server has loaded its data packs. Asking earlier returns nothing on some
 * versions and throws on others, so this deliberately cannot be reached before the point where it
 * would work.
 */
@dev.fabricmultiloader.api.ImplementedByMod
public interface TagApi {

    /** Whether an item is in a tag, e.g. {@code minecraft:planks}. */
    boolean itemInTag(Id item, Id tag);

    /** Whether a stack's item is in a tag. */
    boolean stackInTag(ItemStackRef stack, Id tag);

    /** Whether a block is in a tag. */
    boolean blockInTag(Id block, Id tag);

    /** Every item in a tag; empty if the tag does not exist. */
    List<Id> itemsInTag(Id tag);

    /** Every block in a tag; empty if the tag does not exist. */
    List<Id> blocksInTag(Id tag);
}
