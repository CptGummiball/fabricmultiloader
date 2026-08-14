package dev.fabricmultiloader.runtime.context;

import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Answers {@code ctx.capability(...)} from the manifest first and the payload second.
 *
 * <p>The manifest declaration is checked before the platform is asked, and that ordering is what
 * makes a capability more than a nicer-looking version comparison. The declared set is visible to
 * the validator at build time and appears in the diagnostic report at runtime, so "does this
 * version have data components?" has one answer that tooling, logs and code all agree on. If the
 * platform were asked first, the manifest would become documentation that could quietly drift out
 * of date.
 *
 * <p>A payload declaring a capability it does not actually implement is caught at build time
 * ({@code OMNI-1130}). It is checked again here because the alternative failure mode is an empty
 * {@code Optional} that common code silently skips — a feature simply not happening, with nothing
 * in the log to say why. Once per capability the mismatch is reported loudly instead.
 */
public final class CapabilityResolver {

    private final String modId;
    private final PayloadDescriptor payload;
    private final ModLogger log;
    private final Set<String> reported =
            Collections.synchronizedSet(new LinkedHashSet<String>());

    private volatile Platform platform;

    /**
     * @param modId the container mod id, named in diagnostics
     * @param payload the active payload, whose declared capabilities are authoritative
     * @param log the mod's logger
     */
    public CapabilityResolver(String modId, PayloadDescriptor payload, ModLogger log) {
        this.modId = modId;
        this.payload = payload;
        this.log = log;
    }

    /** Supplies the platform once it exists. Capabilities are unavailable before that. */
    public void bind(Platform platform) {
        this.platform = platform;
    }

    /** Whether the active payload declares this capability. */
    public boolean declares(Capability<?> capability) {
        return capability != null && payload.hasCapability(capability.id());
    }

    /**
     * Resolves a capability.
     *
     * @param capability the capability
     * @param <T> the capability interface
     * @return the implementation, or empty when this payload does not provide it
     */
    public <T> Optional<T> resolve(Capability<T> capability) {
        if (!declares(capability)) {
            return Optional.empty();
        }
        Platform current = platform;
        if (current == null) {
            // Before the platform exists nothing can be provided. Not an error: pre-launch code
            // legitimately asks, and "not yet" and "not on this version" call for the same handling.
            return Optional.empty();
        }

        Optional<T> resolved = current.capability(capability);
        if (resolved == null || !resolved.isPresent()) {
            reportOnce(capability, "declares the capability '" + capability.id()
                    + "' but Platform#capability returned nothing for it");
            return Optional.empty();
        }

        T value = resolved.get();
        if (!capability.type().isInstance(value)) {
            throw new OmniException(ErrorCode.OMNI_2040, Messages.report(ErrorCode.OMNI_2040)
                    .detected("mod", modId)
                    .detected("payload", payload.id())
                    .detected("capability", capability.id())
                    .detected("expected", capability.type().getName())
                    .detected("received", value.getClass().getName())
                    .detail("The payload returned an object that does not implement the capability")
                    .detail("interface. Common code would fail with a ClassCastException at an")
                    .detail("arbitrary later point instead of here, where the cause is visible.")
                    .fix("return an implementation of " + capability.type().getSimpleName()
                            + " from Platform#capability")
                    .build());
        }
        return Optional.of(value);
    }

    private void reportOnce(Capability<?> capability, String problem) {
        if (!reported.add(capability.id())) {
            return;
        }
        log.warn("{}: payload '{}' {} — common code depending on it will be skipped silently. "
                        + "This is a mod bug; ./gradlew validateUniversalJar reports it as OMNI-1130.",
                modId, payload.id(), problem);
    }
}
