package dev.fabricmultiloader.api.registry;

/**
 * What happened when an item was used.
 *
 * <p>Mirrors Minecraft's {@code ActionResult} closely enough that the mapping is obvious, without
 * inheriting the names that changed between versions ({@code ActionResult.CONSUME} gained
 * {@code CONSUME_PARTIAL}, {@code PASS} became {@code TryEmptyHandInteraction} in places). The
 * adapter maps these four onto whatever the running version calls them.
 */
public enum UseResult {

    /** The action succeeded; swing the arm and stop processing. */
    SUCCESS,

    /** The action succeeded but should not swing the arm — typical for consuming an item. */
    CONSUME,

    /** Nothing happened; let vanilla and other mods handle the interaction. */
    PASS,

    /** The action was refused; stop processing without a success animation. */
    FAIL;

    /** Whether the interaction was handled and processing should stop. */
    public boolean isHandled() {
        return this != PASS;
    }
}
