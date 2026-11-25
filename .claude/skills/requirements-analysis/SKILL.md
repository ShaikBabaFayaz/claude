---
name: requirements-analysis
description: Checklists and templates for eliciting and documenting functional and non-functional requirements for any kind of application. Preloaded by the requirements agent.
user-invocable: false
---

# Requirements analysis

## Process
1. Gather sources: the user's request, any user stories/documents given, and the existing repo (README, issues, existing code). Do not re-ask what these already answer.
2. Extract: business goal, users/personas, functional requirements, NFRs, constraints, assumptions, out-of-scope.
3. Write every requirement as a testable statement with an ID (`FR-n`, `NFR-n`) and acceptance criteria (Given/When/Then or measurable condition).
4. Prioritize with MoSCoW. A "Must" without acceptance criteria is incomplete.
5. List gaps as open questions. Each question needs: why it matters, 2-4 options, and your recommended default. Ask only what changes the design or scope.
6. Mark each assumption explicitly; unconfirmed assumptions go to `open_questions`, not silently into the document.

## Gap-finding checklist (ask only if not already answered)
- Users and roles; authentication/authorization needed?
- Core entities and their attributes/relationships; data volume; retention.
- Core operations (CRUD plus domain-specific); validation rules; error cases.
- Interface: web, mobile, CLI, API only? Single user or multi-user? Offline?
- Persistence and integrations (email, payment, third-party APIs).
- Deployment/runtime constraints; mandated or forbidden technologies; team skills.
- NFRs: performance targets, security/privacy/compliance, availability, accessibility, i18n, observability.
- Scope boundary: what is explicitly NOT included in this version.

## Quality bar
Unambiguous, atomic, testable, traceable, no solution/technology prescribed (technology belongs to Stage 2 unless the user mandated it).
