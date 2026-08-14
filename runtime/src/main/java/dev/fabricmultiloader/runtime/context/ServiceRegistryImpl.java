package dev.fabricmultiloader.runtime.context;

import dev.fabricmultiloader.api.ServiceRegistry;
import dev.fabricmultiloader.format.error.ErrorCode;
import dev.fabricmultiloader.format.error.Messages;
import dev.fabricmultiloader.format.error.OmniApiMisuseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mod's services, registered by its payload and read by its common code.
 *
 * <p>Registration is only legal inside {@code Platform#onInitialize}. That is a narrower rule than
 * "before the mod is running" and the narrowness is the point: common code that asks for a service
 * must see the same answer no matter when it asks. If a payload could register during, say, a world
 * load, then two call sites in the same mod would legitimately disagree about whether the service
 * exists, and the resulting bug would depend on player behaviour rather than on the code.
 *
 * <p>The window is therefore opened and sealed explicitly around that one call rather than derived
 * from the lifecycle phase, because "during {@code onInitialize}" is a moment inside a phase, not a
 * phase of its own — and a check that is merely approximately right here would let exactly the case
 * it exists to prevent slip through.
 */
public final class ServiceRegistryImpl implements ServiceRegistry {

    private final String modId;
    private final Map<Class<?>, Object> services = new LinkedHashMap<Class<?>, Object>();

    private volatile boolean open;
    private volatile boolean everOpened;

    /**
     * @param modId the mod this registry belongs to, named in every diagnostic
     */
    public ServiceRegistryImpl(String modId) {
        this.modId = modId;
    }

    /** Opens the registration window. Called by the runtime around {@code Platform#onInitialize}. */
    public void openRegistration() {
        open = true;
        everOpened = true;
    }

    /** Closes the registration window. Called by the runtime; not reversible. */
    public void sealRegistration() {
        open = false;
    }

    /** Whether registration is currently permitted. */
    public boolean isRegistrationOpen() {
        return open;
    }

    @Override
    public <T> T get(Class<T> type) {
        requireType(type);
        Object service;
        synchronized (services) {
            service = services.get(type);
        }
        if (service == null) {
            throw new OmniApiMisuseException(ErrorCode.OMNI_4010,
                    Messages.report(ErrorCode.OMNI_4010)
                            .detected("mod", modId)
                            .detected("service", type.getName())
                            .detected("registered services", describeRegistered())
                            .detail("Common code asked for a service the active payload never")
                            .detail("registered. Every payload must register the services the")
                            .detail("common half depends on, or the mod cannot work on that version.")
                            .fix("register it from Platform#onInitialize in the payload adapter")
                            .fix("or use services().find(...) if some versions legitimately lack it")
                            .build());
        }
        return type.cast(service);
    }

    @Override
    public <T> Optional<T> find(Class<T> type) {
        requireType(type);
        synchronized (services) {
            return Optional.ofNullable(type.cast(services.get(type)));
        }
    }

    @Override
    public boolean has(Class<?> type) {
        if (type == null) {
            return false;
        }
        synchronized (services) {
            return services.containsKey(type);
        }
    }

    @Override
    public <T> void register(Class<T> type, T implementation) {
        requireType(type);
        if (implementation == null) {
            throw new IllegalArgumentException("service implementation for "
                    + type.getName() + " must not be null");
        }
        if (!type.isInstance(implementation)) {
            throw new IllegalArgumentException(implementation.getClass().getName()
                    + " does not implement " + type.getName());
        }
        requireOpen(type);
        synchronized (services) {
            Object previous = services.put(type, implementation);
            if (previous != null && previous != implementation) {
                throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                        Messages.report(ErrorCode.OMNI_4002)
                                .detected("mod", modId)
                                .detected("service", type.getName())
                                .detected("first implementation", previous.getClass().getName())
                                .detected("second implementation", implementation.getClass().getName())
                                .detail("A service was registered twice. Which one common code would")
                                .detail("see depends on registration order, so this is refused rather")
                                .detail("than resolved arbitrarily.")
                                .fix("register each service exactly once per payload")
                                .build());
            }
        }
    }

    @Override
    public Set<Class<?>> registered() {
        synchronized (services) {
            return Collections.unmodifiableSet(
                    new LinkedHashSet<Class<?>>(services.keySet()));
        }
    }

    private void requireOpen(Class<?> type) {
        if (open) {
            return;
        }
        throw new OmniApiMisuseException(ErrorCode.OMNI_4002,
                Messages.report(ErrorCode.OMNI_4002)
                        .detected("mod", modId)
                        .detected("service", type.getName())
                        .detected("registration window", everOpened ? "already closed" : "not open yet")
                        .detail("Services may only be registered from Platform#onInitialize, which")
                        .detail("runs before the mod's own code. Registering later means common code")
                        .detail("would see the registry in different states depending on when it")
                        .detail("looked, which is not a difference a mod can reason about.")
                        .fix("move the register(...) call into Platform#onInitialize")
                        .build());
    }

    private static void requireType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("service type must not be null");
        }
    }

    private String describeRegistered() {
        synchronized (services) {
            if (services.isEmpty()) {
                return "(none)";
            }
            StringBuilder out = new StringBuilder();
            for (Class<?> type : services.keySet()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(type.getSimpleName());
            }
            return out.toString();
        }
    }
}
