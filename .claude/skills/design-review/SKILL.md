---
name: design-review
description: Checklist and severity rubric for reviewing an architecture document for weaknesses, security risks, scalability issues, and missing components. Preloaded by the design-review agent.
user-invocable: false
---

# Design review

## Process
1. Read `.claude/docs/requirements.md` then `.claude/docs/architecture.md`. Review the design against the requirements, not against personal preference.
2. Walk each checklist area below; record only real findings, each with evidence (section reference) and a concrete recommendation.
3. Assign severity: **blocker** (design cannot meet a Must requirement or has an exploitable flaw), **major** (significant risk or gap), **minor** (improvement).
4. Give a verdict: approve / approve with changes / reject.
5. Do NOT edit `.claude/docs/architecture.md` on your own. Return proposed edits as `decisions_needing_approval`. Edit only when the Orchestrator's prompt says which changes the user approved; then record each under "Changes applied".

## Checklist
- **Coverage:** every FR/NFR mapped to a component; missing components (auth, validation, migrations, config, logging, backups, error handling).
- **Security (STRIDE-lite):** spoofing/authn, tampering/integrity, repudiation/audit, information disclosure, denial of service, elevation of privilege; secrets handling; input validation; OWASP Top 10 relevance; data-at-rest/in-transit.
- **Scalability/performance:** bottlenecks, N+1 access patterns, unbounded queries/lists (pagination), statelessness, caching justified, capacity assumptions vs NFRs.
- **Reliability:** failure modes, retries/timeouts, idempotency, transactions/consistency, data loss.
- **Maintainability/testability:** coupling, layering, seams for testing, over- or under-engineering.
- **API/data:** consistency, versioning, error model, constraint and index gaps.
- **Operations:** deployability, config, observability, rollback.
- **Technology fit:** stack choice vs constraints and team, licensing, maturity.
