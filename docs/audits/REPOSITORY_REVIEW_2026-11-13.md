# Repository inventory, necessity and exposure review

Baseline: `35d5ef4`, version 2.0.46 (146), Nrajesh0/Ayva/main. GitHub API confirms
that the repository is public. Counts below cover tracked files at this commit,
excluding these new review artifacts, ignored build output, caches and .git.

## Main findings

1. **High priority, confirmed: a historical private key still compromises the active
   offline licence verifier.** It was removed from the current tree but remains in
   public Git history. Rotate the licence-signing key and replace the verifier's
   public key before trusting this licensing mechanism.
2. Most files are app inputs: 876 production files, including 427 Kotlin files.
   Hundreds of small files are intentional dictionary shards, not duplicate code.
3. Documentation and tests account for 267 files. They do not ship as app source
   in the APK. Removing them does not make the running app faster.
4. Stale scaffold metadata, a mismatched Node tooling manifest, redundant historical
   documentation distributions and a malformed test image are cleanup candidates.
5. Three production helper files appear dormant outside tests. Six resources are
   lint-reported unused candidates. Neither list justifies bulk deletion without
   dependency/test review.

## Inventory

Sizes are decimal MB, not on-disk allocation or compressed APK contribution.

| Category | Files | Bytes | Purpose |
| --- | ---: | ---: | --- |
| Production Kotlin/Java | 427 | 6,601,687 | App logic and UI; approximately 124,236 source lines |
| Production resources/assets/manifest | 449 | 25,319,224 | Runtime data, artwork, fonts, layouts, platform declarations |
| Tests and support | 143 | 1,725,824 | 137 unit-test/support files, 6 instrumented-test files |
| Documentation and review files | 124 | 9,121,856 | 119 files under docs plus root/module Markdown; includes an executable CI guard |
| CI/development tools | 25 | 1,246,984 | Workflows, verification, packaging, asset regeneration |
| Room schema snapshots | 9 | 109,416 | Schema history and development verification |
| Store metadata | 4 | 24,291 | Listing text and icon |
| Build/config/other | 16 | 190,560 | Gradle, wrapper, licences, root metadata and branding |
| **Total** | **1,197** | **44,339,842** | Approximately **44.34 MB / 42.29 MiB** |

Production files represent 73.2% of file count and approximately 72% of tracked
bytes. This does **not** prove every production declaration is reachable. The
per-file CSV provides paths, roles, sizes and Android-main-input classification.

The 449 production non-code files consist of 288 assets, 160 Android resources,
and the manifest. Dictionary data alone uses 259 asset files: 256 compressed
shards plus manifest/auxiliary data/licence. Other assets implement the cats,
animations, vocabulary and offline assistant.

## Sensitive material: confirmed exposure

The scan inspected all 1,197 current tracked files and 3,077 unique historical
blobs reachable from local branches/tags: 194,185,436 historical blob bytes.
Known formats included private PEMs, GitHub/Google/AWS/OpenAI/Slack/Stripe tokens,
credential-bearing URLs and JWTs. Literal credential assignments were separately
reviewed. Matching secret values were not printed or copied into this report.

### Historical licence private key

- Added as root `private.pem` in `43661bb998a5c20153cb0671f66ca41ec7340355`
  on 19 September 2026; removed in `3981775` on 21 September 2026.
- Historical blob: `baef34788b8294c327cbe112647f23ab383f9778`.
- OpenSSL successfully parses it as an EC P-256 private key.
- Its derived public-key SHA-256 fingerprint is
  `476df9274797e7ddc412557382f771aa84f7ddcabc11fd0330fbcf750c526b3b`.
- That fingerprint matches the embedded public key in both the original and
  **current** `app/src/main/java/com/focusbyrj/app/util/LicenseManager.kt`.
- Current code verifies ECDSA signatures over a lifetime-Pro payload. The verifier
  is initialized by FocusApplication and called by SubscriptionScreen. This is
  active code, not an unused historical verifier.

**Impact:** someone retrieving the historical private key can forge accepted
activation licences. No activation token was generated in this review. Deleting
or rewriting history cannot make an already disclosed key trustworthy again.

**Scope:** this is not the user's PIN, VDK, SQLCipher passphrase or media key.
Current vault/data encryption uses separate runtime/device/user-specific keys.
The public key extracted from the published 2.0.45 APK's signing certificate is
`da39699b57e33953ea0a1b0c6f93ccff60bf976891d5a524e1bbea1eeafcbe84`,
which differs from the exposed licence key. That comparison covers this inspected
APK, not every historical release or signing identity.

**Recommended handling:** retire this licence key, generate a replacement outside
Git and AI imports, update the verifier and review any accepted old licences.
Consider history cleanup only after revocation and an explicit coordinated plan;
clones, forks and prior AI imports may retain the original key. Do not rotate the
Android APK signer or reset users' vaults as a response to this licence-key finding.

### Other exposure checks

- No live-looking service tokens or private PEMs were found in the current tracked
  tree by the inspected patterns. The JWT match in Batch10SecurityAuditTest is a
  deliberately fake logging-redaction fixture.
