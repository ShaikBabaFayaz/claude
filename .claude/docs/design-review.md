# Design Review

**Status:** approved by user 2026-11-25 (all findings adopted and applied to `architecture.md`; Stage 3 gate passed)
**Owner:** `design-review` agent | **Date:** 2026-11-25
**Reviewed:** `.claude/docs/architecture.md` (user-approved 2026-11-25) against `.claude/docs/requirements.md` (user-approved 2026-11-25)
**Pass 2 (2026-11-25):** The user approved every recommendation, as D-R1..D-R6, with the specific choices listed in section 6. Each finding below is marked **Adopted**, with a pointer to where it was applied in `architecture.md`.
**Scope note:** The user has already decided the technology choices D1-D9 and Q-A1. They are not reopened here. Every finding below works inside those choices (for example, DR-3 adds a backstop *within* D3. It does not swap pessimistic locking for optimistic locking).

## 1. Verdict

**Approve with changes.**

The architecture is sound overall and well traced. Every FR, AC and NFR maps to a component (section 12 of the architecture). The layering, error model, clock abstraction, seed design and accessibility approach are appropriate. The main problems are in the concurrency design (section 7.3), which is the top-priority goal (goal 1, NFR-5, AC-4.BE.11):

- Two ordinary JPA behaviours can silently corrupt the `active_loan_count` counters under concurrency: full-row updates from writers that hold no lock, and association loading that happens before the lock is taken.
- The stated "CHECK constraints catch a stale read" backstop does not actually work for counters. Hibernate writes absolute values.

These are cheap to fix, but they must be fixed before planning. There is also a group of medium items: error-format coverage outside the MVC layer, N+1 queries, test-context database sharing, AC-X.3 behaviour behind the proxy, and WCAG gaps.

**Status after pass 2:** all 20 findings are adopted and applied to `architecture.md`. I re-read the revised sections and found no remaining must-fix items, so the design is ready for approval at the Stage 3 gate. One new trade-off was recorded: the allowlists must be kept in step with the configured ports (architecture R-12).

Severity scale: **critical** = cannot meet a Must requirement or exploitable flaw (blocker); **high/medium** = significant risk or gap (major); **low** = improvement (minor). No critical findings.

## 2. Summary of findings

| ID | Severity | Must-fix? | Area | Title | Resolution | Applied in architecture.md |
|---|---|---|---|---|---|---|
| DR-1 | high | must-fix | Concurrency | Unlocked full-row updates (member edit) overwrite counters and status | **Adopted** (D-R1) | 2.2 D3 note, 4, 5.1, 5.2 `member.version`, 7.1, 7.3, 13 R-2, ADR-23/24 |
| DR-2 | high | must-fix | Concurrency / JPA cache | Loading `Loan` associations before the lock defeats the lock on return | **Adopted** (D-R1) | 4 `RowLocks`, 5.2 entity mapping, 7.3 rules, 7.5, 13 R-3, ADR-24 |
| DR-3 | high | must-fix | Concurrency | The claimed CHECK backstop does not catch stale counter reads (lost update) | **Adopted** (D-R1) | 5.1/5.2 `version` columns, 6.2 `CONCURRENT_UPDATE`, 7.2, 7.3 backstop + invariant table, 10.2, 13 R-1/R-2/R-13, ADR-23 |
| DR-4 | medium | must-fix | Testability | Concurrency test matrix misses the interleavings in DR-1/DR-2 | **Adopted** (D-R1) | 7.3 race outcomes, 10.6 Concurrency row |
| DR-5 | medium | must-fix | API / errors | Constraint-name translation is ambiguous for `ck_book_active_loans` (and other shared constraints) | **Adopted** (D-R2) | 4, 6.2.1 (new table), 7.2, 10.2, 10.6, ADR-25 |
| DR-6 | medium | must-fix | API / errors | Errors raised outside DispatcherServlet bypass `GlobalExceptionHandler` (violates AC-X.1 "every non-2xx") | **Adopted** (D-R2) | 2.1 F-1 (Boot >= 3.2), 4, 6.2, 10.2, 10.4, 10.6, 12.2, 13 R-10, ADR-26 |
| DR-7 | medium | user decision | Security | No Host/Origin validation: DNS-rebinding bypasses the D7 header mitigation | **Adopted** (D-R3) | 2.2 D7 note, 4, 6 intro, 6.2 `FORBIDDEN_ORIGIN`, 10.1, 10.4, 13 R-11/R-12, ADR-27 |
| DR-8 | medium | must-fix | Performance | N+1 queries on active/overdue loan lists (threatens NFR-1) | **Adopted** (D-R4) | 5.2, 6.3 rows + notes, 10.6, 12.1, 12.3, ADR-29 |
| DR-9 | medium | must-fix | Testability | Test contexts share one named in-memory DB; `@DataJpaTest` replaces the datasource | **Adopted** (D-R4) | 5.4, 10.4, 10.6 (test isolation), ADR-30 |
| DR-10 | medium | must-fix | Frontend / AC-X.3 | "Backend unreachable" through the Vite proxy is an HTTP 5xx with a non-JSON body, not a network error | **Adopted** (D-R4) | 9.1 `http.ts`, 9.3, 10.6, 12.2, ADR-31 |
| DR-11 | medium | must-fix | Accessibility (NFR-3) | Missing page titles, route-change and post-action focus management, visible focus, results announcements, contrast check not automatable in jsdom | **Adopted** (D-R4) | 9.1, 9.2 page titles, 9.4, 10.6, 12.3 |
| DR-12 | low | nice-to-have | Concurrency (docs) | Book delete locks book, then deletes loan rows: an undocumented exception to the global lock order | **Adopted** (D-R6) | 4, 7.3 (lock table + exception paragraph) |
| DR-13 | low | nice-to-have | API | `Location: /api/loans/{id}` points to an endpoint that does not exist | **Adopted** (D-R5) | 6.3 new `GET /api/loans/{id}` row + note, 10.6, 12.1, ADR-28 |
| DR-14 | low | nice-to-have | Security / privacy | Query-string logging puts emails in logs, contradicting section 10.1 | **Adopted** (D-R6) | 4, 10.1, 10.3, 10.6 |
| DR-15 | low | nice-to-have | Validation | Trimming must happen before Bean Validation; locale-safe lower-casing | **Adopted** (D-R6) | 5.3, 10.6 |
| DR-16 | low | nice-to-have | Operations | Proxy target `localhost` vs backend bind `127.0.0.1` (IPv6 resolution on Windows) | **Adopted** (D-R6) | 9.1, 10.4 |
| DR-17 | low | nice-to-have | Security | Header filter should cover all unsafe methods; Swagger UI header mechanism unspecified | **Adopted** (D-R6) | 4, 6 intro, 6.2, 10.1, 13 R-6, ADR-27 |
| DR-18 | low | nice-to-have | Input handling | No length bounds on filter/search query parameters | **Adopted** (D-R6) | 6.3 rows + notes, 10.1, 10.6 |
| DR-19 | low | nice-to-have | Frontend | Query retry policy retries 4xx; picker paging behaviour unspecified | **Adopted** (D-R6) | 9.3, 9.4, 10.6, ADR-31 |
| DR-20 | low | nice-to-have | Documentation | Status/ADR inconsistencies in architecture.md | **Adopted** (D-R6) | header status line, 2.3, 11 (ADR-5/11/12/13/20/21 now Accepted), 13 closing note |

