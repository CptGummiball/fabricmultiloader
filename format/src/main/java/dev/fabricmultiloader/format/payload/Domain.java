package dev.fabricmultiloader.format.payload;

import dev.fabricmultiloader.format.manifest.EnvironmentConstraint;
import dev.fabricmultiloader.format.manifest.Requirements;
import dev.fabricmultiloader.format.version.VersionRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The set of environments a payload applies to: a union of cells over
 * (Minecraft × Java × side).
 *
 * <p>Only these three axes count. Fabric Loader, Fabric API and foreign mod versions are
 * <em>filters</em> — they can remove a payload from consideration but never choose between two — so
 * including them would make a disjointness proof meaningless: two payloads differing only in a
 * filter would look disjoint while both being selectable whenever both filters pass. That case is
 * rejected outright as {@code OMNI-1012} instead.
 *
 * <p>Cell subtraction decomposes along one axis at a time, which yields at most three remainder
 * cells and no overlaps. (An earlier sketch split all three axes independently, giving up to 27
 * cells; the ordered decomposition is exact, simpler and far easier to read in a report.)
 */
public final class Domain {

    /** The empty domain. */
    public static final Domain EMPTY = new Domain(Collections.<Cell>emptyList());

    private final List<Cell> cells;

    private Domain(List<Cell> cells) {
        this.cells = Collections.unmodifiableList(cells);
    }

    /** A domain of one cell. */
    public static Domain of(VersionRange minecraft, VersionRange java, EnvironmentConstraint side) {
        Cell cell = new Cell(minecraft, java, side);
        return cell.isEmpty() ? EMPTY : new Domain(Collections.singletonList(cell));
    }

    /** The domain a payload's requirements describe. */
    public static Domain of(Requirements requires) {
        return of(requires.minecraft(), requires.java(), requires.environment());
    }

    /** A domain from arbitrary cells, dropping empties. */
    public static Domain of(List<Cell> cells) {
        List<Cell> kept = new ArrayList<Cell>(cells.size());
        for (Cell cell : cells) {
            if (!cell.isEmpty()) {
                kept.add(cell);
            }
        }
        return kept.isEmpty() ? EMPTY : new Domain(kept);
    }

    /** The cells making up this domain. */
    public List<Cell> cells() {
        return cells;
    }

