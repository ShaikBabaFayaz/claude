# Verification Report

**Status:** complete - PASS
**Owner:** `verification` agent
**Rule:** nothing is marked passed unless the command was actually run; evidence is mandatory.
**Runs in this report:**
- **Earlier run (run 1):** 2026-11-25, roughly 20:00 to 23:00 IST (the first full verification; verdict then PARTIAL). Windows 11, Git Bash. Java 21.0.12.1, Maven 3.10.0, Node v24.21.0. Sections 2 to 10 below are that run, kept as written except where marked "UPDATED by re-run".
- **Lean re-run (run 2):** 2026-11-25, 23:49 to 23:58 IST, same machine and toolchain, branch `feature`. Section 11. No source file was modified and no git write command was run in either run.

## 1. Verdict

**PASS (after the lean re-run).** Every command executed in this session passed:

- Backend full suite 551/551 (run 2) and `RequestLoggingFilterTest` 10/10 repeated runs with 0 failures.
- Frontend 431/431, lint clean, production build clean.
- The README run commands, executed literally from the repo root, start the backend (health UP) and the Vite dev server (200, proxy returns backend JSON).
- The restart check (NFR-7) and the OpenAPI document check (`X-Requested-With` on all write operations) passed.
- F-1 (flaky test) is resolved on the evidence in section 11 (see the caveat there on what 10 runs can and cannot show).
- The earlier run's live smoke (about 55 `curl` calls, section 4) still stands: no production code changed since.

Why PASS and not PARTIAL: everything that was executed passed, and the remaining NOT RUN items (section 9) are the items deliberately skipped under the user's fast-track decision (T-22, T-23, T-24, real-browser pass, Swagger UI click-through, Maven vulnerability scan, Linux/macOS portability). Section 9 also lists a few smaller unrun items of my own (for example a from-scratch `npm install`, a live 500, PATCH). Those are low value and not blockers, but they are unrun and PASS does not cover them. **PASS here means "all executed checks passed", not "all of NFR-1, NFR-3, NFR-4, NFR-5 (full matrix) and NFR-10 (cross-OS) are proven".** Those requirements remain unverified or partially verified, see section 3 and section 9.

There are no product failures. Open: code-review findings CR-1, CR-3 to CR-12 (known, not verification failures), and environment observation O-1 (Vite binds to IPv6 `::1` only on this machine).

## 2. Evidence (earlier run, run 1)

All commands in this section were run in run 1 (earlier session, see header). Paths are relative to the repo root `C:\Users\ShaikBabaFayaz\Desktop\claude`. Run 2 evidence is in section 11.

| # | Check | Command | Exit code | Result | Key output |
|---|---|---|---|---|---|
| E1 | Backend full test suite, run 1 | `cd backend && mvn -B test` | **1** | **FAIL (1 test)** | `Tests run: 551, Failures: 1, Errors: 0, Skipped: 0`; `RequestLoggingFilterTest.DR_14_parameterNamesButNeverValues:51` (see F-1) |
| E2 | Isolate the failing class, 3 runs | `cd backend && mvn -B -q test -Dtest=RequestLoggingFilterTest` (x3) | 0, 0, 0 | pass | no failures in any of the 3 runs |
| E3 | Backend full test suite, run 2 | `cd backend && mvn -B test` | 0 | PASS | `Tests run: 551, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` |
| E4 | Backend package | `cd backend && mvn -B -DskipTests package` | 0 | PASS | `Building jar: ...target\library-backend-0.0.1-SNAPSHOT.jar`, `BUILD SUCCESS` |
| E5 | Frontend tests | `cd frontend && npm test` | 0 | PASS | `Test Files 31 passed (31)`, `Tests 431 passed (431)` |
| E6 | Frontend lint | `cd frontend && npm run lint` (`eslint . --max-warnings 0`) | 0 | PASS | no output beyond the banner |
| E7 | Frontend build | `cd frontend && npm run build` (`tsc -b && vite build`) | 0 | PASS | `150 modules transformed`, `dist/assets/index-*.js 434.56 kB (gzip 138.19 kB)`, `built in 2.05s` |
| E8 | Port check before start | `netstat -ano \| grep -E ":(8080\|5173) "` | - | ports free | no listener |
| E9 | Start backend | `cd backend && java -jar target/library-backend-0.0.1-SNAPSHOT.jar` (background, default config) | - | started | `Started LibraryApplication in 10.924 seconds`; `Demo data loaded: books=10 members=5 activeLoans=3` |
| E10 | Live API smoke on `127.0.0.1:8080` | about 55 `curl` calls, see section 4 | all as expected | PASS | section 4 |
| E11 | Start Vite dev server | `cd frontend && npm run dev` (background) | - | started | `VITE v7.3.8 ready`, listens on `[::1]:5173` only |
| E12 | Vite serve and proxy | `curl http://localhost:5173/`, `/api/books?size=2`, `/api/loans/overdue`, `POST /api/books` | - | PASS | 200 HTML; 200 backend JSON; 200 backend JSON; 403 `MISSING_REQUIRED_HEADER` from backend |
| E13 | `curl http://127.0.0.1:5173/` | as requested in the brief | 7 (connection refused, `000`) | **does not work on this machine** | Vite listens only on `[::1]:5173` (O-1) |
| E14 | Cleanup | `taskkill //PID 21820 //T //F` (Vite), `taskkill //PID 24876 //T //F` (backend), then `netstat` | 0 | PASS | no listener on 8080 or 5173 afterwards |

## 3. Requirement and acceptance-criteria coverage

