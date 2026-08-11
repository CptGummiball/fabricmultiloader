/**
 * Maven publication metadata for every published module.
 *
 * The repository target is a project-local directory for now; the real
 * repositories (Maven + Gradle Plugin Portal) are wired in implementation
 * step 21. Publishing the metadata correctly from day one keeps the POMs
 * reviewable and makes `publishToMavenLocal` usable for local integration work.
 *
 * Artifact naming follows chapter 22.2: module `format` -> `fabricmultiloader-format`.
 * A module may override `archivesName` and the publication's `artifactId`
 * (only `gradle-plugin` does, becoming `fabricmultiloader-gradle`).
 */

plugins {
    id("fabricmultiloader.java-conventions")
    `maven-publish`
}

val defaultArtifactId = "fabricmultiloader-${project.name}"

base {
    archivesName.set(defaultArtifactId)
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            artifactId = defaultArtifactId
            pom {
                name.set(defaultArtifactId)
                description.set(provider { project.description ?: "FabricMultiLoader module ${project.name}" })
                url.set("https://github.com/CptGummiball/fabricmultiloader")
                licenses {
                    license {
                        // Interim proprietary licence — see LICENSE section 4.
                        name.set("FabricMultiLoader Proprietary Source-Available License")
                        url.set("https://github.com/CptGummiball/fabricmultiloader/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("CptGummiball")
                        name.set("CptGummiball")
                    }
                }
                scm {
                    url.set("https://github.com/CptGummiball/fabricmultiloader")
                    connection.set("scm:git:https://github.com/CptGummiball/fabricmultiloader.git")
                    developerConnection.set("scm:git:ssh://git@github.com/CptGummiball/fabricmultiloader.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "projectLocal"
            url = uri(layout.buildDirectory.dir("maven-repo"))
        }
    }
}
