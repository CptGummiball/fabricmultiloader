package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.json.Json;
import dev.fabricmultiloader.format.json.JsonArray;
import dev.fabricmultiloader.format.json.JsonObject;
import dev.fabricmultiloader.format.version.Interval;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Derives the loader's view of a universal jar from the Omni manifest.
 *
 * <p>This is where the architecture's central claim becomes a file. The Omni manifest is the source
 * of truth, but Fabric never reads it — the selection is made by the solver from these
 * {@code fabric.mod.json} files, so everything the design says about disjoint domains, exclusivity
 * and Java requirements has to survive the translation into them. The validator checks the two
 * agree ({@code OMNI-1011}); the derivation lives here so that there is only one of it.
 *
 * <p>It sits in {@code format} rather than in the Gradle plugin because three consumers need it: the
 * plugin that produces real jars, the loader conformance harness that produces synthetic ones to
 * prove the load-bearing assumption, and the validator that reads them back.
 *
 * <p>Two derivations look like mistakes and are not. {@code depends.java} on the container takes the
 * <em>minimum</em> across payloads — the container must load on the oldest JVM any of its payloads
 * supports, or the diagnostic explaining why none matched would never run. And the container
 * declares Fabric API under {@code recommends}, not {@code depends}: the required minimum differs
 * per payload and is declared hard there, so a container-level version could only be wrong for some
 * payload.
 */
public final class FabricModJsonWriter {

    /** The container's {@code preLaunch} entrypoint class. */
    public static final String CONTAINER_ENTRYPOINT =
            "dev.fabricmultiloader.runtime.entrypoint.ContainerPreLaunch";

    /** The payload's {@code preLaunch} entrypoint class. */
    public static final String PAYLOAD_PRELAUNCH =
            "dev.fabricmultiloader.runtime.entrypoint.PayloadPreLaunch";

    /** The payload's {@code main} entrypoint class. */
    public static final String PAYLOAD_MAIN =
            "dev.fabricmultiloader.runtime.entrypoint.PayloadMain";

    /** The payload's {@code client} entrypoint class. */
    public static final String PAYLOAD_CLIENT =
            "dev.fabricmultiloader.runtime.entrypoint.PayloadClient";

    /** The payload's {@code server} entrypoint class. */
    public static final String PAYLOAD_SERVER =
            "dev.fabricmultiloader.runtime.entrypoint.PayloadServer";

    /**
     * Writes the container's {@code fabric.mod.json}.
     *
     * @param manifest the container manifest
     * @return the JSON document, with a trailing newline
     */
    public static String container(ContainerManifest manifest) {
        return Json.writeDocument(containerJson(manifest));
    }

    /**
     * The container's loader metadata as a JSON tree.
     *
     * @param manifest the container manifest
     * @return the tree
     */
    public static JsonObject containerJson(ContainerManifest manifest) {
        ContainerInfo container = manifest.container();

        JsonObject root = new JsonObject();
        root.set("schemaVersion", 1);
        root.set("id", container.modId());
        root.set("version", container.modVersion().toString());
        if (!container.displayName().isEmpty()) {
            root.set("name", container.displayName());
        }
        root.set("environment", containerEnvironment(manifest).id());

        root.set("entrypoints", new JsonObject()
                .set("preLaunch", new JsonArray().add(CONTAINER_ENTRYPOINT)));

        JsonArray jars = new JsonArray().add(jar(container.runtime().file()));
        for (PayloadDescriptor payload : sortedPayloads(manifest)) {
            jars.add(jar(payload.file()));
        }
        root.set("jars", jars);

        JsonObject depends = new JsonObject();
        depends.set("fabricloader", highestMinimumLoader(manifest).toString());
        depends.set("java", ">=" + container.baselineJavaMajor());
        depends.set(OmniFormat.RUNTIME_MOD_ID, container.runtime().range().toString());
        depends.set("minecraft", rangeArray(minecraftUnion(manifest)));
        root.set("depends", depends);

        if (anyPayloadNeeds(manifest, "fabric-api")) {
            root.set("recommends", new JsonObject().set("fabric-api", "*"));
        }

        JsonArray payloadIds = new JsonArray();
        for (PayloadDescriptor payload : sortedPayloads(manifest)) {
            payloadIds.add(payload.modId());
        }
        root.set("custom", new JsonObject()
                .set("omni", new JsonObject()
                        .set("format", manifest.formatId())
                        .set("manifest", OmniFormat.CONTAINER_MANIFEST_PATH)
                        .set("payloads", payloadIds)));
        return root;
    }