## 3. Findings

### DR-1 (high, must-fix) - Unlocked full-row updates overwrite counters and status

- **Resolution:** Adopted (D-R1). Member edit now takes the member lock, `@Version` is on `member` and `book`, and 7.3 states that every write path locks first. `@DynamicUpdate` is recorded as optional. Applied in architecture 4, 5.2, 7.1, 7.3, R-2, ADR-23/24.

- **Location:** section 7.3 lock table (lists "Update book / delete book" and "Deactivate / reactivate member", but not member edit); section 7.1 (no row for member edit); section 4 `MemberService`; section 13 R-2.
- **Problem:** `PUT /api/members/{id}` (`MemberService.update`) loads the member without a lock. By default Hibernate's UPDATE writes **all** mapped columns, including `active_loan_count` and `status`. Example interleaving:
  1. T1 (edit) reads the member with count = 1.
  2. T2 (checkout) locks the member, sets count = 2 and commits.
  3. T1 flushes `SET name=?, email=?, status='ACTIVE', active_loan_count=1`. H2 lets it wait for T2's row lock and then overwrites the row.

  The counter is now 1 while 2 active loans exist. The member can then reach 4 active loans and `ck_member_active_loans` never fires. In the same way, an edit that runs concurrently with a deactivate can revert `status`. This is exactly the "code path bypasses LoanService" risk R-2 warns about, but it is hidden in ORM behaviour, so the single-writer rule does not prevent it. NFR-5 and AC-4.BE.11 require the invariant to hold under concurrent requests.
- **Recommendation:**
  - `MemberService.update` acquires the member row lock, as deactivate does. Add it to the 7.1 and 7.3 tables.
  - Add a JPA `@Version` column to `book` and `member` as a general lost-update backstop (see DR-3).
  - Optionally use `@DynamicUpdate` on `Book` and `Member` so that edits do not rewrite counter columns.
  - State in 7.3 that **every** write path to `book` or `member` takes the row lock first.

### DR-2 (high, must-fix) - Association loading on return defeats the lock (JPA first-level cache)

- **Resolution:** Adopted (D-R1). `Loan.member`/`Loan.book` are LAZY. A new `RowLocks` helper takes locks with `find`/`refresh` and `PESSIMISTIC_WRITE`. The ids are read from the proxies without initialising them, and "no association navigation before the lock" is now a rule and a review item. Applied in architecture 4, 5.2, 7.3, 7.5, R-3, ADR-24.

