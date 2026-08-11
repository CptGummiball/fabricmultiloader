package dev.fabricmultiloader.format.json;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;

/**
 * A malformed, mistyped, incomplete or oversized JSON document.
 *
 * <p>Always carries a {@link JsonLocation}, so a caller can point at the offending line and at the
 * logical path at the same time. That combination is what makes a generated 400-line manifest
 * debuggable at all.
 */
public final class JsonFormatException extends OmniException {

    private static final long serialVersionUID = 1L;

    private final JsonLocation location;

    /**
     * @param code one of the 3xxx format codes
     * @param report the rendered report
     * @param location where the problem is; never {@code null}
     */
    public JsonFormatException(ErrorCode code, String report, JsonLocation location) {
        super(code, report);
        this.location = location == null ? JsonLocation.UNKNOWN : location;
    }

    /**
     * @param code one of the 3xxx format codes
     * @param report the rendered report
     * @param location where the problem is
     * @param cause the underlying failure, e.g. a {@link NumberFormatException}
     */
    public JsonFormatException(ErrorCode code, String report, JsonLocation location, Throwable cause) {
        super(code, report, cause);
        this.location = location == null ? JsonLocation.UNKNOWN : location;
    }

    /** Where the problem was found. */
    public JsonLocation location() {
        return location;
    }

    /** Shorthand for {@code location().pointer()}. */
    public String pointer() {
        return location.pointer();
    }
}
