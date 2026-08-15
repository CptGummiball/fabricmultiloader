plugins {
    id("fabricmultiloader.java17-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "Test harness for the framework and for mod projects: FakeModContext, " +
    "JAR fixtures, the loader conformance harness and the server harness."

sourceSets {
    create("conformanceTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val conformanceTestImplementation: Configuration by configurations.getting
val conformanceTestRuntimeOnly: Configuration by configurations.getting

dependencies {
    api(project(":format"))
    api(project(":api"))

    // The harness fakes the runtime's view of the world, so it needs the interfaces that view is
    // expressed in — LoaderFacade, and the adapter halves (CommandRegistry, EventBus) that a
    // FakeModContext should delegate to rather than reimplement. A mod author's test then exercises
    // the same command collection and event dispatch logic that runs in the game.
    api(project(":runtime"))

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)

    conformanceTestImplementation(project(":format"))
    conformanceTestImplementation(project(":api"))
    conformanceTestImplementation(project(":runtime"))
    conformanceTestImplementation(libs.bundles.testing)
    conformanceTestRuntimeOnly(libs.junit.platform.launcher)
}

// ---------------------------------------------------------------------------
//  T3 — the loader conformance gate (chapter 32.4)
//
//  The whole architecture rests on one property of Fabric Loader that is not
//  formally specified: a nested mod candidate whose `depends` cannot be
//  satisfied, and which no loaded mod hard-depends on, is *dropped* rather than
//  causing a hard resolution failure. It is not our property to guarantee, so
//  it is measured — against every supported loader line, as a separate task
//  that CI runs nightly and before every release, so a regression in a new
//  loader is found before users find it.
// ---------------------------------------------------------------------------

/**
 * One loader per released line, newest patch of each.
 *
 * Spanning lines rather than sampling patches is deliberate: the solver was
 * rewritten between 0.14 and 0.15 and the candidate type was renamed in 0.16,
 * so these are the boundaries where the behaviour could plausibly change.
 */
val loaderMatrix = listOf("0.14.21", "0.15.11", "0.16.9", "0.16.14", "0.17.3", "0.19.3")

val loaderConfigurations = loaderMatrix.associateWith { version ->
    configurations.create("fabricLoader_" + version.replace('.', '_')) {
        isCanBeConsumed = false
        isCanBeResolved = true
        description = "Fabric Loader $version, resolved as a library for the conformance harness"
    }.also { configuration ->
        dependencies.add(configuration.name, "net.fabricmc:fabric-loader:$version")
    }
}

/**
 * Writes one properties file mapping each loader version to its full resolved
 * classpath. The harness reads it and builds an isolated class loader per
 * version — the only place in the project where a ClassLoader is constructed
 * at all, and it exists solely in test code.
 */
val loaderIndexFile = layout.buildDirectory.file("conformance/loaders.properties")

val writeLoaderIndex = tasks.register("writeLoaderIndex") {
    val classpaths = loaderConfigurations.mapValues { (_, configuration) -> files(configuration) }
    val target = loaderIndexFile
    classpaths.values.forEach { inputs.files(it) }
    outputs.file(target)
    doLast {
        val text = classpaths.entries.joinToString("\n") { (version, classpath) ->
            val joined = classpath.files.joinToString(File.pathSeparator) { it.absolutePath }
            // Backslashes are escape characters in a properties file, and every path on Windows
            // is full of them.
            version + "=" + joined.replace("\\", "\\\\")
        }
        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeText(text + "\n", Charsets.UTF_8)
    }
}

val conformanceTest = tasks.register<Test>("conformanceTest") {
    group = "verification"
    description = "Proves the load-bearing assumption against every supported Fabric Loader."
    testClassesDirs = sourceSets["conformanceTest"].output.classesDirs
    classpath = sourceSets["conformanceTest"].runtimeClasspath
    useJUnitPlatform()
    dependsOn(writeLoaderIndex)
    systemProperty("fabricmultiloader.conformance.index",
        loaderIndexFile.get().asFile.absolutePath)
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Deliberately not wired into `check`: it resolves six loader distributions and
// is a nightly gate, not a per-commit one. `./gradlew :testing:conformanceTest`
// runs it, and conformance.yml runs it on a schedule and before every release.

