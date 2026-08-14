package dev.fabricmultiloader.runtime.boot;

import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.PayloadManifest;
import dev.fabricmultiloader.format.manifest.PayloadManifestReader;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lets a payload run without the container it belongs to.
 *
 * <p>This is what makes the development loop bearable. {@code ./gradlew :versions:mc-1.21.4:runClient}
 * launches one version module as an ordinary Fabric mod; there is no universal jar in the game
 * directory and therefore no container manifest, no payload selection and no runtime identity. The
 * obvious workaround — a second code path for development — would mean the thing developers run
 * every day is not the thing players run, which is the failure mode this framework exists to
 * prevent.
 *
 * <p>Instead every payload carries {@code omni/payload.json}, a copy of the container identity and
 * entrypoints (invariant I4). The runtime reads it, synthesises a one-payload container manifest
 * and continues down exactly the same code path. Same lifecycle, same context, same ordering.
 *
 * <p>Permitted only in a development runtime or with {@code -Dfabricmultiloader.slim=true}, the
 * latter for the slim single-version jars a mod may publish alongside the universal one. In a
 * normal game a payload without its container means the jar was taken apart, and running anyway
 * would hide that.
 */
public final class DevFallback {

    /** Enables the fallback outside a development runtime, for slim single-version jars. */
    public static final String SLIM_PROPERTY = "fabricmultiloader.slim";

    /**
     * Every loaded mod that carries a payload descriptor, keyed by mod id.
     *
     * <p>Scanned rather than asked, for the same reason containers are: the public loader API does
     * not tell an entrypoint which mod declared it, and identifying payloads by the file they carry
     * works regardless of how their metadata was generated.
     *
     * @param loader the loader facade
     * @return the descriptors, ordered by mod id
     */
    public static Map<String, PayloadManifest> discover(LoaderFacade loader) {
        List<String> modIds = new ArrayList<String>(loader.loadedModIds());
        Collections.sort(modIds);

        Map<String, PayloadManifest> found = new LinkedHashMap<String, PayloadManifest>();
        for (String modId : modIds) {
            PayloadManifest descriptor = read(loader, modId);
            if (descriptor != null) {
                found.put(modId, descriptor);
            }
        }
        return Collections.unmodifiableMap(found);
    }

    /**
     * Reads one mod's payload descriptor.
     *
     * @param loader the loader facade
     * @param modId the mod to read from
     * @return the descriptor, or {@code null} if the mod carries none
     * @throws OmniException {@code OMNI-2001} if the file is present but unreadable
     */
    public static PayloadManifest read(LoaderFacade loader, String modId) {
        Optional<Path> path = loader.findPath(modId, OmniFormat.PAYLOAD_DESCRIPTOR_PATH);
        if (!path.isPresent()) {
            return null;
        }
        InputStream in = null;
        try {
            in = Files.newInputStream(path.get());
            return PayloadManifestReader.read(in);
        } catch (IOException e) {
            throw new OmniException(ErrorCode.OMNI_2001, Messages.report(ErrorCode.OMNI_2001)
                    .detected("mod", modId)
                    .detected("entry", OmniFormat.PAYLOAD_DESCRIPTOR_PATH)
                    .detected("problem", e.toString())
                    .detail("The payload descriptor could not be read.")
                    .fix("re-download the mod — the jar is likely corrupted")
                    .build(), e);
        } finally {
            closeQuietly(in);
        }
    }

    /** Whether running a payload without its container is allowed in this environment. */
    public static boolean isPermitted(Environment environment) {
        return environment.isDevelopment() || Boolean.getBoolean(SLIM_PROPERTY);
    }

