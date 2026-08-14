package dev.fabricmultiloader.runtime.adapter;

import dev.fabricmultiloader.api.text.Text;

/**
 * Sending text back from a command — the one signature in this area that genuinely diverged.
 *
 * <p>Until Minecraft 1.19 the call was {@code ServerCommandSource#sendFeedback(Text, boolean)}; from
 * 1.20 it takes a {@code Supplier<Text>} so the message is only built when someone will see it.
 * Broadcasting to operators and reporting a failure differ similarly. Three methods, implemented in
 * three lines per payload, is the whole cost of that divergence — and it keeps the rest of the
 * command layer, which is considerably larger, version-independent.
 *
 * <p>Supplied per invocation by the payload's command adapter and handed to
 * {@link CommandInvocationImpl}.
 */
public interface Feedback {

    /**
     * Replies to whoever ran the command.
     *
     * @param text the message
     */
    void reply(Text text);

    /**
     * Replies and additionally informs other operators, as vanilla commands do.
     *
     * @param text the message
     */
    void broadcast(Text text);

    /**
     * Reports a failure. The command's return value is ignored after this.
     *
     * @param text the message
     */
    void fail(Text text);
}
