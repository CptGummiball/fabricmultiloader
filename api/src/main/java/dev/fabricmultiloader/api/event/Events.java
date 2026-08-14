package dev.fabricmultiloader.api.event;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ImplementedByMod;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ref.BlockPosRef;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import java.util.function.Consumer;

/**
 * Subscribes to game events.
 *
 * <p>Only events whose Fabric API signature has been stable across every supported version are
 * here. That is a real constraint, not a starting point: {@code HudRenderCallback} and
 * {@code ItemTooltipCallback} both changed shape between 1.20.1 and 1.21.x, so exposing them would
 * mean a common signature that cannot be implemented on one of them. Those live behind services
 * instead, and the stability table in {@code docs/common-code.md} records which is which — checked
 * on every framework release by compiling a probe against each matrix version.
 */
@ImplementedByMod
public interface Events {

    /** Fired once when the server has finished starting. */
    Subscription serverStarted(Consumer<ServerRef> handler);

    /** Fired when the server begins shutting down — the last chance to persist state. */
    Subscription serverStopping(Consumer<ServerRef> handler);

    /**
     * Fired at the end of every server tick, twenty times a second.
     *
     * <p>Anything non-trivial here is multiplied by 20 per second; the usual approach is to act
     * only on {@code tickCount() % n == 0}.
     */
    Subscription serverTick(Consumer<ServerRef> handler);

    /** Fired at the end of every client tick. Never fires on a dedicated server. */
    Subscription clientTick(Consumer<ModContext> handler);

    /** Fired when a player finishes joining and can receive packets. */
    Subscription playerJoin(Consumer<PlayerRef> handler);

    /** Fired when a player disconnects. */
    Subscription playerLeave(Consumer<PlayerRef> handler);

    /** Fired when a world is loaded. */
    Subscription worldLoad(Consumer<WorldRef> handler);

    /** Fired when data packs are reloaded, including on {@code /reload}. */
    Subscription dataReload(Consumer<ModContext> handler);

    /** Fired before a block is broken; returning {@code false} cancels it. */
    Subscription blockBroken(BlockBreakHandler handler);

    /** Decides whether a block break may proceed. */
    interface BlockBreakHandler {

        /**
         * @param world the world
         * @param player who is breaking it
         * @param position where
         * @param block which block
         * @return {@code true} to allow, {@code false} to cancel
         */
        boolean onBlockBreak(WorldRef world, PlayerRef player, BlockPosRef position, Id block);
    }

    /**
     * Subscribes to a payload-specific event.
     *
     * <p>The extension point for events a particular Minecraft version has and others do not. The
     * payload defines the key and fires it; common code subscribes only when the capability that
     * provides it is present.
     *
     * @param key identifies the event and its payload type
     * @param handler the handler
     * @param <T> the event payload type
     */
    <T> Subscription custom(EventKey<T> key, Consumer<T> handler);
}
