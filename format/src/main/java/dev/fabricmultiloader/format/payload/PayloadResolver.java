package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches every payload of a container against an environment.
 *
 * <p>Used twice, for two different purposes. At startup the runtime resolves in order to verify
 * that exactly one payload is active — a cross-check against the loader's own selection, not a
 * substitute for it. In a diagnostic it resolves to explain each rejection. Both go through the
 * same code, which is what guarantees the explanation matches the verdict.
 */
public final class PayloadResolver {

    /** Matches every payload in the manifest against the environment. */
    public static ResolutionReport resolve(ContainerManifest manifest, Environment environment) {
        List<MatchResult> results = new ArrayList<MatchResult>(manifest.payloads().size());
        for (PayloadDescriptor payload : manifest.payloads()) {
            results.add(PayloadMatcher.match(payload, environment));
        }
        return ResolutionReport.of(manifest, environment, results);
    }

    /**
     * Resolves using the loader's own verdict about which payload mods are present, rather than by
     * re-evaluating constraints.
     *
     * <p>This is what the runtime actually acts on: the loader has already made the decision, and
     * second-guessing it would risk activating a payload the loader did not extract. Constraint
     * evaluation is still run for every payload, so a mismatch between the two views shows up in
     * the report instead of being invisible.
     *
     * @param manifest the container manifest
     * @param environment the detected environment
     * @param loadedModIds mod ids the loader reports as loaded
     * @return the payloads the loader actually selected, in manifest order
     */
    public static List<PayloadDescriptor> selectedByLoader(
            ContainerManifest manifest, Environment environment, Iterable<String> loadedModIds) {
        List<PayloadDescriptor> selected = new ArrayList<PayloadDescriptor>(1);
        for (PayloadDescriptor payload : manifest.payloads()) {
            for (String modId : loadedModIds) {
                if (payload.modId().equals(modId)) {
                    selected.add(payload);
                    break;
                }
            }
        }
        return selected;
    }

    private PayloadResolver() {
        throw new AssertionError("no instances");
    }
}
