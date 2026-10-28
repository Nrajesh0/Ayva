# ChatScript restricted conversation runtime and prototype history

Upstream: https://github.com/ChatScript/ChatScript
Pinned commit: `9f5eec4736ba22bd992a6498c1e0052e2a795125`.
`run.top` is original Run experiment content. `production.top` is the app meaning pack. The application integration section below supersedes the earlier prototype status.

## Results, 2026-10-25

1. A standalone build disabling server, databases, Japanese, JSON networking, JavaScript, WebSocket and TCP opening failed. `javascript.cpp:20` uses Duktape types outside its `DISCARDJAVASCRIPT` guard. After locally guarding that function for diagnosis, `json.cpp:773` and following curl code still failed with `DISCARDJSONOPEN`. The restricted configuration is not ready for shipping.
2. The standard Linux standalone build with server/databases/Japanese disabled succeeded. JavaScript/curl remain in that baseline; this is not the desired restricted runtime.
3. The default Harry bot gave unrelated childhood replies to the quiet-evening conversation. This confirms that its sample content should not ship as Ayva's persona.
4. An original small `run.top` compiled and handled quiet preferences, crowd avoidance, suggestions, an outdoor correction and a contextual why follow-up.
5. Default baseline first startup + a short conversation used about 478 MiB peak host RSS and 0.91 seconds total wall time. A subsequent custom-pack process using `dict=350000 fact=300000 text=30000`, existing binary dictionary caches and default 80 buffers used about 76 MiB peak host RSS and 0.17 seconds total wall time. These are single Linux process observations, **not Android benchmarks, APK sizes or per-turn latency measurements**, and do not isolate the effect of every configuration change.
6. A compilation attempt with `buffer=20x80` exhausted overflow buffers after compilation. Keep default buffers for this experiment. The source interprets `text=` in thousands of bytes despite ambiguous wording in the command documentation; `text=30000` requests about 30 MB. `text=30000000` overflowed its internal integer calculation and failed allocation during an initial probe.
7. The completed CLI dialogue printed `Buffer allocation underflow` at shutdown despite exiting zero. This needs investigation before embedding.

Raw temporary evidence during this session: `/tmp/run-chatscript-build.log`, `/tmp/run-chatscript-baseline.log`, `/tmp/run-chatscript-baseline-dialogue.log`, `/tmp/run-chatscript-custom-dialogue.log`. Temporary paths are not durable release artifacts.

## Reproduce the successful host baseline

Use an isolated scratch checkout at the pinned revision. Run from `SRC`:

```sh
make standalone -j2 'DEFINES=-DDISCARDSERVER=1 -DDISCARDPOSTGRES=1 -DDISCARDMONGO=1 -DDISCARDMYSQL=1 -DDISCARDMICROSOFTSQL=1 -DDISCARD_TEXT_COMPRESSION=1 -DDISCARD_JAPANESE=1'
```

Create `LOGS`, `USERS`, `TMP` in the checkout root. Copy this folder's `run.top` to `RAWDATA/RUN/run.top`; create `RAWDATA/filesRun.txt` containing `RAWDATA/RUN/`. In the CLI use `:build Run`; restart after compilation with:

```sh
./BINARIES/ChatScript local login=run-probe bot=run language=english userlog=0 serverlog=0 dict=350000 fact=300000 text=30000
```

Try:

```text
I prefer quiet evenings
Crowds wear me out
what could I try?
actually outdoors
what could I try?
why that?
:quit
```

The prototype has intentionally tiny coverage. Its sentinel is not a stable serialization protocol (the engine may normalize underscores); a future adapter must use a tested structured return channel.

## Android follow-up, 2026-10-26

The initial build failures above have now been repaired in `restricted.patch`. The patch gates Duktape/curl implementations correctly, provides failing stubs for disabled HTTP operations, disables the script system-command function, and omits the unused `sys/timeb.h` include on Android. The host restricted build links without curl. No upstream files are copied into app sources.

NDK 27.2.12479018 is installed in this workspace. `build_android.py` exports the pinned committed source into a new scratch directory, applies the patch there, and builds arm64-v8a and x86_64 shared libraries plus a synthetic smoke executable. It preserves the input checkout. Both libraries built successfully (approximately 2.5 MB each, excluding all dictionary/topic assets). The ELF load segments were checked for 0x4000 alignment on ARM64; both linker invocations specify 16-KiB maximum page size.

```sh
python tools/experiments/chatscript/build_android.py /path/to/pinned/ChatScript /path/to/android-ndk-r27c /path/to/new-empty-output
```

The x86_64 runtime was exercised on the existing API 29 test emulator, using synthetic data only. Startup, six conversation turns, preference correction, contextual explanation and explicit `CloseSystem()` all passed. Evidence: `/tmp/run-chatscript-device.log`, ending `RUN_SMOKE failures=0 closed=1`. The ARM64 library was compiled and inspected, not run on a physical device.

The first embedded shutdown failed because the probe supplied a stack output buffer. `PerformChat` retains its output pointer globally, and `CloseSystem` frees it. The probe now reuses `ourMainOutputBuffer`, the engine-owned allocation. It also passes explicit root/read/write paths to `InitSystem`; otherwise initialization can move up a directory while searching for source files. These are mandatory integration constraints, not reasons to ignore shutdown errors. A temporary instrumented library was used to locate the ownership fault and then restored to the pinned implementation before the successful smoke run.

