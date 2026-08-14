package dev.fabricmultiloader.format.payload;

/**
 * One reason a payload does not apply.
 *
 * <p>Deliberately a value object rather than a formatted string: the diagnostic report renders it,
 * the validator can group by {@link Constraint}, and a test can assert on the cause without
 * matching text. The distinction between {@link Constraint#MOD} with a version mismatch and one
 * with the mod absent matters to a user — "update Cloth Config" and "install Cloth Config" are
 * different instructions.
 */
public final class Rejection {

    /** Which kind of requirement failed. */
    public enum Constraint {
        /** {@code requires.minecraft} — a domain constraint. */
        MINECRAFT("Minecraft"),
        /** {@code requires.fabricloader} — a filter. */
        FABRIC_LOADER("Fabric Loader"),
        /** {@code requires.java} — a domain constraint. */
        JAVA("Java"),
        /** {@code requires.environment} — a domain constraint. */
        ENVIRONMENT("side"),
        /** {@code requires.mods} — a filter. */
        MOD("mod");

        private final String label;

        Constraint(String label) {
            this.label = label;
        }

        /** Human-readable name used in reports. */
        public String label() {
            return label;
        }
    }

    private final Constraint constraint;
    private final String subject;
    private final String expected;
    private final String actual;
    private final boolean missing;

    private Rejection(Constraint constraint, String subject, String expected, String actual,
            boolean missing) {
        this.constraint = constraint;
        this.subject = subject;
        this.expected = expected;
        this.actual = actual;
        this.missing = missing;
    }

    /** A constraint whose value is present but out of range. */
    public static Rejection of(Constraint constraint, String expected, String actual) {
        return new Rejection(constraint, constraint.label(), expected, actual, false);
    }

    /** A mod dependency whose installed version is out of range. */
    public static Rejection modVersion(String modId, String expected, String actual) {
        return new Rejection(Constraint.MOD, modId, expected, actual, false);
    }

    /** A mod dependency that is not installed at all. */
    public static Rejection modMissing(String modId, String expected) {
        return new Rejection(Constraint.MOD, modId, expected, "not installed", true);
    }

    /** Which requirement failed. */
    public Constraint constraint() {
        return constraint;
    }

    /** What the requirement is about — a mod id for {@link Constraint#MOD}, else the label. */
    public String subject() {
        return subject;
    }

    /** The declared requirement, rendered. */
    public String expected() {
        return expected;
    }

    /** What was actually found, rendered. */
    public String actual() {
        return actual;
    }

    /** Whether the subject was entirely absent rather than merely out of range. */
    public boolean isMissing() {
        return missing;
    }

    /** A one-line explanation, as it appears in a diagnostic report. */
    public String describe() {
        if (missing) {
            return subject + " " + expected + " — REJECTED: not installed";
        }
        return subject + " " + expected + " — REJECTED: " + actual + " found";
    }

    @Override
    public String toString() {
        return describe();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Rejection)) {
            return false;
        }
        Rejection that = (Rejection) other;
        return constraint == that.constraint && subject.equals(that.subject)
                && expected.equals(that.expected) && actual.equals(that.actual);
    }

    @Override
    public int hashCode() {
        int result = constraint.hashCode();
        result = 31 * result + subject.hashCode();
        result = 31 * result + expected.hashCode();
        return 31 * result + actual.hashCode();
    }
}