- **Location:** section 7.3 ("The locking query is the **first** load of that entity in the transaction"); section 7.5 return sequence; section 5.1/4 (`Loan` entity mapping unspecified); R-3.
- **Problem:** JPA's default fetch type for `@ManyToOne` is EAGER. When `returnLoan` locks the loan, Hibernate also loads the loan's `member` and `book`, either in the same statement or in follow-up selects. That load happens **before** `SELECT member FOR UPDATE`. The later locking call finds the instance already managed. Hibernate then only upgrades the lock and keeps the stale state it already holds. Concrete race:
  1. Return T2 locks loan L1 (member M). Hibernate eager-loads M with the last committed count of 1, while checkout T1 already holds M's lock.
  2. T2 blocks on `M FOR UPDATE` until T1 commits count = 2.
  3. T2 then decrements its stale value 1 to 0 and writes 0. The correct value is 1.

  The same race happens on the book counter, which overstates available copies and allows more active loans than physical copies, while `ck_book_active_loans` still passes. So the rule in 7.3 is violated by the return flow the document itself specifies.
- **Recommendation:**
  - Map `Loan.member` and `Loan.book` as `FetchType.LAZY` explicitly.
  - In `returnLoan`, read the ids through `loan.getMember().getId()` (this does not initialise the proxy), then acquire the locks.
  - Acquire locks with `EntityManager.find(..., PESSIMISTIC_WRITE)` or `refresh(entity, PESSIMISTIC_WRITE)`. `refresh` re-reads the row state after the lock is granted.
  - Add "no association navigation before the lock" to the 7.3 rules and the code-review checklist (R-3).

### DR-3 (high, must-fix) - The constraint backstop does not catch stale counter reads

- **Resolution:** Adopted (D-R1), using the `@Version` option. A conflict maps to 409 `CONCURRENT_UPDATE`. The overstated 7.3 sentence is replaced by an "invariant -> primary / backstop" table. Atomic conditional updates remain only as the R-1 fallback. Applied in architecture 5.1, 5.2, 6.2, 7.2, 7.3, 10.2, R-1, R-2, R-13, ADR-23.

- **Location:** section 7.3 last race bullet ("If, despite locking, a stale read ever slipped through, the flush would violate `ck_book_active_loans` / `ck_member_active_loans` ... The invariant therefore holds even in that case"); section 1 goal 1; R-1, R-2.
- **Problem:** Counters change through entity methods (`book.lendCopy()`), so Hibernate writes an **absolute** value (`SET active_loan_count = 2`), not `active_loan_count + 1`. Suppose a stale read happens (DR-1, DR-2, or the H2 behaviour named in R-1). Both transactions compute the same new value. That is a lost update that stays inside the CHECK range, so no constraint fires. Only `uk_loan_member_active_book` is a real backstop. The count-based invariants (limit 3, copies never exceeded) are not protected in the database once the counter has drifted. The design's core guarantee is therefore overstated.
- **Recommendation (within D3; the pessimistic locks and counters stay):**
  - Add `@Version` (BIGINT `version`) to `book` and `member`. A write based on stale state then fails with `OptimisticLockingFailureException`, which maps to 409 `CONCURRENT_UPDATE`. That makes the backstop real at almost no cost.
  - Alternatively, or in addition, perform counter changes as atomic conditional updates, for example `UPDATE book SET active_loan_count = active_loan_count + 1 WHERE id = ? AND active_loan_count < total_copies`, and check the affected row count. R-1 already names this as a fallback.
  - Correct the 7.3 text and R-1/R-2 so they say which mechanism is the backstop for which invariant.

### DR-4 (medium, must-fix) - Concurrency tests miss mixed interleavings

- **Resolution:** Adopted (D-R1). Five mixed scenarios were added, and invariant checks now run after every iteration (counter = active loans, no 500, no `CONCURRENT_UPDATE`). Applied in architecture 7.3 (race outcomes) and the 10.6 Concurrency row.

- **Location:** section 10.6, Concurrency row (scenarios 1-3 only).
- **Problem:** The three planned scenarios (last copy, 4th loan, double return) each race only one kind of operation. The interleavings that corrupt counters (DR-1, DR-2) are never exercised:
  - return plus checkout on the same member
  - return plus checkout on the same book
  - member edit plus checkout
  - book update (total copies) plus checkout
  - deactivate plus checkout

  NFR-5 requires an automated test that verifies the invariants.
- **Recommendation:** Add these mixed scenarios. After every iteration, assert the counter invariant against the loan rows: `active_loan_count = count(loans where return_date is null)` for every book and member. Also assert that no request ended in a 500.

### DR-5 (medium, must-fix) - Constraint-name translation is ambiguous

- **Resolution:** Adopted (D-R2). Translation is now operation-scoped and case-insensitive, with a "constraint x operation -> code" table, and the tests assert every row. Applied in architecture 4, 6.2.1 (new), 7.2, 10.2, 10.6, ADR-25.

- **Location:** section 4 `ConstraintViolationTranslator` ("Maps named DB constraint violations ... to error codes"); section 6.2 (`NO_COPY_AVAILABLE` "also via `ck_book_active_loans`"); section 7.1 (same constraint is also the backstop for AC-1.BE.6).
- **Problem:**
  - `ck_book_active_loans` backs two different rules. On checkout it means `NO_COPY_AVAILABLE` (409). On book update it means `TOTAL_COPIES_BELOW_ACTIVE_LOANS` (400 with `fieldErrors[totalCopies]`). A translator keyed only on the constraint name has to pick one, so one path returns the wrong status and code. This breaks AC-1.BE.6 or AC-4.BE.3 in the backstop case.
  - H2 reports unquoted identifiers in upper case (`CK_BOOK_ACTIVE_LOANS`), so a case-sensitive lookup would fail and fall through to 500.
