# Code Review

**Status:** approved by user 2026-11-25 with no fixes applied (findings CR-1..CR-12 remain open as known issues)
**Owner:** `code-review` agent | **Approved by user:** yes (2026-11-25)
**Inputs:** `backend/`, `frontend/`, `shared-test-vectors/`, root `README.md`; `.claude/docs/requirements.md`, `.claude/docs/architecture.md` (incl. [DR-n]), `.claude/docs/impl-plan.md`
**Review type:** single round, fast-track delivery (correctness and security first)

Severity scale used (as requested by the Orchestrator): critical / high / medium / low. Mapping to the skill rubric: critical = blocker, high = major, medium = minor with visible impact, low = minor or nit.
"Minor fix" = safe to apply without user approval. "Significant fix" = changes behaviour or design and needs user approval.

Out of scope by the user's fast-track decision (not reported as defects): T-22 full concurrency matrix, T-23 NFR-1 timed test, T-24 frontend accessibility sweep, repeat or flakiness runs, exhaustive test matrices. Accepted and also not reported: Tomcat HTML 400 for unparseable request lines, JVM-locale dependence of H2 `lower()`, tightened `ck_loan_active_key`, focus-ring offset, vitest 3.2.7 dev-only advisories, no authentication.

## 1. Summary and verdict

**Verdict: approve with changes.** No critical or high finding. The business rules, the locking design and the security filter are implemented as the approved architecture describes, and the tests check behaviour rather than just running code. The findings are one medium documentation gap (the OpenAPI document does not describe the error format) and a set of low items: README accuracy, a fallback log path that can write personal data, small error-handling and logging details, duplicate field errors, and frontend cache and notice handling. Two fixes change behaviour and need a user decision (CR-6, CR-9). All other fixes are minor.

Strengths:
- **Loan rules (AC-4.BE.1..10).** `LoanService.checkout` locks the member, then the book, through `RowLocks`. Only then does it check the rules, in the documented order: inactive, duplicate, limit, copy. The due date is `LocalDate.now(clock) + 14` (`LoanPolicy`). `returnLoan` locks the loan, reads the ids from the lazy proxies without initialising them, then locks the member and then the book. "Overdue" means strictly before today (`findOverdue`: `l.dueDate < :today`; `LoanResponse.of` uses `isBefore`). No `now()` call bypasses the injected `Clock` (grep checked).
- **Counters.** They change only through `lendCopy`/`returnCopy`/`startLoan`/`endLoan`, called only from `LoanService` and `DemoDataSeeder` (grep checked).
- **Lock-first on every write path.** Book update and delete, member edit, deactivate and reactivate all lock first. `@Version` is on `Book` and `Member`, and `Loan` associations are LAZY. Book delete locks the book, checks for active loans, then bulk-deletes the returned loans (DR-12).
- **Database backstops.** The schema has named CHECK and UNIQUE constraints, and `ConstraintViolationTranslator` maps them per operation. Its table matches architecture 6.2.1 row for row.
- **RequiredHeaderFilter.** It checks Host first (every method), then Origin (writes only), then the header (writes only). It treats every method except GET/HEAD/OPTIONS as a write and compares values case-insensitively. It is registered after the logging filter and applies to every path, including actuator and springdoc. I found no bypass.
- **Error format.** All three error producers (`GlobalExceptionHandler`, `ApiErrorWriter`, `ApiErrorController`) build their messages themselves, so no stack trace, SQL or constraint name reaches a client. Query-parameter values and request bodies are never logged. `SqlExceptionHelper` logging is off.
- **Configuration and input safety.** H2 console is disabled, actuator exposes `health` only (no details), and there is no CORS configuration. LIKE wildcards are escaped and all values are bind parameters. Jackson typing is strict.
- **Frontend.** `dangerouslySetInnerHTML` is banned by lint, and an XSS test exists. `http.ts` turns any non-ApiError body or network failure into `BACKEND_UNAVAILABLE`, sends the header on every non-safe method, and passes `AbortError` through. Queries retry once, only for 5xx or unavailable; mutations never retry. Server `fieldErrors` are shown inline and focus moves to the first one. Submit buttons are disabled while pending, filters and page live in the URL, and dates are parsed as local dates.
- **Tests.** They assert status, error code, field names and the database state after each call (for example the counters after a rejected checkout). Every AC-n.BE.m and AC-n.FE.m ID appears in at least one test name (grep checked).