    /**
     * Builds the synthetic container manifest a standalone payload runs against.
     *
     * @param descriptor the payload's self-description
     * @param runtimeVersion the running runtime's version
     * @param environment the detected environment
     * @param log the framework logger
     * @return a one-payload container manifest
     * @throws OmniException {@code OMNI-2003} when the fallback is not permitted here
     */
    public static ContainerManifest synthesise(PayloadManifest descriptor, SemVer runtimeVersion,
            Environment environment, ModLogger log) {
        if (!isPermitted(environment)) {
            throw new OmniException(ErrorCode.OMNI_2003, Messages.report(ErrorCode.OMNI_2003)
                    .detected("payload", descriptor.payload().modId())
                    .detected("expects container", descriptor.container().modId())
                    .detected("development runtime", environment.isDevelopment())
                    .detail("This is one implementation of a universal mod, not the mod itself. Its")
                    .detail("container is not installed, so nothing declares the mod's entrypoints,")
                    .detail("version or identity.")
                    .fix("install " + descriptor.container().modId()
                            + " — the single jar that contains this one")
                    .fix("if this is a slim single-version build, start with -D"
                            + SLIM_PROPERTY + "=true")
                    .build());
        }

        log.info("{} is running standalone, without its container ({}) — {}",
                descriptor.payload().modId(), descriptor.container().modId(),
                environment.isDevelopment() ? "development runtime" : SLIM_PROPERTY + "=true");
        log.info("{} identity, entrypoints and constraints come from {}",
                ErrorCode.OMNI_2100.id(), OmniFormat.PAYLOAD_DESCRIPTOR_PATH);

        return descriptor.toContainerManifest(runtimeVersion);
    }

    /**
     * Compares a payload's self-description with what the container says about it.
     *
     * <p>Both files are generated from the same source, so a disagreement is not a difference of
     * opinion — it means the jar was assembled from parts of two builds, or edited afterwards. That
     * is worth catching here rather than letting it surface later as a payload behaving unlike its
     * declared constraints.
     *
     * @param descriptor the payload's own description
     * @param manifest the container manifest
     * @throws OmniException {@code OMNI-2011} on a divergence in identity or constraints
     */
    public static void crossCheck(PayloadManifest descriptor, ContainerManifest manifest) {
        PayloadDescriptor own = descriptor.payload();
        PayloadDescriptor declared = null;
        for (PayloadDescriptor candidate : manifest.payloads()) {
            if (candidate.modId().equals(own.modId())) {
                declared = candidate;
                break;
            }
        }
        if (declared == null) {
            throw divergence(descriptor, manifest, "payload mod id",
                    own.modId(), "not listed in the container at all");
        }
        if (!declared.id().equals(own.id())) {
            throw divergence(descriptor, manifest, "payload id", own.id(), declared.id());
        }
        if (!declared.platformFactory().equals(own.platformFactory())) {
            throw divergence(descriptor, manifest, "platformFactory",
                    own.platformFactory(), declared.platformFactory());
        }
        if (declared.classfileMajor() != own.classfileMajor()) {
            throw divergence(descriptor, manifest, "classfileMajor",
                    String.valueOf(own.classfileMajor()),
                    String.valueOf(declared.classfileMajor()));
        }
        if (!declared.requires().minecraft().equals(own.requires().minecraft())) {
            throw divergence(descriptor, manifest, "requires.minecraft",
                    own.requires().minecraft().toString(),
                    declared.requires().minecraft().toString());
        }
        if (!manifest.container().modId().equals(descriptor.container().modId())) {
            throw divergence(descriptor, manifest, "container modId",
                    descriptor.container().modId(), manifest.container().modId());
        }
    }

    private static OmniException divergence(PayloadManifest descriptor, ContainerManifest manifest,
            String field, String inPayload, String inContainer) {
        return new OmniException(ErrorCode.OMNI_2011, Messages.report(ErrorCode.OMNI_2011)
                .detected("container", manifest.container().modId())
                .detected("payload", descriptor.payload().modId())
                .detected("field", field)
                .detected(OmniFormat.PAYLOAD_DESCRIPTOR_PATH + " says", inPayload)
                .detected(OmniFormat.CONTAINER_MANIFEST_PATH + " says", inContainer)
                .detail("The container and the payload it contains describe the payload")
                .detail("differently. Both files are generated from one source, so this means the")
                .detail("jar was assembled from two builds or modified after being built.")
                .fix("re-download the mod from its official source")
                .fix("if you built it yourself, run ./gradlew clean buildUniversalJar")
                .build());
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // Read-only stream from the loader's file system.
        }
    }

    private DevFallback() {
        throw new AssertionError("no instances");
    }
}
