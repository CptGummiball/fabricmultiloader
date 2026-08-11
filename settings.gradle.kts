pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    }
}

plugins {
    // Provisions missing JDK toolchains automatically (Java 8 target, 17/21/25 compilers).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    }
}

rootProject.name = "fabricmultiloader"

// ---------------------------------------------------------------------------
//  Module layout — see docs/design/part-07-gradle.md, chapter 22.1
// ---------------------------------------------------------------------------
include("format")          // Java 8 · manifest model, JSON, version algebra, resolver
include("api")             // Java 8 · developer SPI
include("runtime")         // Java 8 · Fabric mod: bootstrap, lifecycle, diagnostics
include("processor")       // Java 8 · annotation processor for @UniversalEntrypoint
include("gradle-plugin")   // Java 17 · the four Gradle plugins
include("testing")         // Java 17 · test harness for the framework and for mod projects
include("example")         // UniversalExampleMod — filled in implementation step 17
