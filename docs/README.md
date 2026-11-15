# Developer documentation

These guides describe the current working tree. Git history holds earlier plans,
delivery logs and audits; their test counts and release claims apply only to the
revision reviewed. Current implementation is not a claim of device certification.

| Topic | Current reference |
| --- | --- |
| Build, tests, CI and development workflow | [Build and test](development/BUILD_AND_TEST.md) |
| Source ownership | [Android module](../app/README.md) |
| Features and cross-feature rules | [Feature guide](features/README.md) |
| Portable/manual/automatic backups and restore | [Backup and restore](features/backups/BACKUP_AND_RESTORE.md) |
| Archive encryption, private media and picker return | [Private notes and media](features/notes/ARCHIVE_AND_MEDIA.md) |
| Assistant scope, guarded actions and acceptance | [Offline assistant](assistant/README.md) |
| Vocabulary preservation and attribution | [Vocabulary and dictionary retirement](assistant/DICTIONARY_DATA.md) |
| Settings ownership and search | [Settings](features/settings/SETTINGS_BEHAVIOR.md) |
| Runtime artwork and its authoring tools | [Vizag footer](features/settings/VIZAG_FOOTER.md) |
| Permissions, overlays, reminders and speech lifecycle | [Reliability and permissions](features/system/RELIABILITY_AND_PERMISSIONS.md) |
| Home-screen widget behavior | [Widgets](features/widgets/WIDGETS.md) |
| Data protection and verification limits | [Security](security/README.md) |
| Unresolved findings and acceptance work | [Open findings](security/OPEN_FINDINGS.md) |
| Recorded milestones and previous evidence | [Compact history](releases/CHANGELOG.md) |

Keep one authoritative guide per topic. Update behavior and open findings when
code changes; add test results with their revision and scope, rather than another
delivery document. Preserve security decisions, attribution and recovery rules.
Do not place keys, private notes or real conversation transcripts in these files.

`security/ayva_review_checks.py` is executable CI tooling retained at its existing
path. Artwork JSON inputs/evidence live in `tools/vizag/evidence/`, outside the
guides. The root audit tracker retains historical file classifications consumed
by its PowerShell helper; it is not a current security certificate.
