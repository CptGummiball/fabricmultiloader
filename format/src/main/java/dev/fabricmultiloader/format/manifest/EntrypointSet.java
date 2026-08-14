package dev.fabricmultiloader.format.manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's own entrypoint classes, by lifecycle phase.
 *
 * <p>These are <em>not</em> Fabric entrypoints. Fabric only ever sees the framework's fixed
 * entrypoint classes; those then read this list and instantiate the mod's classes themselves. The
 * indirection is what makes the entrypoint declaration version-independent — one list in the
 * container serves every payload — and it is also what lets the runtime control ordering:
 * the platform is created before common code runs, and deferred registrations are flushed after it.
 */
public final class EntrypointSet {

    /** Lifecycle phases a mod can hook into. */
    public enum Phase {
        /** Before Minecraft classes load. Config and early diagnostics only. */
        PRE_LAUNCH("preLaunch"),
        /** Both sides, after the platform is ready. */
        COMMON("common"),
        /** Physical client only, after {@link #COMMON}. */
        CLIENT("client"),
        /** Dedicated server only, after {@link #COMMON}. */
        SERVER("server");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        /** The key used in metadata. */
        public String id() {
            return id;
        }

        /** Looks a phase up by its metadata key, or {@code null}. */
        public static Phase byId(String id) {
            for (Phase phase : values()) {
                if (phase.id.equals(id)) {
                    return phase;
                }
            }
            return null;
        }
    }

    private final Map<Phase, List<String>> classesByPhase;

    private EntrypointSet(Map<Phase, List<String>> classesByPhase) {
        Map<Phase, List<String>> copy = new LinkedHashMap<Phase, List<String>>();
        for (Phase phase : Phase.values()) {
            List<String> classes = classesByPhase.get(phase);
            copy.put(phase, classes == null || classes.isEmpty()
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(classes)));
        }
        this.classesByPhase = Collections.unmodifiableMap(copy);
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** An empty set — valid to construct, but rejected by the validator ({@code OMNI-1141}). */
    public static EntrypointSet empty() {
        return builder().build();
    }

    /** Class names for a phase, in declaration order; never {@code null}. */
    public List<String> forPhase(Phase phase) {
        return classesByPhase.get(phase);
    }

    /** Whether any phase declares a class. */
    public boolean isEmpty() {
        for (Phase phase : Phase.values()) {
            if (!classesByPhase.get(phase).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Every declared class name across all phases, in phase then declaration order. */
    public List<String> allClasses() {
        List<String> all = new ArrayList<String>();
        for (Phase phase : Phase.values()) {
            all.addAll(classesByPhase.get(phase));
        }
        return Collections.unmodifiableList(all);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (Phase phase : Phase.values()) {
            List<String> classes = classesByPhase.get(phase);
            if (!classes.isEmpty()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(phase.id()).append('=').append(classes);
            }
        }
        return out.length() == 0 ? "(none)" : out.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntrypointSet
                && ((EntrypointSet) other).classesByPhase.equals(classesByPhase);
    }

    @Override
    public int hashCode() {
        return classesByPhase.hashCode();
    }

    /** Mutable builder. */
    public static final class Builder {

        private final Map<Phase, List<String>> classesByPhase =
                new LinkedHashMap<Phase, List<String>>();

        /** Adds a class to a phase. */
        public Builder add(Phase phase, String className) {
            Identifiers.requireClassName(className, "entrypoints." + phase.id());
            List<String> classes = classesByPhase.get(phase);
            if (classes == null) {
                classes = new ArrayList<String>();
                classesByPhase.put(phase, classes);
            }
            if (!classes.contains(className)) {
                classes.add(className);
            }
            return this;
        }

        /** Adds several classes to a phase. */
        public Builder addAll(Phase phase, List<String> classNames) {
            for (String className : classNames) {
                add(phase, className);
            }
            return this;
        }

        /** Builds the immutable set. */
        public EntrypointSet build() {
            return new EntrypointSet(classesByPhase);
        }
    }
}
