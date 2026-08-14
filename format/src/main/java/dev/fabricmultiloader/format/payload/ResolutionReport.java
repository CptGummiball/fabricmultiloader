package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of matching every payload of a container against one environment, and the text a user
 * sees when that outcome is "none".
 *
 * <p>This report is the reason the container deliberately does <em>not</em> declare a hard
 * dependency on its payload alias. It could: Fabric would then refuse to load the container at all
 * when no payload matches. But the message would be
 * "requires examplemod-impl 2.0.0 which is missing" — technically accurate and useless. By letting
 * the container load and evaluating the constraints ourselves, the user is told which requirement
 * actually failed, with the installed version and a link to the right download.
 */
public final class ResolutionReport {

    private final ContainerManifest manifest;
    private final Environment environment;
    private final List<MatchResult> results;
    private final List<MatchResult> matches;

    private ResolutionReport(
            ContainerManifest manifest, Environment environment, List<MatchResult> results) {
        this.manifest = manifest;
        this.environment = environment;
        this.results = Collections.unmodifiableList(results);
        List<MatchResult> matched = new ArrayList<MatchResult>();
        for (MatchResult result : results) {
            if (result.isMatch()) {
                matched.add(result);
            }
        }
        this.matches = Collections.unmodifiableList(matched);
    }

    static ResolutionReport of(
            ContainerManifest manifest, Environment environment, List<MatchResult> results) {
        return new ResolutionReport(manifest, environment, results);
    }

    /** The manifest that was resolved. */
    public ContainerManifest manifest() {
        return manifest;
    }

    /** The environment it was resolved against. */
    public Environment environment() {
        return environment;
    }

    /** One result per payload, in manifest order. */
    public List<MatchResult> results() {
        return results;
    }

    /** Every payload that applies. Should always be exactly one. */
    public List<MatchResult> matches() {
        return matches;
    }

    /** The single applicable payload, or {@code null} if there is none or several. */
    public PayloadDescriptor selected() {
        return matches.size() == 1 ? matches.get(0).payload() : null;
    }

    /** Whether exactly one payload applies — the only healthy outcome. */
    public boolean isResolved() {
        return matches.size() == 1;
    }

    /** Whether no payload applies. */
    public boolean isUnmatched() {
        return matches.isEmpty();
    }

    /**
     * Whether several payloads apply.
     *
     * <p>Impossible with a validated build: the domains are proven disjoint, every payload provides
     * the same alias (and Fabric permits one loaded mod per id), and payloads declare each other in
     * {@code breaks}. It is still checked, because a hand-merged or tampered jar can produce it and
     * silently running two implementations would be far worse than refusing to start.
     */
    public boolean isAmbiguous() {
        return matches.size() > 1;
    }

    /** The diagnostic code for this outcome, or {@code null} when resolved. */
    public ErrorCode errorCode() {
        if (isResolved()) {
            return null;
        }
        return isAmbiguous() ? ErrorCode.OMNI_2004 : ErrorCode.OMNI_2003;
    }

    /**
     * Renders the failure report a user sees.
     *
     * @return the full message, or {@code null} when resolution succeeded
     */
    public String render() {
        if (isResolved()) {
            return null;
        }
        return isAmbiguous() ? renderAmbiguous() : renderUnmatched();
    }

    private String renderUnmatched() {
        Messages.Builder message = Messages.report(ErrorCode.OMNI_2003)
                .detected("Minecraft", environment.minecraft())
                .detected("Fabric Loader", environment.fabricLoader())
                .detected("Fabric API",
                        environment.fabricApi().isUnknown() ? "not installed" : environment.fabricApi())
                .detected("Java", environment.javaMajor())
                .detected("Side", environment.side().id())
                .detected("Mod", manifest.container().modId() + " "
                        + manifest.container().modVersion())
                .detail("This build contains " + results.size()
                        + " version-specific implementation(s). None accepts the environment above:")
                .detail("");

        for (MatchResult result : results) {
            message.detail("  payload  " + result.payload().id());
            for (Rejection rejection : result.rejections()) {
                message.detail("             " + rejection.describe());
            }
        }

        message.detail("");
        message.detail("Supported Minecraft versions:");
        for (MatchResult result : results) {
            message.detail("  " + result.payload().requires().minecraft());
        }

        ContainerManifest.DiagnosticsInfo diagnostics = manifest.diagnostics();
        if (!diagnostics.downloadUrl().isEmpty()) {
            message.fix("check for a newer build: " + diagnostics.downloadUrl());
        }
        message.fix("install one of the supported Minecraft versions, or resolve the requirements above");
        if (!diagnostics.supportUrl().isEmpty()) {
            message.fix("report a missing version: " + diagnostics.supportUrl());
        }
        return message.build();
    }

    private String renderAmbiguous() {
        Messages.Builder message = Messages.report(ErrorCode.OMNI_2004)
                .detected("Minecraft", environment.minecraft())
                .detected("Java", environment.javaMajor())
                .detected("Side", environment.side().id())
                .detected("Mod", manifest.container().modId())
                .detail("Several implementations accept this environment at once:")
                .detail("");
        for (MatchResult match : matches) {
            message.detail("  payload  " + match.payload().id() + "  "
                    + match.payload().requires());
        }
        message.detail("");
        message.detail("A validated build cannot produce this, so the jar was most likely");
        message.detail("modified after it was built.");
        message.fix("re-download the mod from its official source");
        message.fix("if you built it yourself, run ./gradlew validateUniversalJar");
        return message.build();
    }

    @Override
    public String toString() {
        if (isResolved()) {
            return "resolved to " + selected().id();
        }
        return isAmbiguous() ? "ambiguous (" + matches.size() + " matches)" : "unmatched";
    }
}
