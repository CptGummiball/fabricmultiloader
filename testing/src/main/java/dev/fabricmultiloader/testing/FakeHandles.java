package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.ItemStackRef;
import dev.fabricmultiloader.api.registry.BlockHandle;
import dev.fabricmultiloader.api.registry.ItemHandle;
import dev.fabricmultiloader.api.registry.RegistryHandle;
import dev.fabricmultiloader.api.text.Text;

/**
 * The handles a fake registry hands back.
 *
 * <p>A handle is the mod's reference to something that does not exist yet — in the game it becomes
 * bound when the adapter performs the real registration. Here nothing ever binds, and
 * {@link RegistryHandle#isBound()} says so honestly rather than pretending. That matters: code that
 * checks {@code isBound()} before touching a handle is code that behaves correctly during
 * initialisation, and a fake that always returned {@code true} would hide the one bug this check
 * exists to catch.
 *
 * <p>{@code unwrap} fails with a sentence rather than a {@code ClassCastException}: reaching for the
 * Minecraft object in a test that has no Minecraft is a mistake worth naming.
 */
public final class FakeHandles {

    /** A registry handle that records its identifier and never binds. */
    public static class FakeRegistryHandle implements RegistryHandle {

        private final Id id;

        FakeRegistryHandle(Id id) {
            this.id = id;
        }

        @Override
        public Id id() {
            return id;
        }

        @Override
        public boolean isBound() {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new UnsupportedOperationException(
                    "cannot unwrap " + id + " to " + type.getName()
                            + ": this is a FakeModContext, so there is no Minecraft object behind "
                            + "the handle. Move code that needs the real object into the payload.");
        }

        @Override
        public boolean is(Class<?> type) {
            // Honest rather than convenient: there is no Minecraft object, so it is not of any
            // Minecraft type, and code branching on this behaves in a test as it would in the game
            // when the handle is unbound.
            return false;
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    /** An item handle. */
    public static final class FakeItemHandle extends FakeRegistryHandle implements ItemHandle {

        FakeItemHandle(Id id) {
            super(id);
        }

        @Override
        public ItemStackRef stack(int count) {
            return new FakeItemStack(id(), count);
        }

        @Override
        public ItemStackRef stack() {
            return stack(1);
        }

        @Override
        public Text name() {
            return Text.translatable(translationKey());
        }

        @Override
        public String translationKey() {
            return "item." + id().namespace() + "." + id().path();
        }
    }

    /** A block handle, with the item form the mod declared alongside it. */
    public static final class FakeBlockHandle extends FakeRegistryHandle implements BlockHandle {

        private final ItemHandle item;

        FakeBlockHandle(Id id, ItemHandle item) {
            super(id);
            this.item = item;
        }

        @Override
        public ItemHandle item() {
            return item;
        }

        @Override
        public Text name() {
            return Text.translatable(translationKey());
        }

        @Override
        public String translationKey() {
            return "block." + id().namespace() + "." + id().path();
        }
    }

    /** An item stack that carries only what the common API can see of one. */
    public static final class FakeItemStack implements ItemStackRef {

        private final Id item;
        private final int count;
        private final int damage;
        private final int maxDamage;

        /**
         * @param item the item identifier
         * @param count the stack size
         */
        public FakeItemStack(Id item, int count) {
            this(item, count, 0, 0);
        }

        /**
         * @param item the item identifier
         * @param count the stack size
         * @param damage current damage
         * @param maxDamage maximum damage, 0 for an undamageable item
         */
        public FakeItemStack(Id item, int count, int damage, int maxDamage) {
            this.item = item;
            this.count = count;
            this.damage = damage;
            this.maxDamage = maxDamage;
        }

        @Override
        public Id item() {
            return item;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public boolean isEmpty() {
            return count <= 0;
        }

        @Override
        public int damage() {
            return damage;
        }

        @Override
        public int maxDamage() {
            return maxDamage;
        }

        @Override
        public ItemStackRef withCount(int newCount) {
            return new FakeItemStack(item, newCount, damage, maxDamage);
        }

        @Override
        public ItemStackRef copy() {
            return new FakeItemStack(item, count, damage, maxDamage);
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new UnsupportedOperationException(
                    "cannot unwrap a FakeItemStack to " + type.getName()
                            + ": there is no Minecraft in this test");
        }

        @Override
        public boolean is(Class<?> type) {
            return false;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FakeItemStack)) {
                return false;
            }
            FakeItemStack that = (FakeItemStack) other;
            return count == that.count && damage == that.damage
                    && maxDamage == that.maxDamage && item.equals(that.item);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * item.hashCode() + count) + damage;
        }

        @Override
        public String toString() {
            return count + "x " + item;
        }
    }

    /** Creates an item handle. */
    public static ItemHandle item(Id id) {
        return new FakeItemHandle(id);
    }

    /** Creates a block handle without an item form. */
    public static BlockHandle block(Id id) {
        return new FakeBlockHandle(id, null);
    }

    /** Creates a block handle with an item form under the same identifier. */
    public static BlockHandle blockWithItem(Id id) {
        return new FakeBlockHandle(id, new FakeItemHandle(id));
    }

    /** Creates a plain registry handle. */
    public static RegistryHandle plain(Id id) {
        return new FakeRegistryHandle(id);
    }

    private FakeHandles() {
        throw new AssertionError("no instances");
    }
}
