package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.format.Side;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link LoaderFacade} backed by a temporary directory instead of a running game.
 *
 * <p>Everything the bootstrap does — discovery, the exactly-one assertion, integrity verification,
 * report writing — is reachable through this in milliseconds. Those are the parts most worth testing
 * and the parts hardest to exercise inside a real launch, which is the entire reason the facade
 * exists.
 *
 * <p>Published as part of {@code fabricmultiloader-testing} so mod projects can use it too: a test
 * that needs "Minecraft 1.20.1 with Fabric API 0.92.2 and this one payload selected" builds it here
 * in three lines, and runs in milliseconds.
 */
public final class FakeFabricLoader implements LoaderFacade {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Map<String, String> modVersions = new LinkedHashMap<String, String>();
    private final Map<String, Path> modRoots = new LinkedHashMap<String, Path>();
    private final Map<String, Object> objectShare = new LinkedHashMap<String, Object>();
    private final Path gameDir;

    private Side side = Side.SERVER;
    private boolean development;

    /**
     * @param gameDir a temporary directory standing in for the game directory
     */
    public FakeFabricLoader(Path gameDir) {
        this.gameDir = gameDir;
    }

    /** Registers a loaded mod with no files. */
    public FakeFabricLoader withMod(String modId, String version) {
        modVersions.put(modId, version);
        return this;
    }

    /**
     * Registers a loaded mod whose jar contents live in a directory.
     *
     * <p>A directory rather than a real zip on purpose: in a development runtime Fabric hands back
     * exactly that, so this is a shape the production code must handle anyway.
     */
    public FakeFabricLoader withMod(String modId, String version, Path root) {
        modVersions.put(modId, version);
        modRoots.put(modId, root);
        return this;
    }

    /** Adds a file to a registered mod. */
    public FakeFabricLoader withFile(String modId, String pathInJar, String content) {
        Path root = modRoots.get(modId);
        if (root == null) {
            throw new IllegalStateException("register mod '" + modId + "' with a root first");
        }
        try {
            Path target = root.resolve(pathInJar);
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes(UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Adds a binary file to a registered mod. */
    public FakeFabricLoader withFile(String modId, String pathInJar, byte[] content) {
        Path root = modRoots.get(modId);
        if (root == null) {
            throw new IllegalStateException("register mod '" + modId + "' with a root first");
        }
        try {
            Path target = root.resolve(pathInJar);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Sets the physical side. */
    public FakeFabricLoader onSide(Side value) {
        this.side = value;
        return this;
    }

    /** Marks this as a development runtime. */
    public FakeFabricLoader inDevelopment() {
        this.development = true;
        return this;
    }

    /** Removes a mod, simulating one the solver did not select. */
    public FakeFabricLoader withoutMod(String modId) {
        modVersions.remove(modId);
        return this;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modVersions.containsKey(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return Optional.ofNullable(modVersions.get(modId));
    }

    @Override
    public Collection<String> loadedModIds() {
        return new ArrayList<String>(modVersions.keySet());
    }

    @Override
    public Optional<Path> findPath(String modId, String pathInJar) {
        Path root = modRoots.get(modId);
        if (root == null || !modVersions.containsKey(modId)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(pathInJar);
        return Files.exists(candidate) ? Optional.of(candidate) : Optional.<Path>empty();
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean isDevelopment() {
        return development;
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public Path configDir() {
        return gameDir.resolve("config");
    }

    @Override
    public void publish(String key, Object value) {
        objectShare.put(key, value);
    }

    @Override
    public Optional<Object> lookup(String key) {
        return Optional.ofNullable(objectShare.get(key));
    }
}
