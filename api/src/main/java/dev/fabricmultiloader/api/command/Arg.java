package dev.fabricmultiloader.api.command;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ref.PlayerRef;

/**
 * A typed command argument.
 *
 * <p>Deliberately a small fixed set rather than a way to plug in Brigadier argument types.
 * Brigadier itself is stable across supported versions, but its Minecraft-provided argument types
 * are not — several were renamed or gained a registry-access parameter — so exposing them would put
 * a version-specific type in a common signature. What is here covers the arguments a mod command
 * realistically takes; anything else belongs behind a service.
 *
 * @param <T> the parsed value type
 */
public final class Arg<T> {

    /** How a value is parsed and suggested. */
    public enum Kind {
        /** A bounded integer. */
        INTEGER,
        /** A bounded floating-point number. */
        DECIMAL,
        /** A single word. */
        WORD,
        /** A quoted or unquoted string. */
        STRING,
        /** The rest of the line, spaces included. Only valid as the last argument. */
        GREEDY_STRING,
        /** A boolean. */
        BOOLEAN,
        /** An online player, with name completion. */
        PLAYER,
        /** A namespaced identifier. */
        IDENTIFIER
    }

    private final Kind kind;
    private final Class<T> type;
    private final double min;
    private final double max;

    private Arg(Kind kind, Class<T> type, double min, double max) {
        this.kind = kind;
        this.type = type;
        this.min = min;
        this.max = max;
    }

    /** An integer between the given bounds, inclusive. */
    public static Arg<Integer> integer(int min, int max) {
        return new Arg<Integer>(Kind.INTEGER, Integer.class, min, max);
    }

    /** Any integer. */
    public static Arg<Integer> integer() {
        return integer(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** A decimal between the given bounds, inclusive. */
    public static Arg<Double> decimal(double min, double max) {
        return new Arg<Double>(Kind.DECIMAL, Double.class, min, max);
    }

    /** A single word, no spaces. */
    public static Arg<String> word() {
        return new Arg<String>(Kind.WORD, String.class, 0, 0);
    }

    /** A string, quoted if it contains spaces. */
    public static Arg<String> string() {
        return new Arg<String>(Kind.STRING, String.class, 0, 0);
    }

    /** The rest of the command line. Only valid as the final argument. */
    public static Arg<String> greedyString() {
        return new Arg<String>(Kind.GREEDY_STRING, String.class, 0, 0);
    }

    /** A boolean. */
    public static Arg<Boolean> bool() {
        return new Arg<Boolean>(Kind.BOOLEAN, Boolean.class, 0, 0);
    }

    /** An online player. */
    public static Arg<PlayerRef> player() {
        return new Arg<PlayerRef>(Kind.PLAYER, PlayerRef.class, 0, 0);
    }

    /** A namespaced identifier. */
    public static Arg<Id> identifier() {
        return new Arg<Id>(Kind.IDENTIFIER, Id.class, 0, 0);
    }

    /** How the value is parsed. */
    public Kind kind() {
        return kind;
    }

    /** The parsed value's type. */
    public Class<T> type() {
        return type;
    }

    /** Lower bound for numeric kinds. */
    public double min() {
        return min;
    }

    /** Upper bound for numeric kinds. */
    public double max() {
        return max;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }
}
