package dev.fabricmultiloader.api.capability;

/**
 * Typed network payloads — available from Minecraft 1.20.5 onwards.
 *
 * <p>The ordinary {@code Networking} API works everywhere and hides the difference. This capability
 * exposes what the newer model additionally guarantees, for the rare mod that needs to know:
 * payload types are registered per direction ahead of time, so a client's support for a channel is
 * negotiated at join rather than discovered when a packet is dropped.
 */
@dev.fabricmultiloader.api.ImplementedByMod
public interface TypedPayloadApi {

    /**
     * Whether the running version negotiates channel support at join time.
     *
     * <p>When true, {@code ChannelHandle#canReceive} is authoritative. When false it is a best
     * guess, and sending to a client that cannot handle the channel simply does nothing.
     */
    boolean negotiatesChannels();

    /**
     * Whether packet buffers carry registry access, allowing registry-dependent values such as item
     * stacks with components to be encoded.
     */
    boolean supportsRegistrySync();
}
