package dev.fabricmultiloader.runtime.diag;

import dev.fabricmultiloader.api.ModLogger;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes diagnostic files under {@code <gameDir>/.fabricmultiloader/}.
 *
 * <p>Three properties matter here, and all three are about not making a bad situation worse.
 *
 * <p>The temporary file is created <em>in the target directory</em>, never in
 * {@code java.io.tmpdir}. A world-writable temp directory invites a symlink swap between write and
 * move, and creating it alongside the destination also guarantees the atomic move stays within one
 * file system.
 *
 * <p>The move is atomic, so a reader never sees a half-written report — which matters because the
 * most likely reader is somebody attaching the file to a bug report during a crash loop.
 *
 * <p>A write failure never aborts anything. An unwritable game directory is a reason to log and
 * continue, not a reason to stop a launch that would otherwise have worked.
 */
public final class ReportWriter {

    /** Directory the runtime writes into, relative to the game directory. */
    public static final String DIRECTORY = ".fabricmultiloader";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final Path directory;
    private final ModLogger log;

    /**
     * @param gameDir the game directory
     * @param log where to report a failed write
     */
    public ReportWriter(Path gameDir, ModLogger log) {
        this.directory = gameDir.resolve(DIRECTORY);
        this.log = log;
    }

    /**
     * Writes a file, replacing any previous version atomically.
     *
     * @param fileName the file name inside the report directory
     * @param content the content
     * @return the path written, or {@code null} if writing failed
     */
    public Path write(String fileName, String content) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, fileName + "-", ".tmp");
            Files.write(temporary, content.getBytes(UTF_8));
            Path destination = directory.resolve(fileName);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Some network and container file systems cannot do it; a plain replace is still
                // better than not writing the report at all.
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temporary);
            log.warn("could not write {} to {}: {}", fileName, directory, e.toString());
            return null;
        }
    }

    /** The directory reports are written to. */
    public Path directory() {
        return directory;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: a stray temp file is not worth a second failure path.
        }
    }
}
