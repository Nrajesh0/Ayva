# Security review guide

Security findings are tied to the commit/date reviewed. Historical remediation
plans are not a fresh task list, and old “audit not started” or legacy-upgrade
instructions do not describe the current unreleased-format policy. Revalidate a
finding against current code before changing storage, credentials or recovery.

## Latest repository review

The [13 November 2026 repository review](../audits/REPOSITORY_REVIEW_2026-11-13.md)
records an exposed historical licence-signing private key whose public verifier
remains in the app. Retiring that licence key remains a security follow-up;
documentation cleanup did not rotate it, rewrite history or change user data keys.
See that review for evidence, scope and scan limitations. A clean current file
scan does not remove a secret from Git history.

## Storage and backup implementation records

- [Archive/PIN flow](../features/notes/ARCHIVE_FLOW_2026-11-13.md).
- [Backup progress and authenticated media reads](../features/backups/MEDIA_BACKUP_PROGRESS_2026-11-13.md).
- [Simplified backup/restore flow](../features/backups/BACKUP_FLOW_REDESIGN_2026-11-07.md).
- [Automatic backups](../features/backups/AUTOMATIC_FULL_BACKUPS.md) and
  [consolidated historical audits](../features/backups/AUTOMATIC_BACKUP_AUDIT_HISTORY.md).

## Historical security evidence

- [6 November independent review](INDEPENDENT_REVIEW_2026-11-06.md) and
  [backup implementation audit](BACKUP_IMPLEMENTATION_AUDIT_2026-11-06.md).
- [30 October review](SECURITY_REVIEW_2026_10_30.md).
- [29 October whole-app audit](APP_SECURITY_DATA_AUDIT_2026_10_29.md) and
  [remediation record](APP_SECURITY_REMEDIATION_2026_10_29.md).
- [Earlier security review](AYVA_SECURITY_DATA_REVIEW.md),
  [remediation plan](AYVA_REMEDIATION_PLAN.md) and
  [audit plan](APP_SECURITY_DATA_AUDIT_PLAN.md).

These preserve findings, source references, tests and limitations. Old legacy-file
migration proposals and release statuses are historical evidence, not instructions
to enable unsupported formats or rerun completed packages.

## Active source guard and runtime verification

Validation and release workflows run this Python 3 source-contract guard:

```sh
python3 docs/security/ayva_review_checks.py
```

The current script uses the standard library, checks current source contracts and
prints its result; it does not generate or refresh the historical
`ayva_review_checks.json` alongside it. That JSON and dated evidence files remain
historical records, not results for the current checkout.

Source assertions do not establish runtime cryptographic correctness or hardware
Keystore behavior. JVM regressions, native emulator checks and physical-device
acceptance serve distinct purposes; see the [build and test guide](../development/BUILD_AND_TEST.md).
Review `AGENTS.md` before implementation and preserve existing data. Never clear
storage, reset encryption/signing keys or introduce fallback plaintext storage to
make a security check pass. Historical `/workspace` paths describe the original
review environment, not setup requirements on a new machine.
