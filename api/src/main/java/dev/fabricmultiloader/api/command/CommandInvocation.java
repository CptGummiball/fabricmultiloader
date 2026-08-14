package dev.fabricmultiloader.api.command;

import dev.fabricmultiloader.api.ImplementedByFramework;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import dev.fabricmultiloader.api.text.Text;
import java.util.Optional;

/** One execution of a command. */
@ImplementedByFramework
public interface CommandInvocation {

    /**
     * A parsed argument.
     *
     * @param name the argument name from the specification
     * @param type the expected type
     * @param <T> the value type
     * @return the value
     * @throws dev.fabricmultiloader.format.error.OmniApiMisuseException {@code OMNI-4012} if the
     *     name is unknown or the type does not match — both are mod bugs, caught on first execution
     */
    <T> T arg(String name, Class<T> type);

    /** An optional argument, absent when it was not supplied. */
    <T> Optional<T> optionalArg(String name, Class<T> type);

    /**
     * The player who ran the command, absent for the console, a command block or a data pack
     * function. Always handle the absent case: server owners run mod commands from the console.
     */
    Optional<PlayerRef> player();

    /** The world the command ran in. */
    WorldRef world();

    /** Who ran the command. */
    CommandSender sender();

    /** The permission level of whoever ran it, 0–4. */
    int permissionLevel();

    /** Replies to the caller only. */
    void reply(String plainText);

    /** Replies to the caller only, formatted. */
    void reply(Text text);

    /**
     * Replies to the caller and, if the server is configured for it, tells operators too.
     *
     * <p>The right choice for anything that changes state: it is how vanilla reports administrative
     * actions, and it respects {@code sendCommandFeedback}.
     */
    void broadcast(Text text);

    /** Reports a failure. Renders in red and marks the command as failed. */
    void fail(Text text);
}
