package dev.fabricmultiloader.api.command;

import dev.fabricmultiloader.api.ImplementedByMod;

/**
 * Registers commands.
 *
 * <p>Register during initialisation. The adapter defers the actual Brigadier registration until
 * Minecraft fires its command registration event, which happens per world load — so a command
 * declared once here is correctly re-registered every time, without the mod having to know that.
 */
@ImplementedByMod
public interface Commands {

    /**
     * Registers a command tree.
     *
     * @param spec the command
     */
    void register(CommandSpec spec);
}
