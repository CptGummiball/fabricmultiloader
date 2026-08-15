package dev.fabricmultiloader.testing.conformance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a real Fabric Loader decided about a synthetic mod set.
 *
 * <p>Two outcomes matter and they are not the same thing. A payload that was <em>dropped</em> — not
 * selected, launch continues — is the behaviour the whole architecture depends on. A resolution that
 * <em>failed</em> is the loader refusing to start the game. Conflating them is how a harness ends up
 * reporting success while the assumption is broken, so they are separate fields and every test says
 * which one it expects.
 */
public final class ResolutionProbe {

    private final String loaderVersion;
    private final boolean succeeded;
    private final Set<String> selected;
    private final String failure;
    private final Throwable cause;

    private ResolutionProbe(String loaderVersion, boolean succeeded, Set<String> selected,
            String failure, Throwable cause) {
        this.loaderVersion = loaderVersion;
        this.succeeded = succeeded;
        this.selected = Collections.unmodifiableSet(new LinkedHashSet<String>(selected));
        this.failure = failure;
        this.cause = cause;
    }

    /** The loader selected this set of mods and the launch would continue. */
    static ResolutionProbe selected(String loaderVersion, Set<String> modIds) {
        return new ResolutionProbe(loaderVersion, true, modIds, null, null);
    }

    /** The loader refused to resolve and the launch would abort. */
    static ResolutionProbe failed(String loaderVersion, String message, Throwable cause) {
        return new ResolutionProbe(loaderVersion, false,
                Collections.<String>emptySet(), message, cause);
    }

    /** Which loader produced this. */
    public String loaderVersion() {
        return loaderVersion;
    }

    /** Whether resolution succeeded. */
    public boolean succeeded() {
        return succeeded;
    }

    /** The selected mod ids; empty when resolution failed. */
    public Set<String> selectedModIds() {
        return selected;
    }

    /** The loader's own failure message, or {@code null} on success. */
    public String failureMessage() {
        return failure;
    }

    /** The exception the loader threw, or {@code null} on success. */
    public Throwable cause() {
        return cause;
    }

    /** Whether a mod was selected. */
    public boolean selected(String modId) {
        return selected.contains(modId);
    }

    /** The selected mods whose id starts with a prefix — the payloads of one container. */
    public List<String> selectedStartingWith(String prefix) {
        List<String> matches = new ArrayList<String>();
        for (String modId : selected) {
            if (modId.startsWith(prefix)) {
                matches.add(modId);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    @Override
    public String toString() {
        return succeeded
                ? "fabric-loader " + loaderVersion + " selected " + selected
                : "fabric-loader " + loaderVersion + " refused to resolve: " + failure;
    }
}
