package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.List;
import java.util.Map;

/**
 * Serialises a {@link ContainerManifest} in the canonical form.
 *
 * <p>Key order is normative and deliberately <em>not</em> alphabetical (chapter 11.5): fields are
 * grouped so that a reviewer diffing two release artifacts sees related changes together. Since the
 * order is fixed here rather than at the call site, reproducibility does not depend on how a
 * generator happens to build its objects.
 */
public final class ManifestWriter {

    /** Serialises to canonical JSON, without a trailing newline. */
    public static String write(ContainerManifest manifest) {
        return Json.write(toJson(manifest));
    }

    /** Serialises as file content: canonical JSON plus one trailing newline. */
    public static String writeDocument(ContainerManifest manifest) {
        return Json.writeDocument(toJson(manifest));
    }

    /** Builds the JSON tree, in normative key order. */
    public static JsonObject toJson(ContainerManifest manifest) {
        JsonObject root = new JsonObject();
        root.set("formatId", manifest.formatId());
        root.set("schemaVersion", manifest.schemaVersion());
        root.set("generator", generator(manifest.generator()));
        root.set("container", container(manifest.container()));
        root.set("entrypoints", entrypoints(manifest.entrypoints()));

        JsonArray payloads = new JsonArray();
        for (PayloadDescriptor payload : manifest.payloads()) {
            payloads.add(payload(payload));
        }
        root.set("payloads", payloads);
        root.set("diagnostics", diagnostics(manifest.diagnostics()));
        return root;
    }

    private static JsonObject generator(ContainerManifest.GeneratorInfo info) {
        return new JsonObject()
                .set("tool", info.tool())
                .set("version", info.version())
                .set("timestamp", info.timestamp())
                .set("buildJdk", info.buildJdk());
    }

    private static JsonObject container(ContainerInfo info) {
        JsonObject json = new JsonObject()
                .set("modId", info.modId())
                .set("modVersion", info.modVersion().toString())
                .set("displayName", info.displayName())
                .set("commonPackages", stringArray(info.commonPackages()))
                .set("commonPackaging", info.commonPackaging().id())
                .set("baselineJavaMajor", info.baselineJavaMajor());

        ContainerInfo.RuntimeRef runtime = info.runtime();
        json.set("runtime", new JsonObject()
                .set("modId", runtime.modId())
                .set("version", runtime.version().toString())
                .set("range", predicates(runtime.range()))
                .set("file", runtime.file())
                .set("sha256", runtime.sha256()));

        return json
                .set("minRuntime", info.minRuntime().toString())
                .set("payloadAlias", info.payloadAlias())
                .set("strict", info.strict())
                .set("verifyIntegrity", info.verifyIntegrity());
    }

    private static JsonObject entrypoints(EntrypointSet set) {
        JsonObject json = new JsonObject();
        for (EntrypointSet.Phase phase : EntrypointSet.Phase.values()) {
            List<String> classes = set.forPhase(phase);
            if (!classes.isEmpty()) {
                json.set(phase.id(), stringArray(classes));
            }
        }
        return json;
    }

    private static JsonObject payload(PayloadDescriptor payload) {
        JsonObject json = new JsonObject()
                .set("id", payload.id())
                .set("modId", payload.modId())
                .set("modVersion", payload.modVersion().toString())
                .set("displayName", payload.displayName())
                .set("file", payload.file())
                .set("sha256", payload.sha256())
                .set("size", payload.size())
                .set("classfileMajor", payload.classfileMajor())
                .set("priority", payload.priority())
                .set("platformFactory", payload.platformFactory())
                .set("packages", stringArray(payload.packages()))
                .set("requires", requires(payload.requires()))
                .set("provides", stringArray(payload.provides()))
                .set("breaks", stringArray(payload.breaks()));

        MappingsInfo mappings = payload.mappings();
        json.set("mappings", new JsonObject()
                .set("namespace", mappings.namespace())
                .set("provider", mappings.provider())
                .set("build", mappings.build()));

        JsonArray mixins = new JsonArray();
        for (MixinConfigRef mixin : payload.mixins()) {
            mixins.add(new JsonObject()
                    .set("config", mixin.config())
                    .set("environment", mixin.environment().id()));
        }
        json.set("mixins", mixins);
        json.set("refmaps", stringArray(payload.refmaps()));

        if (payload.accessWidener() == null) {
            json.set("accessWidener", (String) null);
        } else {
            json.set("accessWidener", payload.accessWidener());
        }

        return json
                .set("nestedJars", stringArray(payload.nestedJars()))
                .set("resourcesDigest", payload.resourcesDigest())
                .set("capabilities", stringArray(payload.capabilities()));
    }

    private static JsonObject requires(Requirements requires) {
        JsonObject json = new JsonObject()
                .set("minecraft", predicates(requires.minecraft()))
                .set("fabricloader", predicates(requires.fabricLoader()))
                .set("java", predicates(requires.java()))
                .set("environment", requires.environment().id());

        JsonObject mods = new JsonObject();
        for (Map.Entry<String, VersionRange> entry : requires.mods().entrySet()) {
            mods.set(entry.getKey(), predicates(entry.getValue()));
        }
        json.set("mods", mods);

        JsonObject optionalMods = new JsonObject();
        for (Map.Entry<String, VersionRange> entry : requires.optionalMods().entrySet()) {
            optionalMods.set(entry.getKey(), predicates(entry.getValue()));
        }
        return json.set("optionalMods", optionalMods);
    }

    private static JsonObject diagnostics(ContainerManifest.DiagnosticsInfo info) {
        return new JsonObject()
                .set("supportUrl", info.supportUrl())
                .set("documentationUrl", info.documentationUrl())
                .set("downloadUrl", info.downloadUrl())
                .set("contactLabel", info.contactLabel());
    }

    /** Ranges are always written as an array, even with a single element — Fabric reads both. */
    private static JsonArray predicates(VersionRange range) {
        JsonArray array = new JsonArray();
        for (String predicate : range.toPredicates()) {
            array.add(predicate);
        }
        return array;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private ManifestWriter() {
        throw new AssertionError("no instances");
    }
}
