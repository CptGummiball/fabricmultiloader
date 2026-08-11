plugins {
    id("fabricmultiloader.java17-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "Test harness for the framework and for mod projects: FakeModContext, " +
    "JAR fixtures, the loader conformance harness and the server harness."

dependencies {
    api(project(":format"))
    api(project(":api"))

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}
