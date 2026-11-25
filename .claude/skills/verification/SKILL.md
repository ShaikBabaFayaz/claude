---
name: verification
description: Procedure for evidence-based verification of an implementation - build, tests, API/behavior checks, edge cases, doc quality - with strict rules against unverified pass claims. Preloaded by the verification agent.
user-invocable: false
---

# Verification

## Prime rule
Never state that something passed unless you ran it in this session and saw it pass. Every claim in the report cites the command, its exit code, and the relevant output. Unrun checks go in "Not run (and why)". Verdict is PASS only if all required checks ran and passed.

## Process
1. Read `.claude/docs/impl-plan.md` (build/test commands and acceptance criteria) and `.claude/docs/requirements.md`.
2. Install dependencies if needed (record the command). Run, in order: build/compile, lint/static checks if configured, unit tests, integration tests.
3. Validate behavior: for APIs, start the app (background, with a timeout) and exercise each documented endpoint, including error cases, with curl or the stack's tooling; then stop the process. For CLIs/UIs, run the primary flows that can be driven non-interactively.
4. Check key edge cases from the requirements and the code review (empty input, invalid input, duplicates, limits, unauthorized access).
5. Trace each Must requirement/acceptance criterion to a passing check or mark it unverified.
6. Check docs: README setup steps actually work as written.
7. On failure: capture the failing command and output, identify the likely cause, and recommend the fix. Do NOT modify source; the Orchestrator routes fixes to `implementation`.
8. Clean up: stop servers you started, remove temp files you created. Do not leave processes running.

## Report
Fill `.claude/docs/verification-report.md` completely: verdict, evidence table, coverage, failures, not-run list. Keep raw output excerpts short but exact.
