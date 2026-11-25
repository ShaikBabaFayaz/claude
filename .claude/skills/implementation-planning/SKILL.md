---
name: implementation-planning
description: Method for breaking an approved architecture into dependency-ordered, independently verifiable implementation tasks with acceptance criteria. Preloaded by the planning agent.
user-invocable: false
---

# Implementation planning

## Process
1. Read approved requirements, architecture, and design review (including any applied changes). Plan only what is approved.
2. Start with scaffolding: project setup, tooling, build, test harness, config. Then foundations (data layer, shared utilities), then features in vertical slices, then cross-cutting hardening, then documentation.
3. Each task: ID, title, description, dependencies, priority, size (S <= ~1h, M <= ~half day, L = split if possible), requirement references, acceptance criteria, tests to write.
4. Acceptance criteria must be checkable by a command or observable behavior; include the test(s) that prove them.
5. Order topologically by dependencies; show the graph as Mermaid. No cycles. Mark tasks blocked on an open decision and name the decision.
6. Prefer tasks that leave the project buildable and tests green.
7. Every Must requirement must be covered by at least one task; list any that are not.
8. Include the concrete commands for build, test, lint, run (derived from the approved stack) in "Approach and conventions".

## Quality bar
Small, ordered, traceable to requirements, testable, no hidden work (migrations, config, docs, seed data are tasks too).
