package dev.fabricmultiloader.format;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Locale;

/**
 * The <em>physical</em> side a mod is running on: a client distribution or a dedicated server.
 *
 * <p>This is not the logical side. A single-player game runs an integrated server inside a
 * {@link #CLIENT} distribution and still reports {@code CLIENT}, because the distinction that
 * matters for payload selection is which classes exist at all — {@code net.minecraft.client} is
 * absent from a dedicated server jar, and Fabric refuses to load a {@code client} mod there before
 * any class is touched.
 *
 * <p>Deliberately lives in {@code format} rather than {@code api}, even though mod authors see it
 * through {@code ModContext#side()}: the payload matcher needs it, the matcher is shared between
 * the runtime and the validator, and two identically named enums in two packages of one project is
 * a reliable source of wrong imports. Mirrors Fabric's {@code net.fabricmc.api.EnvType} without
 * depending on it.
 */
public enum Side {

    /** A client distribution — {@code net.minecraft.client} classes exist. */
    CLIENT("client"),

    /** A dedicated server — client classes are absent and must never be referenced. */
    SERVER("server");

    private final String id;

    Side(String id) {
        this.id = id;
    }

    /** The lower-case identifier used in metadata: {@code "client"} or {@code "server"}. */
    public String id() {
        return id;
    }

    /** Returns {@code true} for {@link #CLIENT}. */
    public boolean isClient() {
        return this == CLIENT;
    }

    /** Returns {@code true} for {@link #SERVER}. */
    public boolean isServer() {
        return this == SERVER;
    }

    /**
     * Parses {@code "client"} or {@code "server"}.
     *
     * @param value case-insensitive side identifier
     * @return the side
     * @throws OmniException {@code OMNI-3004} if the value is neither
     */
    public static Side parse(String value) {
        Side side = parseOrNull(value);
        if (side == null) {
            throw new OmniException(ErrorCode.OMNI_3004, Messages.report(ErrorCode.OMNI_3004)
                    .detected("value", value == null ? "(null)" : "\"" + value + "\"")
                    .detail("Expected \"client\" or \"server\".")
                    .fix("use one of the two documented side identifiers")
                    .build());
        }
        return side;
    }

    /** Parses, returning {@code null} instead of throwing. */
    public static Side parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if (CLIENT.id.equals(normalised)) {
            return CLIENT;
        }
        return SERVER.id.equals(normalised) ? SERVER : null;
    }
}
