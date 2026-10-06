# Security remediation handoff

Start with [the remediation plan](AYVA_REMEDIATION_PLAN.md). It covers direct upgrades from v1.6.0 onward, maximum notes protection, standard protection for other features, and preservation of existing data. [The security review](AYVA_SECURITY_DATA_REVIEW.md) records findings against commit `eaeeb8367d4bcff6cc935e08a74b9f88d7b5c049`; code links deliberately point to that reviewed commit. Revalidate findings against the current checkout before implementation.

These documents do not implement any application fixes. Work package 1 establishes reproducible builds, repairs test coverage, and creates historical upgrade fixtures. Implement packages in dependency order with reviewed commits; do not reset keys or clear app storage. Read the repository's AGENTS.md before editing.

The optional `ayva_review_checks.py` requires Python 3 and the `cryptography` package. Run it from any directory:

```sh
python docs/security/ayva_review_checks.py
```

It writes `ayva_review_checks.json` alongside the script. The checked-in result is the original audit result, not proof about later commits. These checks are source assertions and synthetic crypto/SQLite models; they do not replace Android builds, migration tests, instrumentation, or real-device Keystore testing. The source checks may intentionally fail once a finding is fixed; replace them with proper application regression tests rather than preserving the insecure behavior.

The review's `/workspace` paths describe the original review environment. They are not setup requirements for a local PC. No genuine user data or credentials are included.
