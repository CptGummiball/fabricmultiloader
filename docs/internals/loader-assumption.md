# The load-bearing assumption

FabricMultiLoader rests on one property of Fabric Loader that no document specifies:

> **A nested mod candidate whose `depends` cannot be satisfied, and which no loaded mod hard-depends on, is
> _not selected_ by the mod solver — rather than causing a resolution failure.**

Everything else follows. A universal JAR carries one complete Fabric mod per Minecraft version; on any given launch
all but one of them are unsatisfiable. If an unsatisfiable nested candidate were fatal, the JAR would refuse to
start on every version except the newest, and the whole architecture would be worthless.

This page records what the property is, why it is believed, how it is measured, and what happens if it ever stops
holding.

---

## 1. Why it is believed

It is the observable behaviour of every Fabric Loader line from 0.14 to 0.19, and it is the reason Jar-in-Jar
libraries with narrow Minecraft ranges work throughout the ecosystem: a library nested in two mods, one built for
1.20.1 and one for 1.21.4, is routinely present in both and routinely resolves to whichever copy fits.

Structurally, the loader models nested mods as *optional* candidates in its SAT problem. A root mod — one found in
the `mods/` folder — is required, so its unmet dependency is a hard conflict. A nested mod is a candidate the
solver may or may not choose, and it only becomes required when something that *is* required depends on it. That
distinction is precisely what the design depends on, and it is why the container deliberately does **not**
hard-`depends` on its payload alias (ADR-007): a hard dependency would turn every non-matching payload back into a
conflict, and would replace our diagnostic with the loader's "requires examplemod-impl, which is missing".

None of that is a promise. It is an implementation detail of someone else's project, so it is measured.

## 2. How it is measured

`./gradlew :testing:conformanceTest` runs
[`LoaderConformanceTest`](../../testing/src/conformanceTest/java/dev/fabricmultiloader/conformance/LoaderConformanceTest.java)
— eight properties against every supported loader line, using each loader's **real** solver.

| | |
|---|---|
| **Loader matrix** | 0.14.21, 0.15.11, 0.16.9, 0.16.14, 0.17.3, 0.19.3 — the newest patch of each released line |
| **Entry point** | `net.fabricmc.loader.impl.discovery.ModResolver.resolve(Collection, EnvType, Map)` |
| **Isolation** | one `URLClassLoader` per loader version, parented to the **platform** loader |
| **Input** | `fabric.mod.json` documents produced by `FabricModJsonWriter` — the same code the assembler uses |
| **Run** | nightly, on manual dispatch, and before every release (`.github/workflows/conformance.yml`) |

Three details of that table carry weight.

**The parent is the platform class loader, not the application loader.** With the application loader as parent,
`ModResolver` would resolve against whatever Fabric Loader happens to be on the test classpath, and the harness
would silently test one version six times while reporting six.

**The metadata is generated, not hand-written.** The property has to hold for the files the build actually emits.
A fixture with hand-tuned `depends` would prove something about a JAR nobody ships.

**Real ZIP files are deliberately not built.** Reaching the solver through `ModDiscoverer` would require
constructing a `FabricLoaderImpl` and a `GameProvider` — a large surface with no bearing on the question, whose
breakage across loader versions would look exactly like the assumption failing.

### The eight properties

| Test | What it asserts |
|---|---|
| `nestedUnsatisfiableIsDropped` | **The assumption.** A payload for 1.20.1 is dropped on 1.21.4; the container and the matching payload load. |
| `exactlyOneSelected` | With three disjoint payloads, exactly one is selected. |
| `providesExclusivity` | Two payloads sharing a `provides` alias are never both selected — tested with mutual `breaks` removed, so the alias is the only thing that can separate them. |
| `breaksExclusivity` | Mutual `breaks` keep two otherwise valid payloads apart. |
| `javaDependencyEvaluated` | `depends.java` is a real solver clause: on Java 17 with Minecraft 1.21.4, the container loads and *no* payload does. |
| `environmentEvaluated` | A `client`-only payload is absent on a dedicated server and present on a client. |
| `runtimeDeduplication` | Two universal mods nesting runtime 1.0.0 and 1.1.0 end up with exactly one `fabricmultiloader`, the newer. |
| `containerRangeError` | A Minecraft version outside the union is a hard loader failure — which is what we want, because the loader's own error names the supported ranges better than we could after the fact. |

