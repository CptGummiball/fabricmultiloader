package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.BlockPosRef;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads a packet payload, mirroring {@link ByteSink} exactly.
 *
 * <p>Packet contents arrive from a remote peer and are therefore untrusted, including from a
 * modified client. A codec should validate what it reads rather than trusting it; the read methods
 * enforce only structural limits, not semantic ones.
 */
@dev.fabricmultiloader.api.ImplementedByMod
public interface ByteSource {

    /** Reads a boolean. */
    boolean readBoolean();

    /** Reads a single byte. */
    int readByte();

    /** Reads a short. */
    int readShort();

    /** Reads a fixed-width int. */
    int readInt();

    /** Reads a variable-length int. */
    int readVarInt();

    /** Reads a long. */
    long readLong();

    /** Reads a float. */
    float readFloat();

    /** Reads a double. */
    double readDouble();

    /** Reads a length-prefixed UTF-8 string. */
    String readString();

    /**
     * Reads a length-prefixed UTF-8 string with an explicit bound.
     *
     * <p>Worth preferring for anything that ends up in a log, a file name or a GUI: a peer is free
     * to send a megabyte where a name was expected.
     *
     * @param maxLength the largest acceptable length
     */
    String readString(int maxLength);

    /** Reads a UUID. */
    UUID readUuid();

    /** Reads a namespaced identifier. */
    Id readId();

    /** Reads a block position. */
    BlockPosRef readBlockPos();

    /** Reads an item stack. */
    ItemStackRef readItemStack();

    /** Reads a length-prefixed byte array. */
    byte[] readBytes();

    /**
     * Reads an enum written by {@link ByteSink#writeEnum}.
     *
     * @param type the enum class
     * @param <E> the enum type
     * @return the constant
     * @throws IllegalArgumentException if the ordinal is out of range, which a modified client can
     *     easily produce
     */
    <E extends Enum<E>> E readEnum(Class<E> type);

    /**
     * Reads a length-prefixed list.
     *
     * @param reader how to read one element
     * @param <E> the element type
     */
    <E> List<E> readList(Function<ByteSource, E> reader);

    /**
     * Reads a length-prefixed list with a bound on its size.
     *
     * @param reader how to read one element
     * @param maxSize the largest acceptable list
     * @param <E> the element type
     */
    <E> List<E> readList(Function<ByteSource, E> reader, int maxSize);

    /**
     * Reads an optional value.
     *
     * @param reader how to read the value when present
     * @param <E> the value type
     */
    <E> Optional<E> readOptional(Function<ByteSource, E> reader);
}
