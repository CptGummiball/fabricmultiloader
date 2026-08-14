package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.hash.Sha256;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Verifies that the active payload is the one the manifest describes.
 *
 * <p>Checks the zip entry <em>inside the container</em>, not the copy the loader extracted. A
 * tampered or truncated download is the case this exists to catch, and the extracted copy is derived
 * from it — verifying the derivative would confirm nothing about the original.
 *
 * <p>Costs about eight milliseconds for a 1.5 MiB payload and can be disabled with
 * {@code -Dfabricmultiloader.verify=false}, which modpack tools that recompress jars legitimately
 * need. Failure is deliberately hard rather than a warning: a hash mismatch is either tampering or
 * corruption, and continuing means running code nobody vouched for.
 */
public final class IntegrityChecker {

    /** System property that disables verification. */
    public static final String DISABLE_PROPERTY = "fabricmultiloader.verify";

    private final LoaderFacade loader;

    /**
     * @param loader the loader facade
     */
    public IntegrityChecker(LoaderFacade loader) {
        this.loader = loader;
    }

    /** Whether verification is enabled at all. */
    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY));
    }

    /**
     * Verifies a payload against its manifest entry.
     *
     * @param containerModId the container holding the payload jar
     * @param payload the descriptor to verify against
     * @throws OmniException {@code OMNI-2013} on a mismatch
     */
    public void verify(String containerModId, PayloadDescriptor payload) {
        if (!isEnabled() || payload.sha256().isEmpty()) {
            return;
        }
        Optional<Path> jar = loader.findPath(containerModId, payload.file());
        if (!jar.isPresent()) {
            throw failure(containerModId, payload, "the nested jar is missing from the container",
                    payload.sha256(), "(entry not found)", -1L);
        }

        long actualSize;
        String actualHash;
        try {
            actualSize = Files.size(jar.get());
            InputStream in = Files.newInputStream(jar.get());
            try {
                actualHash = Sha256.of(in);
            } finally {
                closeQuietly(in);
            }
        } catch (IOException | RuntimeException e) {
            throw failure(containerModId, payload, "the nested jar could not be read: " + e,
                    payload.sha256(), "(unreadable)", -1L);
        }

        if (payload.size() > 0 && actualSize != payload.size()) {
            throw failure(containerModId, payload, "the nested jar has an unexpected size",
                    payload.sha256(), actualHash, actualSize);
        }
        if (!Sha256.matches(payload.sha256(), actualHash)) {
            throw failure(containerModId, payload, "the nested jar does not match its checksum",
                    payload.sha256(), actualHash, actualSize);
        }
    }

    private OmniException failure(String containerModId, PayloadDescriptor payload, String problem,
            String expectedHash, String actualHash, long actualSize) {
        Messages.Builder message = Messages.report(ErrorCode.OMNI_2013)
                .detected("mod", containerModId)
                .detected("payload", payload.id())
                .detected("file", payload.file())
                .detected("expected sha256", expectedHash)
                .detected("actual sha256", actualHash);
        if (actualSize >= 0) {
            message.detected("expected size", payload.size());
            message.detected("actual size", actualSize);
        }
        return new OmniException(ErrorCode.OMNI_2013, message
                .detail(problem + ".")
                .detail("The jar is not the one that was built. That is either corruption in")
                .detail("transit or deliberate modification, and running it either way would mean")
                .detail("executing code nobody vouched for.")
                .fix("re-download the mod from its official source")
                .fix("if a modpack tool recompressed the jar, start with -D"
                        + DISABLE_PROPERTY + "=false")
                .build());
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // Closing a read-only stream from the loader's file system; nothing to recover from.
        }
    }
}
