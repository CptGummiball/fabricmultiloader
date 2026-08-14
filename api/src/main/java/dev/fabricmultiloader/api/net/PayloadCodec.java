package dev.fabricmultiloader.api.net;

import dev.fabricmultiloader.api.ImplementedByMod;

/**
 * Converts a message to and from bytes.
 *
 * <p>Written once in common code and used unchanged on every Minecraft version, because it speaks
 * {@link ByteSink}/{@link ByteSource} rather than a version-specific buffer.
 *
 * <pre>
 * public static final PayloadCodec&lt;RubySync&gt; CODEC = new PayloadCodec&lt;RubySync&gt;() {
 *     public void write(ByteSink out, RubySync value) { out.writeId(value.item()).writeVarInt(value.charge()); }
 *     public RubySync read(ByteSource in) { return new RubySync(in.readId(), in.readVarInt()); }
 * };
 * </pre>
 *
 * @param <T> the message type
 */
@ImplementedByMod
public interface PayloadCodec<T> {

    /**
     * Writes a message.
     *
     * @param out where to write
     * @param value the message
     */
    void write(ByteSink out, T value);

    /**
     * Reads a message.
     *
     * <p>The bytes come from a remote peer, so validate rather than trust. Throwing here is safe:
     * the adapter turns a decoding failure into a dropped packet and a log line rather than a
     * crash, so a malformed message from a modified client cannot take the server down.
     *
     * @param in where to read from
     * @return the message
     */
    T read(ByteSource in);
}