## 2. Findings

| ID | Severity | Category | Location (file:line) | Finding | Suggested fix | Significant change? |
|---|---|---|---|---|---|---|
| CR-1 | medium | docs / conformance (NFR-13, D6, T-25) | `backend/src/main/java/com/library/config/OpenApiConfig.java:23-37`; `backend/src/test/java/com/library/config/OpenApiDocsTest.java:26` | The generated OpenAPI document adds the info block and the `X-Requested-With` parameter, but declares no error responses and no `ApiError` schema. No controller has `@ApiResponse` or `@Schema` (grep found none in `src/main`). So the "machine-readable source of truth" (D6) does not describe the error format or the 400/403/404/409/500 codes that NFR-13 requires. The README covers the body shape but not the code list. T-25's DoD asked the test to check "all endpoints in 6.3" and "the error schema is defined". `OpenApiDocsTest` only checks the loan endpoints and the header. | In the existing `OpenApiCustomizer`, register an `ApiError`/`FieldErrorDto` schema and add the default 400/403/404/409/500 responses (with the code list in the descriptions) to every operation, or add `@ApiResponse` annotations. Extend `OpenApiDocsTest` to assert the book and member paths and the error schema. | minor (documentation only, no runtime behaviour change) |
| CR-2 | low | docs / honesty | `README.md:66`, `README.md:41`, `README.md:19-26`, `README.md:34-37` | Three README statements are inaccurate. (a) Line 66 says `fieldErrors` is "filled for validation errors, empty otherwise". That is false: 409 `ISBN_ALREADY_EXISTS` and `EMAIL_ALREADY_EXISTS` carry `fieldErrors[isbn]` / `[email]` (`BookService.java:126-129`, `MemberService.java:127-130`, architecture 6.2). (b) Line 41 says the header is required on "POST, PUT, DELETE". The filter requires it on every method except GET/HEAD/OPTIONS, PATCH included (`RequiredHeaderFilter.java:36,79-81`). (c) The Run and Tests blocks chain `cd backend` and then `cd frontend` as if in one shell. Run in sequence, the second `cd` fails (the cwd is already `backend/`), and `mvn spring-boot:run` blocks the shell anyway. | (a) "filled for validation errors and for duplicate ISBN/email conflicts". (b) "every write request (any method except GET, HEAD, OPTIONS)". (c) Say "from the repository root, in two terminals" and use `cd ../frontend`, or separate the blocks. | minor |
| CR-3 | low | docs / conformance (T-25) | `README.md` (whole file) | Several README items that T-25 lists are missing. The Vite proxy is mentioned only in passing: there is no word on why it exists (D7) and the `VITE_API_PROXY_TARGET` override is not documented (`frontend/vite.config.ts:14`). Also missing: the error code list, the lock-order/concurrency summary, troubleshooting (IPv6 vs `127.0.0.1`, 403 after a port change, beyond one sentence) and the manual Stage 7 checklist (keyboard, contrast, screen reader). The checklist is partly tied to T-24, which was deferred. | Add short sections: proxy and override variable, error codes, concurrency in one paragraph, troubleshooting, manual verification checklist. | minor |
| CR-4 | low | security / privacy (DR-14, NFR-6) | `backend/src/main/java/com/library/common/error/GlobalExceptionHandler.java:188-191` (also `:194-197`) | The fallback for an untranslated `DataIntegrityViolationException` logs the whole exception (`log.error("...", ex)`). The H2 message in the cause chain contains the violating row values (titles, names, emails). The code avoids exactly this elsewhere: `ConstraintViolationTranslator.java:60` attaches no cause on purpose, and `application.yml:36-40` turns `SqlExceptionHelper` off. The path is reachable only when a violation escapes a service's own translation (for example at commit), so the impact is low. | Log the operation-independent constraint name (`ConstraintViolationTranslator.constraintName(ex)`) and the exception class, without the message or stack of the JDBC cause. Optionally do the same in `handleUnexpected` for `DataAccessException`. | minor |
| CR-5 | low | error handling / observability (NFR-9) | `backend/src/main/java/com/library/common/web/RequestLoggingFilter.java:44-48` | The access line logs `response.getStatus()` in `finally`. An exception thrown downstream that escapes the chain (from a filter; MVC exceptions are all handled) has not yet been turned into a 500 by the container at that point, so the line says `status=200` while the client gets 500 through `/error`. The error dispatch itself is not filtered again (`OncePerRequestFilter` default), so no corrected line follows. Needs confirmation with a test. The reasoning comes from the servlet flow; I did not observe it. | Catch `ServletException \| IOException \| RuntimeException` around `chain.doFilter`, record status 500 for the log line and rethrow. Add one test. | minor |
| CR-6 | low | error handling | `backend/src/main/java/com/library/common/error/ApiErrorController.java:32-47, 49-55` | A direct request to `/error` (no error-dispatch attributes) gets 500 `INTERNAL_ERROR` and is logged at ERROR. This is deliberate and tested (`ApiErrorControllerTest.java:79-81`). But any local client can now create ERROR log entries and a "server failure" response for a request that is really just an unknown route. | When `RequestDispatcher.ERROR_STATUS_CODE` is absent, answer 404 `NOT_FOUND` and log at DEBUG, and update the test. Alternatively keep the current behaviour and record it as accepted. | **significant** (changes a tested, documented response) |
| CR-7 | low | correctness / validation quality | `backend/src/main/java/com/library/common/validation/IsbnValidator.java:22-25`; `backend/src/main/java/com/library/common/validation/LibraryEmailValidator.java:13, 35-38`; `backend/src/main/java/com/library/member/dto/MemberRequest.java:18-21`; `backend/src/main/java/com/library/book/dto/BookRequest.java:26-28` | Both custom validators reject the empty string, and the email validator also rejects more than 254 characters. Together with `@NotBlank` and `@Size(max = 254)`, a blank ISBN or email (after trimming) therefore yields **two** `fieldErrors` on the same field, and an over-long email yields two as well. After `ApiError` sorts them, the UI's last `setError` wins, so a blank ISBN would be shown as "ISBN must be a valid ISBN-10 or ISBN-13." instead of "ISBN is required." (only if client checks are bypassed). The tests use `contains(...)`, so they do not notice. | Make `@Isbn` and `@LibraryEmail` return true for blank input (leave it to `@NotBlank`), and let the email annotation skip the length check (leave it to `@Size`). The programmatic `isValidEmail` can keep its full rule for the shared vectors. Add one assertion of "exactly one error per field". | minor (message-level only) |
| CR-8 | low | frontend / stale cache | `frontend/src/features/books/useBookQueries.ts:77-85`; `frontend/src/features/members/useMemberQueries.ts:64-72` | Book update invalidates only `books`, and member update only `members`. `LoanResponse` rows in the `activeLoans` and `overdueLoans` caches embed the book title/ISBN and the member name. After an edit, the Overdue page and the active-loan list first render the old cached text and correct it only after the refetch on mount (`keepPreviousData` on Overdue makes the stale flash visible). This matches architecture 9.3, so it is low. | Also invalidate `queryKeys.activeLoansRoot` and `queryKeys.overdueLoans` on book update and member update. | minor |
| CR-9 | low | frontend / UX, a11y | `frontend/src/components/NotificationProvider.tsx:54-73`; `frontend/src/app/AppLayout.tsx:52` | The single notice lives above the routes and "stays until it is dismissed or replaced". An error notice (for example "This member cannot be deactivated...") or "Book checked out." therefore stays visible after navigating to another screen, where it is out of context. | Dismiss the notice on the second pathname change after it was raised. That keeps the create/edit flows, which notify and then navigate, working. Alternatively store the pathname with the notice and hide it on other routes. | **significant** (changes UI behaviour that tests rely on) |
| CR-10 | low | correctness / validation drift (R-4) | `frontend/src/lib/validators.ts:67-72`; `backend/src/main/java/com/library/common/validation/TextNormalizer.java:25-27` | The two sides trim differently. The client uses JS `trim()`, which removes NBSP (U+00A0) and BOM (U+FEFF). The server uses `String.strip()` plus `@NotBlank`, which keep NBSP. So a title, author or name consisting only of NBSP is blocked in the UI but accepted by the API, and lengths at the 255/100 boundary can differ by those characters. The server stays authoritative, so the impact is small. | Either strip NBSP/BOM on the server too (`TextNormalizer.trim`) or accept the drift and add an NBSP vector to `shared-test-vectors` that documents the chosen behaviour. | minor |
| CR-11 | low | conformance | `backend/src/main/java/com/library/loan/LoanController.java:66` | `GET /api/loans/{id}` is mapped as `/{id}`, not `/{id:\d+}` as architecture 6.3 (DR-13) says. The behaviour is equivalent: literal `/active` and `/overdue` win, and a non-numeric id gives 400 `VALIDATION_FAILED` (tested in `LoanCheckoutApiTest.java:299`). | Add the regex, or align the architecture note. | minor |
| CR-12 | low | quality (dead code) | `frontend/src/lib/validators.ts:10` | `LIMITS.isbnMaxInput` (40) is not used anywhere: the ISBN form input has no `maxLength`, and `BookRequest.isbn` has no `@Size`. It is harmless because the server normalises and validates, but the constant is misleading. | Remove it, or apply it as `maxLength` on the ISBN input. | minor |

