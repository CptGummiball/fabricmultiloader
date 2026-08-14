package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.ImplementedByMod;

/**
 * Registers network channels.
 *
 * <p>The reference case for how this framework abstracts a Minecraft API that was rebuilt between
 * supported versions. In 1.20.1 a channel is an {@code Identifier} plus a raw {@code PacketByteBuf}
 * handler; from 1.20.5 it is a typed {@code CustomPayload} with a {@code PacketCodec} that must be
 * registered per direction before anyone joins. Common code sees neither: it declares a
 * {@link ChannelSpec} and writes one codec, and each adapter binds it to whatever the running
 * version expects — in roughly forty lines.
 *
 * <p>Register channels during initialisation, not lazily. On 1.20.5+ a channel registered after the
 * first player joins is not part of the negotiated set, and messages on it are dropped without an
 * error anyone will see.
 */
@ImplementedByMod
public interface Networking {

    /**
     * Registers a channel.
     *
     * @param spec identifier, direction and codec
     * @param <T> the message type
     * @return a handle for sending and receiving
     */
    <T> ChannelHandle<T> register(ChannelSpec<T> spec);
}
