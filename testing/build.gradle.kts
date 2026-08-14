plugins {
    id("fabricmultiloader.java17-conventions")
    id("fabricmultiloader.publishing-conventions")
}

description = "Test harness for the framework and for mod projects: FakeModContext, " +
    "JAR fixtures, the loader conformance harness and the server harness."

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
}
