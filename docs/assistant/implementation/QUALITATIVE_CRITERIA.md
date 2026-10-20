# Qualitative priorities and requirements

This slice extends R01/R02/R04/R06/R07/R12. A user comparing named options can distinguish a preference from a requirement, describe each option, correct a description, answer an unknown-quality question, and carry the decision into `/plan`. Evidence and tested fingerprints are recorded in [CURRENT.md](CURRENT.md).

`DiscussionCriteria` adds a versioned record inside the existing encrypted comparison state: up to six properties, six rules, 36 option/property observations, one qualitative top priority, and one pending question. Rules retain the user's source statement. Observations are explicit true/false values; an absent observation stays unknown. There is no database migration, network dependency, model, inferred personal profile or new data source.

“I prefer quiet travel” creates a preference. “I need quiet travel” and “I can't travel overnight” create requirements. A requirement's opposite must be corrected explicitly. Named/ordinal statements such as “the first one is quiet and not overnight” supply evidence; contradictory batches make no changes. The bounded grammar handles `is`, `has`, `involves` and their supported negative forms. Quiet/noisy normalize to one binary property; arbitrary opposites or degrees are not inferred. Earlier requirements/descriptions remain notes until options are named, then supported assertions promote into typed context. Conflicting earlier assertions remain available and block handoff until clarified. Old native commands and short answers cannot be replayed as new question answers.

Full comparison combines numeric limits with known qualitative violations. It asks about unconfirmed requirements before claiming an option fits them. Soft preferences remain visible without excluding options. A top qualitative priority can replace the numeric top priority; asking a question about just cost/time leaves that priority intact. No automatic weighted score or option choice is introduced.

Choosing an option with an unconfirmed requirement is tentative and asks a scoped yes/no question. Only the current option/property can consume that short answer. Explicit resumption restores the question; changing options or answering a different comparison question clears the previous target. A known requirement conflict blocks choice/handoff. An unconfirmed requirement on an already chosen option blocks handoff and asks for the missing description. Existing workspaces and native tasks remain untouched. Clearing a requirement explicitly relaxes it while retaining descriptions.

Example:

```text
/talk let's talk through a trip to Goa
options are train or bus
I need quiet travel
I can't travel overnight
train is quiet and not overnight
bus isn't quiet and is overnight
train costs INR 2500 and takes 6 hours
bus costs INR 1800 and takes 9 hours
compare them
I choose train
let's plan it
```

The plan retains the supplied requirements, descriptions, figures and explicit choice as bounded workspace facts. It does not create native tasks until the existing planning/review flow is used. Previous numeric-only discussion frames still load. “Clear requirement for overnight”, “forget the bus quiet”, “clear qualitative priority” and “forget qualitative criteria” provide scoped repair/forget operations.

Remaining work includes graded/weighted preferences, ranges and uncertainty, richer language and logical relations, independent conversation evaluation, and native task hierarchy/work-session integration. The generic note-taking path still handles unsupported requirements; those notes are not silently treated as enforced predicates.
