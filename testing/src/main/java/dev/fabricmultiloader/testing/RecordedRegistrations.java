package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.net.ChannelSpec;
import dev.fabricmultiloader.api.registry.BlockSpec;
import dev.fabricmultiloader.api.registry.ItemSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a mod declared, in declaration order.
 *
 * <p>This is what makes common code testable without Minecraft, and it is a by-product of the
 * architecture rather than an extra: because {@code Registries} takes a <em>specification</em> and
 * returns a handle, a fake can record the specification verbatim and a test can assert on the exact
 * values the adapter would have used. Nothing is approximated.
 *
 * <p>Order is preserved throughout. Registration order leaks into network protocols and data packs,
 * so "the mod registers these three items" and "the mod registers them in this order" are different
 * claims, and a test should be able to make either.
 */
public final class RecordedRegistrations {

    private final Map<Id, ItemSpec> items = new LinkedHashMap<Id, ItemSpec>();
    private final Map<Id, BlockSpec> blocks = new LinkedHashMap<Id, BlockSpec>();
    private final Map<Id, ItemSpec> blockItems = new LinkedHashMap<Id, ItemSpec>();
    private final List<Id> sounds = new ArrayList<Id>();
    private final Map<Id, String> itemGroups = new LinkedHashMap<Id, String>();
    private final Map<Id, List<Id>> itemGroupContents = new LinkedHashMap<Id, List<Id>>();
    private final Map<Id, ChannelSpec<?>> channels = new LinkedHashMap<Id, ChannelSpec<?>>();
    private final List<Sent> sent = new ArrayList<Sent>();

    private int flushCount;

    // ------------------------------------------------------------------ recording

    void recordItem(Id id, ItemSpec spec) {
        items.put(id, spec);
    }

    void recordBlock(Id id, BlockSpec spec) {
        blocks.put(id, spec);
    }

    void recordBlockItem(Id id, ItemSpec spec) {
        blockItems.put(id, spec);
    }

    void recordSound(Id id) {
        sounds.add(id);
    }

    void recordItemGroup(Id id, String displayNameKey) {
        itemGroups.put(id, displayNameKey);
    }

    void recordItemGroupContent(Id groupId, Id item) {
        List<Id> contents = itemGroupContents.get(groupId);
        if (contents == null) {
            contents = new ArrayList<Id>();
            itemGroupContents.put(groupId, contents);
        }
        contents.add(item);
    }

    void recordChannel(ChannelSpec<?> spec) {
        channels.put(spec.id(), spec);
    }

    void recordSend(Id channel, String target, Object payload) {
        sent.add(new Sent(channel, target, payload));
    }

    void recordFlush() {
        flushCount++;
    }

    // ------------------------------------------------------------------ assertions

    /** Every declared item, by identifier, in declaration order. */
    public Map<Id, ItemSpec> items() {
        return Collections.unmodifiableMap(items);
    }

    /** Every declared block, by identifier, in declaration order. */
    public Map<Id, BlockSpec> blocks() {
        return Collections.unmodifiableMap(blocks);
    }

    /** The item forms declared through {@code blockWithItem}, keyed by the shared identifier. */
    public Map<Id, ItemSpec> blockItems() {
        return Collections.unmodifiableMap(blockItems);
    }

    /** Every declared sound event, in declaration order. */
    public List<Id> sounds() {
        return Collections.unmodifiableList(sounds);
    }

    /** Every declared item group and its title translation key. */
    public Map<Id, String> itemGroups() {
        return Collections.unmodifiableMap(itemGroups);
    }

    /** What was added to each item group, vanilla groups included. */
    public Map<Id, List<Id>> itemGroupContents() {
        return Collections.unmodifiableMap(itemGroupContents);
    }

    /** Every registered network channel, by identifier. */
    public Map<Id, ChannelSpec<?>> channels() {
        return Collections.unmodifiableMap(channels);
    }

    /** Every payload the mod sent, in order. */
    public List<Sent> sent() {
        return Collections.unmodifiableList(sent);
    }

    /**
     * How often the runtime flushed the deferred registrations.
     *
     * <p>Worth asserting in a framework test: exactly once, and after the mod's {@code onInitialize}.
     */
    public int flushCount() {
        return flushCount;
    }

    /** Whether an item with this identifier was declared. */
    public boolean hasItem(Id id) {
        return items.containsKey(id);
    }

    /** Whether a channel with this identifier was registered. */
    public boolean hasChannel(Id id) {
        return channels.containsKey(id);
    }

    @Override
    public String toString() {
        return items.size() + " items, " + blocks.size() + " blocks, "
                + channels.size() + " channels, " + sent.size() + " packets";
    }

    /** One payload handed to a channel, with where it was addressed. */
    public static final class Sent {

        private final Id channel;
        private final String target;
        private final Object payload;

        Sent(Id channel, String target, Object payload) {
            this.channel = channel;
            this.target = target;
            this.payload = payload;
        }

        /** The channel it went out on. */
        public Id channel() {
            return channel;
        }

        /** Where it was addressed: {@code server}, {@code all}, or a player or world description. */
        public String target() {
            return target;
        }

        /** The payload object, un-serialised — assert on it directly. */
        public Object payload() {
            return payload;
        }

        @Override
        public String toString() {
            return channel + " -> " + target + ": " + payload;
        }
    }
}
