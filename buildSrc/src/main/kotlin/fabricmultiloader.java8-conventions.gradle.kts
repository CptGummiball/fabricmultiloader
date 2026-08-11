import dev.fabricmultiloader.build.VerifyClassfileVersionTask

/**
 * For modules that are loaded on the oldest supported JVM: `format`, `api`,
 * `runtime`, `processor`.
 *
 * Java 8 bytecode (class file major 52) is enforced twice — by `--release 8` at
 * compile time (which also rejects newer JDK API, not just newer syntax) and by
 * a class file scan of the produced output. See chapters 14.2 and 14.7.
 *
 * Practical consequence for contributors: no `var`, no records, no sealed types,
 * no switch expressions, no `List.of`. This is the price of staying loadable on
 * Minecraft 1.16.5 era environments; the builder pattern compensates.
 */

plugins {
    id("fabricmultiloader.java-conventions")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

val verifyBytecodeBaseline = tasks.register<VerifyClassfileVersionTask>("verifyBytecodeBaseline") {
    group = "verification"
    description = "Fails if any produced class file exceeds Java 8 bytecode (major 52)."
    classesDirs.from(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
    maxClassfileMajor.set(52)
    moduleName.set(project.name)
    report.set(layout.buildDirectory.file("reports/fabricmultiloader/bytecode-baseline.txt"))
}

tasks.named("check") {
    dependsOn(verifyBytecodeBaseline)
}