    /**
     * Writes one payload's {@code fabric.mod.json} — the file the solver actually selects on.
     *
     * @param manifest the container manifest
     * @param payload the payload to describe
     * @return the JSON document, with a trailing newline
     */
    public static String payload(ContainerManifest manifest, PayloadDescriptor payload) {
        return Json.writeDocument(payloadJson(manifest, payload));
    }

    /**
     * A payload's loader metadata as a JSON tree.
     *
     * @param manifest the container manifest
     * @param payload the payload to describe
     * @return the tree
     */
    public static JsonObject payloadJson(ContainerManifest manifest, PayloadDescriptor payload) {
        ContainerInfo container = manifest.container();
        Requirements requires = payload.requires();

        JsonObject root = new JsonObject();
        root.set("schemaVersion", 1);
        root.set("id", payload.modId());
        root.set("version", payload.modVersion().toString());
        if (!payload.displayName().isEmpty()) {
            root.set("name", payload.displayName());
        }
        root.set("environment", requires.environment().id());

        if (!payload.provides().isEmpty()) {
            JsonArray provides = new JsonArray();
            for (String alias : payload.provides()) {
                provides.add(alias);
            }
            root.set("provides", provides);
        }

        JsonObject entrypoints = new JsonObject();
        entrypoints.set("preLaunch", new JsonArray().add(PAYLOAD_PRELAUNCH));
        entrypoints.set("main", new JsonArray().add(PAYLOAD_MAIN));
        if (requires.environment() != EnvironmentConstraint.SERVER) {
            entrypoints.set("client", new JsonArray().add(PAYLOAD_CLIENT));
        }
        if (requires.environment() != EnvironmentConstraint.CLIENT) {
            entrypoints.set("server", new JsonArray().add(PAYLOAD_SERVER));
        }
        root.set("entrypoints", entrypoints);

        if (!payload.mixins().isEmpty()) {
            JsonArray mixins = new JsonArray();
            for (MixinConfigRef mixin : payload.mixins()) {
                if (mixin.environment() == EnvironmentConstraint.BOTH) {
                    mixins.add(mixin.config());
                } else {
                    mixins.add(new JsonObject()
                            .set("config", mixin.config())
                            .set("environment", mixin.environment().id()));
                }
            }
            root.set("mixins", mixins);
        }
        if (payload.accessWidener() != null && !payload.accessWidener().isEmpty()) {
            root.set("accessWidener", payload.accessWidener());
        }
        if (!payload.nestedJars().isEmpty()) {
            JsonArray jars = new JsonArray();
            for (String file : payload.nestedJars()) {
                jars.add(jar(file));
            }
            root.set("jars", jars);
        }

        JsonObject depends = new JsonObject();
        depends.set("minecraft", rangeArray(requires.minecraft()));
        depends.set("java", requires.java().toString());
        depends.set("fabricloader", requires.fabricLoader().toString());
        depends.set(OmniFormat.RUNTIME_MOD_ID, container.runtime().range().toString());
        // An exact binding to the container: it enforces load ordering, and it stops a payload from
        // one build being mixed with a container from another by hand.
        depends.set(container.modId(), "=" + container.modVersion());
        for (Map.Entry<String, VersionRange> mod : requires.mods().entrySet()) {
            depends.set(mod.getKey(), mod.getValue().toString());
        }
        root.set("depends", depends);

        List<String> otherPayloads = otherPayloadIds(manifest, payload);
        if (!otherPayloads.isEmpty()) {
            JsonObject breaks = new JsonObject();
            for (String other : otherPayloads) {
                breaks.set(other, "*");
            }
            root.set("breaks", breaks);
        }

        root.set("custom", new JsonObject()
                .set("omni", new JsonObject()
                        .set("role", "payload")
                        .set("payloadId", payload.id())
                        .set("container", container.modId()))
                .set("modmenu", new JsonObject()
                        .set("parent", container.modId())
                        .set("badges", new JsonArray().add("library"))));
        return root;
    }

