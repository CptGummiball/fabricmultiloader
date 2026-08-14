package dev.fabricmultiloader.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a reproducible jar.
 *
 * <p>Shared by the payload and container builders, and written to the same rules the real assembler
 * follows (chapter 10.5): a fixed timestamp, entries in lexicographic order, no directory entries.
 * Fixtures that are byte-identical between runs are what let a golden-file test compare a whole jar
 * rather than a hand-picked subset of its contents.
 *
 * <p>Nested jars are stored uncompressed. That is not an optimisation here — it is what the format
 * specifies, so a fixture that deflated them would not be a fixture of the format.
 */
public final class JarWriter {

    /** The ZIP epoch: the smallest timestamp a ZIP entry can represent. */
    public static final long FIXED_TIME = 315532800000L; // 1980-01-01T00:00:00Z

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Map<String, byte[]> deflated = new LinkedHashMap<String, byte[]>();
    private final Map<String, byte[]> stored = new LinkedHashMap<String, byte[]>();

    /** Adds a text entry, compressed. */
    public JarWriter entry(String path, String content) {
        return entry(path, content.getBytes(UTF_8));
    }

    /** Adds a binary entry, compressed. */
    public JarWriter entry(String path, byte[] content) {
        deflated.put(path, content.clone());
        return this;
    }

    /** Adds an entry stored uncompressed — the format's rule for nested jars. */
    public JarWriter storedEntry(String path, byte[] content) {
        stored.put(path, content.clone());
        return this;
    }

    /** Whether an entry has been added. */
    public boolean has(String path) {
        return deflated.containsKey(path) || stored.containsKey(path);
    }

    /** Writes the jar to a file. */
    public Path writeTo(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, toBytes());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    /** The jar as bytes. */
    public byte[] toBytes() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            write(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot build the jar", e);
        }
        return buffer.toByteArray();
    }

    private void write(OutputStream target) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(target);
        zip.setLevel(9);

        List<String> paths = new ArrayList<String>(deflated.keySet());
        paths.addAll(stored.keySet());
        java.util.Collections.sort(paths);

        for (String path : paths) {
            byte[] content = stored.containsKey(path) ? stored.get(path) : deflated.get(path);
            ZipEntry entry = new ZipEntry(path);
            entry.setTime(FIXED_TIME);
            if (stored.containsKey(path)) {
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                CRC32 crc = new CRC32();
                crc.update(content, 0, content.length);
                entry.setCrc(crc.getValue());
            } else {
                entry.setMethod(ZipEntry.DEFLATED);
            }
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }
}
