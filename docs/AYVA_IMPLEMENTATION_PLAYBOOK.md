# Ayva implementation playbook

This document makes the [conversational planner roadmap](AYVA_CONVERSATIONAL_PLANNER_ROADMAP.md) executable across many development sessions. Implementation is authorized; the foundation and backup/conversational-repair continuation have passed functional validation. Use [the current ledger](assistant/implementation/CURRENT.md) for actual delivered behavior, validation, and the remaining roadmap.

## Validation preference

The user explicitly requested necessary tests only. Use focused checks for data persistence, scheduling/authority logic, and reported keyboard/draft regressions. Routine UI and low-impact changes need an appropriate build or lightweight check; do not repeat the full suite, device matrix, or optimized release for every edit. Broaden validation only when a concrete risk, failure, migration, or cross-cutting change warrants it. Record exactly which source and scenarios were checked.

## Current handoff

- Stage: bounded core implementation complete; independent audits, device/release acceptance and the roadmap's expansion frontier remain open. See [the delivery record](assistant/implementation/COMPLETION_IMPLEMENTATION.md) and the current requirement table in the ledger.
- Delivered: keyboard/draft recovery, compact modes/actions, relevant greetings/agenda, structured listening/discussion and comparisons, durable conversational planning, native hierarchy/multiple sessions, work-start reminders, constraints/dependent replanning, reusable templates/explicit memory/preferences, multi-level draft/saved undo/redo and full/rolling archive recovery. Ordinary dictionary replies omit repeated credit footers; dedicated attribution remains.
- New native-task adoption preserves existing IDs/details and parent/prerequisite closure, with completed tasks and occasions retained as read-only context. Review/apply remains mandatory for native writes. Chat agenda uses work intervals and deadlines distinctly.
- Validation: the final affected-only 80-case check and debug build passed (3m 13s), after the broader 174-case integration checkpoint (3m 34s). Source hashes matched during final verification; boundary and whitespace checks passed. Use the exact source fingerprints, suite scopes and logs in CURRENT.md. Earlier 96-case native, full-suite, release and Android 10 evidence belongs to earlier checkpoints. Do not combine separate runs into an inflated count or claim old APKs verify current code.
- User preference: only necessary correctness tests now; conduct independent conversation, performance, visual/accessibility, device/OEM/lifecycle and release audits later. Do not restart the emulator or full suite for routine edits.
- Open expansion: graded/weighted reasoning, richer knowledge and languages, deeper dialogue arbitration, progress-aware replanning and advanced optimization. Promote through concrete journeys and evidence; bounded grammar is not unrestricted understanding.
- Verification was performed on uncommitted branch `work`, based on `e5378ede1869d41be5642fd96fbeff1e6e223aa7`. The user subsequently requested version 2.0.10 / 110 and a push to `main`; consult Git history and the remote ref for publication identity. The debug APK is current only after the latest recorded build; release/instrumentation APKs retain earlier evidence. No rollout occurred. The emulator is stopped.
- No unresolved product question prevents completing the handoff. Preserve all existing changes; never infer a tested source from the base commit alone.

## Start-of-session procedure

1. Read `AGENTS.md`, the current user instructions, this handoff, the relevant roadmap sections, and active slice/decision records.
2. Inspect Git status, actual HEAD, and the affected code. Preserve existing user changes. Use the existing isolated checkout unless the user explicitly requests a worktree.
3. Identify what is implemented, proposed, tested, blocked, and stale. A prior summary or passing test count is not a substitute for current code evidence.
4. Select the next dependency-ready vertical slice. State its user outcome and acceptance gate, including requirement IDs.
5. Check environment activation and only the prerequisites needed for the slice. The prepared environment uses `/workspace/.setup/java-env.sh`; inspect availability before relying on it. Use the repository-pinned Gradle and documented memory override where applicable.
6. Continue authorized work autonomously. Ask for missing information only if it materially blocks progress; prepare concrete choices for consequential product tradeoffs.

## Work in resumable vertical slices

A slice is small enough to implement, review, and validate coherently but must represent meaningful behavior. Examples: preserve a habit draft after keyboard dismissal and recreation; resume a trip plan after process death; move a referenced subtask while keeping a protected appointment fixed. “Add thirty phrases” is not an adequate slice outcome.

Record for every slice:

```text
Slice ID / title:
User journey and requirement IDs:
Current behavior and reproduction:
Target semantic state / visible behavior:
Dependencies and affected contracts:
Implementation scope and migration implications:
Regression risks:
Acceptance scenarios and necessary device checks:
Actual commands, exit status, result paths, and tested revision:
Remaining limitations or blockers:
Next dependency-ready action:
```

