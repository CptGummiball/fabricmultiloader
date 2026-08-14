package dev.fabricmultiloader.api.command;

import dev.fabricmultiloader.format.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A command as a tree of literals and arguments.
 *
 * <p>Brigadier itself is stable across every supported Minecraft version, so this is a convenience
 * layer rather than a compatibility one: it keeps common code free of {@code ServerCommandSource},
 * whose feedback method gained a {@code Supplier} parameter in 1.20, and it makes the permission
 * level explicit instead of a {@code requires} lambda that is easy to forget.
 *
 * <pre>
 * CommandSpec.named("ruby")
 *     .sub(CommandSpec.named("charge")
 *         .arg("amount", Arg.integer(1, 100))
 *         .permissionLevel(2)
 *         .executes(inv -&gt; { … return 1; })
 *         .build())
 *     .build();
 * </pre>
 */
public final class CommandSpec {

    private final String literal;
    private final int permissionLevel;
    private final Map<String, Arg<?>> arguments;
    private final List<CommandSpec> children;
    private final Function<CommandInvocation, Integer> body;
    private final Side onlyOn;

    private CommandSpec(Builder builder) {
        this.literal = builder.literal;
        this.permissionLevel = builder.permissionLevel;
        this.arguments = Collections.unmodifiableMap(
                new LinkedHashMap<String, Arg<?>>(builder.arguments));
        this.children = Collections.unmodifiableList(new ArrayList<CommandSpec>(builder.children));
        this.body = builder.body;
        this.onlyOn = builder.onlyOn;
    }

    /** Starts a command or sub-command with the given literal. */
    public static Builder named(String literal) {
        return new Builder(literal);
    }

    /** The literal that selects this node. */
    public String literal() {
        return literal;
    }

    /** Required permission level, 0–4. */
    public int permissionLevel() {
        return permissionLevel;
    }

    /** Arguments, in declaration order — which is also the order they are typed. */
    public Map<String, Arg<?>> arguments() {
        return arguments;
    }

    /** Sub-commands. */
    public List<CommandSpec> children() {
        return children;
    }

    /** What the command does, or {@code null} if this node only groups sub-commands. */
    public Function<CommandInvocation, Integer> body() {
        return body;
    }

    /** The side this command is registered on, or {@code null} for both. */
    public Side onlyOn() {
        return onlyOn;
    }

    @Override
    public String toString() {
        return "/" + literal + (arguments.isEmpty() ? "" : " " + arguments.keySet());
    }

    /** Mutable builder. */
    public static final class Builder {

        private final String literal;
        private int permissionLevel;
        private final Map<String, Arg<?>> arguments = new LinkedHashMap<String, Arg<?>>();
        private final List<CommandSpec> children = new ArrayList<CommandSpec>();
        private Function<CommandInvocation, Integer> body;
        private Side onlyOn;

        Builder(String literal) {
            if (literal == null || literal.isEmpty()) {
                throw new IllegalArgumentException("a command literal must not be empty");
            }
            this.literal = literal;
        }

        /**
         * Requires a permission level.
         *
         * @param level 0 for everyone, 2 for the usual "operator command", 4 for server management
         */
        public Builder permissionLevel(int level) {
            if (level < 0 || level > 4) {
                throw new IllegalArgumentException("permission level must be 0-4, got " + level);
            }
            this.permissionLevel = level;
            return this;
        }

        /**
         * Adds an argument.
         *
         * @param name how {@link CommandInvocation#arg} refers to it
         * @param type how it is parsed
         */
        public Builder arg(String name, Arg<?> type) {
            if (arguments.containsKey(name)) {
                throw new IllegalArgumentException("duplicate argument name '" + name + "'");
            }
            arguments.put(name, type);
            return this;
        }

        /** Adds a sub-command. */
        public Builder sub(CommandSpec child) {
            children.add(child);
            return this;
        }

        /**
         * Sets what the command does.
         *
         * @param action returns the Brigadier result count; {@code 1} means success, {@code 0}
         *     means the command did nothing, which matters for command blocks and comparators
         */
        public Builder executes(Function<CommandInvocation, Integer> action) {
            this.body = action;
            return this;
        }

        /** Registers the command on one side only. */
        public Builder onlyOn(Side side) {
            this.onlyOn = side;
            return this;
        }

        /** Builds the immutable specification. */
        public CommandSpec build() {
            if (body == null && children.isEmpty()) {
                throw new IllegalStateException(
                        "command '" + literal + "' has neither an action nor sub-commands");
            }
            return new CommandSpec(this);
        }
    }
}
