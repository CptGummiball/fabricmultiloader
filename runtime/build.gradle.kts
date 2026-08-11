plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "The FabricMultiLoader runtime — itself an ordinary Fabric mod " +
    "(mod id 'fabricmultiloader') nested into every universal JAR. Bootstrap, " +
    "environment detection, payload activation, lifecycle and diagnostics."

dependencies {
    api(project(":format"))
    api(project(":api"))

    // compileOnly against the LOWEST supported loader (chapter 9.3): this makes
    // accidental use of newer loader API a compile error rather than a crash on
    // an old loader in the wild.
    compileOnly(libs.fabric.loader)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.fabric.loader)
    testRuntimeOnly(libs.junit.platform.launcher)
}
