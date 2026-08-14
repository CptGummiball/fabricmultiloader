/**
 * The version-independent half of the subsystems every payload would otherwise rewrite.
 *
 * <h2>Why the split runs where it does</h2>
 *
 * <p>Chapter 28.2 of the design describes a {@code CommandsImpl} in this package that registers
 * Brigadier commands through Fabric API's {@code CommandRegistrationCallback}, and an
 * {@code EventsImpl} that subscribes to Fabric API's events, with the runtime compiled against two
 * Fabric API versions to prove they are stable. That cannot be built, and the reason is worth
 * recording because it is not a matter of taste.
 *
 * <p>{@code CommandRegistrationCallback}'s functional method is
 * {@code register(CommandDispatcher<ServerCommandSource>, CommandRegistryAccess,
 * RegistrationEnvironment)}. Two of those three parameters are Minecraft types, so they appear in
 * the method descriptor of any class implementing it. The runtime is a plain library: it is not a
 * Loom build, it is never remapped, and it is loaded unchanged on every supported version. A
 * Minecraft type named in its bytecode resolves against whatever namespace the game is running in —
 * {@code net.minecraft.server.command.ServerCommandSource} in development, {@code net.minecraft.
 * class_2168} in production — so at most one of the two would ever link. Remapping the runtime
 * instead would bind it to one Minecraft version, which is precisely what it exists not to be
 * (invariant I3, validator rule {@code OMNI-1042}).
 *
 * <p>So the adapter is split at the Minecraft boundary rather than at the subsystem boundary:
 *
 * <ul>
 *   <li>{@link dev.fabricmultiloader.runtime.adapter.CommandRegistry} owns command collection, side
 *       filtering, path conflict detection and the flattening a Brigadier translation walks;
 *   <li>{@link dev.fabricmultiloader.runtime.adapter.EventBus} owns subscriptions, unsubscription,
 *       dispatch order and per-handler exception isolation;
 *   <li>{@link dev.fabricmultiloader.runtime.adapter.TextConverter} folds the text model into
 *       whatever type the payload builds, and renders it for logs;
 *   <li>{@link dev.fabricmultiloader.runtime.adapter.Feedback} is the one call whose signature
 *       genuinely diverged across versions, expressed as an interface the payload supplies.
 * </ul>
 *
 * <p>What the payload keeps is the wiring: a {@code CommandRegistrationCallback} listener that walks
 * {@code CommandRegistry#nodes()}, and Fabric API event listeners that call the bus's {@code fire}
 * methods. In the reference example mod that is under forty lines per payload, against the several
 * hundred lines of logic that stay here — so the claim in 28.4 that commands and events need one
 * implementation rather than one per version still holds, in the only form in which it can.
 *
 * <p>The design's stated verification — compiling the runtime against Fabric API 0.92.2 and
 * 0.114.0 — is replaced by a stronger one that already exists: the runtime compiles against no
 * Fabric API at all, and
 * {@code dev.fabricmultiloader.runtime.ForbiddenReferencesTest} proves it at the bytecode level for
 * every class in the module, not just for two.
 */
package dev.fabricmultiloader.runtime.adapter;
