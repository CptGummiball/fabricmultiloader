package dev.fabricmultiloader.format.manifest;

/**
 * Which mappings a payload was built against.
 *
 * <p>Documentation and validation rather than runtime behaviour: the namespace must be
 * {@code intermediary} in a released payload, and an access widener whose header says otherwise is
 * a hard loader failure at startup ({@code OMNI-1082}). Recording the provider and build also makes
 * a mixed matrix — Yarn for one version, Mojang mappings for another — reviewable, which is
 * legitimate precisely because payloads share no bytecode.
 */
public final class MappingsInfo {

    /** The namespace of a released payload. */
    public static final String INTERMEDIARY = "intermediary";

    /** The namespace of a payload inside a development runtime. */
    public static final String NAMED = "named";

    private final String namespace;
    private final String provider;
    private final String build;

    /**
     * @param namespace {@code "intermediary"} in a release, {@code "named"} in a dev run
     * @param provider {@code "yarn"}, {@code "mojang"}, {@code "parchment"}, {@code "layered"}
     * @param build the provider's build identifier, e.g. {@code "1.21.4+build.8"}
     */
    public MappingsInfo(String namespace, String provider, String build) {
        this.namespace = require(namespace, "mappings.namespace");
        this.provider = require(provider, "mappings.provider");
        this.build = build == null ? "" : build;
    }

    /** {@code intermediary} for a released payload. */
    public String namespace() {
        return namespace;
    }

    /** The mapping provider. */
    public String provider() {
        return provider;
    }

    /** The provider's build identifier; may be empty. */
    public String build() {
        return build;
    }

    /** Whether this payload is in the namespace a release must use. */
    public boolean isIntermediary() {
        return INTERMEDIARY.equals(namespace);
    }

    private static String require(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return value;
    }

    @Override
    public String toString() {
        return provider + (build.isEmpty() ? "" : " " + build) + " (" + namespace + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MappingsInfo)) {
            return false;
        }
        MappingsInfo that = (MappingsInfo) other;
        return namespace.equals(that.namespace) && provider.equals(that.provider)
                && build.equals(that.build);
    }

    @Override
    public int hashCode() {
        return (namespace.hashCode() * 31 + provider.hashCode()) * 31 + build.hashCode();
    }
}
