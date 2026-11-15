# Vizag settings footer and artwork tools

The footer cycles nine approved scenes, their place name, “Made with ❤️ in Vizag”
and the handwritten signature linking to the repository. Preserve the approved
288×93 dp presentation and original composition. The signature font's OFL notice
remains in `app/src/main/assets/licenses/Allura-OFL.txt`.

RK Beach, Kailasagiri, Dolphin's Nose, Tenneti Park and Rushikonda use the approved
original-pixel atlases with native `LayeredVizagDrawable` motion. Fishing Harbour,
INS Kursura, Vizag Port and Simhachalam retain their frame animations, posters,
palette transform and playback backend. Theme-specific lossless layers preserve
alpha/vehicle contrast; decode only the current atlas. These are app resources,
distinct from authoring reports and source art.

Preserve the accepted motion: opposite cable-car routes, articulated birds,
walking people, travelling glider/windsurfer, lighthouse beam, Rushikonda's moving
crests/fixed sand boats, RK bay wavefronts and the broad offshore swells. Do not
replace continuous clocks with finite-loop exports or silently alter retained frames.

The next-scene preference persists rotation without immediate repeats. A chosen
navigation entry retains its scene across recomposition/theme/configuration;
returning from a child settings page advances. Posters appear during off-thread
decode. Pause offscreen, outside started lifecycle, on detach and under Android
reduced motion; clear playback callbacks on release. Captions use native scalable
text and the signature has a 48 dp touch target. No artwork/font download is used.

## Authoring and evidence

`tools/vizag/source/scenes.js` and `approved.webp` are approved compositor inputs.
`tools/vizag/evidence/` retains machine JSON inputs and generated frame/motion,
replacement, compression and size evidence. They are not extra runtime assets or
fresh results unless their verifier has actually run for the relevant resources.

```sh
python3 tools/vizag/export_vizag_replacements.py /tmp/ayva-vizag-output
```

Export stages the complete replacement set. Its explicit `--install` validates
and installs only the five replacement scenes, updates the resource manifest and
records evidence. Do not run it merely to view documentation or rewrite unrelated
assets. Python authoring needs NumPy/Pillow and export needs Playwright; Node/browser
verification also uses Playwright and the documented Chromium executable.

Earlier gallery authoring scripts need their external HTML/frame inputs, which
are not bundled here. Frame-export/lossless tools describe the retained animation
pipeline; layered scenes use native-renderer checks instead. See the
[tools index](../../../tools/README.md) and the existing `SettingsVizagFooterTest`
and `LosslessAnimationTest` for actual rendering, pause, theme and frame coverage.
Historical evidence does not certify every OEM decoder or current APK.
