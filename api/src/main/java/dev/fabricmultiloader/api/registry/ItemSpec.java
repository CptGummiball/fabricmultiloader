package dev.fabricmultiloader.api.registry;

import dev.fabricmultiloader.api.Id;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A description of an item, not an item.
 *
 * <p>Constructing one is the whole point: item creation changed from
 * {@code new Item(new FabricItemSettings())} to {@code new Item(new Item.Settings().registryKey(…))}
 * between 1.20.1 and 1.21.2, and a registry key became mandatory along the way. Common code that
 * built the object directly would need one copy per version; common code that describes it needs
 * none, and each adapter translates the description in about fifteen lines.
 */
public final class ItemSpec {

    private final int maxCount;
    private final Rarity rarity;
    private final boolean fireproof;
    private final Integer maxDamage;
    private final Id craftingRemainder;
    private final ItemBehavior behavior;
    private final List<String> tooltipKeys;

    private ItemSpec(Builder builder) {
        this.maxCount = builder.maxCount;
        this.rarity = builder.rarity;
        this.fireproof = builder.fireproof;
        this.maxDamage = builder.maxDamage;
        this.craftingRemainder = builder.craftingRemainder;
        this.behavior = builder.behavior;
        this.tooltipKeys = Collections.unmodifiableList(new ArrayList<String>(builder.tooltipKeys));
    }

    /** Starts a builder with vanilla defaults: stack size 64, common rarity, no behaviour. */
    public static Builder builder() {
        return new Builder();
    }

    /** A plain item with every default. */
    public static ItemSpec simple() {
        return builder().build();
    }

    /** Maximum stack size, 1–99. */
    public int maxCount() {
        return maxCount;
    }

    /** Name colour in tooltips. */
    public Rarity rarity() {
        return rarity;
    }

    /** Whether the item survives lava and fire. */
    public boolean fireproof() {
        return fireproof;
    }

    /** Durability, or {@code null} if the item is not damageable. */
    public Integer maxDamage() {
        return maxDamage;
    }

    /** Item left behind after crafting, e.g. a bucket, or {@code null}. */
    public Id craftingRemainder() {
        return craftingRemainder;
    }

    /** Attached behaviour, or {@code null}. */
    public ItemBehavior behavior() {
        return behavior;
    }

    /** Translation keys appended to the tooltip, in order. */
    public List<String> tooltipKeys() {
        return tooltipKeys;
    }

    /** Whether the item is damageable. */
    public boolean isDamageable() {
        return maxDamage != null && maxDamage > 0;
    }

    @Override
    public String toString() {
        return "ItemSpec[maxCount=" + maxCount + ", rarity=" + rarity
                + (isDamageable() ? ", maxDamage=" + maxDamage : "")
                + (behavior != null ? ", behavior" : "") + "]";
    }

    /** Mutable builder. */
    public static final class Builder {

        private int maxCount = 64;
        private Rarity rarity = Rarity.COMMON;
        private boolean fireproof;
        private Integer maxDamage;
        private Id craftingRemainder;
        private ItemBehavior behavior;
        private final List<String> tooltipKeys = new ArrayList<String>();

        /**
         * Sets the maximum stack size.
         *
         * @param count 1–99
         * @throws IllegalArgumentException outside that range, since Minecraft silently misbehaves
         *     rather than refusing
         */
        public Builder maxCount(int count) {
            if (count < 1 || count > 99) {
                throw new IllegalArgumentException("maxCount must be between 1 and 99, got " + count);
            }
            this.maxCount = count;
            return this;
        }

        /** Sets the rarity. */
        public Builder rarity(Rarity value) {
            this.rarity = value == null ? Rarity.COMMON : value;
            return this;
        }

        /** Makes the item survive fire and lava. */
        public Builder fireproof() {
            this.fireproof = true;
            return this;
        }

        /**
         * Makes the item damageable.
         *
         * <p>Also forces the stack size to 1: Minecraft cannot represent a damageable stack of more
         * than one, and setting both produces an item that behaves unpredictably rather than one
         * that fails to register.
         */
        public Builder maxDamage(int durability) {
            if (durability < 1) {
                throw new IllegalArgumentException("maxDamage must be positive, got " + durability);
            }
            this.maxDamage = Integer.valueOf(durability);
            this.maxCount = 1;
            return this;
        }

        /** Sets the item left behind after crafting. */
        public Builder craftingRemainder(Id item) {
            this.craftingRemainder = item;
            return this;
        }

        /** Attaches behaviour. */
        public Builder behavior(ItemBehavior value) {
            this.behavior = value;
            return this;
        }

        /** Appends a translation key to the tooltip. */
        public Builder tooltip(String translationKey) {
            if (translationKey != null && !translationKey.isEmpty()) {
                tooltipKeys.add(translationKey);
            }
            return this;
        }

        /** Builds the immutable specification. */
        public ItemSpec build() {
            return new ItemSpec(this);
        }
    }
}
