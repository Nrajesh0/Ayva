# Build and test guide

The [tools index](../../tools/README.md) separates CI/release utilities and optional artwork tooling.

Use the existing checkout; inspect AGENTS.md and `git status` before work. Preserve
uncommitted files. Never create a worktree as part of routine environment setup.

## Supported toolchain

- JDK 21 for running Gradle and tests. App Java/Kotlin bytecode remains targeted at 17.
- Gradle 8.9 through the checked-in wrapper, with its distribution SHA-256 enforced.
- Android SDK platform 35, build-tools 34.0.0 (installed by CI),
  and platform-tools for device checks. The app supports API 31 and newer.
  Use Android Studio SDK Manager or the official command-line tools; set `ANDROID_HOME` to the installed SDK.
- Dependency versions remain in `gradle/libs.versions.toml` and the build scripts.

Run from the repository root, with `JAVA_HOME` pointing to JDK 21:

```sh
./gradlew --version
./gradlew :app:testDebugUnitTest --no-daemon --stacktrace --max-workers=1
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --stacktrace --max-workers=1
```

The initial run requires access to the Gradle distribution, Google/Maven dependency
repositories and Robolectric Android artifacts. `--offline` is appropriate after
these caches are populated; a fresh machine is not expected to build offline.
Build-time dependencies do not add online behavior to the shipped assistant.
Do not disable TLS, checksum verification or tests to overcome download failures.

Keep tests and APK optimization in separate invocations to avoid competing for
CPU/memory while native lifecycle tests await asynchronous operations.

Kotlin compiles inside the Gradle JVM, with a shared 4 GiB heap configured in
`gradle.properties`. Cold release compilation exhausted the former separate
2 GiB Kotlin daemon during CI lint. Keep CI worker counts at one and avoid
running lint, tests and APK optimization concurrently in the same checkout.
Debug compilation alone does not verify release compilation or Android's
bytecode verifier; retain the release and native emulator checks.

No release signing secrets are needed. Debug assembly honors a local root
`debug.keystore` when present; otherwise AGP uses its standard generated debug key.
Release verification is unsigned when a release keystore is absent. Never commit
development/release keys, local.properties or credentials.

## When tests run

Tests are development checks, not work performed when someone opens or uses the
installed app. Test source files are not packaged as application features. File
count is not test-case count: one file can contain multiple cases or SDK variants.

| Action | What runs |
| --- | --- |
| Open/use the installed app | No development test suite. Normal application validation and encryption still run. |
| `assembleDebug` / `assembleRelease` locally | APK assembly; these commands do not automatically run the unit or instrumented test suites. |
| `testDebugUnitTest` locally | The debug JVM suite, including Robolectric and applicable Compose/UI tests. Gradle may reuse up-to-date outputs when inputs have not changed. |
| `check` / explicit test tasks | The checks wired to that task; inspect the Gradle task graph for the requested variant. Device tests need an emulator/device and an explicit instrumented test task or runner. |
| GitHub pull request, push to main/master, or manual validation | `validate.yml` runs source guards, debug/release lint, the full debug JVM suite and APK assembly. `native-validation.yml` separately runs native data-safety checks on API 31, 34, 35 and 36 emulators. |
| Qualifying release push, published-release event or manual release workflow | `release.yml` runs source guards, lint and the debug JVM suite again before release assembly/publication. Its push path filter is defined in the workflow. |

Validation and native workflows currently have no documentation path exclusion,
so even a documentation-only PR/main push starts those workflows. A qualifying
source/version push can run JVM tests in both validation and release workflows;
native checks repeat on each configured Android API. This is existing scheduling,
not a requirement to run the full suite for every local edit. No workflow triggers
or release gates were changed during documentation consolidation.

Keep tests covering distinct persistence, privacy, authorization, scheduling,
restore and recovery failures. Similar filenames do not establish redundant
coverage. For routine local edits, use focused tests appropriate to the change:

```sh
./gradlew :app:testDebugUnitTest --tests '*YourTestClass' --no-daemon --max-workers=1
```

Shared storage/security changes warrant broader regressions. Documentation-only
changes usually need link/content checks rather than a full local Android run.
Before deleting or merging test cases, identify retained equivalent setup,
failure conditions and assertions; file-count reduction alone is not a reason.

## Checks and evidence

CI reports are uploaded even on failures. Only a completed Actions run for the
actual commit establishes hosted success. On push, release publication requires
a version-name/code change; ordinary source work does not overwrite the current
release. Explicit release/manual events retain their configured publication behavior.

Read `app/build/test-results/testDebugUnitTest/TEST-*.xml` to confirm executed test
counts, failures, errors and skips. HTML reports are under
`app/build/reports/tests/testDebugUnitTest/`. Record current-run outcomes rather
than reusing old reports after a failed command. APKs are under
`app/build/outputs/apk/`; release is optimized and normally unsigned locally.
Device checks remain separate: unit tests do not establish OEM overlay, speech,
alarm delivery or accessibility behavior on hardware.

## Existing managed-cloud environment

Some previous cloud sessions used toolchains/caches under `/workspace/toolchains`.
An activation helper can be sourced if present; that path is not a repository
dependency or a guarantee about a new session. Reconstruct the supported
toolchain above if absent. Environment proxy/CA settings belong in the managed
environment or user Gradle configuration, never in tracked credentials.

A JDK C2 compiler crash was previously observed in cloud Robolectric workers.
Only for that diagnosed environment issue, append
`--init-script tools/gradle/cloud-test-jvm.gradle` to the verification command.
This limits test-worker JIT to C1 without removing tests/assertions or changing
production compilation. Record its use; normal CI does not enable it by default.
Resource-limited cloud sessions can additionally use
`-Pkotlin.incremental=false` and lower worker counts. In-process Kotlin
compilation is already the project default. Do not silently change app behavior
to fix tooling.

## Vocabulary and language data

The full dictionary and its import/check scripts have been removed. Run the
assistant boundary/security guards and relevant vocabulary/backup tests. The native
`vocab.db`, Room schemas, user progress and saved-word backup paths remain intact.
See the [removal record](../assistant/DICTIONARY_DATA.md).