- **Recommendation:**
  - Translate per operation: each service method catches `DataIntegrityViolationException` around its own flush and maps it using operation context plus constraint name.
  - Compare constraint names case-insensitively.
  - Add an explicit table "constraint x operation -> code" to section 6.2. Have the `@DataJpaTest` constraint tests assert the mapped code.

### DR-6 (medium, must-fix) - Error format not guaranteed outside the MVC layer

- **Resolution:** Adopted (D-R2). Filters write `ApiError` bodies through `ApiErrorWriter`, and a custom `ApiErrorController` renders `/error` with status-to-code mapping (new code `REQUEST_REJECTED`). Whitelabel is disabled, Spring Boot is pinned to >= 3.2, and integration tests cover the filter and `/error` bodies. Applied in architecture 2.1 F-1, 4, 6.2, 10.2, 10.4, 10.6, 12.2, R-10, ADR-26.

- **Location:** section 6.2 ("Every non-2xx response, including framework errors ... uses exactly this body"); section 4 `RequiredHeaderFilter` ("403, standard error JSON"); section 10.2.
- **Problem:** `@RestControllerAdvice` only handles exceptions thrown inside DispatcherServlet handler execution. The following still produce Spring Boot's default `/error` body (`timestamp, status, error, path`), which breaks AC-X.1:
  - responses from servlet filters (`RequiredHeaderFilter`, `RequestLoggingFilter`)
  - container-level rejections (malformed URL or encoding, oversize headers)
  - any exception escaping a filter

  `NoResourceFoundException` for unknown routes also only exists from Spring Boot 3.2 onwards.
- **Recommendation:**
  - `RequiredHeaderFilter` writes the `ApiError` body itself, using the shared `ObjectMapper` and `Clock`.
  - Replace the default error rendering (a custom `ErrorController` or `ErrorAttributes` for `/error`) so container-level errors also produce the standard body with `code` `INTERNAL_ERROR` or a status-specific code.
  - Pin Spring Boot to 3.2 or later in 2.1 F-1.
  - Add an integration test that sends a request without the header and a request to an unknown route, and asserts the exact body shape.

### DR-7 (medium, user decision) - DNS rebinding bypasses the D7 mitigation

- **Resolution:** Adopted (D-R3). `RequiredHeaderFilter` now applies a Host allowlist (localhost/127.0.0.1 on 8080 and 5173) to every request and an Origin check to mutating requests. Both return the new 403 `FORBIDDEN_ORIGIN`. The allowlists are configurable, the Vite proxy uses `changeOrigin: false`, and Vite's host check is kept. Applied in architecture 2.2 D7 note, 4, 6 intro, 6.2, 10.1, 10.4, R-11, R-12, ADR-27.

- **Location:** section 10.1 ("Network exposure", "Cross-site requests"); D7 rationale.
- **Problem:** The `X-Requested-With` check relies on the browser treating a hostile page as cross-origin. With DNS rebinding, an attacker's domain (for example `evil.example:8080`) is re-pointed to `127.0.0.1`. The browser then treats the page as same-origin with the backend. It can send the custom header without a preflight and read every response, including the member names and emails. Binding to 127.0.0.1 does not prevent this, because the browser is the one connecting. The system is meant for local/trusted use (NFR-6), but a malicious website visited on the same machine is an exposure this design otherwise tries to prevent.
- **Recommendation:**
  - In the existing filter, reject any request whose `Host` header is not one of `localhost:8080`, `127.0.0.1:8080`, `localhost:5173` or `127.0.0.1:5173`. The Vite proxy keeps the original Host header by default.
  - For mutating requests, also reject an `Origin` header that is present but not a localhost origin.
  - Keep Vite's built-in host check enabled (`server.allowedHosts` default).

  This is a small, additive change to D7 and does not reopen it. Because it goes beyond what D7 specified, it is listed as a decision (D-R3).

### DR-8 (medium, must-fix) - N+1 queries on loan lists

- **Resolution:** Adopted (D-R4). Loan lists use fetch-join (or DTO projection) queries, with a count query for pages, and the NFR-1 timed test now covers the active and overdue lists. Applied in architecture 5.2, 6.3, 10.6, 12.1, 12.3, ADR-29.

- **Location:** section 6.1 `LoanResponse` (member name, book title/ISBN); section 6.3 `GET /api/loans/active`, `GET /api/loans/overdue` (unpaginated); section 12.3 NFR-1 ("counters avoid per-row count subqueries").
- **Problem:** Building each `LoanResponse` touches `member` and `book`. If the associations are lazy (required by DR-2) or EAGER-with-secondary-select (the JPA default), each row costs up to 2 extra queries. The overdue list is unpaginated (R-8). At the NFR-1 ceiling of 10,000 loans that can mean thousands of queries per request, which puts the 500 ms p95 target at risk. The NFR-1 timed test would find this late.
- **Recommendation:** Use dedicated read queries for loan lists: either JPQL `JOIN FETCH` on member and book (with a separate count query for pages) or a DTO constructor projection. State this in section 5/6.3. Make the NFR-1 timed test cover `GET /api/loans/overdue` with a large overdue set.

