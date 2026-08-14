package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.Id;

/**
 * Declares a network channel: an identifier, a direction and a codec.
 *
 * <p>The direction is not decoration. From 1.20.5 Minecraft requires a payload type to be
 * registered for the direction it will travel, before the first player joins; registering the wrong
 * one produces a disconnect at join time rather than an error at startup. Declaring it here lets
 * the adapter register correctly on versions that need it and ignore it on versions that do not.
 *
 * @param <T> the message type
 */
public final class ChannelSpec<T> {

    /** Which way messages travel. */
    public enum Direction {
        /** Client to server. */
        C2S,
        /** Server to client. */
        S2C,
        /** Both ways. */
        BOTH;

        /** Whether this direction includes client-to-server traffic. */
        public boolean allowsC2S() {
            return this != S2C;
        }

        /** Whether this direction includes server-to-client traffic. */
        public boolean allowsS2C() {
            return this != C2S;
        }
    }

    private final Id id;
    private final Direction direction;
    private final PayloadCodec<T> codec;

    private ChannelSpec(Id id, Direction direction, PayloadCodec<T> codec) {
        if (id == null) {
            throw new IllegalArgumentException("channel id must not be null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("channel codec must not be null");
        }
        this.id = id;
        this.direction = direction;
        this.codec = codec;
    }

    /** A client-to-server channel. */
    public static <T> ChannelSpec<T> c2s(Id id, PayloadCodec<T> codec) {
        return new ChannelSpec<T>(id, Direction.C2S, codec);
    }

    /** A server-to-client channel. */
    public static <T> ChannelSpec<T> s2c(Id id, PayloadCodec<T> codec) {
        return new ChannelSpec<T>(id, Direction.S2C, codec);
    }

    /** A bidirectional channel. */
    public static <T> ChannelSpec<T> both(Id id, PayloadCodec<T> codec) {
        return new ChannelSpec<T>(id, Direction.BOTH, codec);
    }

    /** The channel identifier. */
    public Id id() {
        return id;
    }

    /** Which way messages travel. */
    public Direction direction() {
        return direction;
    }

    /** How messages are encoded. */
    public PayloadCodec<T> codec() {
        return codec;
    }

    @Override
    public String toString() {
        return id + " (" + direction + ")";
    }
}
