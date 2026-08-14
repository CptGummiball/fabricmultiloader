package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.Requirements;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a payload's requirements against a concrete environment.
 *
 * <p>This is <em>not</em> what selects the payload at runtime — Fabric Loader's solver does that,
 * from the constraints the build wrote into each payload's own {@code fabric.mod.json}. The matcher
 * exists so the runtime can independently answer the question the loader cannot: <em>why</em> was
 * nothing selected. Running the same constraints ourselves turns "mod not loaded" into
 * "fabric-api &gt;=0.114.0 required, 0.110.0 installed", which is the difference between a bug
 * report and a fix.
 *
 * <p>It is also how the two implementations are kept honest: if the matcher and the loader ever
 * disagreed, the runtime's exactly-one-payload assertion would fire ({@code OMNI-2003} or
 * {@code OMNI-2004}) instead of the mod silently misbehaving.
 */
public final class PayloadMatcher {

    /** Evaluates one payload, collecting every failed requirement. */
    public static MatchResult match(PayloadDescriptor payload, Environment environment) {
        Requirements requires = payload.requires();
        List<Rejection> rejections = new ArrayList<Rejection>();

        if (!requires.minecraft().test(environment.minecraft())) {
            rejections.add(Rejection.of(Rejection.Constraint.MINECRAFT,
                    render(requires.minecraft()), render(environment.minecraft())));
        }
        if (!requires.java().test(environment.javaVersion())) {
            rejections.add(Rejection.of(Rejection.Constraint.JAVA,
                    render(requires.java()), String.valueOf(environment.javaMajor())));
        }
        if (!requires.environment().accepts(environment.side())) {
            rejections.add(Rejection.of(Rejection.Constraint.ENVIRONMENT,
                    requires.environment().id(), environment.side().id()));
        }
        if (!requires.fabricLoader().test(environment.fabricLoader())) {
            rejections.add(Rejection.of(Rejection.Constraint.FABRIC_LOADER,
                    render(requires.fabricLoader()), render(environment.fabricLoader())));
        }
        for (Map.Entry<String, VersionRange> dependency : requires.mods().entrySet()) {
            String modId = dependency.getKey();
            VersionRange required = dependency.getValue();
            SemVer installed = environment.modVersion(modId);
            if (installed == null) {
                rejections.add(Rejection.modMissing(modId, render(required)));
            } else if (!required.test(installed)) {
                rejections.add(Rejection.modVersion(modId, render(required), render(installed)));
            }
        }
        return rejections.isEmpty()
                ? MatchResult.matched(payload)
                : MatchResult.rejected(payload, rejections);
    }

    /**
     * Which optional dependencies are absent or out of range.
     *
     * <p>Never affects selection. It is reported because "the ModMenu integration is missing" is
     * otherwise indistinguishable from a bug in the mod, and a user has no way to tell.
     */
    public static List<Rejection> inactiveOptionalMods(
            PayloadDescriptor payload, Environment environment) {
        List<Rejection> inactive = new ArrayList<Rejection>();
        for (Map.Entry<String, VersionRange> dependency
                : payload.requires().optionalMods().entrySet()) {
            String modId = dependency.getKey();
            VersionRange required = dependency.getValue();
            SemVer installed = environment.modVersion(modId);
            if (installed == null) {
                inactive.add(Rejection.modMissing(modId, render(required)));
            } else if (!required.test(installed)) {
                inactive.add(Rejection.modVersion(modId, render(required), render(installed)));
            }
        }
        return inactive;
    }

    private static String render(VersionRange range) {
        return range.isAll() ? "*" : range.toString();
    }

    private static String render(SemVer version) {
        return version.isUnknown() ? "unknown" : version.toString();
    }

    private PayloadMatcher() {
        throw new AssertionError("no instances");
    }
}