## 3. Result

As of implementation step 11: **48 of 48 green** — eight properties across six loader lines.

The assumption holds unchanged from 0.14.21 through 0.19.3, including across the solver rewrite between 0.14 and
0.15 and the candidate-type rename in 0.16.

### What the measurement corrected

Two things the design got wrong, both found by running this rather than by reading the loader.

**`environment` is not evaluated by the solver.** It is applied during *discovery*: `ModDiscoverer` skips a mod
that does not load in the current environment and records it in the `envDisabledMods` map, which `ModResolver` then
merely receives. This is *stronger* than chapter 13.7 assumed — a client-only payload on a dedicated server is not
rejected by the solver, it never reaches the solver — but a harness that skipped the discovery step would have
reported the opposite of the truth, so the harness models it.

**Nested candidates must be linked to their parent and passed flat.** `ModCandidateImpl.createPlain(…, nestedMods)`
records the children, but the discoverer additionally calls `addParent` on each one, and `ModResolver.resolve`
receives the flattened set of roots *and* nested candidates. A nested candidate that does not know its parent is
treated as an orphan and never considered — the first version of this harness produced "fabricmultiloader is
missing" for exactly that reason.

## 4. Fragility, and what it costs

The harness reflects into `net.fabricmc.loader.impl`. That is forbidden in the runtime (invariant I1) and
deliberate here, and the API surface it touches is small:

| What | Stability across 0.14 → 0.19 |
|---|---|
| `ModResolver.resolve(Collection, EnvType, Map)` | unchanged |
| `ModCandidate` → `ModCandidateImpl` | renamed in 0.16 — two-name lookup |
| `createPlain`, `createNested`, `addParent`, `getId` | unchanged |
| `ModMetadataParser.parseMetadata(…)` | gained a trailing `boolean` — chosen by parameter count |
| `BuiltinModMetadata.Builder`, `BuiltinMetadataWrapper` | unchanged |

If a loader update breaks the reflection, `conformanceTest` fails loudly with "its internals changed and the
harness needs updating". That is the early warning the gate exists to provide, not a defect in it: a new loader
that this cannot drive is a new loader whose behaviour nobody has checked, and the correct response is to check it
by hand before shipping.

The harness distinguishes a resolution *failure* from a *harness* failure. Anything other than a
`ModResolutionException` is reported as the harness being out of date, so a broken binding can never be mistaken
for a broken assumption.

## 5. If it ever fails

The step is a **gate**. A failure means the architecture is refuted for that loader line, and nothing else proceeds
until the fallback is evaluated (chapter 41):

1. **Confirm it is the assumption, not the harness.** A `ModResolutionException` naming a nested payload is the
   assumption; a `NoSuchMethodException` is the harness.
2. **Establish the boundary.** Which loader versions are affected, and does the container's lack of a hard
   `depends` on the payload alias still hold the line?
3. **Fall back if it does not.** Slim single-version JARs become the primary product, with the universal JAR
   restricted to the loader range where the property holds. The container format already supports this: a slim JAR
   is a payload started with `-Dfabricmultiloader.slim=true`, which is the same code path as the development
   fallback (chapter 9.7) and is tested independently of this gate.

The fallback exists because the assumption might fail. It is not a consolation prize invented afterwards — it is
why `omni/payload.json` carries a copy of the container identity in the first place.

---

## Running it yourself

```bash
./gradlew :testing:conformanceTest
```

Requires network access on the first run: it resolves six Fabric Loader distributions from `maven.fabricmc.net`.
Each is self-contained — Sat4j, Gson and mapping-io are relocated into `net.fabricmc.loader.impl.lib` — so one JAR
per version is the entire isolated classpath.

Adding a loader version is one entry in `loaderMatrix` in
[`testing/build.gradle.kts`](../../testing/build.gradle.kts). Nothing else changes.
