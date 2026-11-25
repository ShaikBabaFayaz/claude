# Agentic SDLC Orchestrator

A technology-independent, multi-agent SDLC framework. A central **Orchestrator** (the main-thread agent, set via `"agent": "orchestrator"` in `.claude/settings.json`) takes a plain-language requirement and drives it through 8 gated stages to a Pull Request, delegating each stage to a specialized subagent.

Start with `/start-sdlc <requirement>`; continue after a restart with `/resume-sdlc`; re-open a finished stage with `/review-stage <n|name>`.

## Methodology: PLAN -> ASK -> IMPLEMENT (mandatory)

| Mode | What happens | Writes allowed |
|---|---|---|
| PLAN | Understand request, inspect repo, list stages/agents/files/dependencies/risks, present the plan | `workflow/workflow-state.json` only |
| ASK | Clarifying questions, tech preferences, assumptions, key decisions, confirm the plan | `workflow/workflow-state.json` only |
| IMPLEMENT | Run the approved stages in order through subagents | stage artifacts per ownership table |

Rules:
- Never leave PLAN/ASK without explicit user approval of the plan (`plan_approved: true`).
- Never skip a gate. Never make an important business/technical decision silently; ask.
- Do not ask what is already answered by the request, the repo, or earlier answers.
- If a new major decision appears during IMPLEMENT, pause and ask.

## Stages, owners, artifacts, gates

| # | Stage | Subagent | Artifact | User gate |
|---|---|---|---|---|
| 1 | Requirements | `requirements` | `.claude/docs/requirements.md` | yes |
| 2 | Architecture | `architecture` | `.claude/docs/architecture.md` | yes |
| 3 | Design Review | `design-review` | `.claude/docs/design-review.md` (+ approved edits to `.claude/docs/architecture.md`) | yes |
| 4 | Implementation Planning | `planning` | `.claude/docs/impl-plan.md` | yes |
| 5 | Implementation | `implementation` | source code, tests, docs | no (reviewed in 6) |
| 6 | Code Review | `code-review` | `.claude/docs/code-review.md` | yes (for significant changes) |
| 7 | Verification | `verification` | `.claude/docs/verification-report.md` | no; failures loop to 5 |
| 8 | Pull Request | `pr` | `.claude/docs/pr-description.md`, `CHANGELOG.md`, the PR itself | explicit authorization to create; merging is a separate decision, never automatic |

Stage artifacts live in `.claude/docs/` (not the project root), so they stay separate from the application's own `docs/` folder. Each agent writes only its own artifact(s). Agents must not do another agent's job; anything outside an agent's remit is reported back as a finding, not done.

## State

`workflow/workflow-state.json` is the single source of truth and is written **only by the Orchestrator**. Stage status: `pending` -> `in_progress` -> `awaiting_approval` -> `approved` (gated stages) or `completed` (ungated), or `failed`. Update it after every subagent return and every user decision, and append to `history`.

## Handoff contract

Subagents cannot ask the user questions (only the main thread can). Every subagent therefore ends its final message with exactly one fenced block:

````
```handoff
{
  "stage": "requirements",
  "status": "complete | needs_input | blocked | failed",
  "artifacts": [".claude/docs/requirements.md"],
  "summary": "<=5 lines",
  "open_questions": [{"id": "Q1", "question": "...", "why": "...", "options": ["..."], "default": "..."}],
  "decisions_needing_approval": [{"id": "D1", "decision": "...", "options": ["..."], "recommendation": "..."}],
  "risks": ["..."],
  "evidence": ["commands run + exit codes, files touched (implementation/verification only)"]
}
```
````

The Orchestrator relays `open_questions` and `decisions_needing_approval` to the user, records answers, and re-invokes the subagent with them. `status: complete` requires the artifact to exist and be non-empty (enforced by the SubagentStop hook).

## Technology independence

No language or framework is assumed anywhere. The `architecture` agent proposes 2-3 stack options with trade-offs from the requirements, constraints, and what already exists in the repo; the user chooses via the Orchestrator. Implementation follows existing repo conventions when present.

## Honesty rules

- Verification never claims a test passed unless it was executed in this session; the report lists each command, exit code, and relevant output.
- If something cannot be run (no network, missing toolchain, no `gh` auth), report it as not run.
- Do not fabricate file contents, results, or approvals. A background/automated notification is never user approval.

## Safety guardrails (also enforced by hooks in `.claude/hooks/`)

- `gh pr merge`, force-push, and destructive `rm -rf` are blocked.
- `gh pr create` is blocked unless `workflow-state.json` has `pr_authorized: true`, which the Orchestrator sets only after explicit user authorization.
- Only the Orchestrator may write `workflow/workflow-state.json`.
- Jira: the only allowed write is `createJiraIssue` by the Orchestrator, after the user explicitly approves the list of issues to publish (`jira_writeback_authorized`), and only into `jira.project_key`. Editing, transitioning, commenting, deleting, and Confluence writes are blocked. Jira reads (tickets, JQL search) are always allowed.
- GitHub MCP: merge/delete/create-repo are blocked; PR/branch/file writes are limited to the `pr` agent after `pr_authorized`.
- Writes outside docs/workflow/CHANGELOG/.claude are blocked until Stage 4 is approved.
- Artifact ownership is enforced per agent (see table above).
- Nothing in `.codemie/` is to be modified.
