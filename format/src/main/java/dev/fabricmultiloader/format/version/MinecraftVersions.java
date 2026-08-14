package dev.fabricmultiloader.format.version;

/**
 * Minecraft version handling, including the scheme change at 26.1.
 *
 * <p>Fabric Loader normalises the {@code minecraft} mod's version into a semantic form before we
 * ever see it: releases stay {@code 1.21.4}, snapshots become {@code 1.21.5-alpha.24.45.a}, and
 * release candidates become {@code 1.21.4-rc.1}. From 26.1 Mojang moves to a two-component,
 * date-derived scheme, which {@link SemVer} absorbs by defaulting the missing patch to zero.
 * Ordering stays correct across the boundary because the comparison is by major first —
 * {@code 1.21.4 < 26.1.0} holds without a special case.
 */
public final class MinecraftVersions {

    /** Maximum minor/patch value that {@link #ordinal(SemVer)} can encode without collision. */
    private static final int ORDINAL_COMPONENT_LIMIT = 99;

    private static final int ORDINAL_MAJOR_FACTOR = 10000;
    private static final int ORDINAL_MINOR_FACTOR = 100;

    /** Parses a Minecraft version tolerantly; never throws. */
    public static SemVer parse(String text) {
        return SemVer.parseLenient(text);
    }

    /**
     * A compact, strictly monotonic encoding for comparisons in common code:
     * {@code 1.20.1 -> 12001}, {@code 1.21.4 -> 12104}, {@code 26.1 -> 260100}.
     *
     * <p>Exposed to mod authors through {@code PlatformInfo#minecraftOrdinal()} so that common code
     * can vary <em>behaviour</em> without touching Minecraft types. It deliberately cannot help
     * with calling a different API — that is the adapter's job, and conflating the two is the most
     * common way a "version-neutral" module quietly stops being neutral.
     *
     * <p>Prereleases collapse onto their release ordinal: a snapshot of 1.21.5 encodes as 12105.
     * For ordering that needs prerelease precision, compare {@link SemVer} values directly.
     *
     * @param version a parsed Minecraft version
     * @return the encoded ordinal
     * @throws IllegalArgumentException if a component exceeds 99, which would break monotonicity
     */
    public static int ordinal(SemVer version) {
        if (version == null) {
            return 0;
        }
        if (version.minor() > ORDINAL_COMPONENT_LIMIT || version.patch() > ORDINAL_COMPONENT_LIMIT) {
            throw new IllegalArgumentException(
                    "cannot encode " + version + " as an ordinal: minor and patch must be <= "
                            + ORDINAL_COMPONENT_LIMIT + " to stay monotonic");
        }
        return version.major() * ORDINAL_MAJOR_FACTOR
                + version.minor() * ORDINAL_MINOR_FACTOR
                + version.patch();
    }

    /** Convenience: parses and encodes in one step. */
    public static int ordinal(String text) {
        return ordinal(parse(text));
    }

    /**
     * Whether this is a snapshot, pre-release or release candidate rather than a full release.
     *
     * <p>Matters because {@code >=1.21.4} does <em>not</em> include {@code 1.21.4-alpha.24.45.a}:
     * a prerelease sorts below its release. Ranges that should cover snapshots need a lower bound
     * of {@link SemVer#withLowestPrerelease()}.
     */
    public static boolean isSnapshot(SemVer version) {
        return version != null && version.isPrerelease() && !version.isUnknown();
    }

    /**
     * The range covering one Minecraft release line, e.g. {@code 1.21} to just below {@code 1.22}.
     *
     * @param from inclusive lower bound
     * @param toExclusive exclusive upper bound
     * @param includeSnapshots whether prereleases of {@code from} are included
     */
    public static VersionRange between(SemVer from, SemVer toExclusive, boolean includeSnapshots) {
        SemVer lower = includeSnapshots ? from.withLowestPrerelease() : from;
        return VersionRange.of(Interval.closedOpen(lower, toExclusive));
    }

    private MinecraftVersions() {
        throw new AssertionError("no instances");
    }
}
