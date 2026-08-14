package dev.fabricmultiloader.runtime.adapter;

import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.command.Arg;
import dev.fabricmultiloader.api.command.CommandInvocation;
import dev.fabricmultiloader.api.command.CommandSpec;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Collects the mod's commands and flattens them into something a Brigadier translation can walk.
 *
 * <p>Everything here is the part that would otherwise be written once per payload and drift: which
 * commands exist, which of them apply on this side, what the full path of each executable node is,
 * and whether two of them collide. What stays in the payload is the wiring — a
 * {@code CommandRegistrationCallback} listener that iterates {@link #nodes()} and builds the
 * corresponding Brigadier tree.
 *
 * <p>Side filtering happens here rather than in the adapter because getting it wrong is invisible
 * until someone runs a dedicated server: a {@code Side.CLIENT} command registered server-side either
 * fails to resolve its client-only argument types or silently shadows a real command.
 *
 * <p>Registration is open while the mod initialises and closed afterwards. Commands registered after
 * the game is running would be missing from the command tree the server already sent to connected
 * clients, so they would exist on the server and not in anyone's tab completion.
 */
public final class CommandRegistry implements Commands {

    private final String modId;
    private final Side side;
    private final ModLogger log;
    private final List<CommandSpec> specs = new ArrayList<CommandSpec>();
    private final Map<String, Node> nodesByPath = new LinkedHashMap<String, Node>();

    private volatile boolean open = true;

    /**
     * @param modId the mod these commands belong to
     * @param side the physical side this game is running
     * @param log the mod's logger
     */
    public CommandRegistry(String modId, Side side, ModLogger log) {
        this.modId = modId;
        this.side = side;
        this.log = log;
    }

    @Override
    public void register(CommandSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("command spec must not be null");
        }
        requireOpen(spec.literal());
        synchronized (specs) {
            if (!appliesHere(spec)) {
                log.debug("{}: command /{} is {}-only and this is a {} — not registered",
                        modId, spec.literal(), spec.onlyOn().id(), side.id());
                return;
            }
            specs.add(spec);
            collect(spec, "", new LinkedHashMap<String, Arg<?>>(), 0);
        }
    }

    /** Closes registration. Called by the runtime once initialisation is complete. */
    public void seal() {
        open = false;
    }

    /** Whether commands may still be registered. */
    public boolean isOpen() {
        return open;
    }

    /** The specs as registered, in registration order, already filtered by side. */
    public List<CommandSpec> specs() {
        synchronized (specs) {
            return Collections.unmodifiableList(new ArrayList<CommandSpec>(specs));
        }
    }

    /**
     * Every executable node, keyed by its full path.
     *
     * <p>This is what an adapter iterates. A node knows its literal path ({@code "ruby give"}), the
     * arguments in declaration order — which is the order Brigadier must chain them in — and the
     * body to run.
     */
    public List<Node> nodes() {
        synchronized (specs) {
            return Collections.unmodifiableList(new ArrayList<Node>(nodesByPath.values()));
        }
    }

    /** The root literals this mod claims, for a diagnostic listing. */
    public List<String> rootLiterals() {
        List<String> roots = new ArrayList<String>();
        synchronized (specs) {
            for (CommandSpec spec : specs) {
                if (!roots.contains(spec.literal())) {
                    roots.add(spec.literal());
                }
            }
        }
        return Collections.unmodifiableList(roots);
    }

    /**
     * Walks the spec tree, recording one node per executable branch.
     *
     * <p>Arguments accumulate down the tree: {@code /ruby give <player> <amount>} is one node with
     * two arguments, not two nodes with one each, because that is the shape Brigadier needs and
     * deriving it is exactly the work every adapter would otherwise repeat.
     */
    private void collect(CommandSpec spec, String prefix, Map<String, Arg<?>> inherited,
            int inheritedPermission) {
        String path = prefix.isEmpty() ? spec.literal() : prefix + " " + spec.literal();

        // Permission accumulates the same way. In Brigadier a `requires` on a parent literal gates
        // the whole subtree, because the subtree is unreachable without traversing it — so the
        // effective level of a node is the highest along its path, and an adapter that read the
        // leaf's own level would silently widen access to every sub-command.
        int permission = Math.max(inheritedPermission, spec.permissionLevel());

        Map<String, Arg<?>> arguments = new LinkedHashMap<String, Arg<?>>(inherited);
        for (Map.Entry<String, Arg<?>> argument : spec.arguments().entrySet()) {
            Arg<?> shadowed = arguments.put(argument.getKey(), argument.getValue());
            if (shadowed != null) {
                throw conflict("argument name '" + argument.getKey() + "' in /" + path,
                        "it is already declared by a parent node; Brigadier would resolve the "
                                + "inner one and silently ignore the outer");
            }
        }

        if (spec.body() != null) {
            Node existing = nodesByPath.get(path);
            if (existing != null) {
                throw conflict("command /" + path,
                        "it is registered twice; which body would run depends on registration "
                                + "order, so it is refused rather than resolved arbitrarily");
            }
            nodesByPath.put(path, new Node(path, spec, arguments, permission));
        }

        for (CommandSpec child : spec.children()) {
            if (appliesHere(child)) {
                collect(child, path, arguments, permission);
            }
        }
    }

    private boolean appliesHere(CommandSpec spec) {
        return spec.onlyOn() == null || spec.onlyOn() == side;
    }

    private void requireOpen(String literal) {
        if (open) {
            return;
        }
        throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                Messages.report(ErrorCode.OMNI_4002)
                        .detected("mod", modId)
                        .detected("command", "/" + literal)
                        .detail("Commands can only be registered while the mod is initialising. The")
                        .detail("server sends its command tree to clients when they connect, so one")
                        .detail("added later exists on the server and in nobody's tab completion.")
                        .fix("register it from onInitialize instead")
                        .build());
    }

    private OmniApiMisuseException conflict(String what, String why) {
        return new OmniApiMisuseException(ErrorCode.OMNI_4002,
                Messages.report(ErrorCode.OMNI_4002)
                        .detected("mod", modId)
                        .detected("problem", what)
                        .detail(why.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                                + why.substring(1) + ".")
                        .fix("give the conflicting node a distinct literal or argument name")
                        .build());
    }

    /**
     * One executable command, with everything an adapter needs to build it.
     *
     * <p>Deliberately not a {@code CommandSpec}: a spec is a tree the mod author wrote, a node is
     * one leaf of it with the parent context already folded in.
     */
    public static final class Node {

        private final String path;
        private final CommandSpec spec;
        private final Map<String, Arg<?>> arguments;
        private final int permissionLevel;

        Node(String path, CommandSpec spec, Map<String, Arg<?>> arguments, int permissionLevel) {
            this.path = path;
            this.spec = spec;
            this.arguments = Collections.unmodifiableMap(
                    new LinkedHashMap<String, Arg<?>>(arguments));
            this.permissionLevel = permissionLevel;
        }

        /** The full literal path, e.g. {@code "ruby give"}. */
        public String path() {
            return path;
        }

        /** The literal path segments, in order. */
        public List<String> segments() {
            return Collections.unmodifiableList(
                    java.util.Arrays.asList(path.split(" ")));
        }

        /** The spec this node came from. */
        public CommandSpec spec() {
            return spec;
        }

        /** Every argument reachable at this node, parents first, in declaration order. */
        public Map<String, Arg<?>> arguments() {
            return arguments;
        }

        /** The effective permission level: the highest declared anywhere along this node's path. */
        public int permissionLevel() {
            return permissionLevel;
        }

        /** What to run. */
        public Function<CommandInvocation, Integer> body() {
            return spec.body();
        }

        @Override
        public String toString() {
            return "/" + path + arguments.keySet();
        }
    }
}
