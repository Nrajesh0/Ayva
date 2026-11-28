# Recorded milestones

This is a compact implementation history, not a complete release list or a claim
that every milestone was published. Use [GitHub Releases](https://github.com/Nrajesh0/Ayva/releases)
for actual artifacts and Git/CI for the exact revision. App metadata is prepared for 2.0.51 / 151. A version bump is not evidence of
publication; use GitHub Releases for the published status.

| Checkpoint | Retained decision/outcome |
| --- | --- |
| Original offline assistant work | Bounded rules/authored content, typed guarded actions, native editors, reviewed plans and durable task receipts; no hosted model or private-Notes capability |
| 2.0.23–2.0.25 | Native Smoke Paw reward presentation and Vizag artwork; single-award/lifecycle, reduced-motion and offline renderer boundaries retained |
| 2.0.31–2.0.37 | Canonical settings/search, widget editors, reminder FIFO and runtime reliability. Experimental battery-health widget subsequently removed; old design records are not active features |
| 29 October recovery work | Authenticated multi-store restore journal/commit marker, maintenance admission, retained originals, private-draft preservation and vocabulary identity coverage |
| 2.0.40–2.0.43 | Portable automatic full backups, saved recovery key/private-note authorization, verified publication, scoped retention, permission hardening and explicit provider/OS limitations |
| 2.0.44–2.0.45 | No legacy plaintext/static-key importers or archive v1–3 readers; five encrypted user stores and one-way vault verifier. Simpler backup setup and default snapshot replacement. Unsupported development data is retained, not reset |
| 2.0.46 | Inline archive authorization, optional write-only HPKE archiving, draft/selection preservation and authenticated media progress |
| 2.0.47 | Retired general dictionary while retaining native vocabulary/progress/credits. Backup/profile/widget coverage and two-minute private-picker return; category-aware chat update controls. Compiler memory and non-inline editor boundaries repaired hosted failures |
| Later local restore work | Remapped interactive card identities, duplicate/orphan preflight checks, preserved chat/drill collisions, operation-owned media cleanup, startup recovery gating and persistent non-sensitive outcomes |
| 15 November source cleanup | Account/editor components and shared models extracted, duplicate pure helpers/record writers consolidated, current invariants clarified; no source features or encryption policy removed |
| Documentation consolidation | Current guides replace overlapping plans/audits/HTML inventories. Artwork machine records move with tool references. Historical file-coverage classifications remain in the root tracker |
| 2.0.48 | Optional first-use spotlight tours, context-aware feature tips and Help replay controls. Guides preserve authorization/data-operation flows; Ayva placement is unchanged |
| 2.0.49 | Flow reliability: recovery/progress controls, encrypted draft and save-conflict handling, consistent blocking rules and durable rewards. Pending task deletes cannot cross snapshot replacement; subtask taps update the latest stored task independently of editor saves |
| 2.0.50 | Notes edit and Trash recovery, private Archive and snapshot restoration, attachment import and thumbnails, widget bindings, and legacy article migration |
| 2.0.51 | Make Notes backup and Archive regression tests independent of startup cleanup and asynchronous save timing |

## Spotlight tour verification

The tour implementation passed 81 distinct targeted JVM/Robolectric cases across
validation runs, including archive/backup/navigation regressions, locked-archive
replay, accessible controls, large text, dismissal and task saving. Source security
and chat-boundary guards passed. Debug and optimized unsigned release APKs built;
debug/release lint reported zero errors, 246/244 warnings and 57 informational
findings each. These checks preceded the metadata-only bump to 2.0.48 / 148;
hosted CI verifies the versioned commit. Device acceptance for TalkBack, rotation,
split-screen and clean-install/update flows remains necessary.

## Source cleanup verification checkpoint

The source cleanup's initial full JVM/Robolectric run executed 1,939 cases in 144
suites with one synthetic credential-fixture race. Only that key-policy test was
isolated from asynchronous app startup; its preservation/refusal assertions stayed.
Final review preserved the two habit forms' existing window rules and fixed invalid
interval bounds. Replacing the three affected suite results with their final
25-case rerun gives 1,940 distinct passing cases, no remaining failures/errors/skips.
These are combined full-run/focused-rerun results, not a second final full-suite run.
The diagnosed cloud C1 test-worker workaround disabled no assertions.

Debug/release lint passed with zero errors, existing 245/243 warnings and 57
informational findings each. Debug, optimized unsigned release and device-test APKs
built. All 820 frozen source/build/check inputs and nine Room schemas matched;
account screenshots were byte-identical. Source token/literal checks preserved
screen bodies and backup/journal ordering except intended helper/cache delegation.
No emulator/phone execution was available for those later local changes. No version
bump, push or publication was part of that checkpoint.

## Historical records

Earlier tracked plans, audits, research and per-release evidence are available in
[the documentation tree at 2ccd875](https://github.com/Nrajesh0/Ayva/tree/2ccd875/docs)
and its Git history. Do not treat old migration proposals, test totals, dated file
inventories or “pending/uncommitted” handoffs as current instructions. Regression
fixtures retain executable failure coverage. Artwork evidence remains with its tools.

Before this consolidation, the complete working documentation—including uncommitted
updates—was preserved locally as
`/workspace/scratch/documentation-consolidation-2026-11-15/before.tar.gz`.
That local archive is not an app/build dependency or a published repository artifact.