- Literal password matches are test fixtures and Android's standard debug signing
  password. The debug password is not a production signing credential.
- No debug.keystore, JKS/P12/PFX or .env file is currently tracked. The AI-import
  report claiming debug.keystore was imported does not establish that it came
  from this Git commit; it may be local/generated or from a different snapshot.
- Workflow references such as `secrets.SIGNING_KEY` are secret **names**, not their
  values. Release key material/passwords are supplied through GitHub Secrets.
- .gitignore covers JKS/keystore/env files but does not cover raw private PEM or
  PKCS12 variants. Add narrowly scoped private-key ignores and a real secret-scan
  check; .gitignore alone is not a security boundary.
- The three tracked security evidence logs reviewed contain instrumentation
  summaries, not dumped vault contents or credentials. The documentation ZIP has
  no sensitive-key filenames; its individual embedded contents were not subjected
  to a separate recursive credential scan.
- vocab.db contains 2,027 word records and 1,773 idiom records. Mastered/bookmarked
  flags are zero. VocabSeed explicitly copies lexical columns and resets progress.
  This is public seed content, not a shipped user notes database.

Limitations: this pattern/manual review is not a formal secret-scanner certification.
It cannot rule out arbitrary custom secrets, image/PDF text, unreachable/dangling
Git objects, remote forks, GitHub Actions artifact contents, or server-side secret
values. No current production-key compromise beyond the identified licence key
is established by these checks.

## What should stay

- **Original Gradle project and wrapper/catalog:** required for reproducible plugin,
  dependency and build configuration. Recreating them changes the project being
  tested, even if it compiles.
- **Tests:** do not ship as release app code. Keep security, data-preservation,
  backup, native and UI coverage. They should not be deleted to make an importer
  or repository look smaller.
- **CI/tools:** check_chat_boundary, check_dictionary, packaging, release_intent,
  native verification and test summary scripts are referenced by workflows.
- **docs/security/ayva_review_checks.py:** despite its docs location, CI executes
  it. Do not delete the entire docs tree as though every file were passive prose.
- **Room schemas:** development/compatibility records; not arbitrary extra runtime
  databases. Keep with Room configuration and schema changes.
- **Dictionary shards, vocabulary seed, fonts, animation assets:** production code
  loads them. AssetManager/dynamic string lookups require explicit reference
  inspection, not just searching for R.drawable names.
- **Workers/receivers/services/widgets:** Android manifests, schedulers, XML and
  persisted worker names can invoke them without ordinary Kotlin callers.
- **Licence/attribution/trademark files:** not application logic, but should not be
  discarded as unneeded clutter. Dictionary/font attribution is present in assets.

Inspection of the built release APK found no docs/, tools/, test-source or fastlane
paths, and confirmed all 256 dictionary shards. This is a packaging spot-check,
not a proof about every possible embedded string.

## Cleanup and consolidation candidates

| Candidate | Evidence | Recommendation |
| --- | --- | --- |
| metadata.json | Declares server-side Gemini capability; no inspected Android/build consumer. Actual app is offline. | Remove or correct external-scaffold metadata once its importer consumer is established. It is not proof of a Gemini backend. |
| package.json | Stale 1.9.0 metadata; declares canvas. Tool code uses browser DOM canvas; a verification script imports undeclared Playwright and hardcodes Chromium. | Define a dedicated reproducible art-tool manifest, or remove this stale root manifest if that workflow is retired. Not needed by Gradle. |
| docs/app-atlas HTML/PDF/ZIP | These three files total 7,110,457 bytes; ZIP repeats the guide and includes generators/diagrams. Snapshot is explicitly 2.0.37 / 396 Kotlin files. | Keep one authoritative distribution or move downloadable bundles to release assets. Retain provenance; no APK-size benefit from deletion. |
| app/src/test/screenshots/greeting.png | 2,868 bytes, not a valid PNG; binary appears text-reencoded. No current reference found. | Remove or regenerate as a real test fixture. It is not a production image. |
| HkdfUtil.kt | Only its declaration appears in production; security tests still reference it. | Review as dormant code; remove with tests that exist only for it, or retain deliberately as future utility. Do not replace BC HPKE internals with it merely to justify it. |
| VaultSyncManager.kt | No production callers found; several tests exercise its alternate JSON import/export API. | Retire/relocate if the alternate sync path is abandoned. Current backup UI uses BackupRestoreManager. Review its tests and import contract together. |
| PreferencesHubHelper.kt | Production declaration only; current references are in Batch12 tests. | Remove or relocate with its helper-only coverage after confirming no intended feature use. |
| Six lint-unused resources | backup_rules.xml; exam_list_card; ic_achievement_focus_champion; ic_bubble_launcher_icon; ic_widget_check_filled; ic_widget_todo. No inspected production reference found. Combined 19,353 bytes. | Verify dynamic/persisted resource use, then delete in a small tested cleanup. Release resource shrinking already reduces the practical APK benefit. |
| Root icon.svg / fastlane | README branding and store listing assets. | Optional for the running APK; keep for branding/distribution or move deliberately. |

