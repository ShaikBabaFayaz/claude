---
name: pull-request
description: Templates and rules for preparing a changelog entry and a Pull Request description with the five required sections, and for creating the PR only after explicit authorization. Preloaded by the pr agent.
user-invocable: false
---

# Pull request

## Preparation (always)
1. Read all `.claude/docs/*.md` artifacts and `workflow/workflow-state.json` (read-only). Confirm Stage 7 verdict; if it is not PASS, state that plainly in the PR body and in your handoff.
2. Inspect git state: `git status`, `git branch --show-current`, `git log --oneline`, `git remote -v`, `gh auth status`. Report anything missing (not a repo, no remote, not authenticated, on the default branch). Do not init repos, add remotes, or change git config yourself; report and let the Orchestrator ask the user.
3. Write `CHANGELOG.md` entry (Keep a Changelog style: Added / Changed / Fixed / Security) and `.claude/docs/pr-description.md`.

## PR description - required sections, in this order
1. **Summary** - what and why, 2-4 sentences, linking requirements.
2. **Changes Made** - grouped by area/component.
3. **Test Evidence** - copy from `.claude/docs/verification-report.md`: real commands, results; say what was not run.
4. **Known Limitations** - open code-review findings, deferred items, unverified areas.
5. **Reviewer Checklist** - checkbox list tailored to the change (architecture conformance, security-sensitive files, migrations, config, docs).

Title: imperative, under 70 characters. When the Orchestrator passes Jira ticket keys, mention each in the Summary and prefix the title with the Epic or main story key.

## Creation (only with authorization)
- Create the PR only if the Orchestrator's prompt states the user explicitly authorized it and `pr_authorized` is true. Then: ensure the work is on a feature branch (create one if on the default branch, only with that same authorization), commit with the repo's message style, push, and run `gh pr create --title ... --body-file .claude/docs/pr-description.md`.
- Never run `gh pr merge`, force-push, or edit the default branch. Merging is a separate user decision.
- Report the PR URL in the handoff.
