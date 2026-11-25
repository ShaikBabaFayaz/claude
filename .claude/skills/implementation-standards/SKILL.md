---
name: implementation-standards
description: Language-agnostic coding, testing, and change-discipline standards for implementing approved SDLC tasks. Preloaded by the implementation agent.
user-invocable: false
---

# Implementation standards

## Discipline
- Implement tasks in `.claude/docs/impl-plan.md` dependency order. Do not start a task whose dependencies are incomplete.
- Follow existing repository conventions (layout, naming, formatting, lint config). In a new repo, follow the idioms of the approved stack.
- Never silently change approved requirements, architecture, or the plan. If reality requires a deviation, stop and report it in `decisions_needing_approval`; do not code around it.
- Do not add features, abstractions, or dependencies beyond the plan. A new dependency requires justification in the handoff (name, purpose, license, maintenance status).
- Do not edit `.claude/docs/requirements.md`, `.claude/docs/architecture.md`, `.claude/docs/design-review.md`, `.claude/docs/impl-plan.md`, or review/verification artifacts. You may update README/usage docs and code comments where needed.

## Quality
- Validate input at boundaries; handle errors explicitly with useful messages; no swallowed exceptions; handle edge cases (empty, null, duplicates, limits, concurrency where relevant).
- No hard-coded secrets; configuration via environment/config files; provide an example config.
- Small functions with clear names; avoid duplication; comments only for non-obvious "why".
- Parameterized queries / safe encoding; never build queries or commands from unsanitized input.

## Testing
- Write tests alongside each task per the plan's test strategy (unit; integration where the plan requires). Cover happy path, validation failures, and edge cases.
- Run the project's build and tests after each task. Record the exact commands and exit codes in `evidence`. If a command can't be run, say so; never imply success.
- Leave the project buildable at the end of every task.

## Handoff
Report per task: done / partial / blocked, files changed, tests added, commands run with exit codes, deviations, and open questions.
