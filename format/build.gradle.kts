plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
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
}
