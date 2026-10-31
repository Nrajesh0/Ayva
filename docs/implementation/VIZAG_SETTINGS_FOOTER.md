# Settings · Made in Vizag

The bottom of `PreferencesHubScreen` now shows one of nine approved animated Vizag scenes, its place name, “Made with ❤️ in Vizag”, and a handwritten “By Rj” signature. The signature opens https://github.com/Nrajesh0/Ayva. It uses the bundled Allura font; the font's OFL license is included in `assets/licenses/Allura-OFL.txt`.

## Visits and playback

- Local `vizag_footer` preferences persist the next scene index. All nine cycle without an immediate repeat, including after app restarts.
- The chosen scene is saved with the navigation entry. Recomposition, theme changes and configuration restoration keep that choice. Returning from a child Settings page advances to the next scene.
- One scene plays at a time. Decode runs on the IO dispatcher, with a matching bundled poster displayed immediately. The footer never fetches artwork or fonts over the network.
- Android 9+ uses the system ImageDecoder / AnimatedImageDrawable. Android 7–8.1 has a native Movie fallback. Playback stops outside the visible viewport, when the screen is not started, on view detachment and under Android's remove-animations setting. Release stops the animation and clears its callback.
- Transparent backgrounds sit directly on the app surface. Light themes use the approved preview's contrast transform while preserving alpha; dark and OLED themes use the original palette. The signature follows the app accent. Text uses Material typography, wraps naturally, and supports enlarged font sizes and a 48 dp signature touch target.

## Approved artwork

01 RK Beach promenade — revised outlines
02 Kailasagiri — revised outlines
03 Dolphin’s Nose Lighthouse — revised outlines
04 Vizag Fishing Harbour — approved Original
05 INS Kursura · S20 — approved Original
06 Tenneti Park · MV Maa — revised outlines
07 Rushikonda — revised outlines, with the retained beach/windsurfer design
08 Vizag Port — approved Original
09 Simhachalam — approved Original

`tools/export_vizag_footer.py` exports the approved gallery's recorded frames into transparent native GIF resources and lossless WebP posters. It excludes baked-in labels; Android renders the location and credit text. `VIZAG_FOOTER_ASSETS.json` records dimensions, eight-second durations, frame counts and frame-difference checks for the nine assets.

## Validation

`:app:testDebugUnitTest --tests 'com.focusbyrj.app.ui.components.SettingsVizagFooterTest' :app:assembleDebug` passed offline. Seven focused tests cover nine-scene persisted rotation, saved-state/theme stability, actual Android 9 animation decoding with alpha, native start/stop/reduced-motion/release behavior, child-page navigation, the GitHub intent, hierarchy/theme/large-text rendering and scrolling the real Settings screen to the footer.

Native-rendered previews are produced under `app/build/reports/vizag-footer/`. Android 7–8.1 compatibility uses the platform Movie implementation and was not device-tested in this environment.

## 2.0.27 playback correction

Modern AnimatedImageDrawable now renders without a forced software View layer. Drawable invalidation redraws the entire scaled/centered View, and an explicit frame tick keeps the Compose host updating while playing. Legacy Movie retains its required software canvas. When Android disables animations, an accessible Play control lets the user explicitly start this scene without changing the phone setting.

Regression checks now cover changing native frame pixels, scrolling the real Settings footer into view with playback enabled, and starting playback through the reduced-motion Play control.

## 2.0.28 environmental motion

All nine keep their approved landmarks and gain independently moving environmental details. RK Beach, Tenneti Park and Rushikonda reuse the exact palm-crown pixels in swaying layers. The seven coastal scenes add rolling wave fronts; birds glide and flap across every scene. Dolphin’s Nose also gains a small bobbing sailboat. Kursura, the port and Simhachalam gain fluttering flags, with additional walkers at the exhibit and temple. The temple pennant uses a saffron outline.

`tools/enrich_vizag_motion.py` adds these layers to the approved gallery source; export uses the existing native GIF/poster pipeline. `tools/verify_vizag_motion_regions.py` verifies the exported native GIF pixels using the recorded component coordinates in `VIZAG_MOTION_SAMPLES.json`. It checks palm crowns, wave fronts, birds and flags individually, requiring at least two independent environmental regions to change in every scene. Results are in `VIZAG_MOTION_REGIONS.json`.

## 2.0.29 motion pacing and selective paint

Walkers now cover 64–72 source pixels per eight seconds instead of crossing the whole scene. Birds glide in one direction with a slower wingbeat; visitors, gondolas and watercraft also use slow directional routes with staggered low-opacity loop resets. Rushikonda has a coral-canopied paraglider on a separate drifting path. Palm crowns sway gently while their cream trunks remain fixed. Flat paint is limited to vehicle/ship bodies, leaving the landscape outlines intact.

`tools/naturalize_vizag_motion.py` applies these changes to the living gallery. `tools/verify_vizag_natural_motion.cjs` measures actual SVG positions for 17 moving subjects through each loop, checks direction while visible and enforces speed limits matched to their drawn scale. Recorded results are in `VIZAG_NATURAL_MOTION.json`.

## 2.0.30 exported-layer corrections

The bus and auto are isolated SVG vehicle shapes, containing no source-image streetlights or railings. Both left-facing vehicles travel left. Earlier bird layers are removed; each scene has one controlled flock that crosses the full viewport from left to right and exits completely before resetting outside the opposite edge. Loops are now sixteen seconds so a full-width flight remains unhurried.

Rushikonda's paraglider is enlarged, placed in a taller sky area, and visible on the initial poster/frame. Settings adds 24 dp more spacing below Setup Guide (56 dp total spacer).

Native GIF checks in `tools/verify_vizag_bird_frames.py` measure rightward bird displacement at three points in every scene, leftward painted vehicle displacement, and initial-frame paraglider pixels. The source-state check also verifies hidden bird-loop endpoints and the absence of copied-image pixels in vehicle sprites. The Settings integration test measures the gap below Setup Guide.
