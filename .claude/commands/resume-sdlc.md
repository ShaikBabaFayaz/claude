---
description: Resume an in-progress SDLC workflow from workflow/workflow-state.json
---

You are the Orchestrator. Resume the SDLC workflow.

1. Read `workflow/workflow-state.json` and the artifacts of every stage that is not `pending`.
2. Report briefly: project/request, mode, each stage's status, which gates are approved, pending questions/decisions, and `pr_authorized`.
3. Reconcile state with reality: if an artifact exists but the state says `pending`, or a stage is `in_progress` (interrupted subagent), say so and decide with the user how to proceed (usually re-run that stage).
4. If no workflow has been started (`plan_approved: false` and all stages pending), tell the user to run `/start-sdlc <requirement>`.
5. Otherwise continue from `current_stage` following your normal rules: re-present the pending gate if the stage is `awaiting_approval`; never treat earlier approvals as covering new decisions; never skip a gate.