No finding concerns: the 3-loan limit, the +14 days due date, one active loan per member per book, the inactive-member rule, counter consistency, overdue strictly before today, lock order, `@Version`, LAZY associations, the delete-book or member-edit/deactivate paths, SQL/LIKE injection, XSS, CORS, actuator/H2 console exposure, or secrets. I checked each of these in the code and found no defect.

## 3. Security review

| Area | Result | Evidence |
|---|---|---|
| Host / Origin / header filter | OK | Host is checked on every request, with no Host header rejected (`RequiredHeaderFilter.java:57-60, 75-77`). Origin is checked on writes only when present; `Origin: null` is rejected because `null` is not on the allowlist. The header value is matched exactly (`:61-70`). "Mutating" is every method except GET/HEAD/OPTIONS (`:36, 79-81`). The filter is registered for all paths after the logging filter (`WebFilterConfig.java`). OPTIONS preflights pass the filter, but no CORS configuration exists, so cross-origin scripts cannot read responses or send the custom header. Error dispatches are not filtered again; they were already checked. No bypass found. |
| DNS rebinding | OK | The Host allowlist blocks reads as well as writes. The Vite proxy uses `changeOrigin: false` (`vite.config.ts:14-17`), so the browser's Host reaches the check. |
| Input validation | OK (see CR-7, CR-10) | Bean Validation on records that are trimmed before validation. Strict Jackson coercion, including number/boolean to string (`JacksonConfig`). Explicit page/size and filter bounds (`QueryParams`, `PageRequestParams`). Path ids are typed `Long`. |
| SQL / LIKE | OK | Criteria API and JPQL with bind parameters only. `LikePatterns.contains` escapes `%`, `_` and `\`, and both specifications pass `ESCAPE`. No native or concatenated SQL in `src/main` (grep). |
| XSS | OK | No `dangerouslySetInnerHTML` (lint rule in `eslint.config.js:23-29`, grep found none), no `innerHTML`/`eval`. All user text is rendered as JSX text. `BooksPage.xss.test.tsx` exists. |
| Error leakage | OK | Messages are built by the handlers. `include-stacktrace/message: never`, whitelabel disabled. `ApiErrorController` returns only the default text. |
| PII in logs | mostly OK (CR-4) | Query values and bodies are never logged (`RequestLoggingFilter`). Filter rejections do not log header values. Services log ids only. `SqlExceptionHelper` is OFF. One fallback logs a full exception (CR-4). README warns against DEBUG for `org.springframework.web`. |
| Exposure | OK | Bound to `127.0.0.1`. Actuator exposes `health` only, with `show-details: never`. H2 console disabled. Swagger and `/v3/api-docs` are behind the same Host check. |
| Secrets | none | No credentials, tokens or keys in the source or config. |
| Authentication | absent by decision (C-4) | README warns "Local use only". |

## 4. Dependency safety

| Check | Command | Result |
|---|---|---|
| npm production dependencies | `npm audit --omit=dev` (in `frontend/`) | exit 0, "found 0 vulnerabilities" |
| npm all dependencies | `npm audit` | 3 advisories, all dev-only through `vitest@3.2.7`: `tinypool <=2.1.1` (2 critical, prototype pollution to RCE in worker options) and `@vitest/mocker <=4.1.11` (moderate). Fix requires `vitest@5` (breaking). Already accepted by the user as dev-only. Not shipped in the build output. |
| Maven versions | `mvn -B -o dependency:tree -Dscope=runtime` | BUILD SUCCESS. Spring Boot parent 3.5.16, spring-webmvc 6.2.19, tomcat-embed-core 10.1.55, hibernate-core 6.6.53.Final, jackson-databind 2.21.4, h2 2.3.232 (BOM-managed, not overridden), springdoc 2.8.17. All versions are pinned (BOM or explicit). |
| Maven vulnerability scan | - | **Not run.** No OWASP dependency-check or similar plugin is configured, and installing one is outside this read-only review. |
| Frontend ranges | `package.json` | Caret ranges, locked by `package-lock.json`. No unnecessary runtime dependencies (React, React Router, TanStack Query, React Hook Form only). |
| Licences | - | Not checked in detail. All direct dependencies are well-known permissive-licence projects (MIT, Apache-2.0, EPL/MPL for H2). |

## 5. Test coverage assessment

Commands run in this session (read-only apart from build output in `target/`):
- `npx vitest run` (frontend): exit 0, 31 files, **431 passed**.
- `npm run lint` (frontend): exit 0.
- `mvn -B -o -q test` (backend): exit 0. Surefire XML has **551 test cases**, 0 failures, 0 errors, 0 skipped. The per-class `.txt` summaries add up to 529, because nested and parameterised invocations are counted differently. The XML count matches the claimed 551.
- `npm run build` was **not** re-run by me (the Orchestrator reports it clean).

Assessment:
- **Traceability.** Every backend criterion AC-1.BE.1..8, AC-2.BE.1..6, AC-3.BE.1..8, AC-4.BE.1..11, AC-X.1 and AC-X.4 appears in at least one backend test name. Every frontend criterion AC-1.FE.1..7, AC-2.FE.1..6, AC-3.FE.1..6, AC-4.FE.1..6, AC-X.2 and AC-X.3 appears in frontend test names (grep counts).
- **Behavioural quality (good).**
  - The loan tests assert response fields, the counters and the loan row count after rejected operations ("nothing changes"), plus the `Location` round trip.
  - The overdue tests cover the boundary at due today.
  - `LockFirstWritersTest` uses a real second transaction holding the row lock. It shows that member edit, deactivate, reactivate, book update and book delete wait for the lock and then act on the committed counter. `RowLocksTest` covers lazy proxies and the refresh of an already-managed entity.
  - The frontend tests cover a double click (no second request), the specific rejection reasons, `BACKEND_UNAVAILABLE` and the "Showing 20 of N" text.
- **Weak spots (not defects, accepted scope).** Only one concurrency test exists for checkout (`LoanCheckoutApiTest.java:306`, last copy and last loan slot). No concurrent test covers the **return** path (return + checkout on the same member or book, double return), and no concurrency test runs per-iteration invariant checks. That is T-22, deferred by the user, so the return-path lock order is verified by reading the code and by `RowLocksTest`, not by a race test. There is no NFR-1 timing (T-23) and no cross-screen axe sweep (T-24).
- **Gaps found that should be closed in the CR fixes.** Assertions that tolerate duplicate field errors (CR-7). `OpenApiDocsTest` covers only the loan paths (CR-1). No test exists for the access-log status of an escaping exception (CR-5).

## 6. Conformance to approved architecture and plan

| Item | Status |
|---|---|
| Package layout, layering (controllers have no repositories or transactions; services `@Transactional`; `open-in-view=false`; DTO records) | conforms |
| D1/D2 (JPA, `schema.sql` + `validate`), named constraints, indexes per 5.2 | conforms. `ck_loan_active_key` tightened as accepted. |
| D3 + DR-1..DR-5, DR-12 (lock order, RowLocks, `@Version`, LAZY, per-operation translation, delete exception) | conforms |
| DR-6 (three error producers), DR-7/DR-17 (filter), DR-14 (no values in logs), DR-15 (`Locale.ROOT`), DR-18 (parameter bounds), DR-9 (unique test DB) | conforms (CR-4, CR-5 are refinements) |
| DR-8 (fetch-join loan lists, statement-count test) | conforms (`LoanRepository`, `LoanListsApiTest.DR_8_noNPlusOne`) |
| DR-13 (`/{id:\d+}`) | small deviation, behaviour equivalent (CR-11) |
| D4/D5/DR-10/DR-11/DR-19 frontend | conforms |
| D6 / T-25 (OpenAPI error schema, README contents) | partially (CR-1, CR-3) |
| T-22, T-23, T-24 | not done by the user's fast-track decision |

## 7. Not reviewed or not run

- **Not read line by line.** Most test files (only the loan, concurrency, RowLocks, error-controller and filter test outlines were read), CSS modules and `global.css`, `styles/contrast.test.ts`, MSW handlers and fixtures, `shared-test-vectors/*.json` contents, `OverduePage`, `MemberForm`/`MemberFormPage` (only grepped), `MemberTable`, `BookTable`, `Pagination`, `FormField`, `RouteErrorBoundary`, `routes.tsx`, `main.tsx`.
- **Not run.**
  - Two-process smoke (backend and Vite dev server together) and any real-browser check: keyboard, focus, `<dialog>`, contrast, screen reader.
  - Swagger UI "Try it out".
  - `npm run build`.
  - Maven vulnerability scan (no tool configured).
  - Repeat or flakiness runs.
- **Reasoned, not observed.** CR-5 follows from the servlet error-dispatch flow and is marked "needs confirmation".
- **Not reviewed.** The architecture doc change for `ck_loan_active_key` that is being applied in parallel was ignored as instructed.

## 8. Resolution status

<!-- Updated on re-review. -->
| ID | Status |
|---|---|
| CR-1..CR-12 | open |

```handoff
{
  "stage": "code-review",
  "status": "complete",
  "artifacts": [".claude/docs/code-review.md"],
  "summary": "Verdict: approve with changes. No critical/high findings: loan rules, Clock use, lock order, @Version, LAZY, counters, filter, error format and XSS/SQL safety match the architecture.\n1 medium (CR-1: OpenAPI doc lacks error schema/responses) and 11 low (README accuracy/gaps, PII in one fallback log, access-log status on escaping exceptions, direct /error = 500, duplicate field errors, stale loan caches after edits, notice persisting across routes, trim drift, /{id} regex, dead constant).\nRe-ran: vitest 431 passed, lint clean, mvn test 551 cases passed; npm audit prod 0 vulns, dev advisories only via vitest (accepted).\nCR-6 and CR-9 change behaviour and need approval; the other 10 fixes are minor.",
  "open_questions": [],
  "decisions_needing_approval": [
    {"id": "D1", "decision": "CR-6: what should a direct request to /error (not an error dispatch) return? Today it is 500 INTERNAL_ERROR, logged at ERROR, and a test expects that.", "options": ["Change to 404 NOT_FOUND, logged at DEBUG, and update ApiErrorControllerTest", "Keep 500 and record it as accepted"], "recommendation": "Change to 404 NOT_FOUND (small, avoids false ERROR log entries)"},
    {"id": "D2", "decision": "CR-9: should the global notice be cleared when the user navigates to another screen? Today it stays until dismissed or replaced.", "options": ["Clear on the second pathname change after the notice was raised (keeps notify-then-navigate flows)", "Store the pathname with the notice and hide it on other routes", "Keep the current behaviour"], "recommendation": "Clear on the second pathname change"}
  ],
  "risks": [
    "Return-path concurrency (return + checkout, double return) is verified only by code reading and RowLocksTest, not by a race test (T-22 deferred by the user).",
    "OpenAPI document does not describe errors (CR-1), so NFR-13 is met only through the README error-shape summary.",
    "No Maven vulnerability scan tool is configured; backend dependency CVE status not verified.",
    "No real-browser or two-process smoke run in this review."
  ],
  "evidence": [
    "npm audit --omit=dev (frontend): exit 0, 0 vulnerabilities",
    "npm audit (frontend): 3 dev-only advisories (tinypool critical x2, @vitest/mocker moderate) via vitest 3.2.7",
    "mvn -B -o dependency:tree -Dscope=runtime (backend): BUILD SUCCESS; Boot 3.5.16, webmvc 6.2.19, tomcat 10.1.55, hibernate 6.6.53, jackson 2.21.4, h2 2.3.232, springdoc 2.8.17",
    "npx vitest run (frontend): exit 0, 31 files, 431 tests passed",
    "npm run lint (frontend): exit 0",
    "mvn -B -o -q test (backend): exit 0; surefire XML 551 testcases, 0 failures/errors/skips",
    "Files written: .claude/docs/code-review.md only"
  ]
}
```
