package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.LifecyclePhase;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.format.payload.PayloadResolver;
import dev.fabricmultiloader.format.payload.ResolutionReport;
import dev.fabricmultiloader.runtime.diag.DiagnosticReport;
import dev.fabricmultiloader.runtime.diag.ReportWriter;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import dev.fabricmultiloader.runtime.log.Log;
import java.nio.file.Path;
import java.util.List;

/**
 * One universal mod, from manifest to active payload.
 *
 * <p>The exactly-one assertion lives here, and it is worth being precise about what it does. Fabric
 * Loader has already made the selection by the time this runs; re-deriving it would risk activating
 * something the loader did not extract. So the loader's verdict is what is acted on, and the
 * constraint evaluation runs alongside it purely to explain the outcome — which also means a
 * disagreement between the two shows up as a diagnostic rather than as silent misbehaviour.
 */
public final class ContainerRuntime {

    /** Property that switches a failed resolution from fatal to a warning. */
    public static final String STRICT_PROPERTY = "fabricmultiloader.strict";

    /** Property that forces a diagnostic report even on success. */
    public static final String REPORT_PROPERTY = "fabricmultiloader.report";

    private final ContainerManifest manifest;
    private final Environment environment;
    private final LoaderFacade loader;
    private final ModLogger log;
    private final LifecycleStateMachine lifecycle;
    private final ReportWriter reports;

    private ResolutionReport resolution;
    private PayloadDescriptor active;
    private dev.fabricmultiloader.api.platform.PlatformInfo platformInfo;

    /**
     * @param manifest the container manifest
     * @param environment the detected environment
     * @param loader the loader facade
     */
    public ContainerRuntime(ContainerManifest manifest, Environment environment, LoaderFacade loader) {
        this.manifest = manifest;
        this.environment = environment;
        this.loader = loader;
        this.log = Log.named(manifest.container().modId());
        this.lifecycle = new LifecycleStateMachine(manifest.container().modId());
        this.reports = new ReportWriter(loader.gameDir(), log);
    }

    /** The container manifest. */
    public ContainerManifest manifest() {
        return manifest;
    }

    /** The detected environment. */
    public Environment environment() {
        return environment;
    }

    /** The container's mod id. */
    public String modId() {
        return manifest.container().modId();
    }

    /** The lifecycle tracker. */
    public LifecycleStateMachine lifecycle() {
        return lifecycle;
    }

    /** The mod's logger. */
    public ModLogger log() {
        return log;
    }

    /** The resolution outcome, once {@link #resolve()} has run. */
    public ResolutionReport resolution() {
        return resolution;
    }

    /** The active payload, or {@code null} if resolution failed. */
    public PayloadDescriptor activePayload() {
        return active;
    }

    /** Whether a payload is active. */
    public boolean isActive() {
        return active != null && !lifecycle.hasFailed();
    }

    /** What the active payload is running on, or {@code null} if resolution failed. */
    public dev.fabricmultiloader.api.platform.PlatformInfo platformInfo() {
        return platformInfo;
    }

    /**
     * Resolves, verifies and reports.
     *
     * @return {@code true} if exactly one payload is active
     * @throws OmniException {@code OMNI-2003} or {@code OMNI-2004} in strict mode
     */
    public boolean resolve() {
        if (resolution != null) {
            log.debug("{} was already resolved; ignoring the repeated call", modId());
            return isActive();
        }
        resolution = PayloadResolver.resolve(manifest, environment);

        List<PayloadDescriptor> selectedByLoader = PayloadResolver.selectedByLoader(
                manifest, environment, loader.loadedModIds());

        if (selectedByLoader.size() == 1) {
            PayloadDescriptor payload = selectedByLoader.get(0);
            warnIfConstraintsDisagree(payload);
            new IntegrityChecker(loader).verify(modId(), payload);
            active = payload;
            platformInfo = new dev.fabricmultiloader.runtime.context.PlatformInfoImpl(
                    environment, payload);
            lifecycle.advanceTo(LifecyclePhase.RESOLVED);
            announce(payload);
            if ("always".equalsIgnoreCase(System.getProperty(REPORT_PROPERTY))) {
                writeReport(null);
            }
            writeLastLaunch(payload);
            return true;
        }

        return fail(selectedByLoader);
    }

