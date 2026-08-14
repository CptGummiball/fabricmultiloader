package dev.fabricmultiloader.format.manifest;

/**
 * One mixin configuration of a payload, with the side it is registered for.
 *
 * <p>The {@code environment} here is the more effective of the two available filters: it stops
 * Fabric from registering the config at all on the wrong side, so Mixin never reads the classes.
 * A config's own {@code "client": [...]} list is a second layer, but not a substitute — Mixin
 * parses and resolves every class of a registered config, and a client mixin resolved on a
 * dedicated server fails on a missing {@code net.minecraft.client} target.
 *
 * <p>Mirrored into the manifest purely so the validator can compare it against the payload's own
 * {@code fabric.mod.json} ({@code OMNI-1011}); the loader reads only the latter.
 */
public final class MixinConfigRef {

    private final String config;
    private final EnvironmentConstraint environment;

    /**
     * @param config the config file name inside the payload, e.g. {@code examplemod-mc1214.mixins.json}
     * @param environment the side the config is registered for
     */
    public MixinConfigRef(String config, EnvironmentConstraint environment) {
        this.config = SafePaths.requireRelativePath(config, "mixins[].config");
        this.environment = environment == null ? EnvironmentConstraint.BOTH : environment;
    }

    /** The config file name inside the payload. */
    public String config() {
        return config;
    }

    /** The side this config is registered for. */
    public EnvironmentConstraint environment() {
        return environment;
    }

    /** Whether this config is registered on both sides. */
    public boolean isUniversal() {
        return environment == EnvironmentConstraint.BOTH;
    }

    @Override
    public String toString() {
        return isUniversal() ? config : config + " (" + environment.id() + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MixinConfigRef)) {
            return false;
        }
        MixinConfigRef that = (MixinConfigRef) other;
        return config.equals(that.config) && environment == that.environment;
    }

    @Override
    public int hashCode() {
        return config.hashCode() * 31 + environment.hashCode();
    }
}