Use typed interfaces and testable boundaries before adding domain breadth. Complete error paths, persistence, and integration alongside the happy path. Do not mark a slice done if only the parser recognizes it but the real UI routes it elsewhere or execution loses the information.

## Durable project records

Create these records when implementation begins, and keep them concise enough to remain useful:

| Record | Contents | Update trigger |
| --- | --- | --- |
| Requirement/capability ledger | R01–R12, supported journeys, state, owner milestone, evidence links, missing dependencies | A behavior is added, deferred, tested, or found broken |
| Active slice | Reproduction, design, affected files, acceptance checks, progress | Meaningful implementation result |
| Decisions | Problem, alternatives, chosen approach, evidence, consequences, reconsideration trigger | Consequential architectural choice |
| Experiments | Hypothesis, bounded prototype, baseline, outcomes, recommended next action | Investigation or capability-expansion spike |
| Failure corpus | Anonymized/synthetic conversation fixture, clock, records, expected state, failure category | Realistic misunderstanding or regression |
| Evaluation report | Tested revision, suite/device scope, actual results, performance, limitations | Milestone/release gate |
| Handoff | Completed work, exact unresolved state, running processes, next command/action | Session boundary or significant task change |

Suggested location: `docs/assistant/implementation/`, with fixtures placed in the relevant test directories. Do not fill these folders with speculative boilerplate in advance. Do not store secrets or private conversations in repository records. Product memory and development records are separate systems.

Use explicit states: proposed, investigated, implementing, implemented-unverified, verified, blocked, deferred, and rejected. “Verified” names its tested scope. A parser unit test does not verify Android Back behavior, and an emulator run does not establish all OEM behavior.

## Discovery work during implementation

At the beginning of each slice identify at least the important uncertainties; do not invent research where the implementation is routine. At completion ask whether an observed limitation should become a bug, a reusable primitive, or an expansion experiment.

Expansion proposal template:

```text
Capability / motivating conversation:
Why the current design cannot satisfy it:
Reusable concept it would add:
Candidate no-model, offline approaches:
Smallest experiment that distinguishes those approaches:
Acceptance examples and adversarial cases:
Expected costs, dependencies, and affected existing behavior:
Decision: integrate now / schedule next / defer / reject:
Evidence and reconsideration trigger:
```

Prioritize wrong actions and lost work, then severe conversational friction, then capabilities that unlock multiple journeys, then optional breadth. If the experiment fails, document why and revise the design. Failure is useful evidence; suppressing an assertion or reducing the supported scope without recording it is not progress.

Before adopting a new library, check offline operation, Android compatibility, license, footprint, deterministic behavior, and maintenance. A no-model solver or grammar library may be appropriate; a model hidden inside a dependency violates R01.

## Validation discipline

- Reproduce a bug before claiming a fix. Write meaningful tests at the layer that can expose the failure.
- For language and planning changes, test full conversations through routing, state persistence, proposal, and execution as appropriate—not only isolated helper functions.
- For data changes, include schema migration, backup/restore, widgets/reminders, native editing, and failure recovery.
- For IME, sheets, predictive Back, animation, and focus, use relevant device tests; report unsupported device coverage as outstanding.
- Run targeted checks during development and required broader checks at integration gates. Reuse valid outputs; repeat expensive full builds when changes or unresolved risks justify them.
- Record actual command results, test counts, skipped cases, and artifacts from the current run. Do not conflate a successful build with functional validation.
- Preserve independent evaluation examples. Once tuned against, move them to regression coverage and refresh the held-out corpus.
- After a change, check diff scope and working-tree status. Do not remove unrelated user changes or silently alter tests to approve new behavior.

## Ending a session without losing the project

Update the handoff with the actual branch/HEAD when available, dirty files, changes made, checks passed/failed/unrun, unresolved design choices, and next action. Include any service/process started and whether it is still needed. Record precise blockers and useful work that remains independent of them.

Do not leave only “continue the plan.” A good handoff says what file or scenario to examine next, what hypothesis is being tested, and what result would change the decision. Avoid dumping the entire conversation; preserve facts and evidence.

When resuming, verify those facts before acting. If a prior design is demonstrably wrong, amend the decision and roadmap rather than following it mechanically. If the user changes priorities, retain the requirement history and adjust dependencies without silently dropping commitments.

## Milestone completion review

Before closing a milestone, verify its user journey, R01–R12 coverage changes, integration behavior, migrations, device requirements, and known limitations. Review what the milestone taught us and select the next capability expansion using evidence.

Declare separately what is ready for development, what is implemented, what is device-verified, and what is release-ready. Baseline gates stay visible even when new ambitious capabilities are added. The long-term objective is sustained, measurable improvement across sessions, not the appearance of completing a checklist.
