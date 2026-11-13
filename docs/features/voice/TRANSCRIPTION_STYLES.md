# Live transcription appearance

Version 2.0.20 (120) adds ten native listening visuals to the compact, shared live-transcription popup used in chat and Notes.

## Choose a style

Open **Settings → Appearance → Live transcription style**. Select a style and press **Apply**. The eye beside each choice opens an animated reference preview with sample text. **Choose style** selects it in the picker; **Apply** saves it. Cancelling either screen leaves the saved preference unchanged. Previews use generated sample levels, request no microphone permission, and never instantiate a speech recognizer.

The default is Liquid Core. Stable preference IDs are stored in `focus_prefs/voice_transcription_style`; unknown IDs safely fall back to the default.

| Style | Native visual |
| --- | --- |
| Liquid Core | Organic gradient orb with moving currents |
| Breathing Halo | Layered, deforming open contours |
| Silk Ribbon | Flowing vector waves |
| Particle Cloud | Bounded, deterministic particle field |
| Pulse Petals | Overlapping curved petals |
| Soft Equalizer | Seven rounded amplitude bars |
| Ripple Pool | Expanding, irregular contours |
| Orbit Weave | Tilted loops and moving points |
| Aurora Veil | Layered translucent wave curtains |
| Harmonic Field | Six rows of small amplitude-responsive marks |

## Recording and readability

The selected visual reacts to smoothed microphone RMS. Real recognizer readiness/audio/partial callbacks establish the listening state. Initial connection, sustained waits, language checks, cooldowns, user pause and errors do not claim to be listening. A previously ready session retains its listening presentation across ordinary phrase gaps for at most two seconds; the recognizer state itself stays accurate. Pausing releases the recognizer, retains text and the callback, and offers explicit Resume. Existing service-error recovery and rate-limit cooldowns remain in force; Resume cannot bypass them.

The transcript is selectable, 16sp with 24sp line height, respects font scaling, and scrolls within a bounded area. New captions follow the end until the reader scrolls upward. Captured text remains usable after interruption; **Use text** is disabled when blank. Captions are never visually truncated to the most recent phrase. Untouched interim captions are not promoted into confirmed task-action input. Explicit user corrections are treated as user-approved text.

## Themes, motion and scope

Visuals use the active Material theme accent with derived shades. Surfaces, neutral controls and transcript colours follow the app theme, including light and OLED backgrounds. The confirmation label chooses contrasting ink for the selected accent.

One Canvas renders only the visible style. Frame progression is capped near 30 fps, smoothing attack and release independently. Paused visuals hold their current shape and fade softly. Frame work stops when inactive, backgrounded, or system animation scale is zero. Reference previews also suspend when backgrounded. No images, models, network downloads, audio files or additional rendering libraries are required.

The UI receives only the current microphone transcript and levels. Chat gains no Notes reader or saved-media capability. The blocking overlays, chat composer, cats, drills, briefs, task cards, vocabulary cards and context pill are unchanged.

## Verification

Final implementation check: **29 focused tests passed** (six native UI/settings/motion checks and 23 speech lifecycle/recovery checks), debug APK 2.0.20 / 120 built successfully, chat dependency boundary passed (116 source files), and `git diff --check` passed.

Focused checks cover all ten native renders on light and dark surfaces (including distinct pixel output), settings persistence and invalid-ID fallback, preview and selection behaviour, large-font controls, blank transcript confirmation, accurate wait/error labels, animation while active, frozen pause/reduced-motion states, and existing speech capture/retry safety. Native reference PNGs are generated under `app/build/reports/voice-styles/`.

Run the two affected classes with the repository's JDK/Android setup:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.focusbyrj.app.ui.speech.VoiceAppearanceTest' \
  --tests 'com.focusbyrj.app.ui.screens.notes.AudioMemoManagerTest'
python tools/check_chat_boundary.py
```

The cloud Robolectric setup may require the existing `tools/gradle/cloud-test-jvm.gradle` init script. No full-suite run is required locally for this change. Physical Android microphone/OEM visual validation remains a device check; no device was connected in the implementation workspace.

## Stable sizing and corrections

The popup reserves a constant 540dp, capped to 86% of the current screen height, and the transcript uses a fixed 144dp viewport in both reading and editing modes. Text growth and partial-result corrections never resize either surface. Longer text scrolls upward, with auto-follow suspended when the reader scrolls back. Short ordinary phrase handoffs preserve the listening visual and badge; actual pause, errors and backoffs longer than two seconds take effect immediately. Idle movement is restrained, while microphone RMS drives overall expansion and movement speed in all ten styles. Stale RMS levels decay toward rest instead of remaining latched.

In chat and Notes, **Edit** pauses capture and opens an editable transcript inside the popup. **Finish editing** returns to the transcript view; the resume button continues capture after the corrected text. **Use text** uses the current corrections, and Cancel discards the capture session. Late callbacks from the old speech connection cannot overwrite edits. Text already in the chat composer or note is preserved through the existing prefix/attachment flow.

Stability follow-up verification: 41 focused checks passed (31 speech lifecycle/level checks and ten native popup/style checks), debug APK assembled, and the chat dependency boundary passed for 117 source files. New checks cover identical popup/pane bounds across long captions and short revisions, transcript tail scrolling, phrase-gap continuity with immediate pause/backoff presentation, and RMS loudness/decay. Physical phone microphone behaviour remains a device verification.
