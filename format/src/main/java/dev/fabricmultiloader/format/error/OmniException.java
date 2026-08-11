package dev.fabricmultiloader.format.error;

/**
 * The base exception for every FabricMultiLoader failure that carries an {@link ErrorCode}.
 *
 * <p>Unchecked on purpose: the bootstrap runs inside Fabric entrypoint signatures that do not
 * permit checked exceptions. The full report lives in {@link #getMessage()} rather than in a side
 * channel, because Fabric's {@code EntrypointUtils} passes exactly that string through into the
 * error GUI on a client and into the server log on a dedicated server. That is how a controlled
 * diagnostic reaches the user without touching a single loader-internal class.
 */
public class OmniException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final String report;

    /**
     * @param code the diagnostic code; must not be {@code null}
     * @param report the rendered report body, typically from {@link Messages}; may be {@code null}
     */
    public OmniException(ErrorCode code, String report) {
        super(buildMessage(code, report));
        this.code = requireCode(code);
        this.report = report;
    }

    /**
     * @param code the diagnostic code; must not be {@code null}
     * @param report the rendered report body; may be {@code null}
     * @param cause the underlying failure, preserved for stack traces
     */
    public OmniException(ErrorCode code, String report, Throwable cause) {
        super(buildMessage(code, report), cause);
        this.code = requireCode(code);
        this.report = report;
    }

    /** The diagnostic code, for programmatic handling and for tests. */
    public final ErrorCode code() {
        return code;
    }

    /** The rendered report body, or {@code null} if this exception carries only a code. */
    public final String report() {
        return report;
    }

    /** Whether this exception carries the given code. Convenient in assertions. */
    public final boolean is(ErrorCode other) {
        return code == other;
    }

    // Static: called from the constructor, so it must not touch instance state.
    private static String buildMessage(ErrorCode code, String report) {
        if (code == null) {
            return report == null ? "" : report;
        }
        if (report == null || report.isEmpty()) {
            return code.id() + "  " + code.title();
        }
        return report;
    }

    private static ErrorCode requireCode(ErrorCode code) {
        if (code == null) {
            throw new IllegalArgumentException("error code must not be null");
        }
        return code;
    }
}