For device reproduction, transfer the matching library and `run_smoke`, plus `DICT/ENGLISH`, `LIVEDATA`, and the compiled `TOPIC/BUILD0` and custom `TOPIC/BUILD1` assets, into a scratch directory. Create `LOGS`, `USERS`, and `TMP` there; invoke `run_smoke /absolute/scratch/directory`. When extracting a host tar archive as the Android shell user, use `tar --no-same-owner`. The successful test is a direct native executable test, not a JNI/app UI test or a 16-KiB Android device test.

## Adoption gates still open

- Establish a versioned JNI/state contract that preserves the capability-free general dialogue boundary.
- Disable or bridge all logs, user files, backups and caches to the app's memory/deletion policy.
- Verify memory caps, cancellation/work limits, process death and restart; Java exceptions cannot contain a native fatal crash. One complete startup/turns/shutdown cycle passed on the emulator; repeated in-process reinitialization is not established.
- Compare complete dialogue quality and release-device resource cost against the implemented Kotlin engine. If it cannot justify those costs, retain Kotlin or separately evaluate RiveScript core. Do not ship two competing dialogue engines.

## Application integration, 2026-10-26

This section supersedes the earlier prototype-only status. The app now packages an
original `production.top` meaning pack and a stateless subprocess worker for ARM64
and x86_64. It does **not** ship the Harry bot as a persona. Kotlin owns the only
conversation frame, all response wording and the existing action boundaries.

`worker.cpp` returns one `RUNNATIVE1:<meaning>` token. It runs from the installed
native library directory (legacy native extraction is required); never execute a
binary extracted into writable app data. Each invocation starts a fresh process,
so an engine crash cannot crash the app's VM and no unsupported reinitialization
is needed. There is a 6-second native CPU limit and a 10-second Java output timeout.
The engine's dictionary/fact/heap sizes are configured, but these are **not** a hard
process RSS cap. 32-bit processes, Android below API 26, main-thread callers, errors and unknown tokens
fall back to Kotlin. This is process crash isolation, not an OS security sandbox:
the child runs under the app UID; only packaged trusted rules may be loaded.

Assets are SHA-256 checked, size/path bounded, extracted once into no-backup private
storage with atomic promotion. They contain public English dictionary/topic data,
not dialogue history. User text is sent only over stdin after initialization. User
caching/logging is disabled and RLIMIT_FSIZE is zero before reading it, preventing
regular-file content writes even through unexpected engine logs. Scratch folders
are deleted after each invocation and orphaned scratch is cleared on the next run.
The encrypted existing Kotlin session remains the sole conversation memory owner.

The device probe found that the upstream logger recurses if its scratch LOGS folder
is absent. The worker now validates LOGS/USERS/TMP before initialization, and the
adapter creates them. A direct worker check then returned RUNADVICE successfully.

### Rebuild the packaged runtime

1. Use an isolated checkout at the pinned revision. Apply `restricted.patch`. Build
   its host compiler with the same disabled-capability definitions from `build_android.py`
   (omit ANDROID and NOMAIN for the host executable), retaining script compilation.
2. Create LOGS/USERS/TMP. Copy `production.top` to `RAWDATA/RUN/run.top`. Put
   `RAWDATA/RUN/` into `RAWDATA/filesRun.txt`. From the checkout root run:
   `./BINARIES/ChatScript local build1=RAWDATA/filesRun.txt userlog=0 serverlog=0 dict=350000 fact=300000 text=30000`.
   Check `TOPIC/BUILD1/script1.txt` contains `~run_meaning` and the current rules.
3. Run `build_android.py CHECKOUT NDK NEW_OUTPUT` as above. It exports committed
   engine sources and applies the patch itself; no local source edits are inherited.
4. Run `package_runtime.py CHECKOUT NEW_OUTPUT RUN_REPOSITORY`. It packages English
   assets only, records upstream revision/NDK/protocol/hashes, installs both workers
   and retains licenses outside the chat UI. Ordinary Gradle builds do not download
   or compile upstream code.
5. Run the small `NativeGeneralDialogueDeviceTest` from the installed APK. This
   validates the Android executable path and asset lifecycle; a host test alone
   cannot establish those properties.

The checked-in native data adds approximately 16 MB compressed plus about 2 MB per
ABI before APK compression. Native fallback startup is a real cost; Kotlin handles
recognized turns first. Physical-device latency, ARM64 execution and Android 16
16-KiB runtime behavior still need release-device measurements. ELF alignment alone
is not proof of device compatibility. No model-like open-domain understanding is
claimed by this integration.

The universal APK includes tiny armeabi-v7a/x86 placeholders so adding native libraries does not make installation fail on 32-bit phones. The adapter checks process bitness before invoking anything, keeping those devices on Kotlin. The build and packaging tools reproduce these placeholders and include their hashes. LLVM runtime notices are bundled with the native licenses.

Immediate duplicate fallback requests now reuse a stateless meaning or abstention for ten seconds (four digest-keyed entries, memory only). Conversation frames and replies are never cached by the adapter. Nonzero worker exits trigger the same one-minute cooldown as exceptions/timeouts; a successful process result is required before caching.
