package dev.fabricmultiloader.format.manifest;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.version.SemVer;
import dev.fabricmultiloader.format.version.VersionRange;

/**
 * The reference matrix from chapter 11.2 as a reusable fixture: Minecraft 1.20.1 on Java 17,
 * 1.21–1.21.1 and 1.21.4 on Java 21.
 */
public final class ManifestFixtures {

    /** The three-payload example container. */
    public static ContainerManifest exampleManifest() {
        return ContainerManifest.builder()
                .generator(new ContainerManifest.GeneratorInfo(
                        "fabricmultiloader-gradle", "1.0.0", "1980-01-01T00:00:00Z", "21.0.7"))
                .container(exampleContainer())
                .entrypoints(EntrypointSet.builder()
                        // One class in two phases is how a small mod is really written, and it is
                        // the case that catches a runtime constructing a fresh instance per phase.
                        .add(EntrypointSet.Phase.PRE_LAUNCH, "com.example.common.ExampleMod")
                        .add(EntrypointSet.Phase.COMMON, "com.example.common.ExampleMod")
                        .add(EntrypointSet.Phase.CLIENT, "com.example.common.ExampleModClient")
                        .build())
                .payload(payload("mc1201", "1.20.1", ">=1.20.1 <1.20.2", ">=17", 61, ">=0.92.2"))
                .payload(payload("mc1211", "1.21.1", ">=1.21 <1.21.2", ">=21", 65, ">=0.102.0"))
                .payload(payload("mc1214", "1.21.4", ">=1.21.4 <1.21.5", ">=21", 65, ">=0.114.0"))
                .diagnostics(new ContainerManifest.DiagnosticsInfo(
                        "https://github.com/example/examplemod/issues",
                        "https://example.github.io/examplemod/",
                        "https://modrinth.com/mod/examplemod",
                        "ExampleMod Support"))
                .build();
    }

    /** The container info of {@link #exampleManifest()}. */
    public static ContainerInfo exampleContainer() {
        return ContainerInfo.builder()
                .modId("examplemod")
                .modVersion("2.0.0")
                .displayName("Universal Example Mod")
                .commonPackages("com.example.common")
                .commonPackaging(CommonPackaging.SHARED)
                .baselineJavaMajor(17)
                .runtime(new ContainerInfo.RuntimeRef(
                        OmniFormat.RUNTIME_MOD_ID,
                        SemVer.of(1, 0, 0),
                        VersionRange.parse(">=1.0.0 <2.0.0"),
                        OmniFormat.NESTED_JAR_ROOT + "fabricmultiloader-runtime-1.0.0.jar",
                        "3f1c00000000000000000000000000000000000000000000000000000000000f"))
                .minRuntime(SemVer.of(1, 0, 0))
                .payloadAlias("examplemod-impl")
                .strict(true)
                .verifyIntegrity(true)
                .build();
    }

    /** One payload of the reference matrix. */
    public static PayloadDescriptor payload(String id, String minecraft, String minecraftRange,
            String javaRange, int classfileMajor, String fabricApiRange) {
        return PayloadDescriptor.builder()
                .id(id)
                .modId("examplemod-" + id)
                .modVersion("2.0.0+mc" + minecraft)
                .displayName("Universal Example Mod (Minecraft " + minecraft + ")")
                .file(OmniFormat.NESTED_JAR_ROOT + "examplemod-" + id + ".jar")
                // Deliberately no checksum: this fixture exists to model the version matrix, and a
                // made-up hash would mean every test that merely wants three payloads would first
                // have to produce three jars whose bytes match it. Integrity has its own tests,
                // which build a manifest over content they actually write.
                .integrity("", 0L)
                .classfileMajor(classfileMajor)
                .priority(0)
                .platformFactory("com.example." + id + ".Platform" + id.substring(2) + "Factory")
                .packages("com.example." + id)
                .requires(Requirements.builder()
                        .minecraft(minecraftRange)
                        .fabricLoader(">=0.14.21")
                        .java(javaRange)
                        .environment(EnvironmentConstraint.BOTH)
                        .mod("fabric-api", fabricApiRange)
                        .optionalMod("modmenu", "*")
                        .build())
                .provides("examplemod-impl")
                .mappings(new MappingsInfo(MappingsInfo.INTERMEDIARY, "yarn", minecraft + "+build.1"))
                .mixin("examplemod-" + id + ".mixins.json", EnvironmentConstraint.BOTH)
                .mixin("examplemod-" + id + ".client.mixins.json", EnvironmentConstraint.CLIENT)
                .refmaps("examplemod-" + id + "-refmap.json")
                .accessWidener("examplemod-" + id + ".accesswidener")
                .resourcesDigest("c7d000000000000000000000000000000000000000000000000000000000000d")
                .capabilities("registries", "commands", "networking.v1", "events.lifecycle")
                .build();
    }

    /** A payload with only the mandatory fields, for minimality tests. */
    public static PayloadDescriptor minimalPayload(String id, String minecraftRange) {
        return PayloadDescriptor.builder()
                .id(id)
                .modId("mod-" + id)
                .modVersion("1.0.0")
                .file(OmniFormat.NESTED_JAR_ROOT + id + ".jar")
                .classfileMajor(61)
                .platformFactory("com.example." + id + ".Factory")
                .packages("com.example." + id)
                .requires(Requirements.builder().minecraft(minecraftRange).build())
                .build();
    }

    private ManifestFixtures() {
        throw new AssertionError("no instances");
    }
}
