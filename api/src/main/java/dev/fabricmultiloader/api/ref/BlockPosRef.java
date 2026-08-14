package dev.fabricmultiloader.api.ref;

/**
 * A block position.
 *
 * <p>A plain value class rather than a handle: block coordinates are three integers in every
 * Minecraft version there has ever been, so wrapping them would add indirection without buying
 * version independence. Adapters convert to and from {@code net.minecraft.util.math.BlockPos}.
 */
public final class BlockPosRef {

    /** The origin. */
    public static final BlockPosRef ORIGIN = new BlockPosRef(0, 0, 0);

    private final int x;
    private final int y;
    private final int z;

    private BlockPosRef(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Creates a position. */
    public static BlockPosRef of(int x, int y, int z) {
        return new BlockPosRef(x, y, z);
    }

    /** Block coordinates containing the given world coordinates. */
    public static BlockPosRef containing(double x, double y, double z) {
        return new BlockPosRef(floor(x), floor(y), floor(z));
    }

    /** The x coordinate. */
    public int x() {
        return x;
    }

    /** The y coordinate. */
    public int y() {
        return y;
    }

    /** The z coordinate. */
    public int z() {
        return z;
    }

    /** A position offset by the given deltas. */
    public BlockPosRef offset(int dx, int dy, int dz) {
        return new BlockPosRef(x + dx, y + dy, z + dz);
    }

    /** The position one block above. */
    public BlockPosRef above() {
        return offset(0, 1, 0);
    }

    /** The position one block below. */
    public BlockPosRef below() {
        return offset(0, -1, 0);
    }

    /** Squared distance to another position, avoiding a square root. */
    public long distanceSquared(BlockPosRef other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPosRef)) {
            return false;
        }
        BlockPosRef that = (BlockPosRef) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }
}
