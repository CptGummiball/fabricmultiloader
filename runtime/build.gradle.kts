import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    id("fabricmultiloader.java8-conventions")
    id("fabricmultiloader.publishing-conventions")
}

// The Java 8 baseline is about the bytecode that ships inside every universal jar, not about the
// tests. Those run on the build's own JDK and consume :testing, which is Java 17 — so the test
// source set is compiled and resolved at 17 while compileJava stays at 8. verifyBytecodeBaseline
// scans compileJava's output only, so the guarantee that matters is untouched.
tasks.named<JavaCompile>("compileTestJava") {
    options.release.set(17)
}
listOf("testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
        }
    }
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
    // The published harness, used by the runtime's own tests. Not circular: :testing depends on
    // this module's main output, and this line is on the test compile classpath. Eating the same
    // food as mod authors keeps FakeFabricLoader honest — a gap in it shows up here first.
    testImplementation(project(":testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}
