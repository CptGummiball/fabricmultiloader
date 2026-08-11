plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "The developer SPI: ModContext, Platform, Registries, Networking, " +
    "Commands, Events, Services and Capabilities. Contains no Minecraft types."

dependencies {
    // `api` rather than `implementation`: mod code that compiles against the SPI
    // must also see SemVer, VersionRange and the Id type from format.
    api(project(":format"))

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}
