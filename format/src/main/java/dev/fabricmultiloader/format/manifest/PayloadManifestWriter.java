package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.Map;

/**
 * Serialises {@code omni/payload.json} — the copy of the container identity that makes a payload
 * self-sufficient.
 *
 * <p>The inverse of {@link PayloadManifestReader}, and deliberately written from the container
 * manifest rather than from a separate model: the two files describe the same payload, and a
 * generator that could produce them from different sources would be able to produce them
 * disagreeing, which is exactly what {@code OMNI-2011} exists to catch. Here that is structurally
 * impossible.
 */
public final class PayloadManifestWriter {

    /**
     * Writes a payload's self-description.
     *
     * @param manifest the container manifest
     * @param payload the payload to describe
     * @return the JSON document, with a trailing newline
     */
    public static String write(ContainerManifest manifest, PayloadDescriptor payload) {
        return Json.writeDocument(toJson(manifest, payload));
    }

    /**
     * Builds the JSON tree.
     *
     * @param manifest the container manifest
     * @param payload the payload to describe
     * @return the tree
     */
    public static JsonObject toJson(ContainerManifest manifest, PayloadDescriptor payload) {
        ContainerInfo container = manifest.container();

        JsonObject root = new JsonObject();
        root.set("formatId", manifest.formatId());
        root.set("schemaVersion", manifest.schemaVersion());
        root.set("payloadId", payload.id());
        root.set("modId", payload.modId());
        root.set("modVersion", payload.modVersion().toString());
        root.set("displayName", payload.displayName());
        root.set("platformFactory", payload.platformFactory());
        root.set("classfileMajor", payload.classfileMajor());
        root.set("packages", strings(payload.packages()));
        if (!payload.provides().isEmpty()) {
            root.set("provides", strings(payload.provides()));
        }
        root.set("mappings", new JsonObject()
                .set("namespace", payload.mappings().namespace())
                .set("provider", payload.mappings().provider())
                .set("build", payload.mappings().build()));

        JsonObject containerRef = new JsonObject();
        containerRef.set("modId", container.modId());
        containerRef.set("modVersion", container.modVersion().toString());
        containerRef.set("displayName", container.displayName());
        containerRef.set("commonPackages", strings(container.commonPackages()));
        containerRef.set("entrypoints", entrypoints(manifest.entrypoints()));
        root.set("container", containerRef);

        root.set("requires", requires(payload.requires()));
        if (!payload.capabilities().isEmpty()) {
            root.set("capabilities", strings(payload.capabilities()));
        }
        if (!payload.resourcesDigest().isEmpty()) {
            root.set("resourcesDigest", payload.resourcesDigest());
        }
        return root;
    }

    private static JsonObject entrypoints(EntrypointSet entrypoints) {
        JsonObject json = new JsonObject();
        for (EntrypointSet.Phase phase : EntrypointSet.Phase.values()) {
            java.util.List<String> classes = entrypoints.forPhase(phase);
            if (!classes.isEmpty()) {
                json.set(phase.id(), strings(classes));
            }
        }
        return json;
    }

    private static JsonObject requires(Requirements requires) {
        JsonObject json = new JsonObject();
        json.set("minecraft", predicates(requires.minecraft()));
        json.set("fabricloader", predicates(requires.fabricLoader()));
        json.set("java", predicates(requires.java()));
        json.set("environment", requires.environment().id());

        if (!requires.mods().isEmpty()) {
            json.set("mods", modMap(requires.mods()));
        }
        if (!requires.optionalMods().isEmpty()) {
            json.set("optionalMods", modMap(requires.optionalMods()));
        }
        return json;
    }

    private static JsonObject modMap(Map<String, VersionRange> mods) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, VersionRange> mod : mods.entrySet()) {
            json.set(mod.getKey(), predicates(mod.getValue()));
        }
        return json;
    }

    private static JsonArray predicates(VersionRange range) {
        JsonArray array = new JsonArray();
        for (String predicate : range.toPredicates()) {
            array.add(predicate);
        }
        return array;
    }

    private static JsonArray strings(java.util.List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private PayloadManifestWriter() {
        throw new AssertionError("no instances");
    }
}