### DR-9 (medium, must-fix) - Test contexts share one in-memory database

- **Resolution:** Adopted (D-R4). Tests use `jdbc:h2:mem:${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000`, and `@DataJpaTest` uses `@AutoConfigureTestDatabase(replace = NONE)`. Applied in architecture 5.4, 10.4, 10.6, ADR-30.

- **Location:** section 5.4 / 10.4 (`jdbc:h2:mem:library;DB_CLOSE_DELAY=-1`); section 8 ("tests set it to `false` except the seed test"); section 10.6 (repository tests with `@DataJpaTest`; concurrency tests "clean up afterwards").
- **Problem:**
  - Spring caches test application contexts. Contexts with different properties (seed on or off, test clock) are separate contexts, but they all connect to the same named database `library`, which `DB_CLOSE_DELAY=-1` keeps alive for the JVM. `schema.sql` then runs again against existing tables: it either fails (CREATE without IF NOT EXISTS) or keeps stale rows. Seed data and concurrency test data then leak into other tests' expectations (for example, page totals and AC-2.BE.5 "total count 0").
  - `@DataJpaTest` by default replaces the datasource with a generated embedded one, so it does not use `LOCK_TIMEOUT` or the production URL.
- **Recommendation:**
  - In tests, use a unique database per context (`spring.datasource.generate-unique-name=true`, or a URL containing `${random.uuid}`).
  - Use `@AutoConfigureTestDatabase(replace = NONE)` together with a test URL that keeps `LOCK_TIMEOUT`.
  - Document this in 10.6.

### DR-10 (medium, must-fix) - Backend-down behaviour behind the proxy (AC-X.3)

- **Resolution:** Adopted (D-R4). `http.ts` maps any non-2xx without a valid `ApiError` JSON body, and any network failure, to `ApiError(BACKEND_UNAVAILABLE)` with a friendly message and Retry. MSW tests cover a 500 with a text body and a 502 with an empty body. Applied in architecture 9.1, 9.3, 10.6, 12.2, ADR-31.

- **Location:** section 9.1 `http.ts` ("network failure -> ApiError(NETWORK_ERROR)"); section 9.3; section 12.2 AC-X.3.
- **Problem:** The browser only talks to the Vite dev server (D7). When the backend is stopped, `fetch` does **not** reject. The proxy answers with an HTTP error (typically 500 or 502 with an empty or plain-text body). The design only maps network failures to the friendly state. A non-JSON error body could leave the JSON parse failing and the UI in an undefined state, which is the "raw error / blank screen" AC-X.3 forbids.
- **Recommendation:** Specify that `http.ts` treats any non-2xx response whose body is not valid `ApiError` JSON as `ApiError(BACKEND_UNAVAILABLE or UNEXPECTED)`, with a friendly message and Retry. Add an MSW test for "500 with text body" and one for "502 empty body".

### DR-11 (medium, must-fix) - Accessibility gaps against WCAG 2.1 AA (NFR-3)

- **Resolution:** Adopted (D-R4). The design now has:
  - a `document.title` per route
  - focus moved to the `<h1>` on route change and to the notice or heading after a row is removed
  - a `:focus-visible` outline with at least 3:1 contrast
  - result counts in `role="status"`
  - `contrast.test.ts` over the colour tokens, plus a manual contrast check in Stage 7

  Applied in architecture 9.1, 9.2, 9.4, 10.6, 12.3.

- **Location:** section 9.4; section 9.2; section 10.6 ("vitest-axe ... no critical violations").
- **Problem:** Several AA (and A) criteria the design does not address:
  - **2.4.2 Page Titled (A):** there is no per-route `document.title`.
  - **2.4.3 Focus Order:** the SPA does not move focus or announce the page after client-side navigation. After a delete or a return removes a row, focus falls back to `<body>`.
  - **2.4.7 Focus Visible:** there is no focus-indicator requirement in the styles.
  - **4.1.3 Status Messages:** the result count after a debounced search or filter (AC-2.FE.2) and the picker result updates are not placed in a live region.
  - **1.4.3 Contrast:** axe's colour-contrast rule does not run in jsdom, so vitest-axe cannot verify the 4.5:1 measure in NFR-3.
- **Recommendation:** Extend 9.4 with:
  - a title per route ("Books - Library")
  - focus moves to the page `<h1>` on route change
  - after a row is removed, focus moves to the success notice or the table heading
  - a visible `:focus-visible` outline with at least 3:1 contrast
  - the match count rendered in `role="status"`
  - a small unit test that computes contrast ratios for the colour-token pairs in `global.css`, or a documented manual contrast check in Stage 7

### DR-12 (low) - Book delete is an undocumented lock-order exception

- **Resolution:** Adopted (D-R6). Book delete is documented as the explicit exception, using one bulk delete of returned loans and a "no cycle" argument. Applied in architecture 4 and 7.3.

