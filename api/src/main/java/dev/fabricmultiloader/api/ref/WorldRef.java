package dev.fabricmultiloader.api.ref;

import dev.fabricmultiloader.api.Id;

/** A handle to a world. */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface WorldRef extends Unwrappable {

    /** The dimension identifier, e.g. {@code minecraft:overworld}. */
    Id dimension();

    /**
     * Whether this is the client's copy of the world.
     *
     * <p>The distinction most mod bugs are made of: a client world holds only what the server has
     * sent, so writing to it produces desynchronisation rather than an error.
     */
    boolean isClient();

    /** The world time in ticks. */
    long time();

    /** The day/night time of day, 0–23999. */
    long timeOfDay();

    /** Whether it is raining. */
    boolean isRaining();

    /** The block identifier at a position, e.g. {@code minecraft:stone}. */
    Id blockAt(BlockPosRef position);

    /** Whether the chunk containing a position is loaded — always check before touching blocks. */
    boolean isLoaded(BlockPosRef position);
}
