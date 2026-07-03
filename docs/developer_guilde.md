# Developer Guide

## Use-case History Tickets

`docs/use_case_history` is an append-only history. Do not edit original epic tickets after they are created, even when an
existing use case changes later.

When a change updates an existing use case, create a new `EP-*` ticket for the same `Use Case ID`. Put only the new or
updated acceptance criteria in the new ticket. The developer task is to merge those criteria into the current
`docs/specs/**/uc.feature` file for that use case.

If a ticket intentionally deletes or replaces an existing scenario, state that explicitly in the new ticket's acceptance
criteria. Otherwise, existing scenarios remain part of the use case.

New use cases get their own first `EP-*` ticket with the full initial acceptance criteria.
