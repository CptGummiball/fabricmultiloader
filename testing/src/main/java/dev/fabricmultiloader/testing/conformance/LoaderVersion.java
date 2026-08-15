package dev.fabricmultiloader.testing.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * One Fabric Loader release, loaded as a library.
 *
 * <p>This is the only place in the project where a {@code ClassLoader} is constructed, and the
 * exception is deliberate and confined: invariant I1 governs what ships inside a universal jar, and
 * nothing here does. The point of the isolation is that several loader versions have to coexist in
 * one JVM with the same class names, which is exactly what a class loader is for.
 *
 * <p>The parent is the <em>platform</em> loader, not the application loader. That matters: with the
 * application loader as parent, {@code net.fabricmc.loader.impl.discovery.ModResolver} would resolve
 * against whatever loader happens to be on the test classpath, and the harness would silently test
 * one version six times. With the platform loader it can only come from the jar under test.
 *
 * <p>Each loader distribution is self-contained — Sat4j, Gson and mapping-io are relocated into
 * {@code net.fabricmc.loader.impl.lib} — so one jar per version is the whole classpath.
 */
public final class LoaderVersion {

    /** System property naming the index Gradle writes, mapping version to classpath. */
    public static final String INDEX_PROPERTY = "fabricmultiloader.conformance.index";

    private static final Map<String, LoaderVersion> CACHE =
            new LinkedHashMap<String, LoaderVersion>();

    private final String version;
    private final List<Path> classpath;

    private ClassLoader isolated;

    private LoaderVersion(String version, List<Path> classpath) {
        this.version = version;
        this.classpath = Collections.unmodifiableList(new ArrayList<Path>(classpath));
    }

    /**
     * Every loader in the matrix, in the order Gradle resolved them.
     *
     * @return the matrix
     * @throws IllegalStateException if the index is missing — the harness only runs from the
     *     {@code conformanceTest} task, which produces it
     */
    public static synchronized List<LoaderVersion> matrix() {
        if (CACHE.isEmpty()) {
            load();
        }
        return Collections.unmodifiableList(new ArrayList<LoaderVersion>(CACHE.values()));
    }

    /** The matrix as version strings, for a parameterised test's display names. */
    public static List<String> versions() {
        List<String> names = new ArrayList<String>();
        for (LoaderVersion loader : matrix()) {
            names.add(loader.version());
        }
        return names;
    }

    /** One loader by version. */
    public static synchronized LoaderVersion of(String version) {
        if (CACHE.isEmpty()) {
            load();
        }
        LoaderVersion loader = CACHE.get(version);
        if (loader == null) {
            throw new IllegalArgumentException(
                    "loader " + version + " is not in the matrix " + CACHE.keySet());
        }
        return loader;
    }

    private static void load() {
        String indexPath = System.getProperty(INDEX_PROPERTY);
        if (indexPath == null) {
            throw new IllegalStateException("-D" + INDEX_PROPERTY + " is not set. The conformance "
                    + "harness resolves real loader distributions and only runs from "
                    + "./gradlew :testing:conformanceTest.");
        }
        Properties properties = new Properties();
        InputStream in = null;
        try {
            in = Files.newInputStream(Paths.get(indexPath));
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the loader index " + indexPath, e);
        } finally {
            closeQuietly(in);
        }

        List<String> ordered = new ArrayList<String>(properties.stringPropertyNames());
        Collections.sort(ordered, LoaderVersion::compareVersions);
        for (String version : ordered) {
            List<Path> classpath = new ArrayList<Path>();
            for (String entry : properties.getProperty(version).split(java.io.File.pathSeparator)) {
                if (!entry.isEmpty()) {
                    classpath.add(Paths.get(entry));
                }
            }
            CACHE.put(version, new LoaderVersion(version, classpath));
        }
    }

    /** Numeric ordering, so 0.9 sorts before 0.16 rather than after it. */
    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            int a = i < leftParts.length ? parse(leftParts[i]) : 0;
            int b = i < rightParts.length ? parse(rightParts[i]) : 0;
            if (a != b) {
                return a - b;
            }
        }
        return left.compareTo(right);
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The loader version, e.g. {@code 0.16.14}. */
    public String version() {
        return version;
    }

    /** The jars making up this loader. */
    public List<Path> classpath() {
        return classpath;
    }

    /** The major and minor as one number, e.g. {@code 16} for {@code 0.16.14}. */
    public int line() {
        String[] parts = version.split("\\.");
        return parts.length > 1 ? parse(parts[1]) : 0;
    }

    /**
     * The isolated class loader, created on first use and kept for the run.
     *
     * <p>Never closed: the loader's static state (its logger, its metadata parser's Gson instance)
     * outlives any single resolution, and re-creating it per test would multiply a millisecond of
     * work by forty-eight.
     */
    public synchronized ClassLoader classLoader() {
        if (isolated == null) {
            URL[] urls = new URL[classpath.size()];
            for (int i = 0; i < urls.length; i++) {
                try {
                    urls[i] = classpath.get(i).toUri().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("cannot use " + classpath.get(i), e);
                }
            }
            isolated = new URLClassLoader("fabric-loader-" + version, urls,
                    ClassLoader.getPlatformClassLoader());
        }
        return isolated;
    }

    @Override
    public String toString() {
        return "fabric-loader " + version;
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing useful to do about a failed close on a read-only stream.
        }
    }
}
