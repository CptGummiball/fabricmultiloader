# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to the versioning rules in
[docs/design/part-10-nfr.md](docs/design/part-10-nfr.md) chapter 42 — one release train for all
framework modules, with the container format and manifest schema versioned independently.

## [Unreleased]

### Added

* **Implementation step 1 — repository scaffold.**
  Gradle 8.11.1 multi-project build with the module layout of chapter 22.1: `format`, `api`,
  `runtime`, `processor`, `gradle-plugin`, `testing`, `example`.
  * Convention plugins in `buildSrc`: `java-conventions` (toolchain, reproducible archives,
    `-Xlint:all -Werror`), `java8-conventions` (`--release 8` for the modules loaded on the
    oldest supported JVM), `java17-conventions` (build-time-only modules),
    `publishing-conventions` (POM metadata, artifact naming per chapter 22.2).
  * `VerifyClassfileVersionTask` — reads the class file header of every produced class and fails
    with `OMNI-1040` if a module exceeds its declared bytecode baseline. Wired into `check` for
    every module. This is the build-logic ancestor of the validator's `ClassfileScanner`
    (chapter 14.4).
  * First real constants, each carried forward by later steps: `OmniFormat` (format id, manifest
    paths, marker prefix), `Side` (physical side with environment-constraint parsing),
    `RuntimeInfo` (mod id, schema version support window), `ProcessorOptions`, `PluginIds`,
    `ClassFiles` (class file version arithmetic including Java 25 → major 69).
  * Project documents: `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`,
    `.editorconfig`.
  * CI: `.github/workflows/build.yml` — unit tests plus a configuration-cache reuse check on
    JDK 17, 21 and 25.

### Notes

* The build runs on **JDK 21**; Gradle 8.11.1 does not support running on JDK 24+. Compilation
  targets are set per module via `--release`.
* Gradle 8.11.1 rather than the 8.12 named in the design document: 8.11.1 was already present in
  the local wrapper cache, the difference is immaterial, and staying on the 8.x line preserves
  Fabric Loom compatibility for implementation step 17.
* Binary compatibility checking (`japicmp` in the design) is deferred to step 6, where `api` gains
  its first published surface and a baseline can exist. Until then `verifyBytecodeBaseline` covers
  the property that actually matters at this stage.
* The `gradle-plugin` module is plain Java for now; `java-gradle-plugin` and Kotlin arrive with
  the first real plugin implementation in step 12.

## [0.0.0] — design phase

### Added

* Complete technical architecture and implementation document, 46 chapters
  ([DESIGN.md](DESIGN.md)): feasibility analysis, the Omni container format, version resolver,
  classloading, mixin and access widener architecture, the common and adapter APIs, the Gradle
  toolchain, error handling, testing, CI/CD, distribution, security, performance, compatibility
  limits, versioning, 11 ADRs, a 21-step implementation plan, answers to 25 hard technical
  questions, and a reality check.
* Interim proprietary licence; Apache-2.0 intended before any third-party productive use.

[Unreleased]: https://github.com/CptGummiball/fabricmultiloader/commits/main
