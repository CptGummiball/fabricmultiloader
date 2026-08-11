package dev.fabricmultiloader.format.error;

/**
 * How a diagnostic affects the outcome of a build or a game launch.
 *
 * <p>The distinction is deliberate and load-bearing: a {@link #WARNING} must never block a build
 * by default, because some of them describe legitimate situations (an access widener entry that
 * only applies to one Minecraft version, for instance). Projects that want zero tolerance opt in
 * with {@code validation { failOnWarnings.set(true) }}.
 */
public enum Severity {

    /** Aborts the build or the launch. The artifact is wrong, not merely suspicious. */
    ERROR,

    /** Reported and counted, but does not abort unless the project opts into strictness. */
    WARNING,

    /** Purely informational — for example a gap in Minecraft version coverage. */
    INFO
}
