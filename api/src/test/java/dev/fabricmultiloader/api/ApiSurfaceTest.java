package dev.fabricmultiloader.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the evolution rules of the public API.
 *
 * <p>Whether an interface may grow an abstract method depends entirely on who implements it, and
 * that question has to be answered when the interface is written rather than when somebody's build
 * breaks two releases later. Enforcing the marker mechanically is the only way that stays true as
 * the surface grows.
 */
class ApiSurfaceTest {

    private static final String API_PACKAGE = "dev.fabricmultiloader.api";

    @Test
    @DisplayName("every public interface declares who implements it")
    void everyInterfaceCarriesAMarker() {
        List<String> unmarked = new ArrayList<String>();
        List<String> doubleMarked = new ArrayList<String>();

        for (Class<?> type : publicApiTypes()) {
            if (!type.isInterface() || type.isAnnotation()) {
                continue;
            }
            if (type.getEnclosingClass() != null) {
                // Nested callback interfaces inherit their intent from the enclosing type;
                // see nestedCallbackInterfacesAreExempt below.
                continue;
            }
            boolean byMod = type.isAnnotationPresent(ImplementedByMod.class);
            boolean byFramework = type.isAnnotationPresent(ImplementedByFramework.class);
            if (byMod && byFramework) {
                doubleMarked.add(type.getName());
            } else if (!byMod && !byFramework) {
                unmarked.add(type.getName());
            }
        }

        assertThat(unmarked)
                .as("interfaces missing @ImplementedByMod or @ImplementedByFramework")
                .isEmpty();
        assertThat(doubleMarked).as("interfaces carrying both markers").isEmpty();
    }

    @Test
    @DisplayName("nested callback interfaces inherit their intent from the enclosing type")
    void nestedCallbackInterfacesAreExempt() {
        // ItemBehavior.Context and ChannelHandle.C2SReceiver are implemented by whoever implements
        // the enclosing type; requiring a separate marker on each would be noise, so the rule above
        // only covers top-level interfaces. This test documents that the exemption is deliberate.
        List<Class<?>> nested = new ArrayList<Class<?>>();
        for (Class<?> type : publicApiTypes()) {
            if (type.isInterface() && type.getEnclosingClass() != null) {
                nested.add(type);
            }
        }
        assertThat(nested).as("nested interfaces exist and are exempt by design").isNotEmpty();
    }

    /**
     * Interfaces that are large by nature, with the reason. Every entry costs each payload a hand-
     * written implementation, so the list is meant to stay short and each addition to be argued.
     *
     * <p>{@code ByteSink}/{@code ByteSource} are a serialisation surface: one method per wire type
     * is the whole point, and the implementations are mechanical delegation to the version's packet
     * buffer. {@code ComponentApi} is a get/set pair per supported value type. {@code Events} is a
     * catalogue — one method per event whose Fabric API signature is stable across every supported
     * version — and the runtime ships the implementation, so a payload writes its own only where a
     * version genuinely diverges.
     */
    private static final List<String> LARGE_BY_DESIGN = Collections.unmodifiableList(
            new ArrayList<String>(java.util.Arrays.asList(
                    API_PACKAGE + ".net.ByteSink",
                    API_PACKAGE + ".net.ByteSource",
                    API_PACKAGE + ".capability.ComponentApi",
                    API_PACKAGE + ".event.Events")));

    @Test
    @DisplayName("interfaces mods implement stay small, apart from a documented few")
    void modImplementedInterfacesAreSmall() {
        for (Class<?> type : publicApiTypes()) {
            if (!type.isInterface() || !type.isAnnotationPresent(ImplementedByMod.class)) {
                continue;
            }
            if (LARGE_BY_DESIGN.contains(type.getName())) {
                continue;
            }
            int abstractMethods = 0;
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && !method.isDefault()) {
                    abstractMethods++;
                }
            }
            // Every abstract method here is something each payload must write by hand, so a large
            // surface directly multiplies the per-version adapter cost this design exists to keep
            // small. Eight is the point at which that stops being obviously fine.
            assertThat(abstractMethods)
                    .as(type.getName() + " abstract method count")
                    .isLessThanOrEqualTo(8);
        }
    }

    @Test
    @DisplayName("the exemption list has no stale entries")
    void exemptionsStillExist() {
        List<String> known = new ArrayList<String>();
        for (Class<?> type : publicApiTypes()) {
            known.add(type.getName());
        }
        assertThat(known).containsAll(LARGE_BY_DESIGN);
    }

    // The Java 8 baseline itself is enforced by --release 8 (records, var and sealed types simply
    // do not compile) and verified on the produced bytecode by the verifyBytecodeBaseline task.
    // Re-checking it reflectively here would need Java 16 API in a test that targets Java 8.

    @Test
    @DisplayName("the surface is actually being scanned, not silently empty")
    void scanFindsTheExpectedTypes() {
        List<String> names = new ArrayList<String>();
        for (Class<?> type : publicApiTypes()) {
            names.add(type.getName());
        }
        assertThat(names).hasSizeGreaterThan(30);
        assertThat(names).contains(
                API_PACKAGE + ".ModContext",
                API_PACKAGE + ".platform.Platform",
                API_PACKAGE + ".registry.Registries",
                API_PACKAGE + ".net.Networking",
                API_PACKAGE + ".command.Commands",
                API_PACKAGE + ".event.Events");
    }

    @Test
    @DisplayName("subsystem interfaces are implemented per payload, context types by the framework")
    void markerAssignmentsAreCorrect() {
        assertThat(dev.fabricmultiloader.api.registry.Registries.class
                .isAnnotationPresent(ImplementedByMod.class)).isTrue();
        assertThat(dev.fabricmultiloader.api.net.Networking.class
                .isAnnotationPresent(ImplementedByMod.class)).isTrue();
        assertThat(dev.fabricmultiloader.api.platform.Platform.class
                .isAnnotationPresent(ImplementedByMod.class)).isTrue();
        assertThat(UniversalMod.class.isAnnotationPresent(ImplementedByMod.class)).isTrue();

        assertThat(ModContext.class.isAnnotationPresent(ImplementedByFramework.class)).isTrue();
        assertThat(ServiceRegistry.class.isAnnotationPresent(ImplementedByFramework.class)).isTrue();
        assertThat(ModLogger.class.isAnnotationPresent(ImplementedByFramework.class)).isTrue();
        assertThat(dev.fabricmultiloader.api.ref.PlayerRef.class
                .isAnnotationPresent(ImplementedByFramework.class)).isTrue();
    }

    // ------------------------------------------------------------------ scanning

    private static List<Class<?>> publicApiTypes() {
        Path root = apiClassesRoot();
        final List<Class<?>> types = new ArrayList<Class<?>>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".class") || name.equals("package-info.class")) {
                        return FileVisitResult.CONTINUE;
                    }
                    String className = root.relativize(file).toString()
                            .replace('\\', '/')
                            .replace('/', '.')
                            .replace(".class", "");
                    if (!className.startsWith(API_PACKAGE)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Class<?> type = Class.forName(className, false,
                                ApiSurfaceTest.class.getClassLoader());
                        if (Modifier.isPublic(type.getModifiers())) {
                            types.add(type);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                        // A class the test classpath cannot resolve is not part of the surface.
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("cannot scan the api classes at " + root, e);
        }
        Collections.sort(types, new java.util.Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> left, Class<?> right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return types;
    }

    private static Path apiClassesRoot() {
        try {
            return Paths.get(ModContext.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the api classes directory", e);
        }
    }
}
