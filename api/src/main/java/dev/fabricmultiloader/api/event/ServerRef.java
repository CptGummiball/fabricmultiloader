package dev.fabricmultiloader.api.event;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ImplementedByFramework;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.Unwrappable;
import dev.fabricmultiloader.api.ref.WorldRef;
import dev.fabricmultiloader.api.text.Text;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A handle to the running server, integrated or dedicated. */
@ImplementedByFramework
public interface ServerRef extends Unwrappable {

    /** Ticks since the server started. */
    long tickCount();

    /** Every connected player. */
    List<PlayerRef> players();

    /** A player by name, if online. */
    Optional<PlayerRef> player(String name);

    /** A world by dimension identifier. */
    Optional<WorldRef> world(Id dimension);

    /** The overworld. */
    WorldRef overworld();

    /** Whether this is a dedicated server rather than a single-player integrated one. */
    boolean isDedicated();

    /** The world save directory — the right place for per-world mod data. */
    Path worldDirectory();

    /** Sends a message to every connected player. */
    void broadcast(Text message);

    /**
     * Runs a task on the server thread.
     *
     * <p>Needed for anything arriving off-thread. The framework already hops threads for network
     * receivers, so this is for a mod's own background work.
     */
    void execute(Runnable task);
}
