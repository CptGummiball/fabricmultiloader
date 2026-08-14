package dev.fabricmultiloader.api.command;

/** What ran a command. */
public enum CommandSender {

    /** A player, in chat. */
    PLAYER,

    /** The server console. */
    CONSOLE,

    /** A command block. */
    COMMAND_BLOCK,

    /** A data pack function or another automated source. */
    FUNCTION;

    /** Whether a player is behind this invocation. */
    public boolean isPlayer() {
        return this == PLAYER;
    }

    /**
     * Whether the source can see chat feedback.
     *
     * <p>A command block that reports success into chat for every redstone pulse is a well-known
     * way to make a server unusable, so anything chatty should check this first.
     */
    public boolean seesFeedback() {
        return this == PLAYER || this == CONSOLE;
    }
}
