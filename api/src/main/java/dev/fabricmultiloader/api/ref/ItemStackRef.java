package dev.fabricmultiloader.api.ref;

import dev.fabricmultiloader.api.Id;

/**
 * A handle to an item stack.
 *
 * <p>Only the properties that survived every Minecraft version are exposed here. Item data
 * deliberately is not: 1.20.5 replaced NBT tags with data components, and no single model covers
 * both. That gap is filled by the {@code components} capability, which a payload provides only
 * where it exists — visibly, rather than by an accessor that silently does nothing on older
 * versions.
 */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface ItemStackRef extends Unwrappable {

    /** The item identifier, e.g. {@code minecraft:diamond_sword}. */
    Id item();

    /** How many items are in the stack. */
    int count();

    /** Whether the stack is empty. */
    boolean isEmpty();

    /** The stack's damage value, or {@code 0} if it is not damageable. */
    int damage();

    /** The maximum damage, or {@code 0} if the item is not damageable. */
    int maxDamage();

    /** A copy of this stack with a different count. */
    ItemStackRef withCount(int count);

    /** A copy of this stack. */
    ItemStackRef copy();
}
