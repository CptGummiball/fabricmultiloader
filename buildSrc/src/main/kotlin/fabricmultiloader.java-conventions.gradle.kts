import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
}

val toolchainVersion = providers.gradleProperty("fabricmultiloader.toolchain").getOrElse("21").toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -options silences "source value 8 is obsolete": the Java 8 baseline is
    // deliberate (chapter 14.7), not an oversight.
    options.compilerArgs.addAll(listOf("-Xlint:all,-options,-processing", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        // Tightened to -Xdoclint:all in implementation step 19 (documentation).
        addStringOption("Xdoclint:none", "-quiet")
    }
}

// Reproducible archives from day one — see chapter 10.5 / G7.
val moduleName = project.name
val moduleVersion = project.version.toString()
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes(
            "Implementation-Title" to moduleName,
            "Implementation-Version" to moduleVersion,
            "Implementation-Vendor" to "FabricMultiLoader",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
