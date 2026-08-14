package dev.fabricmultiloader.runtime.env;

import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniException;
import dev.fabricmultiloader.format.payload.Environment;
import dev.fabricmultiloader.format.version.JavaVersions;
import dev.fabricmultiloader.runtime.loader.LoaderFacade;
import java.util.Optional;

/**
 * Builds an {@link Environment} from what the loader reports.
 *
 * <p>Everything read here is a documented loader API. In particular, the Minecraft version comes
 * from the {@code minecraft} mod container rather than from any Minecraft class, so the whole of
 * detection runs before a single game class is loaded.
 *
 * <p>Version strings are parsed leniently throughout. A mod with an exotic version must not be able
 * to stop the game before the diagnostic layer is running — an unreadable version degrades to
 * {@code UNKNOWN}, which satisfies no minimum requirement and shows up verbatim in the report.
 */
public final class EnvironmentDetector {

    /** Fabric's synthetic mod for the game itself. */
    private static final String MINECRAFT = "minecraft";

    /** Fabric's synthetic mod for the loader. */
    private static final String FABRIC_LOADER = "fabricloader";

    /** The Fabric API aggregate, and the alias it provides. */
    private static final String[] FABRIC_API_IDS = {"fabric-api", "fabric"};

    /**
     * Detects the current environment.
     *
     * @param loader the loader facade
     * @return the environment
     * @throws OmniException {@code OMNI-2010} if Minecraft itself is not present, which means the
     *     runtime was started outside a game — a setup nothing downstream could make sense of
     */
    public static Environment detect(LoaderFacade loader) {
        Optional<String> minecraft = loader.modVersion(MINECRAFT);
        if (!minecraft.isPresent()) {
            throw new OmniException(ErrorCode.OMNI_2010, Messages.report(ErrorCode.OMNI_2010)
                    .detected("loaded mods", loader.loadedModIds().size())
                    .detected("side", loader.side().id())
                    .detail("Fabric Loader reports no 'minecraft' mod, so this is not a game launch.")
                    .detail("FabricMultiLoader has nothing to resolve against here.")
                    .fix("if you are embedding the loader yourself, register the Minecraft game provider")
                    .fix("otherwise report this, including your launcher and its version")
                    .build());
        }

        Environment.Builder environment = Environment.builder()
                .minecraft(minecraft.get())
                .javaMajor(JavaVersions.currentMajor())
                .side(loader.side())
                .development(loader.isDevelopment());

        Optional<String> loaderVersion = loader.modVersion(FABRIC_LOADER);
        if (loaderVersion.isPresent()) {
            environment.fabricLoader(loaderVersion.get());
        }

        for (String candidate : FABRIC_API_IDS) {
            Optional<String> apiVersion = loader.modVersion(candidate);
            if (apiVersion.isPresent()) {
                environment.fabricApi(apiVersion.get());
                break;
            }
        }

        // Every loaded mod, so that requires.mods can be evaluated for any dependency a payload
        // names — including Fabric API's individual modules, which a user may have installed
        // instead of the aggregate.
        for (String modId : loader.loadedModIds()) {
            Optional<String> version = loader.modVersion(modId);
            environment.mod(modId, version.isPresent() ? version.get() : "0.0.0");
        }

        return environment.build();
    }

    private EnvironmentDetector() {
        throw new AssertionError("no instances");
    }
}
