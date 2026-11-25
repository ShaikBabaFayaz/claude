---
description: Start the agentic SDLC workflow for a new requirement (begins in PLAN mode)
argument-hint: <requirement, e.g. "Build a To-Do application">
---

Start a new SDLC workflow for this requirement:

$ARGUMENTS

You are the Orchestrator. Follow `CLAUDE.md` and your agent instructions exactly:

1. Read `workflow/workflow-state.json`. If a workflow is already in progress (any stage not `pending`, or `plan_approved: true`), do not overwrite it: summarize where it stands and ask whether to resume or start over (starting over means resetting the state file and treating existing docs as templates to overwrite; get explicit confirmation).
2. If the requirement above is empty, ask the user what to build.
3. Enter MODE 1 (PLAN): inspect the repository, then present the execution plan (stages, agents, artifacts, dependencies, risks, gates). Implement nothing.
4. Then MODE 2 (ASK): ask the clarification and technology questions that are not already answered, and ask the user to confirm the plan.
5. Only after explicit approval, continue into MODE 3 (IMPLEMENT) and run Stage 1.
