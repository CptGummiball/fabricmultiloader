package dev.fabricmultiloader.testing;

import dev.fabricmultiloader.format.version.JavaVersions;
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

    /**
     * Converts a class file major version into its Java feature version (52 -&gt; 8).
     *
     * <p>Delegates to {@code format}, which is the single source for this arithmetic: the
     * validator, the runtime and the test harness must never disagree about what bytecode a
     * payload contains.
     */
    public static int javaVersionOf(int classFileMajor) {
        return JavaVersions.featureVersionOf(classFileMajor);
    }

    /** Converts a Java feature version into its class file major version (8 -&gt; 52). */
    public static int classFileMajorOf(int javaVersion) {
        return JavaVersions.classFileMajor(javaVersion);
    }

    private ClassFiles() {
        throw new AssertionError("no instances");
    }
}
