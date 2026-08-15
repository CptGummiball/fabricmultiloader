package dev.fabricmultiloader.testing.conformance;

import dev.fabricmultiloader.format.Side;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a real Fabric Loader's mod solver over a synthetic universal mod, and reports what it chose.
 *
 * <h2>What this proves, and why it has to be measured rather than argued</h2>
 *
 * <p>The architecture rests on one property of Fabric Loader that no document specifies: a nested
 * mod candidate whose {@code depends} cannot be satisfied, and which no loaded mod hard-depends on,
 * is <em>dropped</em> rather than causing a resolution failure. Everything else in this project
 * follows from it — if it were false, a universal jar would refuse to launch on every version except
 * the newest, and the fallback path in chapter 41 would become the product.
 *
 * <p>It is the behaviour of every loader line from 0.14 to 0.19 and the reason Jar-in-Jar libraries
 * with narrow Minecraft ranges work throughout the ecosystem. But it is someone else's
 * implementation detail, so it is checked against every supported line, nightly, and before every
 * release — a new loader is therefore tested before users reach it.
 *
 * <h2>How</h2>
 *
 * <p>The loader is driven as a library. Its solver entry point is
 * {@code ModResolver.resolve(Collection, EnvType, Map)}, which is reached reflectively through the
 * isolated class loader of {@link LoaderVersion}. The API shape has been stable across the whole
 * matrix — only the candidate class was renamed ({@code ModCandidate} to {@code ModCandidateImpl} in
 * 0.16) and {@code ModMetadataParser.parseMetadata} gained a trailing flag — so the version-specific
 * part is two lookups, not an adapter per line.
 *
 * <p>Reflection into {@code net.fabricmc.loader.impl} is forbidden in the runtime and deliberate
 * here. If a loader update breaks it, this fails loudly and the assumption is re-examined by hand —
 * which is the early warning the gate exists to provide, not a defect in it.
 */
public final class LoaderConformanceHarness {

    private static final Map<String, Reflection> BINDINGS = new HashMap<String, Reflection>();

    private final LoaderVersion loader;

    /**
     * @param loader the loader to drive
     */
    public LoaderConformanceHarness(LoaderVersion loader) {
        this.loader = loader;
    }

    /** The environment a resolution runs against. */
    public static final class Env {

        private final String minecraft;
        private final String java;
        private final String loaderVersion;
        private final Side side;

        private Env(String minecraft, String java, String loaderVersion, Side side) {
            this.minecraft = minecraft;
            this.java = java;
            this.loaderVersion = loaderVersion;
            this.side = side;
        }

        /**
         * Builds an environment.
         *
         * @param minecraft the Minecraft version, e.g. {@code 1.21.4}
         * @param java the Java version, e.g. {@code 21.0.5}
         * @param side the physical side
         * @return the environment
         */
        public static Env of(String minecraft, String java, Side side) {
            return new Env(minecraft, java, "0.99.0", side);
        }

        /** A server running the given Minecraft and Java versions. */
        public static Env server(String minecraft, String java) {
            return of(minecraft, java, Side.SERVER);
        }

        /** A client running the given Minecraft and Java versions. */
        public static Env client(String minecraft, String java) {
            return of(minecraft, java, Side.CLIENT);
        }

        /**
         * Pins the reported loader version.
         *
         * <p>Defaults to something above every payload's minimum, because the payloads declare a
         * loader floor and a resolution failing on that instead of on the property under test would
         * be a silently passing test.
         */
        public Env withLoaderVersion(String value) {
            return new Env(minecraft, java, value, side);
        }

        @Override
        public String toString() {
            return "mc=" + minecraft + " java=" + java + " side=" + side.id();
        }
    }

    /**
     * Resolves a synthetic mod set.
     *
     * @param container the mods to offer the solver
     * @param env the environment to resolve against
     * @return what the loader chose, or why it refused
     */
    public ResolutionProbe resolve(SyntheticContainer container, Env env) {
        Reflection binding = binding();
        try {
            List<Object> candidates = new ArrayList<Object>();
            candidates.add(binding.builtin("minecraft", env.minecraft));
            candidates.add(binding.builtin("java", env.java));
            candidates.add(binding.builtin("fabricloader", env.loaderVersion));

            Object envType = binding.envType(env.side);

            for (String containerModId : container.containerModIds()) {
                Object containerMetadata = binding.metadata(
                        container.containerModJson(containerModId));
                if (!binding.loadsInEnvironment(containerMetadata, envType)) {
                    continue;
                }

                List<Object> nested = new ArrayList<Object>();
                for (String modJson : container.nestedModJson(containerModId)) {
                    Object metadata = binding.metadata(modJson);
                    // The environment filter runs during discovery, not in the solver — see the
                    // note on the method below. A candidate the discoverer would have skipped is
                    // never constructed here either.
                    if (binding.loadsInEnvironment(metadata, envType)) {
                        nested.add(binding.nested(metadata));
                    }
                }

                Object parent = binding.plain(containerMetadata, containerModId, nested);
                // The nesting is bidirectional in the loader's model and the discoverer links it
                // explicitly: a candidate that does not know its parent is treated as an orphan and
                // is never considered. The solver receives the flattened set, roots and nested
                // alike, and uses isRoot() to tell them apart.
                for (Object child : nested) {
                    binding.addParent.invoke(child, parent);
                }
                candidates.add(parent);
                candidates.addAll(nested);
            }

            Object result = binding.resolve.invoke(null, candidates, envType,
                    new HashMap<String, Set<Object>>());

            Set<String> ids = new LinkedHashSet<String>();
            for (Object candidate : (Collection<?>) result) {
                ids.add((String) binding.getId.invoke(candidate));
            }
            return ResolutionProbe.selected(loader.version(), ids);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (isResolutionFailure(target)) {
                return ResolutionProbe.failed(loader.version(), String.valueOf(target.getMessage()),
                        target);
            }
            throw new IllegalStateException("fabric-loader " + loader.version()
                    + " failed in a way that is not a resolution failure — the harness itself is "
                    + "probably out of date with this loader", target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot drive fabric-loader " + loader.version()
                    + ": its internals changed and the harness needs updating", e);
        }
    }

