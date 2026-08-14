package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.ImplementedByMod;
import dev.fabricmultiloader.api.ref.BlockPosRef;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;

/**
 * Behaviour attached to an item, expressed without Minecraft types.
 *
 * <p>Every method has a default, so a mod implements only what it needs and gaining a new hook in a
 * later release never breaks an existing implementation.
 *
 * <p>The callbacks fire on both sides. Checking {@link Context#world()}'s
 * {@link WorldRef#isClient()} before mutating state is not optional: doing work on the client copy
 * produces desynchronisation rather than an error, which is among the harder mod bugs to track
 * down.
 */
@ImplementedByMod
public interface ItemBehavior {

    /** Called when the player right-clicks holding the item. */
    default UseResult onUse(Context context) {
        return UseResult.PASS;
    }

    /** Called when the player right-clicks a block holding the item. */
    default UseResult onUseOnBlock(BlockContext context) {
        return UseResult.PASS;
    }

    /**
     * Called every tick while the stack sits in an inventory.
     *
     * <p>Runs for every matching stack of every player each tick, so anything expensive here is
     * multiplied by the player count.
     *
     * @param stack the stack in question
     * @param holder the player holding it
     * @param selected whether it is the currently selected hotbar slot
     */
    default void onInventoryTick(ItemStackRef stack, PlayerRef holder, boolean selected) {
    }

    /** What was involved in a use interaction. */
    interface Context {

        /** The player using the item. */
        PlayerRef player();

        /** The world the interaction happened in. */
        WorldRef world();

        /** The stack being used. */
        ItemStackRef stack();

        /** Which hand the item was in. */
        Hand hand();
    }

    /** A use interaction targeting a block. */
    interface BlockContext extends Context {

        /** The block that was clicked. */
        BlockPosRef position();

        /** The identifier of the clicked block. */
        dev.fabricmultiloader.api.Id block();
    }
}
