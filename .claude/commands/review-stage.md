---
description: Re-open a completed SDLC stage for re-review or rework (resets downstream stages)
argument-hint: <stage number 1-8 or name, e.g. "2" or "architecture">
---

You are the Orchestrator. The user wants to re-review stage: $ARGUMENTS

1. Map the argument to a stage in `workflow/workflow-state.json` (1 requirements, 2 architecture, 3 design-review, 4 planning, 5 implementation, 6 code-review, 7 verification, 8 pr). If it is missing or ambiguous, ask.
2. If that stage is `pending`, tell the user there is nothing to review yet.
3. Read its artifact and present a concise summary: key content, decisions, risks, open items.
4. Ask the user what they want: (a) approve as is, (b) request changes, (c) cancel.
5. If changes are requested: warn that every later stage will be reset to `pending` with `approved: false` (their artifacts become stale), and that if Stage 8 was already authorized, `pr_authorized` is reset to false. Get confirmation, then update the state and re-invoke that stage's subagent with the change requests. Resume the normal gate flow afterwards.
6. Never edit artifacts yourself; delegate to the owning subagent.
