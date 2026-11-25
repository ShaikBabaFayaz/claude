---
name: code-review
description: Rubric and checklist for reviewing implemented code for correctness, security, error handling, maintainability, DRY, dependency safety, and test coverage. Preloaded by the code-review agent.
user-invocable: false
---

# Code review (SDLC stage 6)

## Process
1. Read `.claude/docs/impl-plan.md` and `.claude/docs/architecture.md` to know what should exist, then review the implemented source and tests. Review everything the plan produced; use `git diff`/`git log` when a repo exists, otherwise Glob/Read.
2. You are read-only on source: report, never fix. Write only `.claude/docs/code-review.md`.
3. Each finding: ID, severity, category, `file:line`, what is wrong and why it matters, suggested fix, and whether the fix is a *significant change* (alters behavior, architecture, public API, or needs a new dependency) requiring user approval.
4. Only report issues you have verified by reading the code. Mark anything uncertain as "needs confirmation".

## Severity
- **blocker:** wrong behavior on a requirement, security vulnerability, data loss, build breakage.
- **major:** likely bug, missing error handling on a realistic failure, missing tests for core logic.
- **minor:** maintainability, duplication, naming, small inefficiency.
- **nit:** style.

## Checklist
- **Correctness:** meets acceptance criteria; off-by-one, null/empty, state handling, concurrency, time/timezone, numeric precision.
- **Security:** injection (SQL/command/template), XSS, authn/authz checks on every protected path, IDOR, secrets in code or logs, unsafe deserialization, path traversal, CSRF/CORS, input validation, error messages leaking internals.
- **Error handling:** failures surfaced consistently; no swallowed errors; resources cleaned up; sensible status codes/messages.
- **Quality:** readability, cohesion, layering per the architecture, dead code, overly clever code, DRY.
- **Dependencies:** necessity, pinned versions, known vulnerabilities (use the stack's audit tool if available and say whether it was run), license.
- **Tests:** presence and quality for core logic, edge cases, failure paths; flaky or tautological tests.
- **Conformance:** deviations from approved architecture/plan.
- **Docs:** README/run instructions accurate.
