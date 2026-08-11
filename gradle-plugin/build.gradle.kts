plugins {
    id("fabricmultiloader.java17-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "The four Gradle plugins (settings, common, version, universal): " +
    "matrix parsing, metadata generation, resource merging, container assembly and validation."

// Published as `fabricmultiloader-gradle`, not `fabricmultiloader-gradle-plugin`.
base {
    archivesName.set("fabricmultiloader-gradle")
}
publishing.publications.named<MavenPublication>("maven") {
    artifactId = "fabricmultiloader-gradle"
}

dependencies {
    // Same version algebra and manifest model as the runtime — the whole point
    // of `format` being a separate module (chapter 22.2).
    implementation(project(":format"))

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The `java-gradle-plugin` and Kotlin setup arrives with the first real plugin
// implementation in step 12; until then this module is plain Java so that step 1
// stays free of a Kotlin toolchain download.
