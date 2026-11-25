---
name: architecture-design
description: Method for technology-independent architecture design - stack option analysis, component and data design, API contracts, Mermaid diagrams, ADRs. Preloaded by the architecture agent.
user-invocable: false
---

# Architecture design

## Process
1. Read `.claude/docs/requirements.md` fully; note constraints, NFRs, and any user-mandated technologies. Inspect the repo for existing stack/conventions; prefer them over new choices.
2. **Stack options:** propose 2-3 viable stacks (never just one, unless the user or repo mandates it). For each: components, pros, cons, fit to NFRs, operational complexity, team/skill assumptions. Give a recommendation, and return it as a `decisions_needing_approval` item. Do not finalize the stack without user approval; write the document with the recommended option marked "pending approval".
3. **Components:** responsibilities, boundaries, and dependencies; keep the simplest structure that satisfies the NFRs (avoid microservices/queues/caches the requirements do not justify).
4. **Data:** entities, attributes, keys, relationships, constraints, indexes, migration approach. Use an ER diagram when there are 3+ entities.
5. **API contracts:** for each interface: path/operation, request, response, error model, auth, idempotency. Choose one consistent error format.
6. **Interactions:** sequence diagrams for the 2-3 most important flows.
7. **Cross-cutting:** authn/authz, input validation, error handling, logging, config/secrets, testing strategy, build/deploy.
8. **ADRs:** every significant decision gets Decision / Alternatives / Rationale / Status.
9. **Traceability:** map every FR/NFR to the component(s) that satisfy it; flag uncovered requirements.

## Mermaid
Use `flowchart`, `sequenceDiagram`, `erDiagram`, `classDiagram` where they clarify. Keep diagrams small and syntactically valid (quote labels with special characters).

## Quality bar
Every requirement traced; every decision has a rationale; no technology named without a reason; no unexplained buzzwords.
