package dev.fabricmultiloader.testing;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads class file headers without loading or parsing a class.
 *
 * <p>Used by the validator's rules and by JAR fixtures to assert that a payload really carries
 * the bytecode level it declares. Only the first eight bytes of a class file are touched — magic,
 * minor version, major version — which is what makes it safe to inspect bytecode targeting a
 * newer JVM than the one running the check (chapter 14.4).
 */
public final class ClassFiles {

    /** {@code 0xCAFEBABE} as a signed 32-bit integer. */
    private static final int MAGIC = 0xCAFEBABE;

    /** Class file major = Java feature version + 44 (Java 8 = 52, 21 = 65, 25 = 69). */
    private static final int JAVA_MAJOR_OFFSET = 44;

    /** Reads the class file major version of a {@code .class} file. */
    public static int majorVersionOf(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            return majorVersionOf(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read class file " + classFile, e);
        }
    }

    /** Reads the class file major version from a stream positioned at the class file start. */
    public static int majorVersionOf(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int magic = data.readInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException(
                    "not a class file: magic=0x" + Integer.toHexString(magic));
        }
        data.readUnsignedShort(); // minor — ignored
        return data.readUnsignedShort();
    }

    /** Converts a class file major version into its Java feature version (52 -> 8). */
    public static int javaVersionOf(int classFileMajor) {
        if (classFileMajor < 45) {
            throw new IllegalArgumentException("invalid class file major version: " + classFileMajor);
        }
        return classFileMajor - JAVA_MAJOR_OFFSET;
    }

    /** Converts a Java feature version into its class file major version (8 -> 52). */
    public static int classFileMajorOf(int javaVersion) {
        if (javaVersion < 1) {
            throw new IllegalArgumentException("invalid Java version: " + javaVersion);
        }
        return javaVersion + JAVA_MAJOR_OFFSET;
    }

    private ClassFiles() {
        throw new AssertionError("no instances");
    }
}
