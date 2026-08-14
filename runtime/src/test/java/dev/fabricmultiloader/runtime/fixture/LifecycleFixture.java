package dev.fabricmultiloader.runtime.fixture;

import dev.fabricmultiloader.format.OmniFormat;
import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.format.manifest.ContainerManifest;
import dev.fabricmultiloader.format.manifest.EntrypointSet;
import dev.fabricmultiloader.format.manifest.EnvironmentConstraint;
import dev.fabricmultiloader.format.manifest.ManifestFixtures;
import dev.fabricmultiloader.format.manifest.ManifestWriter;
import dev.fabricmultiloader.format.manifest.MappingsInfo;
import dev.fabricmultiloader.format.manifest.PayloadDescriptor;
import dev.fabricmultiloader.format.manifest.Requirements;
import dev.fabricmultiloader.runtime.FakeLoader;
import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.boot.RuntimeBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds a resolved single-payload container around the fake platform, with one knob per failure
 * mode a test wants to trigger.
 *
 * <p>Every test in this area needs the same six mods installed, a manifest on disk and a resolved
 * container before it can assert anything at all. Assembling that inline would bury the one line
 * each test is actually about.
 */
public final class LifecycleFixture {

    /** The container's mod id, matching the reference manifest fixture. */
    public static final String CONTAINER = "examplemod";

    /** The active payload's mod id. */
    public static final String PAYLOAD = "examplemod-mc1214";

    private String platformFactory = "com.example.mc1214.Platform1214Factory";
    private String[] packages = {"com.example.mc1214"};
    private String[] capabilities = {"registries", "components"};
    private EntrypointSet entrypoints = ManifestFixtures.exampleManifest().entrypoints();
    private Side side = Side.SERVER;
    private boolean development;
    private boolean payloadLoaded = true;

    /** Names a different platform factory class. */
    public LifecycleFixture factory(String fqcn) {
        this.platformFactory = fqcn;
        return this;
    }

    /** Overrides the packages the payload declares as its own. */
    public LifecycleFixture packages(String... values) {
        this.packages = values;
        return this;
    }

    /** Overrides the capability ids the payload declares. */
    public LifecycleFixture capabilities(String... values) {
        this.capabilities = values;
        return this;
    }

    /** Overrides the mod's entrypoint classes. */
    public LifecycleFixture entrypoints(EntrypointSet value) {
        this.entrypoints = value;
        return this;
    }

    /** Sets the physical side. */
    public LifecycleFixture side(Side value) {
        this.side = value;
        return this;
    }

    /** Marks the loader as a development runtime. */
    public LifecycleFixture inDevelopment() {
        this.development = true;
        return this;
    }

    /** Simulates the solver not selecting the payload. */
    public LifecycleFixture withoutPayload() {
        this.payloadLoaded = false;
        return this;
    }

    /** The manifest this fixture writes. */
    public ContainerManifest manifest() {
        return ContainerManifest.builder()
                .container(ManifestFixtures.exampleContainer())
                .entrypoints(entrypoints)
                .payload(payload())
                .build();
    }

    /** The single payload, built from the current settings. */
    public PayloadDescriptor payload() {
        return PayloadDescriptor.builder()
                .id("mc1214")
                .modId(PAYLOAD)
                .modVersion("2.0.0+mc1.21.4")
                .displayName("Universal Example Mod (Minecraft 1.21.4)")
                .file(OmniFormat.NESTED_JAR_ROOT + PAYLOAD + ".jar")
                .integrity("", 0L)
                .classfileMajor(65)
                .priority(0)
                .platformFactory(platformFactory)
                .packages(packages)
                .requires(Requirements.builder()
                        .minecraft(">=1.21.4 <1.21.5")
                        .fabricLoader(">=0.14.21")
                        .java(">=21")
                        .environment(EnvironmentConstraint.BOTH)
                        .mod("fabric-api", ">=0.114.0")
                        .build())
                .provides("examplemod-impl")
                .mappings(new MappingsInfo(MappingsInfo.INTERMEDIARY, "yarn", "1.21.4+build.1"))
                .capabilities(capabilities)
                .build();
    }

    /** The loader this fixture installs the container into. */
    public FakeLoader loader(Path tempDir) throws IOException {
        Path gameDir = Files.createDirectories(tempDir.resolve("game"));
        Path containerRoot = Files.createDirectories(tempDir.resolve("container"));

        FakeLoader loader = new FakeLoader(gameDir)
                .withMod("minecraft", "1.21.4")
                .withMod("fabricloader", "0.16.9")
                .withMod("fabric-api", "0.114.0")
                .withMod("fabricmultiloader", "1.0.0")
                .withMod(CONTAINER, "2.0.0", containerRoot)
                .onSide(side);
        if (development) {
            loader.inDevelopment();
        }
        if (payloadLoaded) {
            loader.withMod(PAYLOAD, "2.0.0+mc1.21.4");
        }
        loader.withFile(CONTAINER, OmniFormat.CONTAINER_MANIFEST_PATH,
                ManifestWriter.write(manifest()));
        return loader;
    }

    /**
     * Resolves the container.
     *
     * @param tempDir a per-test temporary directory
     * @return the resolved container runtime
     * @throws IOException if the temporary files cannot be written
     */
    public ContainerRuntime resolve(Path tempDir) throws IOException {
        return RuntimeBootstrap.forTesting(loader(tempDir)).resolveContainer(CONTAINER);
    }
}
