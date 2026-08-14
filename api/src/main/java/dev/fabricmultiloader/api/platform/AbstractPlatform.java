package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.ServiceRegistry;

/**
 * Convenient base class for a payload's {@link Platform}.
 *
 * <p>Holds the platform information and the service registry so a concrete adapter only has to
 * supply the four subsystems and whatever lifecycle hooks it needs.
 *
 * <pre>
 * public final class Platform1214 extends AbstractPlatform {
 *     Platform1214(ModContext ctx) { super(ctx); … }
 *     &#64;Override public Registries registries() { return registries; }
 *     &#64;Override public void onInitialize(ModContext ctx) {
 *         services().register(OreGenService.class, new OreGenService1214());
 *     }
 * }
 * </pre>
 */
public abstract class AbstractPlatform implements Platform {

    private final PlatformInfo info;
    private final ServiceRegistry services;

    /**
     * @param ctx the context passed to the factory
     */
    protected AbstractPlatform(ModContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("mod context must not be null");
        }
        this.info = ctx.platform();
        this.services = ctx.services();
    }

    @Override
    public final PlatformInfo info() {
        return info;
    }

    /** The service registry, for use in {@link #onInitialize}. */
    protected final ServiceRegistry services() {
        return services;
    }
}
