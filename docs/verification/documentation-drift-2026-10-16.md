# Documentation consistency, 2026-10-16

Reviewed the build-796 working tree against the active guides, release metadata,
and dated verification evidence. The target remains Paper 26.3 build 175 STABLE,
API `26.3.build.175-stable`, JDK 25.0.4.1 compilation and Java 27 server runtime.
The official [build-175 metadata](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175)
still matches the channel and server checksum pinned in `gradle.properties`.

## Corrections and guards

- Documented locally cached owner names and UUID fallback in the guides and
  `/shops debug placeholders` help. Qualified reconciliation timing for large
  loaded-shop collections and slow servers.
- Corrected the release checklist to use a disposable Java 27 server and removed
  the maintained-server launch command from staging instructions.
- Marked older verification records as historical, retaining their checksums,
  test counts, and original targets. Clarified that disconnect invalidates queued
  shop-location writes that have not started; an in-flight SQL write may finish.
- Extended release checks to the readiness and troubleshooting guides and to
  server-runtime references. Extended local Markdown link checks to AGENTS,
  TODO, and CONTEXT. Fixed broken-link diagnostics to work with Gradle's
  configuration cache. These checks complement manual review of behavior prose.

## Validation

`./gradlew clean build :plugin:paperSmokeJars --no-build-cache` passed with
JDK 25.0.4.1: **315 tests, zero failures, errors, or skips**. Release metadata,
generated descriptors, documentation contracts, and local link checks passed.
Four temporary drift probes were rejected: an incorrect readiness-guide Paper
build, an incorrect TODO server runtime, an incorrect troubleshooting runtime,
and a missing local link in TODO. Each original file was restored afterward.
Logs are retained locally under `logs/docs-drift-2026-10-16/` and excluded from Git.

```text
plugin/build/libs/1MB-ShopChest-v1.15.4-SNAPSHOT-796-j25-26.3.jar
SHA-256: 7b73caf20a31e5cf5dc90920de21fe5afb3417ba2d8a692441abb440a1287985
```

Before the help-text correction, the clean build reproduced the exact JAR hash
in the [2026-10-11 stable record](paper-26.3-stable-2026-10-11.md). The artifact
above differs because of the corrected placeholder help text; this pass reran
the Java 25 build and tests, not the Java 27 suite or disposable-server tests.
No server was started and no staged JAR was replaced. The October 11 runtime
and installation evidence applies to its recorded artifact. The pre-live beta
status and remaining player/infrastructure checks still apply.
