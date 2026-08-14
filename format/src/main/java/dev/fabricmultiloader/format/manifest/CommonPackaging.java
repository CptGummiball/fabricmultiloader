package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import java.util.Locale;

/**
 * Where the mod's platform-neutral common code lives inside a universal jar.
 *
 * <p>{@link #SHARED} is the default and relies on a documented property of Fabric Loader: all mod
 * classes are defined by one {@code KnotClassLoader}, so a payload can see the container's classes
 * directly. {@link #EMBEDDED} exists because that property, while stable across loader 0.14–0.17,
 * is not a contract — per-mod class isolation has been discussed more than once. Switching is a
 * one-line matrix change rather than a redesign, and the mode is covered in CI so that it works
 * when it is needed instead of having to be repaired first (chapter 41.3).
 */
public enum CommonPackaging {

    /** Common classes live only in the container; payloads reference them. The default. */
    SHARED("shared"),

    /**
     * Common classes are copied into every payload and omitted from the container. Costs
     * {@code (payloads - 1) x} the size of common, and makes the mod's public API payload-bound.
     */
    EMBEDDED("embedded");

    private final String id;

    CommonPackaging(String id) {
        this.id = id;
    }

    /** The value as written in metadata. */
    public String id() {
        return id;
    }

    /**
     * Parses {@code "shared"} or {@code "embedded"}.
     *
     * @throws OmniException {@code OMNI-3004} for anything else
     */
    public static CommonPackaging parse(String value, String field) {
        if (value != null) {
            String normalised = value.trim().toLowerCase(Locale.ROOT);
            for (CommonPackaging packaging : values()) {
                if (packaging.id.equals(normalised)) {
                    return packaging;
                }
            }
        }
        throw new OmniException(ErrorCode.OMNI_3004, Messages.report(ErrorCode.OMNI_3004)
                .detected("field", field)
                .detected("value", value == null ? "(null)" : "\"" + value + "\"")
                .detail("Expected \"shared\" or \"embedded\".")
                .fix("set container.commonPackaging in gradle/fabricmultiloader.toml")
                .build());
    }

    @Override
    public String toString() {
        return id;
    }
}
