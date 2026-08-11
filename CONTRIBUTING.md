# Contributing to FabricMultiLoader

Thanks for your interest. Please read the licence situation first: this project is **not open
source yet**. Contributions are welcome, but by submitting one you grant the copyright holder the
right to relicense it as part of the work — see [LICENSE](LICENSE) section 3. The intent is to move
to Apache-2.0 before third parties use the framework productively (section 4).

---

## Setup

| Requirement | Version | Note |
|---|---|---|
| JDK to **run Gradle** | **21** | Gradle 8.11.1 does not run on JDK 24+. Set `JAVA_HOME` accordingly. |
| JDK to **compile** | 21 | Provisioned via toolchains; `foojay-resolver` downloads it if missing. |
| Gradle | 8.11.1 | Use the wrapper (`./gradlew`), never a system Gradle. |

```bash
git clone https://github.com/CptGummiball/fabricmultiloader
cd fabricmultiloader
./gradlew build
```

The second run must report `Configuration cache entry reused.` and `up-to-date` — if it does not,
something declares its inputs or outputs incorrectly, and that is a bug worth fixing.

---

## The rules that are not negotiable

These follow from the architecture. Each one is enforced by the build, not by review alone.

### 1. `format`, `api`, `runtime` and `processor` are Java 8

They are loaded on the oldest supported JVM (Minecraft 1.16.5 era). Consequences:

* no `var`, no records, no sealed types, no switch expressions, no text blocks
* no `List.of`, `Map.of`, `Optional#isEmpty`, `String#isBlank`, `Stream#toList`
* use the builder pattern instead of records

Enforced by `--release 8` (which rejects newer *API*, not just newer syntax) **and** by
`verifyBytecodeBaseline`, which reads the class file header of every produced class. Compensating
freedom: `gradle-plugin` and `testing` are Java 17 and may use everything.

### 2. No custom ClassLoader, ever

FabricMultiLoader never creates, wraps or reflects on a `ClassLoader`, and never touches
`net.fabricmc.loader.impl.**`. Isolation comes from payloads not being on the classpath at all, not
from loader boundaries. A custom ClassLoader would bypass Knot's transformer chain and silently
disable mixins and access wideners (ADR-002).

The single exception is the loader conformance harness in `testing`, which loads different Fabric
Loader versions into isolated class loaders. It is test code and is never shipped.

### 3. No dependencies in `format`

Not even a JSON library. `format` runs inside every universal JAR *and* inside the Gradle plugin;
anything added here is shipped to every player and risks colliding with Minecraft's own copy
(chapter 11.7).

### 4. Every failure path needs a code, a doc anchor and a test

A new error condition means:

1. an `OMNI-xxxx` code in `ErrorCode` (1xxx build, 2xxx runtime, 3xxx format, 4xxx API misuse),
2. a section in `docs/errors.md` — `ErrorCodeDocumentationTest` fails without it,
3. a message following the format in chapter 29.1: code, title, detected state, why it matters,
   at least one concrete fix, a docs link,
4. a test that triggers it.

Codes are never reused. A removed code stays documented as "removed in 1.x" so old logs remain
searchable.

### 5. Build-time over runtime

If something can be checked when the JAR is built, it must not be checked when the player launches
the game. Prefer a validator rule over a runtime guard; prefer a compile error over both.

---

## Working on the code

```bash
./gradlew :format:test                  # a single module
./gradlew check                         # tests + bytecode baselines
./gradlew build                         # everything
```

### Commit messages

Imperative subject line under 72 characters, a blank line, then *why* rather than *what* — the diff
already says what. Reference the design chapter when a change implements or deviates from it.

### Pull requests

* One concern per PR.
* Tests for anything with behaviour. A bug fix without a regression test will be asked for one.
* If a change contradicts the design document, say so explicitly and propose the doc change in the
  same PR. The document is normative; silent divergence is the one thing that will make this
  project unmaintainable.

### Code style

`.editorconfig` is authoritative: UTF-8, LF, 4 spaces, 120 columns. Java compiles with
`-Xlint:all -Werror`, so warnings are errors — including in tests.

---

## Implementation order

The project follows the plan in
[docs/design/part-12-implementation-plan.md](docs/design/part-12-implementation-plan.md). Steps are
ordered so that each builds only on completed ones. Two orderings are deliberate and should not be
shortcut:

* **`format` comes before everything.** The runtime and the Gradle plugin share it, which is the
  only way build-time and runtime decisions cannot diverge.
* **The loader conformance gate (step 11) comes before the Gradle plugin (steps 12–16).** It
  verifies the one load-bearing assumption of the whole architecture for ~7 days of effort;
  discovering a problem after 30 days of plugin work would be far more expensive.

---

## Questions

Open an issue. For anything security-related, follow [SECURITY.md](SECURITY.md) instead.
