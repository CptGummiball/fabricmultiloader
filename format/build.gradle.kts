plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
    // The reference version matrix as a fixture, shared with the runtime's tests. Keeping one copy
    // means the two cannot drift apart, which for a matrix that encodes real Minecraft versions is
    // the difference between a test that guards behaviour and one that guards a stale assumption.
    `java-test-fixtures`
}

description = "Manifest model, JSON parser, version algebra and payload resolver — " +
    "shared verbatim by the runtime and the Gradle plugin so build-time and " +
    "runtime decisions cannot diverge."

// INVARIANT: this module has no runtime dependencies at all — not even Gson.
// See chapter 11.7 for the rationale. Anything added to `api`/`implementation`
// here would end up inside every universal JAR.
dependencies {
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Test-only, never published or shipped. VersionPredicateEquivalenceTest checks this module's
    // predicate implementation differentially against the real Fabric Loader one — the whole
    // architecture assumes the two agree about which payload a constraint selects, so that
    // agreement is verified rather than trusted.
    testImplementation(libs.fabric.loader)
}