    /**
     * A resolution failure is the loader saying "these mods cannot run together"; anything else is
     * the harness being wrong about the loader. Matching on the class name rather than the type is
     * unavoidable — the exception class belongs to the isolated loader, not to this one.
     */
    private static boolean isResolutionFailure(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current.getClass().getName().endsWith("ModResolutionException")) {
                return true;
            }
        }
        return false;
    }

    private synchronized Reflection binding() {
        Reflection existing = BINDINGS.get(loader.version());
        if (existing == null) {
            existing = new Reflection(loader);
            BINDINGS.put(loader.version(), existing);
        }
        return existing;
    }

    /** Everything version-specific about driving one loader, resolved once. */
    private static final class Reflection {

        private final LoaderVersion loader;
        private final ClassLoader isolated;
        private final Class<?> candidateClass;
        private final Class<?> envTypeClass;
        private final Method createPlain;
        private final Method createNested;
        private final Method parseMetadata;
        private final Method resolve;
        private final Method getId;
        private final Method addParent;
        private final Method loadsInEnvironment;
        private final Object versionOverrides;
        private final Object dependencyOverrides;
        private final boolean parseMetadataTakesDevelopmentFlag;
        private final Map<String, Object> builtins = new LinkedHashMap<String, Object>();

        Reflection(LoaderVersion loader) {
            this.loader = loader;
            this.isolated = loader.classLoader();
            try {
                // Renamed in 0.16; the shape is otherwise unchanged across the whole matrix.
                this.candidateClass = firstOf(
                        "net.fabricmc.loader.impl.discovery.ModCandidateImpl",
                        "net.fabricmc.loader.impl.discovery.ModCandidate");
                this.envTypeClass = load("net.fabricmc.api.EnvType");
                Class<?> metadataClass = load("net.fabricmc.loader.impl.metadata.LoaderModMetadata");
                Class<?> parserClass = load("net.fabricmc.loader.impl.metadata.ModMetadataParser");
                Class<?> resolverClass = load("net.fabricmc.loader.impl.discovery.ModResolver");
                Class<?> versionOverridesClass =
                        load("net.fabricmc.loader.impl.metadata.VersionOverrides");
                Class<?> dependencyOverridesClass =
                        load("net.fabricmc.loader.impl.metadata.DependencyOverrides");

                this.createPlain = candidateClass.getDeclaredMethod("createPlain",
                        List.class, metadataClass, boolean.class, Collection.class);
                this.createPlain.setAccessible(true);
                this.createNested = candidateClass.getDeclaredMethod("createNested",
                        String.class, long.class, metadataClass, boolean.class, Collection.class);
                this.createNested.setAccessible(true);
                this.getId = candidateClass.getMethod("getId");
                this.addParent = candidateClass.getDeclaredMethod("addParent", candidateClass);
                this.addParent.setAccessible(true);
                this.loadsInEnvironment =
                        metadataClass.getMethod("loadsInEnvironment", envTypeClass);
                this.resolve = resolverClass.getMethod("resolve",
                        Collection.class, envTypeClass, Map.class);

                Method parser = null;
                boolean withFlag = false;
                for (Method candidate : parserClass.getMethods()) {
                    if ("parseMetadata".equals(candidate.getName())) {
                        parser = candidate;
                        withFlag = candidate.getParameterCount() == 6;
                    }
                }
                if (parser == null) {
                    throw new NoSuchMethodException("ModMetadataParser.parseMetadata");
                }
                this.parseMetadata = parser;
                this.parseMetadataTakesDevelopmentFlag = withFlag;

                this.versionOverrides = versionOverridesClass.getConstructor().newInstance();
                // Points at a directory holding no fabric_loader_dependencies.json, which is the
                // normal case and the one a conformance run must model.
                this.dependencyOverrides = dependencyOverridesClass
                        .getConstructor(Path.class)
                        .newInstance(Paths.get("build", "conformance", "no-overrides"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot bind to fabric-loader " + loader.version()
                        + " — its internals changed and the harness needs updating", e);
            }
        }

        private Class<?> load(String name) throws ClassNotFoundException {
            return Class.forName(name, true, isolated);
        }

        private Class<?> firstOf(String... names) throws ClassNotFoundException {
            ClassNotFoundException last = null;
            for (String name : names) {
                try {
                    return load(name);
                } catch (ClassNotFoundException e) {
                    last = e;
                }
            }
            throw last;
        }

        Object metadata(String modJson) throws ReflectiveOperationException {
            InputStream in = new ByteArrayInputStream(modJson.getBytes(StandardCharsets.UTF_8));
            List<String> parentPaths = Collections.emptyList();
            if (parseMetadataTakesDevelopmentFlag) {
                return parseMetadata.invoke(null, in, "synthetic", parentPaths,
                        versionOverrides, dependencyOverrides, Boolean.FALSE);
            }
            return parseMetadata.invoke(null, in, "synthetic", parentPaths,
                    versionOverrides, dependencyOverrides);
        }

        /**
         * Whether the loader would offer this mod to the solver at all.
         *
         * <p>A finding of this harness, and one worth stating plainly: {@code environment} is
         * <em>not</em> evaluated by {@code ModResolver}. The discoverer applies it and records what
         * it dropped in the {@code envDisabledMods} map the solver then merely receives. That is
         * stronger than the design assumed — a client-only payload on a dedicated server is not
         * rejected by the solver, it never reaches the solver — but it means a harness that skipped
         * this step would report the opposite of the truth.
         */
        boolean loadsInEnvironment(Object metadata, Object envType)
                throws ReflectiveOperationException {
            return (Boolean) loadsInEnvironment.invoke(metadata, envType);
        }

        Object plain(Object metadata, String modId, Collection<Object> nested)
                throws ReflectiveOperationException {
            List<Path> paths = Collections.singletonList(
                    Paths.get("build", "conformance", modId + ".jar"));
            return createPlain.invoke(null, paths, metadata, Boolean.FALSE, nested);
        }

        Object nested(Object metadata) throws ReflectiveOperationException {
            // The hash is the loader's identity for a nested jar and is what deduplication keys
            // on, so it has to follow the content — two containers nesting different runtime
            // versions must not look like the same file.
            long hash = (metadata.toString().hashCode() * 31L
                    + String.valueOf(getMetadataVersion(metadata)).hashCode()) & 0xFFFFFFFFL;
            return createNested.invoke(null, "META-INF/jars/synthetic.jar", Long.valueOf(hash),
                    metadata, Boolean.FALSE, Collections.emptyList());
        }

        private Object getMetadataVersion(Object metadata) {
            try {
                return metadata.getClass().getMethod("getVersion").invoke(metadata);
            } catch (ReflectiveOperationException e) {
                return metadata;
            }
        }

        /**
         * A built-in mod — {@code minecraft}, {@code java}, {@code fabricloader} — exactly as the
         * loader constructs one.
         *
         * <p>{@code BuiltinModMetadata.Builder#build} returns the public {@code ModMetadata}, which
         * the solver cannot use; the loader adapts it through the package-private
         * {@code BuiltinMetadataWrapper}, and so does this. Building the same three mods from
         * synthetic {@code fabric.mod.json} documents would have been easier and would have made
         * them {@code type: "fabric"} rather than {@code "builtin"} — a difference the solver's
         * error paths do look at.
         */
        Object builtin(String modId, String version) throws ReflectiveOperationException {
            String key = modId + "@" + version;
            Object existing = builtins.get(key);
            if (existing != null) {
                return existing;
            }
            Class<?> builderClass =
                    load("net.fabricmc.loader.impl.metadata.BuiltinModMetadata$Builder");
            Object builder = builderClass.getConstructor(String.class, String.class)
                    .newInstance(modId, version);
            Object metadata = builderClass.getMethod("build").invoke(builder);

            Class<?> wrapperClass =
                    load("net.fabricmc.loader.impl.discovery.BuiltinMetadataWrapper");
            Class<?> modMetadataClass = load("net.fabricmc.loader.api.metadata.ModMetadata");
            java.lang.reflect.Constructor<?> wrap =
                    wrapperClass.getDeclaredConstructor(modMetadataClass);
            wrap.setAccessible(true);

            Object candidate = createPlain.invoke(null,
                    Collections.singletonList(Paths.get("build", "conformance", modId)),
                    wrap.newInstance(metadata), Boolean.FALSE, Collections.emptyList());
            builtins.put(key, candidate);
            return candidate;
        }

        Object envType(Side side) throws ReflectiveOperationException {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<Enum>) envTypeClass.asSubclass(Enum.class),
                    side == Side.CLIENT ? "CLIENT" : "SERVER");
            return value;
        }
    }
}
