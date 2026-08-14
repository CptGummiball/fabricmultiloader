package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.capability.ComponentApi;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link ComponentApi}, so common code guarded on
 * {@code Capabilities.COMPONENTS} can be tested on both branches.
 *
 * <p>Data components are the clearest case for testing a capability rather than a version number:
 * the same mod must work with them on 1.20.5 and later and without them before, and only one of
 * those paths is exercised by whichever Minecraft version a developer happens to run. Handing this
 * to {@code FakeModContext.builder().capability(...)} tests the first; leaving it out tests the
 * second.
 *
 * <p>Values are keyed by stack identity rather than by content: two stacks of the same item are
 * different stacks in the game, and a fake that merged them would let a bug through.
 */
public final class FakeComponents implements ComponentApi {

    private final Map<Key, Object> values = new LinkedHashMap<Key, Object>();

    @Override
    public Optional<Integer> getInt(ItemStackRef stack, Id component) {
        return read(stack, component, Integer.class);
    }

    @Override
    public Optional<String> getString(ItemStackRef stack, Id component) {
        return read(stack, component, String.class);
    }

    @Override
    public Optional<Boolean> getBoolean(ItemStackRef stack, Id component) {
        return read(stack, component, Boolean.class);
    }

    @Override
    public ItemStackRef setInt(ItemStackRef stack, Id component, int value) {
        return write(stack, component, Integer.valueOf(value));
    }

    @Override
    public ItemStackRef setString(ItemStackRef stack, Id component, String value) {
        return write(stack, component, value);
    }

    @Override
    public ItemStackRef setBoolean(ItemStackRef stack, Id component, boolean value) {
        return write(stack, component, Boolean.valueOf(value));
    }

    @Override
    public ItemStackRef remove(ItemStackRef stack, Id component) {
        values.remove(new Key(stack, component));
        return stack;
    }

    @Override
    public boolean has(ItemStackRef stack, Id component) {
        return values.containsKey(new Key(stack, component));
    }

    /** How many component values are held, for a test that wants to assert cleanup. */
    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return "FakeComponents(" + values.size() + " values)";
    }

    private <T> Optional<T> read(ItemStackRef stack, Id component, Class<T> type) {
        Object value = values.get(new Key(stack, component));
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("component " + component + " holds a "
                    + value.getClass().getSimpleName() + ", read as " + type.getSimpleName());
        }
        return Optional.of(type.cast(value));
    }

    private ItemStackRef write(ItemStackRef stack, Id component, Object value) {
        values.put(new Key(stack, component), value);
        return stack;
    }

    /** Identity of a stack plus a component id. */
    private static final class Key {

        private final ItemStackRef stack;
        private final Id component;

        Key(ItemStackRef stack, Id component) {
            this.stack = stack;
            this.component = component;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return stack == that.stack && component.equals(that.component);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(stack) * 31 + component.hashCode();
        }
    }
}
