plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "The FabricMultiLoader runtime — itself an ordinary Fabric mod " +
    "(mod id 'fabricmultiloader') nested into every universal JAR. Bootstrap, " +
    "environment detection, payload activation, lifecycle and diagnostics."

// The runtime ships as a Fabric mod, so its fabric.mod.json carries the real version.
// Deliberately no `minecraft` dependency: this library must load on every supported version.
tasks.named<ProcessResources>("processResources") {
    val moduleVersion = project.version.toString()
    inputs.property("version", moduleVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to moduleVersion)
    }
}

dependencies {
    api(project(":format"))
    api(project(":api"))

    // compileOnly against the LOWEST supported loader (chapter 9.3): this makes
    // accidental use of newer loader API a compile error rather than a crash on
    // an old loader in the wild.
    compileOnly(libs.fabric.loader)

    // Mixin is on the system class loader in every Fabric environment (chapter 13.2), so the
    // conditional mixin plugin can implement IMixinConfigPlugin without the runtime ever shipping
    // or depending on Mixin at run time. Exactly one class references it, and only that class is
    // loaded — by Mixin itself, when a payload's config names it as its plugin.
    compileOnly(libs.sponge.mixin)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.fabric.loader)
    testImplementation(libs.sponge.mixin)
    testImplementation(testFixtures(project(":format")))
    testRuntimeOnly(libs.junit.platform.launcher)
}
