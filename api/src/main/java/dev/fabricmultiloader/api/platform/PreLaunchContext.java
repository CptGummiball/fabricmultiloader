package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.ImplementedByFramework;
import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.format.Side;
import java.nio.file.Path;

/**
 * What is available before Minecraft classes are loaded.
 *
 * <p>Deliberately much smaller than {@link dev.fabricmultiloader.api.ModContext}: at this point no
 * Minecraft class has been touched, and touching one would defeat the purpose of the phase — mixins
 * are applied as classes load, so a pre-launch hook that loads a game class can silently prevent
 * another mod's mixin from ever applying.
 *
 * <p>Useful for reading configuration that later initialisation depends on, and for early
 * diagnostics. Registry, networking and event calls are not reachable from here at all.
 */
@ImplementedByFramework
public interface PreLaunchContext {

    /** The mod id. */
    String modId();

    /** A logger named after the mod. */
    ModLogger log();

    /** The game directory. */
    Path gameDir();

    /** The mod's own configuration directory. */
    Path modConfigDir();

    /** What the mod is running on, including which payload was selected. */
    PlatformInfo platform();

    /** The physical side. */
    Side side();

    /** Whether this is a development runtime. */
    boolean isDevelopment();
}
