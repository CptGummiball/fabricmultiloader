package dev.fabricmultiloader.runtime.adapter;

import dev.fabricmultiloader.api.command.Arg;
import dev.fabricmultiloader.api.command.CommandInvocation;
import dev.fabricmultiloader.api.command.CommandSender;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import dev.fabricmultiloader.api.text.Text;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One command execution, assembled from what the payload extracted out of Brigadier.
 *
 * <p>The adapter pulls the argument values out of the {@code CommandContext} — the only step that
 * needs Brigadier — and everything after that is here: typed lookup, the distinction between a
 * missing and a wrongly-typed argument, permission reporting and the three feedback calls.
 *
 * <p>Argument type mismatches are worth a word. {@code arg("amount", String.class)} against an
 * integer argument is a mod bug that Brigadier would report as a {@code ClassCastException} from
 * inside a lambda, with a stack trace that names neither the command nor the argument. Here it
 * names both, plus the type that was actually declared.
 */
public final class CommandInvocationImpl implements CommandInvocation {

    private final CommandRegistry.Node node;
    private final Map<String, Object> values;
    private final CommandSender sender;
    private final int permissionLevel;
    private final PlayerRef player;
    private final WorldRef world;
    private final Feedback feedback;

    /**
     * @param node the command being run
     * @param values the parsed argument values, by name
     * @param sender who ran it
     * @param permissionLevel the sender's permission level
     * @param player the sender as a player, or {@code null} for console and command blocks
     * @param world the world the command ran in
     * @param feedback how to send text back
     */
    public CommandInvocationImpl(CommandRegistry.Node node, Map<String, Object> values,
            CommandSender sender, int permissionLevel, PlayerRef player, WorldRef world,
            Feedback feedback) {
        this.node = node;
        this.values = Collections.unmodifiableMap(
                new LinkedHashMap<String, Object>(values == null
                        ? Collections.<String, Object>emptyMap() : values));
        this.sender = sender;
        this.permissionLevel = permissionLevel;
        this.player = player;
        this.world = world;
        this.feedback = feedback;
    }

    @Override
    public <T> T arg(String name, Class<T> type) {
        Object value = require(name, type);
        if (value == null) {
            throw new IllegalArgumentException("command /" + node.path() + " has no argument '"
                    + name + "'; it declares " + node.arguments().keySet());
        }
        return type.cast(value);
    }

    @Override
    public <T> Optional<T> optionalArg(String name, Class<T> type) {
        Object value = require(name, type);
        return value == null ? Optional.<T>empty() : Optional.of(type.cast(value));
    }

    @Override
    public Optional<PlayerRef> player() {
        return Optional.ofNullable(player);
    }

    @Override
    public WorldRef world() {
        return world;
    }

    @Override
    public CommandSender sender() {
        return sender;
    }

    @Override
    public int permissionLevel() {
        return permissionLevel;
    }

    @Override
    public void reply(String plainText) {
        reply(Text.literal(plainText));
    }

    @Override
    public void reply(Text text) {
        feedback.reply(text);
    }

    @Override
    public void broadcast(Text text) {
        feedback.broadcast(text);
    }

    @Override
    public void fail(Text text) {
        feedback.fail(text);
    }

    @Override
    public String toString() {
        return "/" + node.path() + " by " + sender;
    }

    /**
     * Looks a value up and checks it against what the command declared, rather than against what
     * happens to be in the map — so a typo in the name and a wrong type give different messages.
     */
    private Object require(String name, Class<?> requested) {
        if (requested == null) {
            throw new IllegalArgumentException("argument type must not be null");
        }
        Arg<?> declared = node.arguments().get(name);
        if (declared == null) {
            return null;
        }
        if (!requested.isAssignableFrom(declared.type())) {
            throw new IllegalArgumentException("command /" + node.path() + " declares argument '"
                    + name + "' as " + declared.type().getSimpleName()
                    + ", but it was read as " + requested.getSimpleName());
        }
        Object value = values.get(name);
        if (value != null && !requested.isInstance(value)) {
            throw new IllegalArgumentException("command /" + node.path() + " received '" + name
                    + "' as " + value.getClass().getSimpleName()
                    + ", but it is declared " + declared.type().getSimpleName()
                    + " — the payload's command adapter extracted the wrong type");
        }
        return value;
    }
}