- **Location:** section 7.3 ("Every write path acquires row locks ... in this order and never in reverse"; delete book: "book"); section 4 `BookService` (deletes returned loans).
- **Problem:** `BookService.delete` locks the book and then deletes that book's loan rows, which takes loan row locks after the book lock. That is the reverse of loan -> member -> book. It cannot deadlock in practice: only returned loans are deleted, and a return of an already-returned loan takes no book lock. But the statement in 7.3 is not true as written.
- **Recommendation:** Document delete-book as an explicit exception with that reasoning. Alternatively, delete the returned loans with one bulk `DELETE FROM loan WHERE book_id = ? AND return_date IS NOT NULL` after the zero-active-loans check, and explain why no cycle can form.

### DR-13 (low) - `Location` header points to a missing endpoint

- **Resolution:** Adopted (D-R5). `GET /api/loans/{id}` was added, mapped as `{id:\d+}` so it does not collide with `/active` and `/overdue`, with tests and traceability. Applied in architecture 6.3, 10.6, 12.1, ADR-28.

- **Location:** section 6.3 `POST /api/loans` ("+ `Location: /api/loans/{id}`"); there is no `GET /api/loans/{id}` row.
- **Problem:** The `Location` header points to a resource the API does not serve (404).
- **Recommendation:** Add `GET /api/loans/{id}` (returns a `LoanResponse`; useful for tests) or drop the `Location` header for loans.

### DR-14 (low) - Emails can end up in logs

- **Resolution:** Adopted (D-R6). Only query-parameter names are logged, never their values. Applied in architecture 4, 10.1, 10.3, 10.6.

- **Location:** section 10.3 ("method, path, query string"); section 10.1 Personal data ("emails stay out of logs").
- **Problem:** The `search` parameter of `GET /api/members` (D9) commonly contains an email address, and the request log records the query string. The two sections contradict each other.
- **Recommendation:** Log the path plus parameter names only, or redact the value of `search`.

### DR-15 (low) - Trim before validation; locale-safe lower-casing

- **Resolution:** Adopted (D-R6). Values are trimmed in the request record's compact constructor, and lower-casing uses `toLowerCase(Locale.ROOT)`. Applied in architecture 5.3 and 10.6.

- **Location:** section 5.3 ("length limits apply after trimming", where: "request DTO binding / service"); `email_normalized = lower(email)`.
- **Problem:**
  - If trimming happens in the service, Bean Validation has already checked the untrimmed value, so the "after trimming" limits in 5.3 are not what gets enforced (for example, a 255-character title plus surrounding spaces would be rejected).
  - Java's `toLowerCase()` without a locale is wrong under some default locales (the Turkish dotted/dotless I), which would break case-insensitive uniqueness.
- **Recommendation:** Trim in the request record's compact constructor (before validation). Use `toLowerCase(Locale.ROOT)`. State both in 5.3.

### DR-16 (low) - Proxy target vs bind address

- **Resolution:** Adopted (D-R6). The proxy target defaults to `http://127.0.0.1:8080` (with `changeOrigin: false`). Applied in architecture 9.1 and 10.4.

- **Location:** section 10.4 (proxy target `http://localhost:8080`; backend `server.address=127.0.0.1`).
- **Problem:** On Windows (the current platform), `localhost` can resolve to `::1` first. The backend listens only on IPv4, so the proxy can fail with ECONNREFUSED, depending on Node's address-family selection.
- **Recommendation:** Default the proxy target to `http://127.0.0.1:8080`.

### DR-17 (low) - Header filter scope and Swagger UI

- **Resolution:** Adopted (D-R6). The header is required on every method except GET/HEAD/OPTIONS, and an `OpenApiCustomizer` declares it as a required header parameter for Swagger UI. Applied in architecture 4, 6 intro, 6.2, 10.1, R-6, ADR-27.

- **Location:** section 4 `RequiredHeaderFilter` ("mutating requests"); section 6 ("POST/PUT/DELETE"); R-6 ("Swagger UI configured to send it").
- **Problem:** The filter is defined by an enumerated list of methods, so PATCH or any future method would be unprotected. The design also does not say how Swagger UI would send the header.
- **Recommendation:** Require the header on every method except GET, HEAD and OPTIONS. Declare `X-Requested-With` as a global required header parameter (default `library-ui`) through an `OpenApiCustomizer`, so Swagger UI includes it.

### DR-18 (low) - Unbounded query parameters

- **Resolution:** Adopted (D-R6). Text filters are limited to 255 characters (`search` to 254), `memberId`/`bookId` must be >= 1, and violations return 400 `VALIDATION_FAILED` with a field error. Applied in architecture 6.3, 10.1, 10.6.

- **Location:** section 6.3 `GET /api/books` (`title`, `author`, `isbn`), `GET /api/members` (`search`), `GET /api/loans/active` (`memberId`, `bookId`).
- **Problem:** The filter strings have no length limit, and the ids are not stated to be positive. Long strings make the `LIKE` scan and the log lines bigger. Neither is serious at local scope.
- **Recommendation:** Limit text filters to 255 characters (`search` to 254) and require ids to be >= 1. Return 400 with a field error otherwise.

### DR-19 (low) - Frontend retry and picker paging

- **Resolution:** Adopted (D-R6). Queries are retried only on 5xx or `BACKEND_UNAVAILABLE`, and pickers show "Showing 20 of N - refine your search". Applied in architecture 9.3, 9.4, 10.6, ADR-31.

