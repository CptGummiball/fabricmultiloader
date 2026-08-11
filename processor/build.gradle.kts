plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "Annotation processor that derives the Omni entrypoint list from " +
    "@UniversalEntrypoint, so mod authors do not have to declare entrypoints twice."

dependencies {
    implementation(project(":format"))
    compileOnly(project(":api"))

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}
