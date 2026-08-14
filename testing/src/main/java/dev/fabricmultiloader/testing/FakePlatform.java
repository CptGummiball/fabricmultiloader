package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.api.Capability;
import dev.fabricmultiloader.api.ModContext;
import dev.fabricmultiloader.api.command.Commands;
import dev.fabricmultiloader.api.event.Events;
import dev.fabricmultiloader.api.net.Networking;
import dev.fabricmultiloader.api.platform.AbstractPlatform;
import dev.fabricmultiloader.api.platform.CrashContext;
import dev.fabricmultiloader.api.platform.PlatformFactory;
import dev.fabricmultiloader.api.platform.Platform;
import dev.fabricmultiloader.api.platform.PreLaunchContext;
import dev.fabricmultiloader.api.registry.Registries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link Platform} that borrows its subsystems from a {@link FakeModContext} and writes down which
 * lifecycle hooks were called.
 *
 * <p>For testing the framework's own ordering guarantees, and for a mod author who wants to run the
 * real lifecycle against fake subsystems rather than call {@code onInitialize} by hand. Its factory
 * is {@link Factory}; a manifest that names {@code dev.fabricmultiloader.testing.FakePlatform$Factory}
 * needs {@code dev.fabricmultiloader.testing} among its payload packages, which is what the runtime
 * checks before loading it.
 */
public final class FakePlatform extends AbstractPlatform {

    private final FakeModContext context;
    private final List<String> calls = new ArrayList<String>();
    private final Map<String, Object> capabilities = new LinkedHashMap<String, Object>();

    /**
     * @param ctx the context to borrow subsystems from; must be a {@link FakeModContext}
     */
    public FakePlatform(ModContext ctx) {
        super(ctx);
        if (!(ctx instanceof FakeModContext)) {
            throw new IllegalArgumentException(
                    "FakePlatform needs a FakeModContext, got " + ctx.getClass().getName());
        }
        this.context = (FakeModContext) ctx;
        calls.add("constructed");
    }

    /** Makes a capability available from this platform. */
    public <T> FakePlatform withCapability(Capability<T> capability, T implementation) {
        capabilities.put(capability.id(), implementation);
        return this;
    }

    /** Which lifecycle hooks ran, in order. */
    public List<String> calls() {
        return Collections.unmodifiableList(new ArrayList<String>(calls));
    }

    @Override
    public Registries registries() {
        return context.registries();
    }

    @Override
    public Networking networking() {
        return context.networking();
    }

    @Override
    public Commands commands() {
        return context.commands();
    }

    @Override
    public Events events() {
        return context.events();
    }

    @Override
    public void onPreLaunch(PreLaunchContext ctx) {
        calls.add("onPreLaunch");
    }

    @Override
    public void onInitialize(ModContext ctx) {
        calls.add("onInitialize");
    }

    @Override
    public void onInitializeClient(ModContext ctx) {
        calls.add("onInitializeClient");
    }

    @Override
    public void onInitializeServer(ModContext ctx) {
        calls.add("onInitializeServer");
    }

    @Override
    public <T> Optional<T> capability(Capability<T> capability) {
        Object value = capabilities.get(capability.id());
        return value == null ? Optional.<T>empty() : Optional.of(capability.type().cast(value));
    }

    @Override
    public void installCrashContext(CrashContext ctx) {
        calls.add("installCrashContext");
        ctx.add("Adapter", "FakePlatform");
    }

    @Override
    public String toString() {
        return "FakePlatform" + calls;
    }

    /** The factory a manifest names to get a {@link FakePlatform}. */
    public static final class Factory implements PlatformFactory {

        @Override
        public Platform create(ModContext ctx) {
            return new FakePlatform(ctx);
        }
    }
}