- **Location:** section 9.3 ("Default retry for queries: 1 automatic retry"); section 9.4 pickers.
- **Problem:**
  - Retrying 4xx responses (for example, a 404 on the edit page of a deleted book) only delays the error.
  - The picker shows the results of a paginated API (20 per page), but the design does not say what happens when there are more matches than fit.
- **Recommendation:** Retry only on network or 5xx errors. In the pickers, show the first page with "Showing 20 of N, refine your search".

### DR-20 (low) - Document consistency

- **Resolution:** Adopted (D-R6). The changes:
  - The status line now reads "approved 2026-11-25; revised after design review ..., pending Stage 3 approval". The "Approved by user: yes (2026-11-25)" field is kept.
  - The "still pending" sentence in 2.3 is replaced.
  - ADR-5, 11, 12, 13, 20 and 21 are marked Accepted.

  Applied in the architecture header, 2.3, 11 and the 13 closing note.

- **Location:** architecture header ("Status: draft - awaiting user approval" next to "Approved by user: yes"); section 2.3 ("The approval gate for this architecture document as a whole is still pending"); section 11, ADR-5, 11, 12, 13, 20, 21 marked "Proposed".
- **Problem:** The document's status markers contradict its approval.
- **Recommendation:** When the approved changes are applied, update the status line, remove the "still pending" sentence and mark those ADRs Accepted.

## 4. Checklist results (areas with no findings)

- **Coverage / traceability:** All FR-1..FR-24, all AC-n.BE.m and AC-n.FE.m, AC-X.1..4 and NFR-1..13 are mapped (architecture 6.3, 9.2, 12). I found no traceability holes. The D9 `search` extension is traced to AC-4.FE.1.
- **Data model:** Constraints match the rules. `uk_loan_member_active_book` with nullable `active_book_id` correctly relies on H2 allowing multiple NULLs. `ck_member_inactive_no_loans` is a valid backstop for AC-3.BE.6. The seed loans are consistent with the 14-day rule and the overdue boundary.
- **Deadlock freedom (checkout / return / update / deactivate):** Correct for the listed paths. Checkout and return both take the member lock before the book lock. DR-12 is a documentation gap, not a deadlock.
- **Security (STRIDE-lite):** Spoofing and elevation are accepted risks (C-4, R-11). XSS, SQL injection (with LIKE escaping), H2 console, actuator exposure and stack-trace leakage are all addressed. Repudiation/audit is out of scope (requirements section 10). The open security items are DR-7, DR-14 and DR-17.
- **Technology fit:** Consistent with C-1..C-8. springdoc/Boot version pinning is already tracked in R-10. With DR-6, pin Spring Boot to 3.2 or later.
- **Operations:** Configuration, health and README coverage are adequate for the local-only scope.

## 5. Proposed changes needing user approval

Pass 1 proposed D-R1..D-R6 in its handoff. On 2026-11-25 the user approved all of them, with these specific choices:
- **D-R1** (DR-1..DR-4): `@Version` on `book` and `member` as the lost-update backstop, with conflicts -> 409 `CONCURRENT_UPDATE`. Member row lock on edit. LAZY `Loan` associations, with locks taken via `find`/`refresh` and `PESSIMISTIC_WRITE` before state is read. Mixed-interleaving concurrency tests with invariant checks.
- **D-R2** (DR-5, DR-6): operation-scoped, case-insensitive constraint translation with a "constraint x operation -> code" table. Filter-written `ApiError` bodies and a custom `/error` rendering. Spring Boot >= 3.2.
- **D-R3** (DR-7): Host allowlist (localhost/127.0.0.1 on 8080 and 5173) and an Origin check for mutating requests, both in `RequiredHeaderFilter`.
- **D-R4**: DR-8, DR-9, DR-10, DR-11.
- **D-R5** (DR-13): add `GET /api/loans/{id}`.
- **D-R6**: DR-12, DR-14..DR-20.

No decisions remain open.

## 6. Changes applied

All of the following edits were made to `.claude/docs/architecture.md` on 2026-11-25, strictly within the approved scope. Changed text is marked **[DR-n]** in the document. The user's earlier decisions D1-D9 and Q-A1 are unchanged. Their option tables are kept as the historical record, with refinement notes added under D3 and D7.

