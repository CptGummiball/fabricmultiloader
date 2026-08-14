package dev.fabricmultiloader.api.platform;

import dev.fabricmultiloader.api.ImplementedByFramework;
import dev.fabricmultiloader.format.version.SemVer;
import java.util.Optional;

/** What the mod is running on. */
@ImplementedByFramework
public interface PlatformInfo {

    /** The running Minecraft version. */
    SemVer minecraft();

    /** The running Fabric Loader version. */
    SemVer fabricLoader();

    /** The installed Fabric API version, absent if it is not installed. */
    Optional<SemVer> fabricApi();

    /** The JVM feature version: 17, 21, 25, … */
    int javaMajor();

    /** Which payload is active, e.g. {@code mc1214}. Worth including in any bug report. */
    String payloadId();

    /** {@code intermediary} in production, {@code named} in a development runtime. */
    String mappingNamespace();

    /**
     * Whether the running Minecraft version satisfies the given Fabric predicates, OR-combined.
     *
     * <pre>
     * if (ctx.platform().minecraftIn("&gt;=1.21")) { … }
     * </pre>
     *
     * <p>For varying <em>behaviour</em>. It cannot help with calling a different Minecraft API —
     * that is the adapter's job — and conflating the two is the most common way a module that was
     * meant to be version-neutral quietly stops being so.
     */
    boolean minecraftIn(String... predicates);

    /**
     * A compact, monotonic encoding of the Minecraft version:
     * {@code 1.20.1 -> 12001}, {@code 1.21.4 -> 12104}, {@code 26.1 -> 260100}.
     *
     * <p>Convenient for range checks in common code. Prereleases collapse onto their release, so
     * anything needing snapshot precision should compare {@link #minecraft()} directly.
     */
    int minecraftOrdinal();
}
