package dev.fabricmultiloader.api.ref;

import dev.fabricmultiloader.api.text.Text;
import dev.fabricmultiloader.format.Side;
import java.util.UUID;

/** A handle to a player. */
@dev.fabricmultiloader.api.ImplementedByFramework
public interface PlayerRef extends Unwrappable {

    /** The player's stable identifier. Prefer this over the name for anything persisted. */
    UUID uuid();

    /** The player's current name. */
    String name();

    /** Which side this handle came from. */
    Side side();

    /** The world the player is in. */
    WorldRef world();

    /** Player x coordinate. */
    double x();

    /** Player y coordinate. */
    double y();

    /** Player z coordinate. */
    double z();

    /** The block position the player occupies. */
    BlockPosRef blockPosition();

    /**
     * Whether the player has at least the given permission level (0–4, vanilla's operator scale).
     *
     * <p>Adapters route this through a permission API when one is installed, so a server using
     * LuckPerms behaves as its administrator expects rather than falling back to operator levels.
     */
    boolean hasPermission(int level);

    /** The stack in the player's main hand. */
    ItemStackRef mainHandItem();

    /** Sends a plain chat message. */
    void sendMessage(String plainText);

    /** Sends a formatted chat message. */
    void sendMessage(Text text);

    /** Sends a message to the action bar above the hotbar. */
    void sendActionBar(Text text);
}
