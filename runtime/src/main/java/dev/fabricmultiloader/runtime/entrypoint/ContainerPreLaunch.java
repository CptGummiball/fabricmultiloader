package dev.fabricmultiloader.runtime.entrypoint;

import dev.fabricmultiloader.runtime.boot.ContainerRuntime;
import dev.fabricmultiloader.runtime.boot.RuntimeBootstrap;
import java.util.List;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * The first FabricMultiLoader code that ever runs.
 *
 * <p>Every container declares this as its {@code preLaunch} entrypoint. Everything before this point
 * is declarative — Fabric read some JSON, its solver picked exactly one payload, and it merged the
 * access wideners and registered the mixin configs of whatever it selected. No class of ours was
 * touched, which is precisely why payloads for other Minecraft versions cannot cause trouble: they
 * were never extracted.
 *
 * <p>Pre-launch is also the right place to abort. It runs before the first Minecraft class loads, so
 * a failure here leaves no half-initialised registry and no partially applied mixins behind — and
 * Fabric renders the thrown message in its error dialog, which is how a controlled diagnostic
 * reaches the user without touching a single loader-internal class.
 *
 * <p>Fabric invokes an entrypoint once per declaring mod without telling the instance which one, and
 * the public loader API deliberately does not expose entrypoint metadata —
 * {@code EntrypointMetadata} lives under {@code net.fabricmc.loader.impl}, which this project does
 * not touch. So containers are identified by the presence of a manifest instead. That turns out to
 * be the better answer anyway: it finds every universal mod in the process regardless of how its
 * entrypoint happens to be declared, and the first invocation resolves them all while the rest are
 * no-ops.
 */
public final class ContainerPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        RuntimeBootstrap bootstrap = RuntimeBootstrap.get();
        List<String> containers = bootstrap.discoverContainers();
        if (containers.isEmpty()) {
            bootstrap.log().warn("no universal mod carries a container manifest, although this "
                    + "entrypoint was invoked — the jar that declared it may be incomplete");
            return;
        }
        for (String containerModId : containers) {
            ContainerRuntime runtime = bootstrap.resolveContainer(containerModId);
            if (runtime.isActive()) {
                bootstrap.log().debug("{} resolved to payload {}",
                        containerModId, runtime.activePayload().id());
            }
        }
    }
}
