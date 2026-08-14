package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonLimits;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.json.JsonPointer;
import dev.fabricmultiloader.format.json.JsonValue;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads {@code META-INF/omni-container.json} into a {@link ContainerManifest}.
 *
 * <p>Two behaviours look inconsistent and are not. <b>Unknown fields are ignored</b> when reading:
 * that is what allows the format to gain optional fields without a schema bump, and it means an
 * older runtime keeps working with a newer container as long as the semantics did not change (a
 * container that genuinely needs newer semantics raises {@code minRuntime} instead). The
 * <b>validator rejects</b> the very same unknown fields via {@link #findUnknownFields(String)},
 * because nothing unknown may arise in a project's own build output.
 */
public final class ManifestReader {

    private static final Set<String> ROOT_KEYS = keys(
            "formatId", "schemaVersion", "generator", "container", "entrypoints", "payloads",
            "diagnostics");
    private static final Set<String> GENERATOR_KEYS = keys("tool", "version", "timestamp", "buildJdk");
    private static final Set<String> CONTAINER_KEYS = keys(
            "modId", "modVersion", "displayName", "commonPackages", "commonPackaging",
            "baselineJavaMajor", "runtime", "minRuntime", "payloadAlias", "strict",
            "verifyIntegrity", "signatures", "experiments");
    private static final Set<String> RUNTIME_KEYS = keys("modId", "version", "range", "file", "sha256");
    private static final Set<String> PAYLOAD_KEYS = keys(
            "id", "modId", "modVersion", "displayName", "file", "sha256", "size", "classfileMajor",
            "priority", "platformFactory", "packages", "requires", "provides", "breaks", "mappings",
            "mixins", "refmaps", "accessWidener", "nestedJars", "resourcesDigest", "capabilities",
            "experiments");
    private static final Set<String> REQUIRES_KEYS = keys(
            "minecraft", "fabricloader", "java", "environment", "mods", "optionalMods");
    private static final Set<String> MAPPINGS_KEYS = keys("namespace", "provider", "build");
    private static final Set<String> MIXIN_KEYS = keys("config", "environment");
    private static final Set<String> DIAGNOSTICS_KEYS = keys(
            "supportUrl", "documentationUrl", "downloadUrl", "contactLabel");

    /** Reads a manifest from JSON text. */
    public static ContainerManifest read(String json) {
        return read(Json.parseObject(json));
    }

    /** Reads a manifest from a stream of UTF-8 JSON. The stream is consumed but not closed. */
    public static ContainerManifest read(InputStream in) {
        return read(Json.parse(in, JsonLimits.DEFAULT).asObject());
    }

    /** Reads a manifest from an already-parsed document. */
    public static ContainerManifest read(JsonObject root) {
        String formatId = root.getString("formatId");
        int schemaVersion = root.getInt("schemaVersion");
        requireSupported(formatId, schemaVersion);

        ContainerManifest.Builder manifest = ContainerManifest.builder()
                .formatId(formatId)
                .schemaVersion(schemaVersion)
                .container(readContainer(root.getObject("container")))
                .entrypoints(readEntrypoints(root.optObject("entrypoints")))
                .diagnostics(readDiagnostics(root.optObject("diagnostics")));

        JsonObject generator = root.optObject("generator");
        if (generator != null) {
            manifest.generator(new ContainerManifest.GeneratorInfo(
                    generator.optString("tool", ""),
                    generator.optString("version", ""),
                    generator.optString("timestamp", ""),
                    generator.optString("buildJdk", "")));
        }

        JsonArray payloads = root.getArray("payloads");
        for (int i = 0; i < payloads.size(); i++) {
            manifest.payload(readPayload(payloads.getObject(i)));
        }
        return manifest.build();
    }

    private static void requireSupported(String formatId, int schemaVersion) {
        boolean knownFamily = formatId != null && formatId.startsWith("omni/");
        if (!knownFamily || schemaVersion > OmniFormat.SCHEMA_VERSION || schemaVersion < 1) {
            throw new OmniException(ErrorCode.OMNI_2002, Messages.report(ErrorCode.OMNI_2002)
                    .detected("formatId", formatId == null ? "(missing)" : formatId)
                    .detected("schemaVersion", schemaVersion)
                    .detected("this runtime supports", OmniFormat.FORMAT_ID
                            + " up to schema " + OmniFormat.SCHEMA_VERSION)
                    .detail(knownFamily
                            ? "This container was built by a newer FabricMultiLoader than the one installed."
                            : "This file does not look like an Omni container at all.")
                    .fix(knownFamily
                            ? "update the mod that ships the newest FabricMultiLoader runtime"
                            : "re-download the mod — the jar may be corrupted")
                    .build());
        }
    }

    private static ContainerInfo readContainer(JsonObject json) {
        JsonObject runtime = json.getObject("runtime");
        ContainerInfo.RuntimeRef runtimeRef = new ContainerInfo.RuntimeRef(
                runtime.getString("modId"),
                SemVer.parse(runtime.getString("version")),
                readRange(runtime, "range", VersionRange.ALL),
                runtime.getString("file"),
                runtime.optString("sha256", ""));

        ContainerInfo.Builder container = ContainerInfo.builder()
                .modId(json.getString("modId"))
                .modVersion(json.getString("modVersion"))
                .displayName(json.optString("displayName", ""))
                .commonPackaging(CommonPackaging.parse(
                        json.optString("commonPackaging", CommonPackaging.SHARED.id()),
                        "container.commonPackaging"))
                .baselineJavaMajor(json.getInt("baselineJavaMajor"))
                .runtime(runtimeRef)
                .minRuntime(SemVer.parse(json.optString("minRuntime", "1.0.0")))
                .payloadAlias(json.getString("payloadAlias"))
                .strict(json.optBoolean("strict", true))
                .verifyIntegrity(json.optBoolean("verifyIntegrity", true));

        for (String packageName : json.getArray("commonPackages").asStringList()) {
            container.commonPackages(packageName);
        }
        return container.build();
    }

    private static EntrypointSet readEntrypoints(JsonObject json) {
        EntrypointSet.Builder entrypoints = EntrypointSet.builder();
        if (json == null) {
            return entrypoints.build();
        }
        for (String key : json.keys()) {
            EntrypointSet.Phase phase = EntrypointSet.Phase.byId(key);
            if (phase == null) {
                continue; // forward compatibility: an unknown phase is ignored, not fatal
            }
            entrypoints.addAll(phase, json.getArray(key).asStringList());
        }
        return entrypoints.build();
    }

    private static ContainerManifest.DiagnosticsInfo readDiagnostics(JsonObject json) {
        if (json == null) {
            return ContainerManifest.DiagnosticsInfo.empty();
        }
        return new ContainerManifest.DiagnosticsInfo(
                json.optString("supportUrl", ""),
                json.optString("documentationUrl", ""),
                json.optString("downloadUrl", ""),
                json.optString("contactLabel", ""));
    }

    private static PayloadDescriptor readPayload(JsonObject json) {
        PayloadDescriptor.Builder payload = PayloadDescriptor.builder()
                .id(json.getString("id"))
                .modId(json.getString("modId"))
                .modVersion(json.getString("modVersion"))
                .displayName(json.optString("displayName", ""))
                .file(json.getString("file"))
                .integrity(json.optString("sha256", ""), json.getLong("size"))
                .classfileMajor(json.getInt("classfileMajor"))
                .priority(json.optInt("priority", 0))
                .platformFactory(json.getString("platformFactory"))
                .requires(readRequires(json.getObject("requires")))
                .resourcesDigest(json.optString("resourcesDigest", ""))
                .accessWidener(json.has("accessWidener") && !json.require("accessWidener").isNull()
                        ? json.getString("accessWidener") : null);

        payload.packages(toArray(json.getArray("packages").asStringList()));
        payload.provides(toArray(optStringList(json, "provides")));
        payload.breaks(toArray(optStringList(json, "breaks")));
        payload.refmaps(toArray(optStringList(json, "refmaps")));
        payload.nestedJars(toArray(optStringList(json, "nestedJars")));
        payload.capabilities(toArray(optStringList(json, "capabilities")));

        JsonObject mappings = json.optObject("mappings");
        if (mappings != null) {
            payload.mappings(new MappingsInfo(
                    mappings.getString("namespace"),
                    mappings.getString("provider"),
                    mappings.optString("build", "")));
        }

        JsonArray mixins = json.optArray("mixins");
        if (mixins != null) {
            for (JsonValue entry : mixins) {
                if (entry.isString()) {
                    payload.mixin(entry.asString(), EnvironmentConstraint.BOTH);
                } else {
                    JsonObject mixin = entry.asObject();
                    payload.mixin(mixin.getString("config"), EnvironmentConstraint.parse(
                            mixin.optString("environment", EnvironmentConstraint.BOTH.id()),
                            "payloads[].mixins[].environment"));
                }
            }
        }
        return payload.build();
    }

    private static Requirements readRequires(JsonObject json) {
        Requirements.Builder requires = Requirements.builder()
                .minecraft(readRange(json, "minecraft", VersionRange.ALL))
                .fabricLoader(readRange(json, "fabricloader", VersionRange.ALL))
                .java(readRange(json, "java", VersionRange.ALL))
                .environment(EnvironmentConstraint.parse(
                        json.optString("environment", EnvironmentConstraint.BOTH.id()),
                        "requires.environment"));

        JsonObject mods = json.optObject("mods");
        if (mods != null) {
            for (String modId : mods.keys()) {
                requires.mod(modId, readRange(mods, modId, VersionRange.ALL));
            }
        }
        JsonObject optionalMods = json.optObject("optionalMods");
        if (optionalMods != null) {
            for (String modId : optionalMods.keys()) {
                requires.optionalMod(modId, readRange(optionalMods, modId, VersionRange.ALL));
            }
        }
        return requires.build();
    }

    /** Accepts both a single predicate string and Fabric's OR array of them. */
    private static VersionRange readRange(JsonObject parent, String key, VersionRange fallback) {
        JsonValue value = parent.get(key);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isString()) {
            return VersionRange.parse(value.asString());
        }
        List<String> predicates = value.asArray().asStringList();
        if (predicates.isEmpty()) {
            return VersionRange.EMPTY;
        }
        return VersionRange.parse(toArray(predicates));
    }

    private static List<String> optStringList(JsonObject json, String key) {
        JsonArray array = json.optArray(key);
        return array == null ? Collections.<String>emptyList() : array.asStringList();
    }

    private static String[] toArray(List<String> values) {
        return values.toArray(new String[0]);
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }

    // ------------------------------------------------------------------ validator support

    /**
     * Finds fields the current schema does not define, as JSON pointers.
     *
     * <p>Used by the validator for {@code OMNI-1002}. Readers ignore what this reports; a project's
     * own build must not produce any of it, because an unrecognised field is either a typo or a
     * generator that has drifted ahead of its reader.
     *
     * @param json the manifest document
     * @return unknown field pointers, in document order; empty when the manifest is clean
     */
    public static List<String> findUnknownFields(String json) {
        List<String> unknown = new ArrayList<String>();
        JsonObject root = Json.parseObject(json);
        collectUnknown(root, ROOT_KEYS, JsonPointer.ROOT, unknown);

        collectUnknown(root.optObject("generator"), GENERATOR_KEYS, "/generator", unknown);
        collectUnknown(root.optObject("diagnostics"), DIAGNOSTICS_KEYS, "/diagnostics", unknown);

        JsonObject container = root.optObject("container");
        if (container != null) {
            collectUnknown(container, CONTAINER_KEYS, "/container", unknown);
            collectUnknown(container.optObject("runtime"), RUNTIME_KEYS, "/container/runtime", unknown);
        }

        JsonObject entrypoints = root.optObject("entrypoints");
        if (entrypoints != null) {
            for (String key : entrypoints.keys()) {
                if (EntrypointSet.Phase.byId(key) == null) {
                    unknown.add(JsonPointer.child("/entrypoints", key));
                }
            }
        }

        JsonArray payloads = root.optArray("payloads");
        if (payloads != null) {
            for (int i = 0; i < payloads.size(); i++) {
                String base = JsonPointer.index("/payloads", i);
                JsonObject payload = payloads.getObject(i);
                collectUnknown(payload, PAYLOAD_KEYS, base, unknown);
                collectUnknown(payload.optObject("requires"), REQUIRES_KEYS,
                        base + "/requires", unknown);
                collectUnknown(payload.optObject("mappings"), MAPPINGS_KEYS,
                        base + "/mappings", unknown);
                JsonArray mixins = payload.optArray("mixins");
                if (mixins != null) {
                    for (int m = 0; m < mixins.size(); m++) {
                        JsonValue entry = mixins.get(m);
                        if (entry.isObject()) {
                            collectUnknown(entry.asObject(), MIXIN_KEYS,
                                    JsonPointer.index(base + "/mixins", m), unknown);
                        }
                    }
                }
            }
        }
        return unknown;
    }

    private static void collectUnknown(
            JsonObject json, Set<String> known, String pointer, List<String> into) {
        if (json == null) {
            return;
        }
        for (String key : json.unknownKeys(known)) {
            into.add(JsonPointer.child(pointer, key));
        }
    }

    private ManifestReader() {
        throw new AssertionError("no instances");
    }
}
