# Discussion comparison reasoning

This slice extends R01/R02/R04/R06/R07/R12. The user can supply option figures, ask about trade-offs, correct one figure, resolve a missing-value question, and carry the current evidence into `/plan`. It uses local typed state and decimal arithmetic, with no new dependency or database migration. Validation results and fingerprints are in [CURRENT.md](CURRENT.md).

The previous discussion frame retained statements but could not calculate comparisons. `DiscussionComparison` now stores option-specific cost/travel-time quantities, explicitly scoped upper limits, one cost/time priority, and a pending measurement question inside the encrypted discussion session. Existing frames without this field remain readable. All values come from user statements; minutes normalize durations, while currency identities remain distinct. No exchange rates are invented.

Full comparison first identifies known limit violations, then asks for missing or incomparable figures. Complete eligible figures support lower-cost/lower-time priority analysis or a Pareto comparison. Equal figures are reported as a tie. Qualitative discussion notes remain available; numeric results describe the supplied cost/time figures, without claiming those figures determine the whole decision. Single-factor questions inspect one dimension without changing the user's overall priority.

A plain trip budget is **not** automatically a per-option cost bound. Bounds use explicit statements such as “cost limit is INR 3000” and “travel time limit is 8 hours”. A conflicting replacement requires “actually”. A compound edit containing an unaccepted contradiction changes neither figure. Missing values stay unknown; a short reply can fill only the currently asked option/measurement. Changing the options clears that target and drops figures belonging to removed options.

A new choice known to violate a limit is declined. If revised evidence makes an existing choice violate a limit, the assistant asks for a correction or another choice before handoff. A blocked handoff stays in Talk and leaves all existing workspaces intact. Accepted handoff preserves compact comparison figures, limits, priority and an explicitly chosen option as workspace facts. It adds no invented steps or native task writes.

One supported journey:

```text
/talk let's talk through a trip to Goa
options are train or bus
train costs INR 2500 and takes 6 hours
bus costs INR 1800
compare them
nine hours
which is cheaper?
prioritize travel time
travel time limit is 8 hours
compare them
I choose train
let's plan it
```

Other supported repairs include “actually the second one takes 7.5 hours”, “forget the bus cost”, “clear time limit”, and “forget comparison figures”. Cost input supports INR/USD/EUR/GBP with numeric amounts. Duration input supports the existing word/fraction grammar and numeric minute/hour/day quantities. This is bounded evidence reasoning, not arbitrary financial advice, live travel knowledge, automatic booking, general constraint solving, or native hierarchy/session integration.

Next useful expansion: typed qualitative criteria and protected constraints with explicit provenance, uncertainty/ranges, richer reference repair, and independently authored conversation evaluation. Preserve the current mutation boundary and the native hierarchy/session milestone rather than expanding phrase lists without end-to-end evidence.
