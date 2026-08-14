package dev.fabricmultiloader.runtime.adapter;

import dev.fabricmultiloader.api.Id;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.event.EventKey;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.event.ServerRef;
import dev.fabricmultiloader.api.event.Subscription;
import dev.fabricmultiloader.api.ref.BlockPosRef;
import dev.fabricmultiloader.api.ref.PlayerRef;
import dev.fabricmultiloader.api.ref.WorldRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Subscriptions, dispatch order and failure containment — the part of an event system that has
 * nothing to do with Minecraft.
 *
 * <p>The payload subscribes to Fabric API's events and calls the {@code fire} methods here. What
 * this owns is everything that would otherwise be reimplemented per version and get subtly
 * different each time: handlers run in subscription order, a handler that unsubscribes during
 * dispatch does not disturb the iteration, and a handler that throws does not take the others — or
 * the tick loop — down with it.
 *
 * <p>That last point is the reason this class exists at all. {@code serverTick} fires twenty times a
 * second; a handler throwing there without containment produces either a crashed server or a log
 * file growing by megabytes a minute. Each failing handler is reported once, with the mod and the
 * handler class named, and then muted.
 */
public final class EventBus implements Events {

    private final String modId;
    private final ModLogger log;

    private final Slot<Consumer<ServerRef>> serverStarted = new Slot<Consumer<ServerRef>>();
    private final Slot<Consumer<ServerRef>> serverStopping = new Slot<Consumer<ServerRef>>();
    private final Slot<Consumer<ServerRef>> serverTick = new Slot<Consumer<ServerRef>>();
    private final Slot<Consumer<ModContext>> clientTick = new Slot<Consumer<ModContext>>();
    private final Slot<Consumer<PlayerRef>> playerJoin = new Slot<Consumer<PlayerRef>>();
    private final Slot<Consumer<PlayerRef>> playerLeave = new Slot<Consumer<PlayerRef>>();
    private final Slot<Consumer<WorldRef>> worldLoad = new Slot<Consumer<WorldRef>>();
    private final Slot<Consumer<ModContext>> dataReload = new Slot<Consumer<ModContext>>();
    private final Slot<BlockBreakHandler> blockBroken = new Slot<BlockBreakHandler>();

    private final Map<String, Slot<Consumer<?>>> custom =
            new ConcurrentHashMap<String, Slot<Consumer<?>>>();
    private final Set<String> muted =
            Collections.synchronizedSet(new LinkedHashSet<String>());

    /**
     * @param modId the mod these subscriptions belong to
     * @param log the mod's logger
     */
    public EventBus(String modId, ModLogger log) {
        this.modId = modId;
        this.log = log;
    }

    // ------------------------------------------------------------------ subscription

    @Override
    public Subscription serverStarted(Consumer<ServerRef> handler) {
        return serverStarted.add(handler);
    }

    @Override
    public Subscription serverStopping(Consumer<ServerRef> handler) {
        return serverStopping.add(handler);
    }

    @Override
    public Subscription serverTick(Consumer<ServerRef> handler) {
        return serverTick.add(handler);
    }

    @Override
    public Subscription clientTick(Consumer<ModContext> handler) {
        return clientTick.add(handler);
    }

    @Override
    public Subscription playerJoin(Consumer<PlayerRef> handler) {
        return playerJoin.add(handler);
    }

    @Override
    public Subscription playerLeave(Consumer<PlayerRef> handler) {
        return playerLeave.add(handler);
    }

    @Override
    public Subscription worldLoad(Consumer<WorldRef> handler) {
        return worldLoad.add(handler);
    }

    @Override
    public Subscription dataReload(Consumer<ModContext> handler) {
        return dataReload.add(handler);
    }

    @Override
    public Subscription blockBroken(BlockBreakHandler handler) {
        return blockBroken.add(handler);
    }

    @Override
    public <T> Subscription custom(EventKey<T> key, Consumer<T> handler) {
        if (key == null) {
            throw new IllegalArgumentException("event key must not be null");
        }
        return slotFor(key).add(cast(handler));
    }

    // ------------------------------------------------------------------ dispatch

    /** Fires {@code serverStarted}. */
    public void fireServerStarted(ServerRef server) {
        dispatch("serverStarted", serverStarted, server);
    }

    /** Fires {@code serverStopping}. */
    public void fireServerStopping(ServerRef server) {
        dispatch("serverStopping", serverStopping, server);
    }

    /** Fires {@code serverTick}. */
    public void fireServerTick(ServerRef server) {
        dispatch("serverTick", serverTick, server);
    }

    /** Fires {@code clientTick}. */
    public void fireClientTick(ModContext ctx) {
        dispatch("clientTick", clientTick, ctx);
    }

    /** Fires {@code playerJoin}. */
    public void firePlayerJoin(PlayerRef player) {
        dispatch("playerJoin", playerJoin, player);
    }

    /** Fires {@code playerLeave}. */
    public void firePlayerLeave(PlayerRef player) {
        dispatch("playerLeave", playerLeave, player);
    }

    /** Fires {@code worldLoad}. */
    public void fireWorldLoad(WorldRef world) {
        dispatch("worldLoad", worldLoad, world);
    }

    /** Fires {@code dataReload}. */
    public void fireDataReload(ModContext ctx) {
        dispatch("dataReload", dataReload, ctx);
    }