| # | Section(s) | Change | Findings |
|---|---|---|---|
| 1 | Header, conventions | Status line set to "approved 2026-11-25; revised after design review ..., pending Stage 3 approval"; the "Approved by user: yes (2026-11-25)" field is kept; the `[DR-n]` marker convention is added | DR-20 |
| 2 | 2.1 F-1 | Spring Boot pinned to >= 3.2 | DR-6 |
| 3 | 2.2 D3, D7 | Refinement notes (`@Version`, lock-first, LAZY; Host/Origin, all unsafe methods). The decisions themselves are unchanged. | DR-1..DR-4, DR-7, DR-17 |
| 4 | 2.3 | "Approval gate still pending" sentence replaced | DR-20 |
| 5 | 4 | New `ApiErrorWriter`, `ApiErrorController`, `RowLocks` (`common/persistence`); `ConstraintViolationTranslator` made operation-scoped; `RequiredHeaderFilter` extended (Host, Origin, unsafe methods, writes `ApiError`); `RequestLoggingFilter` logs parameter names only; `MemberService` and `BookService` lock first; `OpenApiCustomizer`; layering rule "lock before write" | DR-1, DR-2, DR-5, DR-6, DR-7, DR-12, DR-14, DR-17 |
| 6 | 5.1, 5.2 | `version BIGINT` on `book` and `member`; CHECK-only limitation stated; `Loan` associations explicitly LAZY; counter-invariant text extended | DR-1, DR-2, DR-3, DR-8 |
| 7 | 5.3 | Trim in the record compact constructor before Bean Validation; `toLowerCase(Locale.ROOT)` | DR-15 |
| 8 | 5.4 | Unique test DB per context (pointer to 10.6) | DR-9 |
| 9 | 6 intro, 6.2 | Header required on every method except GET/HEAD/OPTIONS; Host/Origin rule; the three error-body producers; new codes 403 `FORBIDDEN_ORIGIN` and other-4xx `REQUEST_REJECTED`; `CONCURRENT_UPDATE` extended to version conflicts; `NOT_FOUND` tied to Boot >= 3.2 | DR-3, DR-6, DR-7, DR-17 |
| 10 | 6.2.1 (new) | "Constraint x operation -> code" table, case-insensitive matching, per-service flush translation | DR-5 |
| 11 | 6.3 | New `GET /api/loans/{id}` (`{id:\d+}`); query-parameter bounds (255/254, ids >= 1); fetch-join loan list queries | DR-8, DR-13, DR-18 |
| 12 | 7.1, 7.2 | Member-edit row; `version` backstops; "no lost update" row; optimistic-lock mapping; per-operation translation | DR-1, DR-3, DR-5 |
| 13 | 7.3 | Rewritten within D3: lock-first rule for every write path; member edit and delete-book rows; `RowLocks` and the LAZY / no-navigation rules; `@Version` backstop; invariant -> mechanism table; delete-book exception with the no-cycle argument; mixed race outcomes. The incorrect "CHECKs catch stale reads" sentence was removed. | DR-1, DR-2, DR-3, DR-4, DR-12 |
| 14 | 7.4, 7.5 | Sequences show the version-conditional UPDATEs and the lock-then-read return flow | DR-2, DR-3 |
| 15 | 9.1-9.4 | Proxy target `127.0.0.1`; `http.ts` `BACKEND_UNAVAILABLE`; `usePageTitle`, `useRouteFocus`, `contrast.test.ts`; page-title list; retry policy; picker "Showing 20 of N"; focus, visible-focus, live-region and contrast rules | DR-10, DR-11, DR-16, DR-19 |
| 16 | 10.1 | Vite host check kept; new DNS-rebinding row; header on all unsafe methods + Swagger `OpenApiCustomizer`; no parameter values in logs; query bounds | DR-7, DR-14, DR-17, DR-18 |
| 17 | 10.2 | Handler list incl. `OptimisticLockingFailureException`; outside-MVC producers; `/error` status -> code mapping | DR-3, DR-5, DR-6 |
| 18 | 10.3 | Log line uses parameter names only | DR-14 |
| 19 | 10.4 | `server.error.whitelabel.enabled=false`; `library.security.allowed-hosts` / `allowed-origins`; proxy target and `changeOrigin: false`; test DB note | DR-6, DR-7, DR-9, DR-16 |
| 20 | 10.6 | Repository tests with `replace = NONE` and a unique DB; the constraint x operation assertions; API tests for every error producer, the new endpoint, bounds, version conflict, trimming/locale, log redaction; 5 mixed concurrency scenarios + per-iteration invariants; frontend tests for `BACKEND_UNAVAILABLE`, retry, picker paging, titles/focus/live regions; contrast unit test + manual Stage 7 check; test-isolation paragraph; NFR-1 test incl. loan lists | DR-3..DR-11, DR-13..DR-15, DR-17..DR-19 |
| 21 | 11 | ADR-5/11/12/13/20/21 marked Accepted; new ADR-23..ADR-31 | DR-1..DR-10, DR-13, DR-17, DR-19, DR-20 |
| 22 | 12.1-12.3 | Traceability updated (FR-12, FR-15, FR-19..FR-22, AC-X.1, AC-X.3, NFR-1, NFR-3, NFR-5, NFR-6, NFR-11) | all |
| 23 | 13 | R-1, R-2, R-3, R-6, R-10, R-11 updated; new R-12 (allowlist vs ports) and R-13 (generic `CONCURRENT_UPDATE` message); closing note records the design-review revision | DR-1..DR-3, DR-6, DR-7, DR-17, DR-20 |

Choices made while applying, within the approved scope:
- The new 403 code for Host/Origin rejection is named `FORBIDDEN_ORIGIN`.
- The other-4xx fallback for `/error` is `REQUEST_REJECTED`.
- The frontend code for "backend unavailable" is `BACKEND_UNAVAILABLE`, which replaces the former `NETWORK_ERROR`.
- `@DynamicUpdate` is recorded as optional, as DR-1 proposed.
- The concurrency tests treat any `CONCURRENT_UPDATE` as a failure, so the backstop cannot mask a locking gap.

These are naming and detail choices that follow from the approved recommendations. If the user prefers other names, they can be changed at the Stage 3 gate.
