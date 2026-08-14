package dev.fabricmultiloader.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces invariant I1 on the shipped bytecode: FabricMultiLoader creates no ClassLoader and
 * touches no loader internals.
 *
 * <p>This is the architecture's load-bearing boundary, not a style preference. A custom ClassLoader
 * would bypass Knot's transformer chain, which silently disables mixins and access wideners for
 * anything loaded through it — a failure that looks like "my mixin does not apply" and takes days to
 * trace back. Reflection into {@code net.fabricmc.loader.impl} breaks on loader updates in ways that
 * only surface for users on a version the developer never ran.
 *
 * <p>Checking the source would be easy to evade and easy to get wrong. Class files carry every type
 * a class references as a UTF-8 constant, so scanning the produced bytecode catches a reference
 * however it was written — including one introduced by a dependency being shaded in.
 */
class ForbiddenReferencesTest {

    /** Internal names that must not appear anywhere in the runtime's bytecode, and why. */
    private static final Map<String, String> FORBIDDEN = forbidden();

    private static Map<String, String> forbidden() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("java/net/URLClassLoader",
                "a custom ClassLoader bypasses Knot's transformer chain (invariant I1, ADR-002)");
        map.put("java/lang/ClassLoader",
                "the runtime must never construct or subclass a ClassLoader (invariant I1)");
        map.put("net/fabricmc/loader/impl/",
                "loader internals change between versions; only net.fabricmc.loader.api is stable");
        map.put("org/spongepowered/asm/",
                "the runtime must not depend on Mixin — payload mixins are the loader's business");
        map.put("net/minecraft/",
                "the runtime loads on every supported version and must name no Minecraft type (I3)");
        map.put("com/mojang/",
                "same as net/minecraft: no game types in a version-independent library");
        map.put("net/fabricmc/fabric/api/",
                "Fabric API signatures differ across versions; adapters belong in payloads");
        return map;
    }

    /**
     * {@code Thread#setContextClassLoader} is the other way to disturb class loading without ever
     * constructing a loader, so it is checked by method name.
     */
    private static final List<String> FORBIDDEN_METHODS =
            Arrays.asList("setContextClassLoader", "defineClass", "addURL");

    @Test
    @DisplayName("no shipped class references a ClassLoader, loader internals or Minecraft")
    void shippedBytecodeIsClean() {
        List<String> violations = new ArrayList<String>();

        for (Path classFile : runtimeClassFiles()) {
            String contents = readAsLatin1(classFile);
            String className = classFile.getFileName().toString();

            for (Map.Entry<String, String> rule : FORBIDDEN.entrySet()) {
                if (contents.contains(rule.getKey())) {
                    // FabricLoaderFacade is the single, deliberate exception: it is the one class
                    // allowed to speak to the loader, and only through net.fabricmc.loader.api.
                    if (isPermitted(classFile, rule.getKey())) {
                        continue;
                    }
                    violations.add(className + " references " + rule.getKey()
                            + " — " + rule.getValue());
                }
            }
            for (String method : FORBIDDEN_METHODS) {
                if (contents.contains(method)) {
                    violations.add(className + " calls " + method
                            + " — the runtime must not influence class loading");
                }
            }
        }

        assertThat(violations).as("forbidden references in the shipped runtime").isEmpty();
    }

    @Test
    @DisplayName("the loader API is reached through exactly one class")
    void loaderApiIsConfinedToTheFacade() {
        List<String> touching = new ArrayList<String>();

        for (Path classFile : runtimeClassFiles()) {
            if (readAsLatin1(classFile).contains("net/fabricmc/loader/api/")) {
                touching.add(classFile.getFileName().toString());
            }
        }

        // Two: the facade, and the pre-launch entrypoint, which has to implement Fabric's interface
        // in order to be an entrypoint at all. Anything beyond that means the abstraction has leaked
        // and the "twelve stable methods" claim can no longer be checked by reading one file.
        assertThat(touching).containsExactlyInAnyOrder(
                "FabricLoaderFacade.class", "ContainerPreLaunch.class");
    }

    @Test
    @DisplayName("the scan actually sees the runtime, rather than passing on an empty set")
    void scanIsNotVacuous() {
        assertThat(runtimeClassFiles()).hasSizeGreaterThan(10);
    }

    private static boolean isPermitted(Path classFile, String reference) {
        String name = classFile.getFileName().toString();
        if (!"FabricLoaderFacade.class".equals(name) && !"ContainerPreLaunch.class".equals(name)) {
            return false;
        }
        // Even the exempt classes may only use the public API, never internals.
        return !reference.startsWith("net/fabricmc/loader/impl/");
    }

    private static List<Path> runtimeClassFiles() {
        final Path root = runtimeClassesRoot();
        final List<Path> found = new ArrayList<Path>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.getFileName().toString().endsWith(".class")) {
                        found.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("cannot scan the runtime classes at " + root, e);
        }
        return found;
    }

    /**
     * Class files are read as Latin-1 so every byte maps to one character. UTF-8 would mangle the
     * constant pool's length prefixes into multi-byte sequences and could split a searched-for name.
     */
    private static String readAsLatin1(Path file) {
        try {
            return new String(Files.readAllBytes(file), Charset.forName("ISO-8859-1"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    private static Path runtimeClassesRoot() {
        try {
            return Paths.get(dev.fabricmultiloader.runtime.RuntimeInfo.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the runtime classes directory", e);
        }
    }
}
