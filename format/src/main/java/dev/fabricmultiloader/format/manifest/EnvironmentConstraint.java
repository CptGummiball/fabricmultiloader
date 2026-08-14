package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Locale;

/**
 * Which physical sides a payload accepts — Fabric's {@code environment} field.
 *
 * <p>Part of a payload's selection domain, alongside the Minecraft and Java ranges, because the
 * loader evaluates it <em>before</em> classloading: a {@code client} payload is not extracted at
 * all on a dedicated server, so its client-only mixins and assets never exist there. That makes it
 * a genuine dimension of the disjointness proof rather than a runtime filter — two payloads may
 * share a Minecraft range if one is client-only and the other server-only.
 */
public enum EnvironmentConstraint {

    /** Both sides. */
    BOTH("*"),

    /** Client distributions only. */
    CLIENT("client"),

    /** Dedicated servers only. */
    SERVER("server");

    private final String id;

    EnvironmentConstraint(String id) {
        this.id = id;
    }

    /** The value as written in metadata: {@code "*"}, {@code "client"} or {@code "server"}. */
    public String id() {
        return id;
    }

    /** Whether a payload with this constraint runs on the given side. */
    public boolean accepts(Side side) {
        if (side == null) {
            return false;
        }
        switch (this) {
            case CLIENT:
                return side.isClient();
            case SERVER:
                return side.isServer();
            case BOTH:
            default:
                return true;
        }
    }

    /** Whether two constraints share at least one side — the disjointness question. */
    public boolean intersects(EnvironmentConstraint other) {
        return accepts(Side.CLIENT) && other.accepts(Side.CLIENT)
                || accepts(Side.SERVER) && other.accepts(Side.SERVER);
    }

    /**
     * This constraint minus the other, or {@code null} if nothing remains.
     *
     * <p>Used by the domain subtraction: a lower-priority payload keeps only the sides a
     * higher-priority one has not already claimed.
     */
    public EnvironmentConstraint subtract(EnvironmentConstraint other) {
        boolean client = accepts(Side.CLIENT) && !other.accepts(Side.CLIENT);
        boolean server = accepts(Side.SERVER) && !other.accepts(Side.SERVER);
        if (client && server) {
            return BOTH;
        }
        if (client) {
            return CLIENT;
        }
        return server ? SERVER : null;
    }

    /**
     * Parses {@code "*"}, {@code "client"} or {@code "server"}.
     *
     * @throws OmniException {@code OMNI-3004} for anything else
     */
    public static EnvironmentConstraint parse(String value, String field) {
        if (value != null) {
            String normalised = value.trim().toLowerCase(Locale.ROOT);
            for (EnvironmentConstraint constraint : values()) {
                if (constraint.id.equals(normalised)) {
                    return constraint;
                }
            }
        }
        throw new OmniException(ErrorCode.OMNI_3004, Messages.report(ErrorCode.OMNI_3004)
                .detected("field", field)
                .detected("value", value == null ? "(null)" : "\"" + value + "\"")
                .detail("Expected \"*\", \"client\" or \"server\".")
                .fix("set environment to one of the three documented values")
                .build());
    }

    @Override
    public String toString() {
        return id;
    }
}