    // ------------------------------------------------------------------ derivations

    /**
     * The container's environment: {@code *} unless every payload agrees on one side.
     *
     * <p>Narrowing it when they do matters on a dedicated server: a client-only universal mod is
     * then not loaded at all, rather than loading and reporting that it has nothing to run.
     *
     * @param manifest the container manifest
     * @return the environment constraint for the container
     */
    public static EnvironmentConstraint containerEnvironment(ContainerManifest manifest) {
        EnvironmentConstraint common = null;
        for (PayloadDescriptor payload : manifest.payloads()) {
            EnvironmentConstraint environment = payload.requires().environment();
            if (environment == EnvironmentConstraint.BOTH) {
                return EnvironmentConstraint.BOTH;
            }
            if (common == null) {
                common = environment;
            } else if (common != environment) {
                return EnvironmentConstraint.BOTH;
            }
        }
        return common == null ? EnvironmentConstraint.BOTH : common;
    }

    /**
     * The highest of the payloads' minimum loader versions.
     *
     * <p>The maximum, and the asymmetry with {@code java} is deliberate. The loader version is the
     * same for every payload in a given launch, so a container loading on an older loader than one
     * of its payloads needs could never select that payload — and would report a confusing "no
     * payload matches" instead of the loader's own clear "requires fabricloader &gt;= x".
     *
     * @param manifest the container manifest
     * @return the range to declare on the container
     */
    public static VersionRange highestMinimumLoader(ContainerManifest manifest) {
        SemVer highest = null;
        for (PayloadDescriptor payload : manifest.payloads()) {
            SemVer minimum = lowerBound(payload.requires().fabricLoader());
            if (minimum != null && (highest == null || highest.isLowerThan(minimum))) {
                highest = minimum;
            }
        }
        return highest == null ? VersionRange.ALL : VersionRange.parse(">=" + highest);
    }

    /**
     * The union of every payload's Minecraft range, merged into normal form.
     *
     * @param manifest the container manifest
     * @return the union
     */
    public static VersionRange minecraftUnion(ContainerManifest manifest) {
        VersionRange union = VersionRange.EMPTY;
        for (PayloadDescriptor payload : manifest.payloads()) {
            union = union.union(payload.requires().minecraft());
        }
        return union;
    }

    private static boolean anyPayloadNeeds(ContainerManifest manifest, String modId) {
        for (PayloadDescriptor payload : manifest.payloads()) {
            if (payload.requires().mods().containsKey(modId)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> otherPayloadIds(
            ContainerManifest manifest, PayloadDescriptor payload) {
        List<String> others = new ArrayList<String>();
        for (PayloadDescriptor candidate : manifest.payloads()) {
            if (!candidate.modId().equals(payload.modId())) {
                others.add(candidate.modId());
            }
        }
        Collections.sort(others);
        return others;
    }

    private static List<PayloadDescriptor> sortedPayloads(ContainerManifest manifest) {
        List<PayloadDescriptor> payloads = new ArrayList<PayloadDescriptor>(manifest.payloads());
        Collections.sort(payloads, new Comparator<PayloadDescriptor>() {
            @Override
            public int compare(PayloadDescriptor left, PayloadDescriptor right) {
                return left.id().compareTo(right.id());
            }
        });
        return payloads;
    }

    private static SemVer lowerBound(VersionRange range) {
        List<Interval> intervals = range.intervals();
        return intervals.isEmpty() ? null : intervals.get(0).min();
    }

    private static JsonObject jar(String file) {
        return new JsonObject().set("file", file);
    }

    /**
     * A range as Fabric's OR array of predicates.
     *
     * <p>Always an array, even for a single interval: Fabric accepts both forms, and a uniform shape
     * makes the validator's comparison against the Omni manifest a comparison rather than a special
     * case.
     */
    private static JsonArray rangeArray(VersionRange range) {
        JsonArray array = new JsonArray();
        for (String predicate : range.toPredicates()) {
            array.add(predicate);
        }
        return array;
    }

    private FabricModJsonWriter() {
        throw new AssertionError("no instances");
    }
}