Legend: **AT** = verified by automated test (the AC ID appears in the name or `@DisplayName` of a test in a suite that passed in E3/R1 or E5/R5; the "files" count is from `grep -l` on `backend/src/test` or `frontend/src`). **SM** = verified by live smoke in run 1 (section 4). **NV** = not verified. I checked that a test with the ID exists and that the suite passed. I did not read every test body, so "AT" means "a passing test is labelled with this AC", not "I proved its assertions are complete". Run 2 re-ran the full backend and frontend suites (R1, R5), so every AT row below also holds for the current tree.

### US-1 Manage the book catalogue (Must)

| AC | Status | Evidence |
|---|---|---|
| AC-1.BE.1 create, available = total | AT (1 file) + SM | E3; smoke S1 (`201`, `totalCopies 2`, `availableCopies 2`) |
| AC-1.BE.2 field validation | AT (4) + SM | E3; smoke S3, S4 (blank title/author/isbn, copies 0 -> 400 `VALIDATION_FAILED` with per-field `fieldErrors`) |
| AC-1.BE.3 invalid ISBN | AT (4) + SM | E3; smoke S3 (bad check digit -> 400, field `isbn`). Hyphenated ISBN `978-1-86197-271-2` stored as `9781861972712` (S1, and again R12 in run 2) |
| AC-1.BE.4 duplicate ISBN | AT (3) + SM | E3; smoke S2 (409 `ISBN_ALREADY_EXISTS`), S31 (update to another book's ISBN -> 409) |
| AC-1.BE.5 get, 404 | AT (3) + SM | E3; smoke S12 (`GET /api/books/9999` -> 404 `BOOK_NOT_FOUND`) |
| AC-1.BE.6 copies below active loans | AT (5) + SM | E3; smoke S29 (copies 1 with 2 loans -> 400 `TOTAL_COPIES_BELOW_ACTIVE_LOANS`), S30 (copies 2 -> 200, `availableCopies 0`) |
| AC-1.BE.7 delete with active loan | AT (4) + SM | E3; smoke S13 (`DELETE /api/books/3` -> 409 `BOOK_HAS_ACTIVE_LOANS`) |
| AC-1.BE.8 delete without loans | AT (1) + SM | E3; smoke S32 (204), S33 (then 404) |
| AC-1.FE.1 list columns and actions | AT (2) | E5 |
| AC-1.FE.2 inline client errors | AT (4) | E5 |
| AC-1.FE.3 server errors inline, values kept | AT (4) | E5 |
| AC-1.FE.4 delete confirmation | AT (2) | E5 |
| AC-1.FE.5 delete refused message | AT (1) | E5 |
| AC-1.FE.6 success notice and list refresh | AT (3) | E5 |
| AC-1.FE.7 empty, loading, error states | AT (3) | E5 |

All US-1 FE criteria are verified by component tests (jsdom plus mocked API) only. No real browser was used.

### US-2 Search and browse (Must)

| AC | Status | Evidence |
|---|---|---|
| AC-2.BE.1 text filters, AND, ISBN ignoring hyphens | AT (3) + SM | E3; smoke S5 (`title=PRAGMATIC`, case-insensitive), S6 (`isbn=978-0-13&author=martin` -> only Clean Code) |
| AC-2.BE.2 availability filter | AT (2) + SM | E3; smoke S5, S7 (`unavailable` -> only Design Patterns) |
| AC-2.BE.3 page/size, totals, size > 100 rejected | AT (5) + SM | E3; smoke S9 (`page=1&size=3` -> `totalElements 11, totalPages 4`), S10 (`size=101` -> 400), S11 (`size=0` -> 400) |
| AC-2.BE.4 deterministic order (title, ISBN) | AT (2) + SM | E3; seeded list in title order |
| AC-2.BE.5 empty result, not an error | AT (2) + SM | E3; smoke S8 (200, `content []`, `totalElements 0`) |
| AC-2.BE.6 totalCopies and availableCopies in each item | AT (1) + SM | E3; all book responses |
| AC-2.FE.1..6 | AT (4, 6, 4, 3, 4, 2 files) | E5 |

### US-3 Manage members (Must)

| AC | Status | Evidence |
|---|---|---|
| AC-3.BE.1 register, ACTIVE | AT (1) + SM | E3; smoke S14 (201, `status ACTIVE`) |
| AC-3.BE.2 name/email validation | AT (2) + SM | E3; smoke S16 (blank name, bad email -> 400 with both fields) |
| AC-3.BE.3 duplicate email, case-insensitive | AT (4) + SM | E3; smoke S15 (`SMOKE@example.ORG` vs `Smoke@Example.org` -> 409 `EMAIL_ALREADY_EXISTS`, `fieldErrors[email]`) |
| AC-3.BE.4 edit, unknown id 404 | AT (3) + SM (404 only) | E3; smoke S18 (`deactivate` of 9999 -> 404 `MEMBER_NOT_FOUND`). Member edit success (PUT /api/members) was not exercised by smoke |
| AC-3.BE.5 deactivate without loans | AT (1) + SM | E3; smoke S25 (member 5 -> 200, `INACTIVE`) |
| AC-3.BE.6 deactivate with active loan | AT (4) + SM | E3; smoke S17 (member 1 -> 409 `MEMBER_HAS_ACTIVE_LOANS`) |
| AC-3.BE.7 reactivate | AT (1) + SM | E3; smoke S27 (member 5 -> 200, `ACTIVE`) |
| AC-3.BE.8 paginated list, status filter | AT (2) + SM (partial) | E3; smoke `GET /api/members?size=100` (name order) and `?status=INACTIVE` (empty page, 200) |
| AC-3.FE.1..6 | AT (4, 6, 2, 1, 2, 2 files) | E5 |

### US-4 Borrow and return (Must)

| AC | Status | Evidence |
|---|---|---|
| AC-4.BE.1 checkout, due = +14, copies -1 | AT (1) + SM | E3; smoke S19: checkout and due `2026-11-25` -> `2026-12-09` (today + 14, `date -d '+14 days'`); book 5 available 4 -> 3 (S36) |
| AC-4.BE.2 loan limit 3 | AT (1) + SM | E3; smoke S22 (4th loan for member 4 -> 409 `LOAN_LIMIT_REACHED`) |
| AC-4.BE.3 no copy | AT (1) + SM | E3; smoke S23 (book 3 -> 409 `NO_COPY_AVAILABLE`) |
| AC-4.BE.4 inactive member | AT (1) + SM | E3; smoke S26 (409 `MEMBER_INACTIVE`) |
| AC-4.BE.5 same-book duplicate | AT (2) + SM | E3; smoke S20 (409 `DUPLICATE_ACTIVE_LOAN`) |
| AC-4.BE.6 unknown member or book | AT (1) + SM | E3; smoke S28 (404 `MEMBER_NOT_FOUND`, 404 `BOOK_NOT_FOUND`) |
| AC-4.BE.7 return, copies +1 | AT (1) + SM | E3; smoke S37 (200, `returnDate 2026-11-25`), book 5 available 3 -> 4 (S38) |
| AC-4.BE.8 already returned / unknown | AT (2) + SM | E3; smoke S39 (409 `LOAN_ALREADY_RETURNED`, count stays 4), S28b (`return` of 9999 -> 404 `LOAN_NOT_FOUND`) |
| AC-4.BE.9 overdue list | AT (1) + SM | E3; smoke S0 (one entry, loan 1, `dueDate 2026-11-19`, `daysOverdue 6`, with member, book, dates) |
| AC-4.BE.10 active loans, filter, paging | AT (1) + SM | E3; smoke `GET /api/loans/active?size=100` (3 seeded loans) and `?memberId=4` |
| AC-4.BE.11 concurrent last copy / last slot | AT (4 files) - **limited** | E3. Only one race test for checkout (`LoanCheckoutApiTest` last copy and last slot) plus `RowLocksTest` and `LockFirstWritersTest`. The full matrix (T-22) is NOT RUN; the return path has no race test. Not verified by smoke (single client). |
| AC-4.FE.1..6 | AT (4, 2, 2, 3, 4, 2 files) | E5 |

### Cross-cutting

| Item | Status | Evidence |
|---|---|---|
| AC-X.1 error format | AT (11 files) + SM | Every error body seen in smoke has exactly `status, code, message, timestamp, fieldErrors` (about 30 bodies, section 4). No stack trace or SQL in any body. Statuses seen: 400, 403, 404, 409. **A 500 was never produced**, so that status is AT only. |
| AC-X.2 navigation | AT (2 files) | E5 (component tests only) |
| AC-X.3 backend unreachable, retry | AT (16 files) | E5 (mocked network failure only; the live backend-down case was not exercised) |
| AC-X.4 demo data | AT (1) + SM | E3; smoke: 10 books, 5 members, 3 active loans, one overdue, no member above 3 loans (S0). Run 2 R10 saw the same counts again after a restart |
| FR-22, FR-24 | SM | as above |
| FR-23 | AT | E5 |
| NFR-5 concurrency | **partial** | one race test only (T-22 skipped) |
| NFR-6 validation / XSS | AT | server 400s in smoke; `BooksPage.xss.test.tsx` exists in the passing suite |
| NFR-7 reset on restart / demo data | **SM (UPDATED by re-run)** | Run 2 R12 to R14: a book created with the header was gone after a backend restart and the demo data (10 books, 5 members, 3 active loans) was back |
| NFR-9 health, logging | SM (health) | `GET /actuator/health` -> `{"status":"UP"}` (run 1 and run 2). Log content was not inspected beyond the start-up lines |
| NFR-10 portability, README steps | **partial (UPDATED by re-run)** | README run commands now executed literally from the repo root and work (R7 to R9). Windows only; from-scratch `npm install` and Linux/macOS not run (section 9) |
| NFR-1 performance | **NOT RUN** | T-23 skipped |
| NFR-2, 3, 4 UI performance, accessibility, browsers | **NOT RUN** | T-24 and no browser pass |
| NFR-13 API docs | **partial (UPDATED by re-run)** | `GET /v3/api-docs` returns 200, `openapi 3.1.0`, all 9 write operations carry a required `X-Requested-With` header parameter (R15). CR-1 (error response schema) was not assessed in run 2 and stays open. Swagger UI click-through NOT RUN |

## 4. API / behavior validation (live smoke, run 1)

Backend: `java -jar backend/target/library-backend-0.0.1-SNAPSHOT.jar`, default config, `127.0.0.1:8080`. Writes sent `X-Requested-With: library-ui`. "Today" = 2026-11-25. Seed ids: books 1..10, members 1..5, loans 1..3. **This smoke was run once, in run 1. It was not repeated in run 2; it still stands because no production code changed between the runs (the only change was a test-only edit to `RequestLoggingFilterTest`, plus the README).**

| # | Request | Expected | Observed |
|---|---|---|---|
| S0 | `GET /actuator/health` | UP | 200 `{"status":"UP"}` |
| S0a | `GET /api/loans/overdue` | seeded overdue loan, 6 days | 200, one item: loan 1, Ada Lovelace, Design Patterns, `dueDate 2026-11-19`, `overdue true`, `daysOverdue 6` |
| S0b | `GET /api/books?size=100`, `/api/members?size=100`, `/api/loans/active?size=100` | demo data | 10 books, 5 members, 3 active loans (loan 2 due today: `overdue false`) |
| S1 | `POST /api/books` valid, ISBN `978-1-86197-271-2` | 201 | 201, id 11, `isbn 9781861972712` (normalised), `availableCopies 2` |
| S2 | `POST /api/books` same ISBN | 409 | 409 `ISBN_ALREADY_EXISTS`, `fieldErrors:[{field:isbn,...}]` |
| S3 | `POST /api/books` ISBN with bad check digit | 400 | 400 `VALIDATION_FAILED`, `fieldErrors:[{isbn,"ISBN must be a valid ISBN-10 or ISBN-13."}]` |
| S4 | `POST /api/books` all fields blank, copies 0 | 400 | 400 `VALIDATION_FAILED`, 5 field errors (author, isbn x2, title, totalCopies) |
| S5 | `GET /api/books?title=PRAGMATIC&availability=available` | 1 match | 200, `totalElements 1` |
| S6 | `GET /api/books?isbn=978-0-13&author=martin` | 1 match | 200, Clean Code only |
| S7 | `GET /api/books?availability=unavailable` | Design Patterns | 200, one item, `availableCopies 0` |
| S8 | `GET /api/books?title=zzzznomatch` | empty page | 200, `content [], totalElements 0, totalPages 0` |
| S9 | `GET /api/books?page=1&size=3` | page 1 | 200, 3 items, `page 1, size 3, totalElements 11, totalPages 4` |
| S10 | `GET /api/books?size=101` | 400 | 400 `VALIDATION_FAILED`, field `size` |
| S11 | `GET /api/books?size=0` | 400 | 400 `VALIDATION_FAILED`, field `size` |
| S12 | `GET /api/books/9999` | 404 | 404 `BOOK_NOT_FOUND` |
| S13 | `DELETE /api/books/3` (active loan) | 409 | 409 `BOOK_HAS_ACTIVE_LOANS` |
| S14 | `POST /api/members` valid | 201 | 201, id 6, `ACTIVE`, `activeLoanCount 0` |
| S15 | `POST /api/members` same email, other case | 409 | 409 `EMAIL_ALREADY_EXISTS`, `fieldErrors:[{email,...}]` |
| S16 | `POST /api/members` blank name, bad email | 400 | 400 `VALIDATION_FAILED`, fields `email` and `name` |
| S17 | `POST /api/members/1/deactivate` (has loan) | 409 | 409 `MEMBER_HAS_ACTIVE_LOANS` |
| S18 | `POST /api/members/9999/deactivate` | 404 | 404 `MEMBER_NOT_FOUND` |
| S19 | `POST /api/loans {4,5}` | 201, due +14 | 201, `checkoutDate 2026-11-25`, `dueDate 2026-12-09` |
| S20 | same `{4,5}` again | 409 | 409 `DUPLICATE_ACTIVE_LOAN` |
| S21 | `POST /api/loans {4,6}` and `{4,7}` | 201 | 201, 201 (member 4 now has 3 loans) |
| S22 | `POST /api/loans {4,8}` | 409 | 409 `LOAN_LIMIT_REACHED` ("maximum of 3 active loans") |
| S23 | `POST /api/loans {5,3}` (0 copies) | 409 | 409 `NO_COPY_AVAILABLE` |
| S25 | `POST /api/members/5/deactivate` | 200 | 200, `INACTIVE` |
| S26 | `POST /api/loans {5,8}` | 409 | 409 `MEMBER_INACTIVE` |
| S27 | `POST /api/members/5/reactivate` | 200 | 200, `ACTIVE` |
| S28 | loan with member 9999; with book 9999; `GET /api/loans/9999`; `POST /api/loans/9999/return` | 404 | 404 `MEMBER_NOT_FOUND`; 404 `BOOK_NOT_FOUND`; 404 `LOAN_NOT_FOUND`; 404 `LOAN_NOT_FOUND` |
| S36 | `GET /api/books/5` before return | available 3 | `availableCopies 3` |
| S37 | `POST /api/loans/4/return` | 200 | 200, `returnDate 2026-11-25` |
| S38 | `GET /api/books/5` after return | available 4 | `availableCopies 4` |
| S39 | `POST /api/loans/4/return` again | 409 | 409 `LOAN_ALREADY_RETURNED`; book 5 stays at `availableCopies 4` |
| S40 | `POST /api/books` without the header | 403 | 403 `MISSING_REQUIRED_HEADER` |
| S41 | `GET /api/books` with `Host: evil.example` | 403 | 403 `FORBIDDEN_ORIGIN` (reads are blocked too) |
| S42 | `POST` and `DELETE` with `Host: evil.example`; `POST` with `Origin: http://evil.example` | 403 | 403 `FORBIDDEN_ORIGIN` for all three |
| S43 | `GET /h2-console` | not exposed | 404 `NOT_FOUND` (JSON error body) |
| S44 | `GET /actuator/env`, `/beans`, `/metrics`, `/info` | not exposed | 404 `NOT_FOUND` each. `GET /actuator` returns only links to `self` and `health` |
| S45 | `GET /api/nonexistent`; `GET /api/books/abc`; `POST` with malformed JSON | JSON errors | 404 `NOT_FOUND`; 400 `VALIDATION_FAILED` (field `id`); 400 `MALFORMED_REQUEST` |
| S46 | `POST /api/loans {5,6}`, then `PUT /api/books/6` copies 1 / 2 / ISBN of another book | 400 / 200 / 409 | 201; 400 `TOTAL_COPIES_BELOW_ACTIVE_LOANS` (field `totalCopies`); 200 (`availableCopies 0`); 409 `ISBN_ALREADY_EXISTS` |
| S47 | `DELETE /api/books/11` (no loans), then `GET` | 204, then 404 | 204; 404 `BOOK_NOT_FOUND` |

**Error body shape:** in every error response above the keys were exactly `status`, `code`, `message`, `timestamp`, `fieldErrors`. `fieldErrors` is non-empty for `VALIDATION_FAILED`, `ISBN_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS` and `TOTAL_COPIES_BELOW_ACTIVE_LOANS`, and `[]` otherwise. No stack trace, SQL or class name appeared.

**Vite serve and proxy (E12, run 1):** `GET http://localhost:5173/` -> 200 with the Vite index HTML (`<div id="root">`, title "Library"). `GET http://localhost:5173/api/books?size=2` -> 200 with the backend's JSON (`totalElements 10` after S47). `GET http://localhost:5173/api/loans/overdue` -> the same overdue loan. `POST` through the proxy without the header -> the backend's 403 `MISSING_REQUIRED_HEADER`. This is a serve and proxy check, not a UI test. The brief asked for `127.0.0.1:5173`; that address refused connections (E13, O-1), so `localhost` was used (the `::1` address also worked). Run 2 repeated the `localhost` root and `/api/books` proxy checks with the literal README command (R9).

## 5. Edge cases exercised (run 1)

Empty or blank input (S4, S16); invalid ISBN check digit (S3); ISBN with hyphens, normalised (S1); duplicate ISBN on create and on update (S2, S46); case-insensitive duplicate email (S15); copies below active loans (S46); delete with and without active loans (S13, S47); page size 0 and 101 (S10, S11); empty search result (S8); loan limit (S22); no copies (S23); inactive member (S26); duplicate same-book loan (S20); double return (S39); unknown ids for every entity (S12, S18, S28); wrong-type path id (S45); malformed JSON (S45); missing header, bad Host, bad Origin (S40..S42); hidden H2 console and actuator endpoints (S43, S44); loan due today is not overdue (seeded loan 2 shows `overdue false`, S0b).

Code-review items:

- **CR-2 (README accuracy):** the working-tree `README.md` (it shows `M` in `git status`) no longer says what CR-2 describes. (a) It now says `fieldErrors` is also filled for 409 `ISBN_ALREADY_EXISTS`, 409 `EMAIL_ALREADY_EXISTS` and 400 `TOTAL_COPIES_BELOW_ACTIVE_LOANS`. This matches what smoke observed (S2, S15, S46). (b) It now says "every request except GET, HEAD and OPTIONS (in practice POST, PUT, DELETE)", which is accurate in substance. I did not send a PATCH, so the PATCH case was not observed. (c) The Run and Tests blocks now use `mvn -f backend/pom.xml ...` and `npm --prefix frontend ...` from the repo root. **UPDATED by re-run:** the README run and test commands were then executed literally from the repo root in run 2 (R1, R5 to R9) and worked, so CR-2 is resolved in both text and practice.
- **CR-7 (duplicate field errors): confirmed by observation.** A blank ISBN returns two `isbn` entries (S4: "ISBN is required." and "ISBN must be a valid ISBN-10 or ISBN-13."). This is a known open low finding, not a new failure.
- **CR-5 (access log says `status=200` for an escaping exception): not observed.** I found no cheap way to make an exception escape from a filter in the running app, so it stays "reasoned, not observed".

## 6. Build status

| Item | Status |
|---|---|
| Backend compile + package | OK (E4; run 2 R1 compiles with tests) |
| Backend tests | **UPDATED by re-run:** 551 run, 0 failures in run 2 (R1). Run 1: one failure on the first run (E1, F-1), 551/551 on the rerun (E3) |
| Frontend type-check + build | OK (E7, R7 in run 2) |
| Frontend lint (zero warnings allowed) | OK (E6, R6 in run 2) |
| Frontend tests | 431 passed (E5, R5 in run 2) |
| Artefacts created by my runs | `backend/target/` (rebuilt), `frontend/dist/` (created by `npm run build`). I did not check whether `.gitignore` covers them. |

## 7. Documentation quality

- README prerequisites, run, test, security, API and known-limits sections exist and agree with what was observed: ports, header requirement, 403 codes, error shape, demo data count (10 books, 5 members, 3 loans, one overdue, one due today: S0b), size limit 1..100.
- **UPDATED by re-run:** the README run commands were executed **literally from the repo root** in run 2: `mvn -f backend/pom.xml spring-boot:run` (R7) and `npm --prefix frontend run dev` (R9), and the test and build commands `mvn -B -f backend/pom.xml test`, `npm --prefix frontend test`, `npm --prefix frontend run lint`, `npm --prefix frontend run build` (R1, R5 to R7). All worked as written. Not run: `npm --prefix frontend install` (dependencies were already present; a from-scratch install was not done, so a fresh-clone flow is unverified).
- The README says the frontend is at `http://localhost:5173`; that works (R9). `http://127.0.0.1:5173` did not work on this machine in run 1 (O-1). The README says nothing on this, which CR-3 already lists as a missing troubleshooting item.
- CR-1 (OpenAPI lacks the error schema) and CR-3 (README gaps) remain open as known issues. Run 2 fetched `/v3/api-docs` (R15, header parameter only); the error schema was not examined.

## 8. Failures and recommended fixes

### F-1 Flaky unit test `RequestLoggingFilterTest.DR_14_parameterNamesButNeverValues` (test defect, not a product defect) - **RESOLVED (evidence in section 11)**

Status: found in run 1, fixed by `implementation` (test-only change), resolved in run 2. The original finding is kept below for the record.

- **Command (run 1):** `cd backend && mvn -B test` (E1), exit 1.
- **Output (run 1):**
  ```
  [ERROR]   RequestLoggingFilterTest.DR_14_parameterNamesButNeverValues:51
  Expecting no elements of:
    ["Request rejected: code=VALIDATION_FAILED fields=[size]",
     "request method=GET path=/test-errors/paging params=[page, size, search, title] status=400 durationMs=1 requestId=7a47a19c-5f61-422a-b144-f73d1444f9c1"]
  to match given predicate but this element did:
    "request method=GET path=/test-errors/paging ... durationMs=1 requestId=7a47a19c-..."
  Tests run: 551, Failures: 1, Errors: 0, Skipped: 0
  ```
- **Likely cause (from reading the test as it was then, `RequestLoggingFilterTest.java:49-51`):** the "no value was logged" check used `m.contains("=7")` to look for the request parameter `page=7`. The access line also contains `requestId=<random UUID>`, and a UUID that starts with `7` yields the substring `=7`. The same applies to `durationMs=7`. In that run the id began `7a47a19c`. The filter did not log a parameter value.
- **Frequency estimate (run 1):** about 1 in 16 runs from the UUID alone, plus extra chance from `durationMs=7`.
- **Fix (by `implementation`, as stated in the re-run brief):** test-only change, request values replaced by distinctive non-hex strings of the form `zq-...`, so they cannot collide with a hex UUID or a duration. No production code changed.
- **Evidence of resolution (run 2):** full backend suite 551/551, exit 0 (R1); `RequestLoggingFilterTest` run 10 times in a row, 10 exits of 0, 0 failures (R2); the surefire report of the last run shows `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. Caveat: 11 green runs (R1 plus R2) are consistent with the fix but do not prove the absence of a rare flake. The old defect had roughly a 1 in 16 chance per run from the UUID alone, so the old code would still have passed 11 runs in a row about half of the time ((15/16)^11 is about 0.49). These runs alone are therefore weak statistical evidence. The stronger evidence is that the fix removes the cause: the asserted values `zq-...` cannot appear in a hex UUID or in a duration, so a collision is impossible by construction. I confirmed the fix only through its behaviour in R1 and R2; I did not re-read the edited test source in this run.

No product (production code) failures were found.

### Observation O-1 (environment, not a failure)

`npm run dev` binds Vite to `[::1]:5173` only (`netstat`: `TCP [::1]:5173 LISTENING`). `curl http://127.0.0.1:5173/` returned connection refused (`000`) in run 1. `http://localhost:5173/` and `http://[::1]:5173/` both returned 200. In run 2 the literal README command again bound `[::1]:5173` only (R9). The README uses `localhost`, so the documented flow works. A user who types `127.0.0.1:5173` will get a refused connection. The backend allowlist includes `127.0.0.1` on 5173, but the Vite server does not listen on IPv4 here. Possible follow-up: document it (CR-3) or set `server.host` in `vite.config.ts`.

## 9. Not run (and why)

Deliberately skipped by the user's fast-track decision (not failures; these are still NOT RUN after the re-run):
- T-22 full concurrency matrix. Only the existing last-copy and last-member-slot race tests ran (as part of the backend suite, E3 and R1). No race test covers the return path (return + checkout, double return).
- T-23 NFR-1 timed performance test (p95 <= 500 ms at 10,000 books).
- T-24 frontend accessibility sweep (axe or similar), and the NFR-3 criteria (keyboard-only, contrast >= 4.5:1, screen reader).
- A real-browser pass: keyboard, focus, `<dialog>`, contrast, screen reader, and the 768 px layout. All 431 frontend tests are jsdom component tests with mocked HTTP. No UI-to-backend flow was driven end to end.
- Swagger UI "Try it out" click-through (NFR-13). Only the raw `/v3/api-docs` JSON was fetched (R15).
- Maven dependency vulnerability scan (no plugin configured; same as code review).
- Linux and macOS portability (NFR-10) and the browser matrix (NFR-4). Windows only.

Not run for other reasons (smaller items; not covered by the PASS verdict):
- **UPDATED by re-run:** the README commands were run literally in run 2 except `npm --prefix frontend install`. A from-scratch `npm install` (fresh clone, NFR-10) was not run; `node_modules` already existed.
- **UPDATED by re-run:** the repeat/flakiness run was done for `RequestLoggingFilterTest` only (R2). No other test was looped, and the full suites were run once each in run 2.
- PATCH or other methods on the header rule; `PUT /api/members/{id}` success path by smoke (covered only by automated tests).
- Smoke checks for a 500 response and for "backend unreachable" in the UI (covered only by automated tests).
- CR-5 (access log status for an escaping exception): not observable cheaply.
- The OpenAPI error response schema (CR-1) was not examined in run 2.
- Run 2 did not repeat the full run 1 smoke (about 55 calls); it relies on "no production code changed". I did not verify that claim with `git diff` (read-only git was not part of this re-run); it rests on the Orchestrator's brief that the only change was test-only plus the README.
- I did not read all 551 + 431 test bodies. AC coverage above is by labelled test names plus a green suite.

## 10. Residual risks

1. Rare flake: F-1 is fixed by construction, but a future non-hermetic assertion in another test could do the same; only 10 repeats of one class were measured.
2. Concurrency on the return path and the broader lock matrix are verified only by code reading, `RowLocksTest`, `LockFirstWritersTest` and one checkout race test.
3. No browser-level evidence for accessibility, keyboard flow or contrast (NFR-3), and no performance measurement (NFR-1).
4. Open code-review findings CR-1 (OpenAPI error schema), CR-3 (README gaps), CR-4..CR-12 remain as known issues; CR-7 was reproduced in run 1.
5. `127.0.0.1:5173` does not work on this machine (O-1).
6. Other `java.exe` and `node.exe` processes were running on the machine (run 2 end: node 18564 and 27804, java 21876). I did not start them and left them alone. Ports 8080 and 5173 had no LISTENING socket at the end of both runs.

## 11. Lean re-run (run 2): 2026-11-25, 23:49 to 23:58 IST

Scope from the Orchestrator: confirm F-1 fixed, re-run suites, run the README commands literally, restart check, OpenAPI check. All commands were run in this session from the repo root `C:\Users\ShaikBabaFayaz\Desktop\claude`. Throwaway logs went to `/tmp` and were removed.

### Evidence

| # | Check | Command | Exit code | Result | Key output |
|---|---|---|---|---|---|
| R0 | Port state at start (23:49) | `netstat -ano \| grep -E ":(8080\|5173) "` | 0 | **not free at that moment** | listeners on `127.0.0.1:8080` (PID 26908) and `[::1]:5173` (PID 22916) with established connections. Not started by me (see note below). By 23:53 both were gone (`netstat ... LISTENING` empty) and before I started anything of my own the ports were free |
| R1 | Backend full suite | `mvn -B -f backend/pom.xml test` | **0** | **PASS** | `Tests run: 551, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` |
| R2 | F-1 repeat, 10 runs | `mvn -B -q -f backend/pom.xml test -Dtest=RequestLoggingFilterTest` x10 | 0 x10 | **PASS, 0 failures in 10** | run 1 to 10 each `exit=0`, `failures=0`. `-q` prints no test counts, so I read the surefire report of the last run: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` (`backend/target/surefire-reports/com.library.common.web.RequestLoggingFilterTest.txt`, timestamp 23:53) |
| R5 | Frontend tests | `npm --prefix frontend test` | **0** | **PASS** | `Test Files 31 passed (31)`, `Tests 431 passed (431)`, `Duration 29.30s` |
| R6 | Frontend lint | `npm --prefix frontend run lint` (`eslint . --max-warnings 0`) | **0** | **PASS** | no warnings or errors printed |
| R7a | Frontend build | `npm --prefix frontend run build` (`tsc -b && vite build`) | **0** | **PASS** | `150 modules transformed`, `dist/assets/index-uJAtJYt-.js 434.56 kB (gzip 138.19 kB)`, `built in 1.58s` |
| R7 | README backend command | `mvn -f backend/pom.xml spring-boot:run` (background, repo root) | - | started | `Started LibraryApplication in 6.89 seconds`; `Demo data loaded: books=10 members=5 activeLoans=3`; listener `127.0.0.1:8080` (PID 3192) |
| R8 | Backend health | `curl http://127.0.0.1:8080/actuator/health` | 0 | **PASS** | HTTP 200 `{"status":"UP"}` |
| R9 | README frontend command | `npm --prefix frontend run dev` (background, repo root) | - | started | `VITE v7.3.8 ready in 492 ms`, `Local: http://localhost:5173/`, listener `[::1]:5173` only (PID 25056) |
| R9a | Vite root | `curl http://localhost:5173/` | 0 | **PASS** | HTTP 200, body contains `<div id="root">` |
| R9b | Vite proxy | `curl "http://localhost:5173/api/books?size=2"` | 0 | **PASS** | HTTP 200, backend JSON `{"content":[{"id":1,"title":"Clean Code",...},{"id":3,"title":"Design Patterns",...}],"page":0,"size":2,"totalElements":10,"totalPages":5}` |
| R10 | NFR-7 step 1: before | `GET /api/books?size=100` | 0 | 10 books | `totalElements=10` |
| R11 | NFR-7 step 2: create | `POST /api/books` with `X-Requested-With: library-ui`, body `{"title":"Restart Check Book","author":"Verifier","isbn":"978-1-86197-271-2","totalCopies":2}` | 0 | **PASS** | HTTP 201, `{"id":11,...,"isbn":"9781861972712","totalCopies":2,"availableCopies":2}`; `GET /api/books?title=Restart` returned it (`totalElements 1`) |
| R12 | NFR-7 step 3: stop | `taskkill //PID 3192 //T //F` (the app listener I started), then `netstat` | 0 | stopped | children 8836 and 3192 terminated; no listener on 8080; the Maven launcher exited by itself (build error from the killed forked JVM, expected) |
| R13 | NFR-7 step 4: start again | `mvn -f backend/pom.xml spring-boot:run` (background) | - | started | `Demo data loaded: books=10 members=5 activeLoans=3` (PID 5900); health 200 |
| R14 | NFR-7 step 5: compare | `GET /api/books?size=100`, `/api/members?size=100`, `/api/loans/active?size=100`, `GET /api/books/11` | 0 | **PASS** | books `totalElements=10`, "Restart Check Book" present = false; members 5; active loans 3; `GET /api/books/11` -> 404 `BOOK_NOT_FOUND` |
| R15 | OpenAPI document | `curl http://127.0.0.1:8080/v3/api-docs` (run once, 1st backend instance), parsed with `node` | 0 | **PASS** | HTTP 200, `openapi=3.1.0`, 11 paths. All 9 write operations (`POST /api/books`, `PUT` and `DELETE /api/books/{id}`, `POST /api/members`, `PUT /api/members/{id}`, `POST /api/members/{id}/deactivate`, `.../reactivate`, `POST /api/loans`, `POST /api/loans/{id}/return`) have a header parameter `X-Requested-With` (`"required":true`, `"schema":{"type":"string","default":"library-ui"}`, description "CSRF guard: must be library-ui on every write request."). The 7 GET operations have none |
| R16 | Cleanup | `taskkill //PID 5900 //T //F` (backend), `taskkill //PID 25056 //T //F` (Vite), then `netstat -ano \| grep -E ":(8080\|5173) .*LISTENING"` | 0 | **PASS** | both trees terminated; the LISTENING grep matched nothing (grep exit 1). Only `TIME_WAIT`, `FIN_WAIT_2`, `CLOSE_WAIT`, `SYN_SENT` leftovers from other clients remain, which is not a listener |

(R3, R4 and R8..R16 numbering follows the order the checks were run; R3 and R4 are not used.)

### Notes

- **R0 observation:** at 23:49 something else held 8080 and 5173 (PIDs 26908 and 22916; `tasklist` did not list those PIDs a few seconds later, and I could not identify them: the `wmic` command is not available and a PowerShell attempt was blocked by the permission system, which I did not try to work around). I did not kill them. By 23:53 the LISTEN entries were gone. The backend tests use their own context and were unaffected (R1 exit 0 at that time). Both of my servers started cleanly on the free ports, so there was no conflict. A process from some other session or tool occupied these ports for a while; I mention it because it could confuse a person running the README commands at the same time.
- **Processes started by me:** the Maven launchers and app JVM for the backend (two times: PIDs 3192 then 5900, with child JVMs 8836 and 7540) and the npm/Vite tree (PID 25056 and its child 28328). All were stopped (R12, R16). Other `java.exe` and `node.exe` processes (PIDs 21876, 18564, 27804) were not started by me and were left alone.
- **R15:** `/v3/api-docs` was fetched once against the first backend instance. The `X-Requested-With` check is a parse of the JSON, not a visual check in Swagger UI.
- **Section 4 smoke** was not repeated in run 2 (see the note there).
- **Temporary files:** `/tmp` logs and JSON (`be_full.log`, `rl_*.log`, `fe_*.log`, `be_run*.log`, `h*.json`, `idx.html`, `api*.json`/`.txt`) were removed. `backend/target/` and `frontend/dist/` were rebuilt by the build commands.

### Result of the re-run

- F-1: resolved on the evidence (R1, R2), with the caveat in section 8.
- README commands: work as written from the repo root (R1, R5 to R9), except that `npm install` was not run.
- NFR-7: verified (R10 to R14).
- NFR-13 (partial): `X-Requested-With` documented on all write operations (R15).
- All executed checks passed; no failures. Verdict updated to PASS (section 1), scoped as stated there.

```handoff
{
  "stage": "verification",
  "status": "complete",
  "artifacts": [".claude/docs/verification-report.md"],
  "summary": "Verdict PASS (all executed checks passed). Backend 551/551 (exit 0); RequestLoggingFilterTest repeated 10x, 0 failures, F-1 marked resolved (fix is by construction plus 11 green runs); frontend 431/431, lint and build clean.\nREADME commands run literally from repo root: spring-boot:run health UP, Vite 200 and /api/books proxy returns backend JSON. NFR-7 restart check passed (new book gone, demo data 10/5/3 back). /v3/api-docs 200, openapi 3.1.0, all 9 write operations list X-Requested-With.\nStill NOT RUN: T-22 full concurrency matrix (no return-path race test), T-23 NFR-1 timing, T-24 accessibility sweep, real-browser keyboard/contrast/screen-reader pass, Swagger UI click-through, Maven vulnerability scan, Linux/macOS portability; plus minor: from-scratch npm install, PATCH, live 500.\nAll processes I started were stopped; no LISTENING socket on 8080 or 5173 at the end.",
  "open_questions": [],
  "decisions_needing_approval": [],
  "risks": [
    "PASS means all executed checks passed; NFR-1, NFR-3, NFR-4, full NFR-5 matrix (return path has no race test) and cross-OS portability remain unverified (fast-track skips)",
    "F-1 resolved by construction (non-hex zq- values) and 11 green runs; 10 repeats of one class cannot prove absence of a rare flake. I did not re-read the edited test source",
    "Run 1 smoke (about 55 calls) not repeated; relies on the brief's statement that no production code changed since (not checked with git diff)",
    "At 23:49 ports 8080 and 5173 were held by processes not started by me (PIDs 26908, 22916; gone by 23:53, unidentified, not killed); possible interference if README commands are run concurrently from another session",
    "127.0.0.1:5173 still refused on this machine because Vite binds [::1] only; localhost:5173 works (O-1)",
    "Open code-review findings CR-1 (OpenAPI error schema, not examined in re-run), CR-3 to CR-12 remain; CR-2 resolved in README text and practice"
  ],
  "evidence": [
    "mvn -B -f backend/pom.xml test: exit 0 (Tests run: 551, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS)",
    "mvn -B -q -f backend/pom.xml test -Dtest=RequestLoggingFilterTest x10: exit 0 on all 10 (0 failures; last surefire report Tests run: 7, Failures: 0)",
    "npm --prefix frontend test: exit 0 (31 files, 431 tests passed)",
    "npm --prefix frontend run lint: exit 0",
    "npm --prefix frontend run build: exit 0 (150 modules, built in 1.58s)",
    "mvn -f backend/pom.xml spring-boot:run (background): started, demo data books=10 members=5 activeLoans=3; curl /actuator/health 200 {\"status\":\"UP\"}",
    "npm --prefix frontend run dev (background): curl http://localhost:5173/ 200; curl http://localhost:5173/api/books?size=2 200 with backend JSON",
    "POST /api/books with X-Requested-With: 201 (id 11); taskkill //PID 3192 //T //F exit 0; spring-boot:run again; GET /api/books totalElements=10, book 11 -> 404, members 5, active loans 3",
    "curl http://127.0.0.1:8080/v3/api-docs: 200, openapi 3.1.0, 9 of 9 write operations carry X-Requested-With",
    "taskkill //PID 5900 //T //F and //PID 25056 //T //F exit 0; netstat LISTENING grep for 8080/5173 matched nothing",
    "Files written: .claude/docs/verification-report.md only; /tmp logs removed; backend/target and frontend/dist rebuilt by build commands; no git write commands run"
  ]
}
```
