package dev.fabricmultiloader.format.json;

/**
 * Hard bounds applied while parsing.
 *
 * <p>Manifest content is treated as fundamentally untrusted: a universal JAR can be tampered with,
 * repacked by a modpack tool, or simply corrupted in transit. Without limits, a crafted document
 * could exhaust the heap or the stack before any validation runs — and it would do so during
 * {@code preLaunch}, where the failure is hardest to diagnose. The limits are deliberately far
 * above anything a real manifest needs (a three-payload manifest is roughly 4 KiB).
 *
 * @see dev.fabricmultiloader.format.error.ErrorCode#OMNI_3003
 */
public final class JsonLimits {

    /** The limits used for container manifests and payload descriptors. */
    public static final JsonLimits DEFAULT = new JsonLimits(1024 * 1024, 64, 4096, 65536);

    /** No limits at all. For tests and for trusted, locally generated documents. */
    public static final JsonLimits UNLIMITED =
            new JsonLimits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int maxDocumentChars;
    private final int maxDepth;
    private final int maxContainerEntries;
    private final int maxStringLength;

    private JsonLimits(int maxDocumentChars, int maxDepth, int maxContainerEntries, int maxStringLength) {
        this.maxDocumentChars = requirePositive(maxDocumentChars, "maxDocumentChars");
        this.maxDepth = requirePositive(maxDepth, "maxDepth");
        this.maxContainerEntries = requirePositive(maxContainerEntries, "maxContainerEntries");
        this.maxStringLength = requirePositive(maxStringLength, "maxStringLength");
    }

    /** Maximum size of the whole document, in characters. Default 1 MiB. */
    public int maxDocumentChars() {
        return maxDocumentChars;
    }

    /** Maximum nesting depth of objects and arrays. Default 64. */
    public int maxDepth() {
        return maxDepth;
    }

    /** Maximum number of members in one object, or elements in one array. Default 4096. */
    public int maxContainerEntries() {
        return maxContainerEntries;
    }

    /** Maximum length of a single string value or key, in characters. Default 65536. */
    public int maxStringLength() {
        return maxStringLength;
    }

    /** Starts from {@link #DEFAULT}; every bound can be overridden. */
    public static Builder builder() {
        return new Builder(DEFAULT);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    /** Mutable builder for {@link JsonLimits}. */
    public static final class Builder {

        private int maxDocumentChars;
        private int maxDepth;
        private int maxContainerEntries;
        private int maxStringLength;

        Builder(JsonLimits template) {
            this.maxDocumentChars = template.maxDocumentChars;
            this.maxDepth = template.maxDepth;
            this.maxContainerEntries = template.maxContainerEntries;
            this.maxStringLength = template.maxStringLength;
        }

        /** Sets the maximum document size in characters. */
        public Builder maxDocumentChars(int value) {
            this.maxDocumentChars = value;
            return this;
        }

        /** Sets the maximum nesting depth. */
        public Builder maxDepth(int value) {
            this.maxDepth = value;
            return this;
        }

        /** Sets the maximum number of entries per object or array. */
        public Builder maxContainerEntries(int value) {
            this.maxContainerEntries = value;
            return this;
        }

        /** Sets the maximum length of a single string. */
        public Builder maxStringLength(int value) {
            this.maxStringLength = value;
            return this;
        }

        /** Builds the immutable limits. */
        public JsonLimits build() {
            return new JsonLimits(maxDocumentChars, maxDepth, maxContainerEntries, maxStringLength);
        }
    }
}