    /**
     * The constraint evaluation and the loader's selection should always agree. If they do not, the
     * mod still runs — the loader is authoritative — but the discrepancy is recorded, because it
     * means either the manifest and the payload metadata drifted apart in the build, or the jar was
     * modified afterwards.
     */
    private void warnIfConstraintsDisagree(PayloadDescriptor payload) {
        for (dev.fabricmultiloader.format.payload.MatchResult result : resolution.results()) {
            if (result.payload().id().equals(payload.id()) && !result.isMatch()) {
                log.warn("{}: Fabric selected payload '{}', but its own constraints reject this "
                                + "environment ({}). The jar's metadata is inconsistent — "
                                + "continuing with the loader's decision.",
                        modId(), payload.id(), result.rejections());
            }
        }
    }

    private boolean fail(List<PayloadDescriptor> selectedByLoader) {
        boolean ambiguous = selectedByLoader.size() > 1;
        ErrorCode code = ambiguous ? ErrorCode.OMNI_2004 : ErrorCode.OMNI_2003;
        String message = resolution.render();
        if (message == null) {
            // The loader selected nothing although our own evaluation found a match — the reverse
            // of the disagreement above, and just as much a metadata problem.
            message = dev.fabricmultiloader.format.error.Messages.report(code)
                    .detected("mod", modId())
                    .detected("payloads in container", manifest.payloads().size())
                    .detected("payloads loaded", selectedByLoader.size())
                    .detail("Fabric Loader did not select exactly one implementation of this mod,")
                    .detail("although its declared constraints allow one. The container's metadata")
                    .detail("and its payload metadata disagree.")
                    .fix("re-download the mod from its official source")
                    .fix("if you built it yourself, run ./gradlew validateUniversalJar")
                    .build();
        }

        Path report = writeReport(message);
        String fullMessage = report == null
                ? message
                : message + "\n  A full report was written to\n    " + report + "\n";

        lifecycle.fail();
        if (isStrict()) {
            throw new OmniException(code, fullMessage);
        }
        log.warn("{} is not active: {}", modId(), code.id());
        log.warn("{}", fullMessage);
        log.warn("OMNI-2101 continuing without {} because {}=false", modId(), STRICT_PROPERTY);
        return false;
    }

    private void announce(PayloadDescriptor payload) {
        log.info("{} {} -> payload '{}' ({} {})",
                modId(), manifest.container().modVersion(), payload.id(),
                payload.modId(), payload.modVersion());
        log.info("                    mc={} loader={} fabric-api={} java={} side={}",
                environment.minecraft(), environment.fabricLoader(),
                environment.fabricApi().isUnknown() ? "none" : environment.fabricApi(),
                environment.javaMajor(), environment.side().id());
    }

    private Path writeReport(String headline) {
        return reports.write(DiagnosticReport.failureFileName(modId()),
                DiagnosticReport.render(manifest, environment, resolution, headline));
    }

    private void writeLastLaunch(PayloadDescriptor payload) {
        reports.write(DiagnosticReport.lastLaunchFileName(modId()),
                DiagnosticReport.renderLastLaunch(manifest, environment, payload));
    }

    /**
     * Whether a failed resolution aborts the launch.
     *
     * <p>Strict by default. A mod that silently does nothing produces follow-on failures nobody can
     * attribute to it later; a server administrator who would rather tolerate that can say so
     * explicitly, per mod or globally.
     */
    private boolean isStrict() {
        String perMod = System.getProperty(STRICT_PROPERTY + "." + modId());
        if (perMod != null) {
            return !"false".equalsIgnoreCase(perMod);
        }
        String global = System.getProperty(STRICT_PROPERTY);
        if (global != null) {
            return !"false".equalsIgnoreCase(global);
        }
        return manifest.container().strict();
    }
}
