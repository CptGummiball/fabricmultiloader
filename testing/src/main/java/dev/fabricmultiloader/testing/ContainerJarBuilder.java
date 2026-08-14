package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.hash.Sha256;
import dev.fabricmultiloader.format.manifest.ContainerInfo;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.FabricModJsonWriter;
import dev.fabricmultiloader.format.manifest.ManifestWriter;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a complete universal jar: the container mod, with every payload nested inside it.
 *
 * <p>The manifest is rewritten as it goes, because the hash and size of a nested jar are only known
 * once that jar exists. Computing them here rather than accepting placeholders is what makes the
 * fixture usable for integrity tests: the container that comes out is one whose {@code sha256}
 * entries are true, so a test that corrupts a payload afterwards is testing the check rather than
 * the fixture.
 */
public final class ContainerJarBuilder {

    private final ContainerManifest source;
    private final JarWriter jar = new JarWriter();
    private final Map<String, byte[]> nested = new LinkedHashMap<String, byte[]>();

    private ContainerManifest resolved;

    /**
     * @param manifest the container manifest, whose payload hashes may still be empty
     */
    public ContainerJarBuilder(ContainerManifest manifest) {
        this.source = manifest;
    }

    /**
     * Nests a payload jar, recording its real hash and size in the manifest.
     *
     * @param payload the payload descriptor
     * @param content the payload jar
     * @return this builder
     */
    public ContainerJarBuilder payload(PayloadDescriptor payload, byte[] content) {
        nested.put(payload.file(), content.clone());
        resolved = null;
        return this;
    }

    /** Nests a payload built by {@link PayloadJarBuilder}. */
    public ContainerJarBuilder payload(PayloadJarBuilder builder) {
        return payload(builder.payload(), builder.toBytes());
    }

    /** Nests the runtime library jar. */
    public ContainerJarBuilder runtime(byte[] content) {
        nested.put(source.container().runtime().file(), content.clone());
        return this;
    }

    /** Adds an arbitrary entry, e.g. a common class file or the icon. */
    public ContainerJarBuilder entry(String path, String content) {
        jar.entry(path, content);
        return this;
    }

    /** Replaces the generated {@code fabric.mod.json}. */
    public ContainerJarBuilder overrideModJson(String content) {
        jar.entry("fabric.mod.json", content);
        return this;
    }

    /**
     * The manifest as it will be written, with the real hash and size of every nested payload.
     *
     * @return the resolved manifest
     */
    public ContainerManifest manifest() {
        if (resolved != null) {
            return resolved;
        }
        ContainerManifest.Builder rebuilt = ContainerManifest.builder()
                .formatId(source.formatId())
                .schemaVersion(source.schemaVersion())
                .generator(source.generator())
                .container(source.container())
                .entrypoints(source.entrypoints())
                .diagnostics(source.diagnostics());

        for (PayloadDescriptor payload : source.payloads()) {
            byte[] content = nested.get(payload.file());
            rebuilt.payload(content == null
                    ? payload
                    : withIntegrity(payload, Sha256.of(content), content.length));
        }
        resolved = rebuilt.build();
        return resolved;
    }

    /** Writes the container jar to a file. */
    public Path writeTo(Path file) {
        return build().writeTo(file);
    }

    /** The container jar as bytes. */
    public byte[] toBytes() {
        return build().toBytes();
    }

    private JarWriter build() {
        ContainerManifest complete = manifest();
        if (!jar.has("fabric.mod.json")) {
            jar.entry("fabric.mod.json", FabricModJsonWriter.container(complete));
        }
        jar.entry(OmniFormat.CONTAINER_MANIFEST_PATH, ManifestWriter.writeDocument(complete));
        for (Map.Entry<String, byte[]> entry : nested.entrySet()) {
            // STORED, per chapter 10.5: re-deflating an already-compressed zip buys under a
            // percent and makes the loader's extraction measurably slower.
            jar.storedEntry(entry.getKey(), entry.getValue());
        }
        return jar;
    }

    private static PayloadDescriptor withIntegrity(
            PayloadDescriptor payload, String sha256, long size) {
        PayloadDescriptor.Builder rebuilt = PayloadDescriptor.builder()
                .id(payload.id())
                .modId(payload.modId())
                .modVersion(payload.modVersion())
                .displayName(payload.displayName())
                .file(payload.file())
                .integrity(sha256, size)
                .classfileMajor(payload.classfileMajor())
                .priority(payload.priority())
                .platformFactory(payload.platformFactory())
                .requires(payload.requires())
                .mappings(payload.mappings())
                .accessWidener(payload.accessWidener())
                .resourcesDigest(payload.resourcesDigest());

        rebuilt.packages(payload.packages().toArray(new String[0]));
        rebuilt.provides(payload.provides().toArray(new String[0]));
        rebuilt.breaks(payload.breaks().toArray(new String[0]));
        rebuilt.refmaps(payload.refmaps().toArray(new String[0]));
        rebuilt.nestedJars(payload.nestedJars().toArray(new String[0]));
        rebuilt.capabilities(payload.capabilities().toArray(new String[0]));
        for (dev.fabricmultiloader.format.manifest.MixinConfigRef mixin : payload.mixins()) {
            rebuilt.mixin(mixin.config(), mixin.environment());
        }
        return rebuilt.build();
    }

    /** Convenience: the container's identity. */
    public ContainerInfo container() {
        return source.container();
    }
}
