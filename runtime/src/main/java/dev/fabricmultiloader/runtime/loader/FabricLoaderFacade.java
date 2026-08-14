package dev.fabricmultiloader.runtime.loader;

import dev.fabricmultiloader.format.Side;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * The real {@link LoaderFacade}, and the only class in the runtime that touches
 * {@code net.fabricmc}.
 *
 * <p>Everything used here has been present and behaviourally stable since Fabric Loader 0.14.0,
 * which is what the runtime is compiled against on purpose: using a newer method becomes a compile
 * error rather than a {@code NoSuchMethodError} on somebody's older loader. Twelve methods in total,
 * all from {@code net.fabricmc.loader.api} — nothing from {@code net.fabricmc.loader.impl}, and no
 * reflection into loader internals anywhere.
 */
public final class FabricLoaderFacade implements LoaderFacade {

    private final FabricLoader loader;

    /** Wraps the running loader. */
    public FabricLoaderFacade() {
        this(FabricLoader.getInstance());
    }

    FabricLoaderFacade(FabricLoader loader) {
        this.loader = loader;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return modId != null && loader.isModLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        Optional<ModContainer> container = loader.getModContainer(modId);
        if (!container.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(container.get().getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public Collection<String> loadedModIds() {
        Collection<String> ids = new ArrayList<String>();
        for (ModContainer container : loader.getAllMods()) {
            ids.add(container.getMetadata().getId());
        }
        return ids;
    }

    @Override
    public Optional<Path> findPath(String modId, String pathInJar) {
        if (modId == null || pathInJar == null) {
            return Optional.empty();
        }
        Optional<ModContainer> container = loader.getModContainer(modId);
        if (!container.isPresent()) {
            return Optional.empty();
        }
        return container.get().findPath(pathInJar);
    }

    @Override
    public Side side() {
        return loader.getEnvironmentType() == EnvType.CLIENT ? Side.CLIENT : Side.SERVER;
    }

    @Override
    public boolean isDevelopment() {
        return loader.isDevelopmentEnvironment();
    }

    @Override
    public Path gameDir() {
        return loader.getGameDir();
    }

    @Override
    public Path configDir() {
        return loader.getConfigDir();
    }

    @Override
    public void publish(String key, Object value) {
        loader.getObjectShare().put(key, value);
    }

    @Override
    public Optional<Object> lookup(String key) {
        return Optional.ofNullable(loader.getObjectShare().get(key));
    }
}
