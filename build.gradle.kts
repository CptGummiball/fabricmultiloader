plugins {
    base
}

description = "FabricMultiLoader — one universal Fabric mod JAR for many Minecraft versions"

// No cross-project configuration here on purpose (ADR-005): every module applies
// its own convention plugin from buildSrc. The bytecode baseline check is wired
// into each module's own `check` task, so `./gradlew check` covers everything.
