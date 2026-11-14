# Repository tools

Run commands from the repository root. Android builds and tests are described in
the [build guide](../docs/development/BUILD_AND_TEST.md).

| Purpose | Files |
| --- | --- |
| CI source checks | `check_chat_boundary.py`, `check_dictionary.py`, and `../docs/security/ayva_review_checks.py` |
| Test reporting and device checks | `summarize_unit_tests.py`, `verify_native_data_safety.sh` |
| Release packaging and intent | `package_release_apks.py`, `release_intent.py` |
| Dictionary regeneration | `import_dictionary.py`, with editorial input in `data/`; see the [dictionary guide](../docs/assistant/DICTIONARY_DATA.md) |
| Optional diagnosed cloud test-JVM workaround | `gradle/cloud-test-jvm.gradle`; see the build guide before using it |
| Artwork authoring, export and verification | `vizag/`, described below |

## Vizag artwork

These tools maintain the Settings footer artwork. Their complete behavior and
historical evidence are in the [footer record](../docs/features/settings/VIZAG_FOOTER.md).
Generated reports stay in `docs/implementation/`; packaged artwork stays under
`app/src/main/res/`.

| Files under `tools/vizag/` | Role |
| --- | --- |
| `source/scenes.js`, `source/approved.webp` | Approved compositor and source artwork for the five layered replacements |
| `export_vizag_replacements.py` | Builds a staged replacement set; `--install` explicitly installs the validated set |
| `export_vizag_footer.py`, `compress_vizag_animations.py` | Recorded-frame export and frame-checked lossless compression for the animation pipeline |
| `enrich_vizag_motion.py`, `naturalize_vizag_motion.py`, `fix_vizag_layers.py`, `refine_vizag_routes.py` | Earlier gallery authoring stages; these require their corresponding external gallery HTML input |
| `verify_vizag_motion_regions.py`, `verify_vizag_bird_frames.py`, `verify_vizag_natural_motion.cjs` | Pixel-region/frame checks and browser-based motion checks |

For example:

```sh
python3 tools/vizag/export_vizag_replacements.py /tmp/ayva-vizag-output
```

Python art tools require NumPy/Pillow; replacement export also requires Python
Playwright. The `.cjs` verifier requires Node Playwright. Browser tools currently
use `/usr/bin/chromium`. These optional dependencies are separate from Gradle and
are not required for normal Android builds.

Some tools write resources, test fixtures or generated evidence. Use them for an
intentional artwork update; external gallery/frame inputs are not bundled here.
The earlier authoring stages are retained for reproducibility rather than renamed
or described as runtime app fixes.
