package dev.fabricmultiloader.format.error;

/**
 * Signals a programming error in mod code rather than a defect in the framework or a problem with
 * the user's environment — the 4xxx code range.
 *
 * <p>The distinction matters for triage. A {@link OmniException} with a 2xxx code usually means
 * "the user has to change something"; a 4xxx code means "the mod author has to change something",
 * and no amount of reinstalling will help. Keeping them apart as types lets crash reporters and
 * modpack tooling route them differently.
 */
public final class OmniApiMisuseException extends OmniException {

    private static final long serialVersionUID = 1L;

    /**
     * @param code must be a 4xxx code
     * @param report the rendered report body; may be {@code null}
     */
    public OmniApiMisuseException(ErrorCode code, String report) {
        super(requireApiCode(code), report);
    }

    /**
     * @param code must be a 4xxx code
     * @param report the rendered report body; may be {@code null}
     * @param cause the underlying failure
     */
    public OmniApiMisuseException(ErrorCode code, String report, Throwable cause) {
        super(requireApiCode(code), report, cause);
    }

    private static ErrorCode requireApiCode(ErrorCode code) {
        if (code == null) {
            throw new IllegalArgumentException("error code must not be null");
        }
        if (code.category() != ErrorCode.Category.API) {
            throw new IllegalArgumentException(
                    "OmniApiMisuseException requires a 4xxx code, got " + code.id()
                            + " (" + code.category() + ")");
        }
        return code;
    }
}
