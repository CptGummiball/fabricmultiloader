package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonLimits;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.json.JsonValue;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Reads {@code omni/payload.json} into a {@link PayloadManifest}.
 *
 * <p>Follows the same rule as {@link ManifestReader}: unknown fields are ignored here and rejected
 * by the validator, so the format can gain optional fields without a schema bump while a project's
 * own build output stays clean.
 *
 * <p>The fields a payload descriptor does not carry are the ones only a container can know — where
 * the nested jar sits, its hash and size, its build-time priority. Those are filled with neutral
 * values, which is exactly right for the development fallback: without a container there is no
 * nested jar to locate or verify.
 */
public final class PayloadManifestReader {

    /** Reads a payload manifest from JSON text. */
    public static PayloadManifest read(String json) {
        return read(Json.parseObject(json));
    }

    /** Reads a payload manifest from a stream of UTF-8 JSON. The stream is consumed but not closed. */
    public static PayloadManifest read(InputStream in) {
        return read(Json.parse(in, JsonLimits.DEFAULT).asObject());
    }

    /** Reads a payload manifest from an already-parsed document. */
    public static PayloadManifest read(JsonObject root) {
        String formatId = root.getString("formatId");
        int schemaVersion = root.getInt("schemaVersion");
        requireSupported(formatId, schemaVersion);

        PayloadManifest.ContainerRef container = readContainer(root.getObject("container"));

        String modId = root.getString("modId");
        PayloadDescriptor.Builder payload = PayloadDescriptor.builder()
                .id(root.getString("payloadId"))
                .modId(modId)
                // A payload version is derivable from the container's, so it stays optional; a
                // generator that records it explicitly is preferred and simply wins here.
                .modVersion(root.optString("modVersion", container.modVersion().toString()))
                .displayName(root.optString("displayName", container.displayName()))
                // Where this payload would sit inside a container. Nothing reads it here — a
                // standalone payload is the mod on disk, and the integrity check is skipped along
                // with the hash below — but the canonical location is the honest value to report in
                // a diagnostic, and the descriptor does not record one because only a container
                // knows it.
                .file(OmniFormat.NESTED_JAR_ROOT + modId + ".jar")
                .integrity("", 0L)
                .classfileMajor(root.getInt("classfileMajor"))
                .priority(0)
                .platformFactory(root.getString("platformFactory"))
                .requires(readRequires(root.getObject("requires")))
                .resourcesDigest(root.optString("resourcesDigest", ""));

        payload.packages(toArray(root.getArray("packages").asStringList()));
        payload.capabilities(toArray(optStringList(root, "capabilities")));
        payload.provides(toArray(optStringList(root, "provides")));

        JsonObject mappings = root.optObject("mappings");
        if (mappings != null) {
            payload.mappings(new MappingsInfo(
                    mappings.getString("namespace"),
                    mappings.getString("provider"),
                    mappings.optString("build", "")));
        }

        return PayloadManifest.builder()
                .formatId(formatId)
                .schemaVersion(schemaVersion)
                .container(container)
                .payload(payload.build())
                .build();
    }

    private static void requireSupported(String formatId, int schemaVersion) {
        boolean knownFamily = formatId != null && formatId.startsWith("omni/");
        if (!knownFamily || schemaVersion > OmniFormat.SCHEMA_VERSION || schemaVersion < 1) {
            throw new OmniException(ErrorCode.OMNI_2002, Messages.report(ErrorCode.OMNI_2002)
                    .detected("file", OmniFormat.PAYLOAD_DESCRIPTOR_PATH)
                    .detected("formatId", formatId == null ? "(missing)" : formatId)
                    .detected("schemaVersion", schemaVersion)
                    .detected("this runtime supports", OmniFormat.FORMAT_ID
                            + " up to schema " + OmniFormat.SCHEMA_VERSION)
                    .detail(knownFamily
                            ? "This payload was built by a newer FabricMultiLoader than the one installed."
                            : "This file does not look like an Omni payload descriptor at all.")
                    .fix(knownFamily
                            ? "update the mod that ships the newest FabricMultiLoader runtime"
                            : "re-download the mod — the jar may be corrupted")
                    .build());
        }
    }

    private static PayloadManifest.ContainerRef readContainer(JsonObject json) {
        return new PayloadManifest.ContainerRef(
                json.getString("modId"),
                SemVer.parseLenient(json.getString("modVersion")),
                json.optString("displayName", ""),
                json.getArray("commonPackages").asStringList(),
                readEntrypoints(json.optObject("entrypoints")));
    }

    private static EntrypointSet readEntrypoints(JsonObject json) {
        EntrypointSet.Builder entrypoints = EntrypointSet.builder();
        if (json == null) {
            return entrypoints.build();
        }
        for (String key : json.keys()) {
            EntrypointSet.Phase phase = EntrypointSet.Phase.byId(key);
            if (phase == null) {
                continue; // forward compatibility, as in the container manifest
            }
            entrypoints.addAll(phase, json.getArray(key).asStringList());
        }
        return entrypoints.build();
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

    private PayloadManifestReader() {
        throw new AssertionError("no instances");
    }
}
