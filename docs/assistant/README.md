# Offline assistant guide

Start with [status and limits](STATUS.md). The assistant uses bounded offline
rules and authored content; a historical roadmap is not evidence that every
planned capability exists.

## Maintained references and implementation

| Purpose | Reference |
| --- | --- |
| Delivered scope, handoff and acceptance limits | [Status](STATUS.md) and [capability matrix](CAPABILITY_MATRIX.md) |
| Source-specific implementation record | [Implementation ledger](implementation/CURRENT.md) |
| Conversation quality and trip-checklist follow-ups | [Conversation quality](implementation/CONVERSATION_QUALITY_CURRENT.md) and [trip checklist](implementation/TRIP_CHECKLIST_CONVERSATION.md) |
| General/focused chat and guided planning contracts | [Chat experience implementation records](implementation/CHAT_EXPERIENCES.md) |
| On-device acceptance requirements | [Device acceptance](DEVICE_ACCEPTANCE.md) |
| Dictionary data, licensing and reproducibility | [Dictionary guide](DICTIONARY_DATA.md) |
| Build/test commands and CI scheduling | [Build guide](../development/BUILD_AND_TEST.md) |
| Development and handoff workflow | [Assistant workflow](../development/ASSISTANT_WORKFLOW.md) |

The remaining files under `implementation/` preserve specific contracts for native
planning, comparison reasoning, chat cleanup, icon selection and quality criteria.
Their stated verification limits remain relevant; they are not separate release
certificates.

## Plans, historical records and research

[Conversation-quality planning](CONVERSATION_QUALITY_PLAN.md) and
[focused-experience planning](FOCUSED_CHAT_EXPERIENCES_PLAN.md) describe goals and
acceptance criteria. Check status and implementation before assuming a goal is
delivered or a previously proposed task is still outstanding.

Original records are grouped by purpose rather than spread across the docs root:

- [Planning baseline](history/PLANNING_BASELINE.md): original batch plan and
  conversational-planner roadmap.
- [Foundation delivery](history/FOUNDATION_DELIVERY.md): initial offline batches,
  B0/B1 foundation, B3–B11 continuation and core completion.
- [Conversation batches](history/CONVERSATION_BATCHES.md): continuity, language,
  capabilities, preferences/planning and later repairs.
- [Chat UI and controls](history/CHAT_UI_AND_CONTROLS.md): interaction design,
  response controls, settings help and manual app-rule authorization.
- [Reviews](history/REVIEWS.md): original behavior and conversation-quality reviews.

`research/` retains dated comparison, source/provenance and reuse investigations.
These are supporting research, not an instruction to install a proposed stack or
replace the implemented architecture. Historical records keep their original
findings and test evidence; their contents tables link directly to each record.
