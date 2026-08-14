package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;

/**
 * A registered channel: send messages, and receive them.
 *
 * <p>Receivers always run on the game thread. Minecraft decodes packets on a network thread, and
 * every version requires hopping back before touching world state — 1.20.1 needs an explicit
 * {@code server.execute}, later versions supply a context that has already done it. The adapter
 * normalises that away, so a handler written once is thread-correct everywhere. Getting this wrong
 * by hand produces corruption that appears only under load.
 *
 * @param <T> the message type
 */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface ChannelHandle<T> {

    /** The channel identifier. */
    Id id();

    /** Handles a message sent by a client. */
    interface C2SReceiver<T> {

        /**
         * @param payload the decoded message
         * @param sender the player who sent it — always validate that they may do what it asks
         * @param ctx the mod context
         */
        void accept(T payload, PlayerRef sender, ModContext ctx);
    }

    /** Handles a message sent by the server. */
    interface S2CReceiver<T> {

        /**
         * @param payload the decoded message
         * @param ctx the mod context
         */
        void accept(T payload, ModContext ctx);
    }

    /**
     * Registers the server-side receiver. Call during initialisation, not per player.
     *
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4013} on a
     *     channel that does not travel client-to-server
     */
    void receiveOnServer(C2SReceiver<T> receiver);

    /**
     * Registers the client-side receiver. Silently ignored on a dedicated server, so common code
     * can call it unconditionally.
     */
    void receiveOnClient(S2CReceiver<T> receiver);

    /**
     * Sends a message to the server.
     *
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4013} if called
     *     on a dedicated server
     */
    void sendToServer(T payload);

    /** Sends a message to one player. */
    void sendTo(PlayerRef player, T payload);

    /** Sends a message to every player in a world. */
    void sendToAllIn(WorldRef world, T payload);

    /** Sends a message to every connected player. */
    void sendToAll(T payload);

    /**
     * Whether the player's client has this channel registered.
     *
     * <p>Worth checking before sending anything optional: a vanilla client, or one without this
     * mod, will simply drop the packet, and on some versions logs a warning for every one.
     */
    boolean canReceive(PlayerRef player);
}