    /**
     * Fires {@code blockBroken}.
     *
     * @return {@code false} if any handler vetoed the break
     */
    public boolean fireBlockBroken(WorldRef world, PlayerRef player, BlockPosRef position, Id block) {
        boolean allowed = true;
        for (BlockBreakHandler handler : blockBroken.handlers()) {
            try {
                if (!handler.onBlockBreak(world, player, position, block)) {
                    // Every handler still runs: one of them vetoing is not a reason for the others
                    // to be skipped, and a handler that only observes would otherwise see a
                    // different set of events depending on subscription order.
                    allowed = false;
                }
            } catch (Throwable thrown) {
                // Fail open. A mod bug making the world unbreakable is a far worse outcome than the
                // same bug letting a block break that should have been protected.
                report("blockBroken", handler, thrown);
            }
        }
        return allowed;
    }

    /**
     * Fires a mod-defined event.
     *
     * @param key the event
     * @param payload the value handed to each handler
     * @param <T> the payload type
     */
    public <T> void fireCustom(EventKey<T> key, T payload) {
        if (key == null) {
            return;
        }
        Slot<Consumer<?>> slot = custom.get(key.id());
        if (slot == null) {
            return;
        }
        for (Consumer<?> handler : slot.handlers()) {
            try {
                EventBus.<T>cast(handler).accept(payload);
            } catch (Throwable thrown) {
                report(key.id(), handler, thrown);
            }
        }
    }

    /** How many handlers an event currently has. Used by tests and the diagnostic report. */
    public int subscriberCount(String eventName) {
        Slot<?> slot = slotByName(eventName);
        return slot == null ? 0 : slot.handlers().size();
    }

    /** Every event with at least one subscriber, for the diagnostic report. */
    public List<String> activeEvents() {
        List<String> active = new ArrayList<String>();
        for (String name : new String[] {"serverStarted", "serverStopping", "serverTick",
                "clientTick", "playerJoin", "playerLeave", "worldLoad", "dataReload",
                "blockBroken"}) {
            if (subscriberCount(name) > 0) {
                active.add(name);
            }
        }
        for (Map.Entry<String, Slot<Consumer<?>>> entry : custom.entrySet()) {
            if (!entry.getValue().handlers().isEmpty()) {
                active.add(entry.getKey());
            }
        }
        Collections.sort(active);
        return Collections.unmodifiableList(active);
    }

    private <T> void dispatch(String eventName, Slot<Consumer<T>> slot, T payload) {
        for (Consumer<T> handler : slot.handlers()) {
            try {
                handler.accept(payload);
            } catch (Throwable thrown) {
                report(eventName, handler, thrown);
            }
        }
    }

    /**
     * A per-tick event with a broken handler would otherwise fill a log file faster than anyone
     * could read it, so each handler is reported once and then muted. The first report carries the
     * stack trace, which is the only part anyone needs.
     */
    private void report(String eventName, Object handler, Throwable thrown) {
        String key = eventName + "#" + handler.getClass().getName();
        if (!muted.add(key)) {
            return;
        }
        log.error("{}: handler {} threw on event '{}' and was muted for the rest of this session. "
                        + "Other handlers are unaffected. This is a bug in {}.",
                thrown, modId, handler.getClass().getName(), eventName, modId);
    }

    private Slot<?> slotByName(String eventName) {
        if ("serverStarted".equals(eventName)) {
            return serverStarted;
        }
        if ("serverStopping".equals(eventName)) {
            return serverStopping;
        }
        if ("serverTick".equals(eventName)) {
            return serverTick;
        }
        if ("clientTick".equals(eventName)) {
            return clientTick;
        }
        if ("playerJoin".equals(eventName)) {
            return playerJoin;
        }
        if ("playerLeave".equals(eventName)) {
            return playerLeave;
        }
        if ("worldLoad".equals(eventName)) {
            return worldLoad;
        }
        if ("dataReload".equals(eventName)) {
            return dataReload;
        }
        if ("blockBroken".equals(eventName)) {
            return blockBroken;
        }
        return custom.get(eventName);
    }

    private Slot<Consumer<?>> slotFor(EventKey<?> key) {
        Slot<Consumer<?>> existing = custom.get(key.id());
        if (existing != null) {
            return existing;
        }
        Slot<Consumer<?>> created = new Slot<Consumer<?>>();
        Slot<Consumer<?>> raced = custom.putIfAbsent(key.id(), created);
        return raced == null ? created : raced;
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T> cast(Consumer<?> handler) {
        // EventKey pairs an id with a type and Events#custom is generic in both, so the only way to
        // reach here with a mismatch is an unchecked cast in mod code.
        return (Consumer<T>) handler;
    }

    /**
     * One event's handlers.
     *
     * <p>Copy-on-write because dispatch is far more frequent than subscription and because a
     * handler unsubscribing itself mid-dispatch — which is how a one-shot subscription is written —
     * must not produce a {@code ConcurrentModificationException}.
     */
    private static final class Slot<H> {

        private final CopyOnWriteArrayList<H> handlers = new CopyOnWriteArrayList<H>();

        Subscription add(final H handler) {
            if (handler == null) {
                throw new IllegalArgumentException("event handler must not be null");
            }
            handlers.add(handler);
            return new Subscription() {
                @Override
                public void unsubscribe() {
                    handlers.remove(handler);
                }

                @Override
                public boolean isActive() {
                    return handlers.contains(handler);
                }
            };
        }

        List<H> handlers() {
            return handlers;
        }
    }
}