    /** Whether the domain contains nothing. */
    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /** Whether the two domains share at least one environment. */
    public boolean intersects(Domain other) {
        for (Cell mine : cells) {
            for (Cell theirs : other.cells) {
                if (mine.intersects(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The overlap of the two domains. */
    public Domain intersect(Domain other) {
        List<Cell> overlaps = new ArrayList<Cell>();
        for (Cell mine : cells) {
            for (Cell theirs : other.cells) {
                Cell overlap = mine.intersect(theirs);
                if (overlap != null) {
                    overlaps.add(overlap);
                }
            }
        }
        return of(overlaps);
    }

    /** Everything in either domain. Cells are kept as they are; no merging is attempted. */
    public Domain union(Domain other) {
        List<Cell> combined = new ArrayList<Cell>(cells.size() + other.cells.size());
        combined.addAll(cells);
        combined.addAll(other.cells);
        return of(combined);
    }

    /** Everything in this domain but not in the other. */
    public Domain subtract(Domain other) {
        List<Cell> remaining = new ArrayList<Cell>(cells);
        for (Cell cut : other.cells) {
            List<Cell> next = new ArrayList<Cell>();
            for (Cell piece : remaining) {
                next.addAll(piece.subtract(cut));
            }
            remaining = next;
            if (remaining.isEmpty()) {
                break;
            }
        }
        return of(remaining);
    }

    /**
     * Whether this domain can be written back as a single payload's requirements.
     *
     * <p>True when every cell shares the same Java range and side, so only the Minecraft ranges
     * need to be OR-combined — which is what a Fabric {@code depends} array expresses. Subtraction
     * can in principle produce a domain that is not of this shape; the build then has to reject the
     * configuration rather than silently widen it.
     */
    public boolean isExpressibleAsRequirements() {
        if (cells.isEmpty()) {
            return false;
        }
        Cell first = cells.get(0);
        for (Cell cell : cells) {
            if (!cell.java().equals(first.java()) || cell.side() != first.side()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The Minecraft range covering every cell. Only meaningful together with
     * {@link #isExpressibleAsRequirements()}.
     */
    public VersionRange minecraftUnion() {
        VersionRange union = VersionRange.EMPTY;
        for (Cell cell : cells) {
            union = union.union(cell.minecraft());
        }
        return union;
    }

    @Override
    public String toString() {
        if (cells.isEmpty()) {
            return "(empty)";
        }
        StringBuilder out = new StringBuilder();
        for (Cell cell : cells) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(cell);
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Domain && ((Domain) other).cells.equals(cells);
    }

    @Override
    public int hashCode() {
        return cells.hashCode();
    }

    /** One box in the (Minecraft × Java × side) product. */
    public static final class Cell {

        private final VersionRange minecraft;
        private final VersionRange java;
        private final EnvironmentConstraint side;

        /**
         * @param minecraft Minecraft versions
         * @param java Java feature versions
         * @param side accepted physical sides
         */
        public Cell(VersionRange minecraft, VersionRange java, EnvironmentConstraint side) {
            this.minecraft = minecraft == null ? VersionRange.EMPTY : minecraft;
            this.java = java == null ? VersionRange.EMPTY : java;
            this.side = side;
        }

        /** Minecraft versions. */
        public VersionRange minecraft() {
            return minecraft;
        }

        /** Java feature versions. */
        public VersionRange java() {
            return java;
        }

        /** Accepted sides. */
        public EnvironmentConstraint side() {
            return side;
        }

        /** Whether any axis is empty, which makes the whole cell empty. */
        public boolean isEmpty() {
            return side == null || minecraft.isEmpty() || java.isEmpty();
        }

        /** Whether the two cells overlap on every axis. */
        public boolean intersects(Cell other) {
            return !isEmpty() && !other.isEmpty()
                    && minecraft.intersects(other.minecraft)
                    && java.intersects(other.java)
                    && side.intersects(other.side);
        }

        /** The overlap, or {@code null} if the cells are disjoint. */
        public Cell intersect(Cell other) {
            if (!intersects(other)) {
                return null;
            }
            EnvironmentConstraint sharedSide = side == other.side
                    ? side
                    : (side == EnvironmentConstraint.BOTH ? other.side : side);
            return new Cell(
                    minecraft.intersect(other.minecraft),
                    java.intersect(other.java),
                    sharedSide);
        }

        /**
         * This cell minus the other, decomposing one axis at a time.
         *
         * @return at most three non-overlapping cells
         */
        public List<Cell> subtract(Cell other) {
            if (isEmpty()) {
                return Collections.emptyList();
            }
            if (!intersects(other)) {
                return Collections.singletonList(this);
            }
            List<Cell> remainder = new ArrayList<Cell>(3);

            VersionRange minecraftRest = minecraft.subtract(other.minecraft);
            if (!minecraftRest.isEmpty()) {
                remainder.add(new Cell(minecraftRest, java, side));
            }

            VersionRange minecraftShared = minecraft.intersect(other.minecraft);
            if (minecraftShared.isEmpty()) {
                return remainder;
            }

            VersionRange javaRest = java.subtract(other.java);
            if (!javaRest.isEmpty()) {
                remainder.add(new Cell(minecraftShared, javaRest, side));
            }

            VersionRange javaShared = java.intersect(other.java);
            if (javaShared.isEmpty()) {
                return remainder;
            }

            EnvironmentConstraint sideRest = side.subtract(other.side);
            if (sideRest != null) {
                remainder.add(new Cell(minecraftShared, javaShared, sideRest));
            }
            return remainder;
        }

        @Override
        public String toString() {
            return "mc" + minecraft + " java" + java + " " + side;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Cell)) {
                return false;
            }
            Cell that = (Cell) other;
            if (isEmpty() && that.isEmpty()) {
                return true;
            }
            return minecraft.equals(that.minecraft) && java.equals(that.java) && side == that.side;
        }

        @Override
        public int hashCode() {
            if (isEmpty()) {
                return 0;
            }
            return (minecraft.hashCode() * 31 + java.hashCode()) * 31
                    + (side == null ? 0 : side.hashCode());
        }
    }
}