The three dormant Kotlin candidates total 387 source lines. This is a focused
candidate list, not a claim that every other function is required. An initial
symbol scan also flagged DataRecoveryTools.kt, but manual review found its
same-line composable declarations and StorageInventory call sites: **keep it**.

Exact duplicate files are intentional:

- tools/data/dictionary_plain_english.json and its packaged asset are source/runtime
  copies; the dictionary check verifies their equality.
- Launcher and round-launcher XML are identical but expose distinct Android icon
  roles. Keep both names.

## Structural maintenance

The app has 427 production Kotlin files but large responsibilities remain inside
individual files: AccountScreen 4,621 lines; KeepNoteEditor 3,768; NotesViewModel
2,480; ArchiveVaultDialogs 2,305; BubbleChatActivity 2,201. Other near-2,000-line
files include NotesScreen, AyvaTalkEngine, AyvaChatTimeline and BackupRestoreManager.
Extract cohesive UI/state/storage responsibilities with regression coverage.
Merging many source files into larger files would not establish necessity or
improve performance. R8 is enabled for release builds and removes unreachable code;
static reference counts alone are not sufficient grounds for deleting entry points.

The four large Vizag animated WebP resources total 8,972,596 bytes and have explicit
SettingsVizagFooter references. They are real visual features. Optimizing/removing
those affects appearance; documentation cleanup does not. The large cat Rive and
Lottie assets are also runtime inputs and should be assessed against supported
appearance choices, not deleted for their size alone.

## Recommended order

1. Retire the exposed licence-signing key and fix secret prevention.
2. Correct stale scaffold/tool metadata; remove/regenerate the invalid screenshot.
3. Consolidate historical guide distributions if repository size/importing matters.
4. Review dormant helpers and unused resource candidates in isolated cleanup changes.
5. Split the largest responsibilities gradually while retaining data/security tests.

No app source, credentials, signing configuration, history or runtime assets were
changed or deleted in this review. Only this report and its inventory CSV were
created. No commit, push or publication was performed. Functional tests were not
rerun for this read-only audit; lint findings cited are from the prior build.

## Cleanup applied after review

At the user's request, removed the invalid test screenshot and duplicate visual
guide PDF/ZIP (4,145,308 tracked bytes). The HTML guide and CSV records remain;
the guide README now links only to retained files and labels its historical scope.
Root metadata retains its external-tool structure but removes the unimplemented
server-side Gemini capability and describes the offline Android app accurately.

Application sources, assets, schemas, tests with active references, tooling,
licences, and the three dormant helpers were retained. The inventory CSV above
remains a baseline record of commit 35d5ef4; it is not silently rewritten as a
post-cleanup inventory. Licence-key rotation/history remediation is not performed
by this file cleanup and remains the high-priority security follow-up. No APK or
vault signing/encryption keys were changed.

## Documentation consolidation follow-up

At the user's request, combined AUTOMATIC_BACKUP_AUDIT.md and
AUTOMATIC_BACKUP_SECOND_AUDIT.md into
[one historical record](../features/backups/AUTOMATIC_BACKUP_AUDIT_HISTORY.md),
preserving both audits and their verification details. Removed the duplicate
assistant DELIVERY_SUMMARY.md and replaced the repetitive STATUS.md log with a
[current handoff](../assistant/STATUS.md); pinned Git history retains the original
status/delivery text. Local Markdown links were updated.

Added a [documentation entry point](../README.md), updated the existing
[build/test guide](../development/BUILD_AND_TEST.md) to explain actual workflow
scheduling and focused local testing, and replaced stale security-startup
instructions with a [security guide](../security/README.md) distinguishing current
source checks from historical evidence. Detailed unique implementation/audit
records, test sources/resources, schemas, CI tooling and store metadata remain.
No test execution triggers or application source changed in this consolidation;
no full local Android suite was required for these documentation edits.

## Documentation organization follow-up

Grouped implementation Markdown under `docs/features/` by backups, notes, widgets,
settings, system reliability, habits, voice and appearance. Build/testing and the
assistant development workflow now live under `docs/development/`. The docs root
contains only its entry-point README. The [feature guide](../features/README.md)
and [assistant guide](../assistant/README.md) provide topic navigation.

Consolidated 35 original records into 10 pages, preserving their complete bodies,
verification details and source order within each topic. The combined records
have contents links, explicit section anchors and historical scope labels:

- Assistant conversation batches, UI/app controls, foundation delivery, old plans
  and reviews, plus related chat-experience implementation records.
- Exam widget changes, settings organization/search, and Vizag footer/compression.
- Eight release-note files, newest recorded entry first, explicitly a partial history.

Moved 23 other documents into the relevant topic/development/audit directories.
Updated local Markdown links and retained fragment targets for merged sections.
Pinned GitHub history URLs and historical inventory/atlas data remain snapshots.
Generated evidence paths, the CI security guard, workflows, tests, application
sources, resources and encryption/data handling are unchanged by this step.

After adding two navigation pages, this step reduces Markdown files under docs
from 90 to 67 (23 fewer files). The broader documentation/review category drops
from 123 to 100 files, including supporting JSON/CSV/HTML/log/script evidence.
These counts describe the working tree, not Git history or generated build output.
