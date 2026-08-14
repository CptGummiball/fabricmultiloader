package dev.fabricmultiloader.runtime.mixin;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validator rule {@code OMNI-1035}, checked against the framework's own bytecode.
 *
 * <p>{@code ConditionalMixinPlugin} is the only FabricMultiLoader class that can run before
 * {@code preLaunch} — Mixin instantiates it during {@code select()}. At that point
 * {@code RuntimeBootstrap} has not run, no container has been resolved, and there is no diagnostic
 * report to write a failure into. A reference from this package into the rest of the runtime would
 * therefore not merely be untidy: it would initialise the framework at the one moment when its own
 * error reporting does not exist yet, turning any problem into an unexplained crash before the mod
 * list is even complete.
 *
 * <p>The rule is checked on class files rather than on imports, because a reference introduced
 * indirectly — a helper that happens to touch the bootstrap — is exactly the kind that gets added
 * without anyone noticing.
 */
class MixinPluginIsolationTest {

    /** What the mixin package must not reach, and why. */
    private static final Map<String, String> FORBIDDEN = forbidden();

    private static Map<String, String> forbidden() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put("dev/fabricmultiloader/runtime/boot/",
                "the plugin runs before RuntimeBootstrap and must not trigger it");
        map.put("dev/fabricmultiloader/runtime/payload/",
                "payload activation happens in preLaunch, long after mixin selection");
        map.put("dev/fabricmultiloader/runtime/context/",
                "no ModContext exists yet when mixin configs are selected");
        map.put("dev/fabricmultiloader/runtime/entrypoint/",
                "entrypoints have not been invoked at this point");
        map.put("dev/fabricmultiloader/runtime/diag/",
                "there is nowhere to write a diagnostic report before preLaunch");
        map.put("dev/fabricmultiloader/api/",
                "the developer SPI is for mod code, not for the pre-preLaunch phase");
        map.put("net/minecraft/",
                "no Minecraft class may be named by a class loaded on every version (I3)");
        map.put("com/mojang/",
                "same as net/minecraft");
        map.put("net/fabricmc/fabric/api/",
                "Fabric API is a mod and may not even be loaded when mixins are selected");
        return map;
    }

    @Test
    @DisplayName("the mixin package reaches only the JDK, format, the loader facade and Mixin")
    void mixinPackageIsIsolated() {
        List<String> violations = new ArrayList<String>();

        for (Path classFile : mixinClassFiles()) {
            String contents = readAsLatin1(classFile);
            String name = classFile.getFileName().toString();
            for (Map.Entry<String, String> rule : FORBIDDEN.entrySet()) {
                if (contents.contains(rule.getKey())) {
                    violations.add("OMNI-1035: " + name + " references " + rule.getKey()
                            + " — " + rule.getValue());
                }
            }
        }

        assertThat(violations).as("isolation violations in runtime.mixin").isEmpty();
    }

    @Test
    @DisplayName("the plugin reaches the loader only through the facade")
    void usesTheLoaderFacade() {
        // The design sketch called FabricLoader.getInstance() directly here. Going through the
        // facade instead keeps the loader API countable in one file — the claim ForbiddenReferences
        // Test checks — and is what makes the plugin testable against a fake loader at all.
        String plugin = readAsLatin1(classFile("ConditionalMixinPlugin.class"));

        assertThat(plugin).contains("dev/fabricmultiloader/runtime/loader/LoaderFacade");
        assertThat(plugin).doesNotContain("net/fabricmc/loader/api/FabricLoader");
    }

    @Test
    @DisplayName("the scan actually sees the mixin package")
    void scanIsNotVacuous() {
        assertThat(mixinClassFiles()).hasSizeGreaterThanOrEqualTo(4);
    }

    private static Path classFile(String fileName) {
        for (Path candidate : mixinClassFiles()) {
            if (candidate.getFileName().toString().equals(fileName)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no class file named " + fileName);
    }

    private static List<Path> mixinClassFiles() {
        final List<Path> found = new ArrayList<Path>();
        try {
            Files.walkFileTree(mixinPackageRoot(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.getFileName().toString().endsWith(".class")) {
                        found.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("cannot scan the mixin package", e);
        }
        return found;
    }

    private static String readAsLatin1(Path file) {
        try {
            return new String(Files.readAllBytes(file), Charset.forName("ISO-8859-1"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    private static Path mixinPackageRoot() {
        try {
            Path classesRoot = Paths.get(ConditionalMixinPlugin.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return classesRoot.resolve("dev/fabricmultiloader/runtime/mixin");
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the mixin package", e);
        }
    }
}
