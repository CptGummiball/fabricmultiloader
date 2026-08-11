import dev.fabricmultiloader.build.VerifyClassfileVersionTask

/**
 * For build-time-only modules that never run inside Minecraft:
 * `gradle-plugin` and `testing`.
 *
 * Gradle 8.x runs on Java 17+, so 17 is the safe floor here. These modules may
 * use records, `var`, sealed types and switch expressions freely.
 */

plugins {
    id("fabricmultiloader.java-conventions")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

val verifyBytecodeBaseline = tasks.register<VerifyClassfileVersionTask>("verifyBytecodeBaseline") {
    group = "verification"
    description = "Fails if any produced class file exceeds Java 17 bytecode (major 61)."
    classesDirs.from(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
    maxClassfileMajor.set(61)
    moduleName.set(project.name)
    report.set(layout.buildDirectory.file("reports/fabricmultiloader/bytecode-baseline.txt"))
}

tasks.named("check") {
    dependsOn(verifyBytecodeBaseline)
}
