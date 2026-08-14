package dev.fabricmultiloader.runtime.context;

import dev.fabricmultiloader.api.ModLogger;
import dev.fabricmultiloader.api.platform.PlatformInfo;
import dev.fabricmultiloader.api.platform.PreLaunchContext;
import dev.fabricmultiloader.format.Side;
import java.nio.file.Path;

/**
 * What a pre-launch hook can see.
 *
 * <p>A thin projection of {@link ModContextImpl} rather than a second implementation, so the two
 * cannot answer the same question differently. What it deliberately does <em>not</em> forward are
 * the four subsystems: at pre-launch no Minecraft class has been loaded, and the reason to keep it
 * that way is not tidiness. Mixins are applied as classes load, so a hook that touches a game class
 * early can permanently prevent another mod's mixin from ever applying to it — a failure that
 * surfaces in someone else's mod, with nothing pointing back here.
 *
 * <p>The restriction is structural: there is no method on this interface to reach them through, so
 * the mistake is not available to make.
 */
public final class PreLaunchContextImpl implements PreLaunchContext {

    private final ModContextImpl delegate;

    /**
     * @param delegate the mod's full context
     */
    public PreLaunchContextImpl(ModContextImpl delegate) {
        this.delegate = delegate;
    }

    @Override
    public String modId() {
        return delegate.modId();
    }

    @Override
    public ModLogger log() {
        return delegate.log();
    }

    @Override
    public Path gameDir() {
        return delegate.gameDir();
    }

    @Override
    public Path modConfigDir() {
        return delegate.modConfigDir();
    }

    @Override
    public PlatformInfo platform() {
        return delegate.platform();
    }

    @Override
    public Side side() {
        return delegate.side();
    }

    @Override
    public boolean isDevelopment() {
        return delegate.isDevelopment();
    }

    @Override
    public String toString() {
        return "preLaunch(" + delegate.modId() + ")";
    }
}
