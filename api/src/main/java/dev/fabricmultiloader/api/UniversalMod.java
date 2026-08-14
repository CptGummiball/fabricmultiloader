package dev.fabricmultiloader.api;

/**
 * The mod's shared entry point — the one class that runs on every supported Minecraft version.
 *
 * <p>Called once, on both client and dedicated server, after the version-specific platform has been
 * created and before deferred registrations are flushed. That ordering is the reason content can be
 * declared here in a version-neutral way at all: the adapter is already available to translate it,
 * and nothing has been committed to Minecraft's registries yet.
 *
 * <p>Everything in this class is compiled exactly once and shipped in the container, so it must not
 * reference Minecraft types. The compiler will not stop you — the validator will
 * ({@code OMNI-1042}), and it does so precisely because such a reference works on the version you
 * happened to build against and fails on every other.
 *
 * <pre>
 * &#64;UniversalEntrypoint
 * public final class ExampleMod implements UniversalMod {
 *     &#64;Override public void onInitialize(ModContext ctx) {
 *         ctx.registries().item(Id.of("examplemod", "ruby"), ItemSpec.builder().build());
 *     }
 * }
 * </pre>
 */
@ImplementedByMod
public interface UniversalMod {

    /**
     * Initialises the mod.
     *
     * @param ctx access to logging, config, registries, networking, commands, events and services
     */
    void onInitialize(ModContext ctx);
}
