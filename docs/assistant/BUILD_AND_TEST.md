# Reproducible assistant verification

Use the existing checkout; inspect AGENTS.md and `git status` before work. Preserve
uncommitted files. Never create a worktree as part of routine environment setup.

## Supported toolchain

- JDK 21 for running Gradle and tests. App Java/Kotlin bytecode remains targeted at 17.
- Gradle 8.9 through the checked-in wrapper, with its distribution SHA-256 enforced.
- Android SDK platform 35, build tools selected by Android Gradle Plugin 8.7.3,
  and platform-tools for device checks. Use Android Studio SDK Manager or the
  official command-line tools; set `ANDROID_HOME` to the installed SDK.
- Dependency versions remain in `gradle/libs.versions.toml` and the build scripts.

Run from the repository root, with `JAVA_HOME` pointing to JDK 21:

```sh
./gradlew --version
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace --max-workers=2
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace --max-workers=2
```

The initial run requires access to the Gradle distribution, Google/Maven dependency
repositories and Robolectric Android artifacts. `--offline` is appropriate after
these caches are populated; a fresh machine is not expected to build offline.
Build-time dependencies do not add online behavior to the shipped assistant.
Do not disable TLS, checksum verification or tests to overcome download failures.

Keep tests and APK optimization in separate invocations to avoid competing for
CPU/memory while native lifecycle tests await asynchronous operations.

No release signing secrets are needed. Debug assembly honors a local root
`debug.keystore` when present; otherwise AGP uses its standard generated debug key.
Release verification is unsigned when a release keystore is absent. Never commit
development/release keys, local.properties or credentials.

## Checks and evidence

`.github/workflows/validate.yml` runs all unit tests and debug/release assembly on
PRs and pushes to main/master, without publishing or signing secrets. Test reports
are uploaded even on failures. The release workflow also runs unit tests before
release assembly/publication and uses the same wrapper/JDK. On push, release publication requires a version-name/code change; ordinary source/build-infrastructure work does not overwrite the current release. Explicit release/manual events retain publication behavior. Only a completed
Actions run for the actual commit establishes CI success.

Read `app/build/test-results/testDebugUnitTest/TEST-*.xml` to confirm executed test
counts, failures, errors and skips. HTML reports are under
`app/build/reports/tests/testDebugUnitTest/`. Record current-run outcomes rather
than reusing old reports after a failed command. APKs are under
`app/build/outputs/apk/`; release is optimized and normally unsigned locally.
Device checks remain separate: unit tests do not establish OEM overlay, speech,
alarm delivery or accessibility behavior on hardware.

## Existing managed-cloud environment

This session already has toolchains/caches under `/workspace/toolchains`. Its
activation helper can be sourced when present; it is not a dependency of this
repository or a portable path for another account. Reconstruct the supported
toolchain above if absent. Environment proxy/CA settings belong in the managed
environment or user Gradle configuration, never in tracked credentials.

A JDK C2 compiler crash was previously observed in cloud Robolectric workers.
Only for that diagnosed environment issue, append
`--init-script tools/gradle/cloud-test-jvm.gradle` to the verification command.
This limits test-worker JIT to C1 without removing tests/assertions or changing
production compilation. Record its use; normal CI does not enable it by default.
Resource-limited cloud sessions can additionally use
`-Pkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false`
and lower worker counts. Do not silently change app behavior to fix tooling.

## Dictionary artifact integrity

Run `python3 tools/check_dictionary.py` alongside `python3 tools/check_chat_boundary.py`. Both non-publishing validation and release workflows run these before tests/builds. The dictionary check validates the pinned source identity, all 256 compressed `.dict` assets, manifest counts/hashes, editorial sense IDs, notices and byte budgets. It does not fetch the source or a model at runtime/build validation. To reproduce data intentionally, use tools/import_dictionary.py as described in DICTIONARY_DATA.md.

The release publication decision was tested against the B2 base for this unchanged-version continuation and returned `publish=false`. Workflow edits can trigger a build; they cannot automatically overwrite the existing release without version-name/code changes under the push gate.
