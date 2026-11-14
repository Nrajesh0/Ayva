# Documentation guide

Use the entry points below instead of reading the documentation file by file.
Feature records describe the app; plans and dated audits describe their recorded
checkpoint. Historical test counts and release statements are not current CI or
device-acceptance results.

| What you need | Start here |
| --- | --- |
| Build, test commands and when CI runs | [Build and test guide](development/BUILD_AND_TEST.md) |
| Backup, archive, widgets, settings and other feature behavior | [Feature guide](features/README.md) |
| Offline assistant scope, implementation and plans | [Assistant guide](assistant/README.md), starting with its [status](assistant/STATUS.md) |
| Backup/restore and image-handling deep dive | [14 November review](audits/BACKUP_AND_MEDIA_REVIEW_2026-11-14.md) |
| Security findings, evidence and verification limits | [Security guide](security/README.md) |
| Repository inventory, cleanup and outstanding findings | [Cleanup review and follow-up](audits/CODE_CLEANUP_REVIEW.md), [13 November repository review](audits/REPOSITORY_REVIEW_2026-11-13.md) and its [baseline inventory](audits/REPOSITORY_FILE_INVENTORY_2026-11-13.csv) |
| Historical release changes | [Recorded release notes](releases/CHANGELOG.md) |
| Historical visual/source map | [Visual guide](app-atlas/README.md), pinned to its older source snapshot |

## How the files are organized

- `development/`: build/testing instructions and the assistant development workflow.
- `features/`: behavior and implementation records grouped by feature.
- `assistant/`: maintained status/capability references, specific implementation
  records, future plans, and explicitly separated `history/` and `research/`.
- `security/` and `audits/`: reviews, findings and evidence. Read dates and scope
  before treating an old proposed fix as current work.
- `releases/` and `app-atlas/`: historical release notes and the pinned visual map.
- `implementation/*.json` and `design/*.json`: retained generated evidence at the
  original paths. Some art tools read/write these paths; they are not loose guides.

The CI-used `security/ayva_review_checks.py` remains executable tooling. Tests,
Room schemas, build configuration, licences, provenance and store metadata also
serve maintenance/distribution purposes even when they are not APK inputs.

Maintain one clear entry point for a topic. Update it when behavior changes;
combine related delivery records rather than adding another competing status page.
Preserve unique security, authorization, recovery and provenance details. Keep
historical records labelled, and update local links whenever a document moves.
