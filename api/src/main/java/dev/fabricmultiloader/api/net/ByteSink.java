package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.BlockPosRef;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Writes a packet payload without naming Minecraft's buffer type.
 *
 * <p>This interface is what lets a codec live in common code. The underlying buffer changed from
 * {@code PacketByteBuf} to {@code RegistryByteBuf} when networking was reworked in 1.20.5, and a
 * codec written against either would compile for exactly one version family. Written against
 * {@code ByteSink}, it compiles once and every adapter supplies the buffer it has.
 *
 * <p>{@link #writeItemStack} is deliberately part of the interface rather than something callers
 * assemble from primitives: item stack serialisation moved from NBT to registry-aware data
 * components, and only the adapter can do it correctly for the running version.
 *
 * <p>Every method returns {@code this} so a codec reads as a sequence rather than a block of
 * statements.
 */
@dev.fabricmultiloader.api.ImplementedByMod
public interface ByteSink {

    /** Writes a boolean. */
    ByteSink writeBoolean(boolean value);

    /** Writes a single byte. */
    ByteSink writeByte(int value);

    /** Writes a short. */
    ByteSink writeShort(int value);

    /** Writes a fixed-width int. Prefer {@link #writeVarInt} for values that are usually small. */
    ByteSink writeInt(int value);

    /** Writes a variable-length int — one byte for values below 128. */
    ByteSink writeVarInt(int value);

    /** Writes a long. */
    ByteSink writeLong(long value);

    /** Writes a float. */
    ByteSink writeFloat(float value);

    /** Writes a double. */
    ByteSink writeDouble(double value);

    /** Writes a length-prefixed UTF-8 string. */
    ByteSink writeString(String value);

    /** Writes a UUID as two longs. */
    ByteSink writeUuid(UUID value);

    /** Writes a namespaced identifier. */
    ByteSink writeId(Id value);

    /** Writes a block position in Minecraft's packed form. */
    ByteSink writeBlockPos(BlockPosRef value);

    /** Writes an item stack using whatever encoding the running version uses. */
    ByteSink writeItemStack(ItemStackRef value);

    /** Writes a length-prefixed byte array. */
    ByteSink writeBytes(byte[] value);

    /** Writes an enum by ordinal, as a var int. */
    ByteSink writeEnum(Enum<?> value);

    /**
     * Writes a length-prefixed list.
     *
     * @param values the elements
     * @param writer how to write one element
     * @param <E> the element type
     */
    <E> ByteSink writeList(List<E> values, BiConsumer<ByteSink, E> writer);

    /**
     * Writes an optional value as a presence flag followed by the value.
     *
     * @param value the value, possibly absent
     * @param writer how to write it when present
     * @param <E> the value type
     */
    <E> ByteSink writeOptional(Optional<E> value, BiConsumer<ByteSink, E> writer);
}
