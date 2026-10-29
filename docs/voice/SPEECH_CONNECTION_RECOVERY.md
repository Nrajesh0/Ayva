# Speech connection investigation (Android 16)

## What changed from the earlier working implementation

The older `849abea` AudioMemoManager used `SpeechRecognizer.createSpeechRecognizer(context)`, which selects Android's default recognition service. It did not request offline recognition. A later revision preferred the dedicated on-device service; `cb90386` removed the network-capable fallback to enforce the app's offline constraint. Current capture uses `createOnDeviceSpeechRecognizer` exclusively. This is a source-code/provider-selection difference, not a demonstrated difference between Antigravity and the cloud compiler.

These APIs can resolve to different services on the same phone. The default recognizer can use its own Internet permission even though Ayva has no Internet permission. `EXTRA_PREFER_OFFLINE` alone does not provide the dedicated on-device API's guarantee. Do not restore an automatic default-provider fallback while claiming strict offline operation.

## Why error 10 was being mishandled

The screenshot establishes that the error-10 recovery branch ran before any visible transcript. It does not establish which Android/provider failure caused that error.

Android 16's SpeechRecognitionManagerServiceImpl returns ERROR_TOO_MANY_REQUESTS (10) when `createService` returns null. That includes both connection capacity exhaustion and a configured component that does not resolve to a recognition service. Therefore a permanently missing/disabled/misconfigured on-device provider can look like rate limiting. `isOnDeviceRecognitionAvailable` alone is not a working-microphone check.

SpeechRecognizerImpl queues START while establishing its connection. On a connection error its pending messages are not drained. Retrying `startListening` repeatedly on that same failed object can enqueue additional starts, which may be dispatched together if it later connects. Recreate a rejected startup connection once after the cooldown instead of stacking requests on it. This is a recovery for a transient failed bind, not a repair of a missing system service.

Sources inspected from Android's source mirror:

- [SpeechRecognitionManagerServiceImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/speech/SpeechRecognitionManagerServiceImpl.java) — `createSessionLocked`, `createService`, `componentMapsToRecognitionService`.
- [SpeechRecognizerImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/speech/SpeechRecognizerImpl.java) — `startListening`, `connectToSystemService`, pending message dispatch and cleanup.
- [RemoteSpeechRecognitionService](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/speech/RemoteSpeechRecognitionService.java) — client ownership and cleanup.

## App changes

- Distinguish a rejected initial connection from an established provider throttling capture. One clean startup retry is allowed after the cooldown; repeated rejection gives an explicit connection error and Voice settings action.
- Keep the existing bounded backoff for a provider that has actually connected. Readiness alone does not reset its failure counter; real words do.
- Reuse the existing recognizer for BUSY instead of repeatedly creating clients. Other transient errors retain bounded recovery.
- Ignore duplicate start requests while a session is active.
- Allow one capture owner per app process. A new screen pauses the previous capture and retains each caller's text locally; it never reads another caller's transcript. Closing the chat composition explicitly releases capture.
- Display a decreasing retry countdown instead of showing “Connecting” throughout a cooldown.
- A failed capture offers **Copy speech details**. It copies only manufacturer/model, Android version, numeric error, connection stage and speech-service configuration. No audio, captions, Notes data or device identifiers are included. The firmware resource lookup is best-effort diagnostic metadata, never a routing decision.
- Retain finalized-versus-partial action provenance, offline-only service selection, transcript preservation, and no model downloads.

## Verification and limits

Final local verification: **46 focused tests passed** (29 speech, six popup/style checks, 11 Notes-isolation checks), `assembleDebug` succeeded, and the chat boundary checker passed for 117 source files. No full-suite run was performed.

Run AudioMemoManagerTest, VoiceAppearanceTest and AssistantNotesIsolationTest, then assembleDebug and the chat dependency boundary checker. Tests simulate Android service events; they cannot prove a particular manufacturer's speech provider works.

No physical device is connected in this workspace. To establish the remaining device-specific cause, reproduce on the user's phone and inspect Android speech-manager/provider logs for `serviceComponent is not RecognitionService`, connection/session limits, or provider-specific error 10. Ayva logs numeric error, connection stage and attempt count, never recognized words. Device speech-service availability/installation cannot be repaired by changing the APK's transcription animation or compiler.
