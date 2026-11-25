# Requirements

**Status:** awaiting user approval (questions Q1-Q8 resolved)
**Owner:** `requirements` agent | **Approved by user:** yes (2026-11-25)
**Source:** user request, `workflow/brief.md` (decisions already made + draft stories US-1..US-4), user answers to Q1-Q8 (recommended defaults accepted)

Conventions: priorities use MoSCoW (M/S/C/W). Items marked **[CONFIRMED Qn]** were proposed as defaults and have been confirmed by the user in answer to the open question Qn in section 9. Acceptance criteria are labelled **[BE]** (backend / API behaviour) or **[FE]** (frontend / UI behaviour) so they can be copied verbatim into the Jira Frontend and Backend sub-tasks. IDs: `US-n` story, `FR-n` functional requirement, `AC-n.BE.m` / `AC-n.FE.m` acceptance criterion of story n, `NFR-n` non-functional requirement.

## 1. Overview and scope

A small library management system used by a single librarian to maintain a catalogue of books, register and manage library members, and record borrowing and returning of books. It consists of a Spring Boot backend exposing a web API and a React (TypeScript) single-page frontend. Data is held in memory and is lost on restart (accepted by the user). There is no login; whoever opens the application acts as the librarian.

In scope (this version):
- Book catalogue management (create, view, edit, delete) with ISBN validation and uniqueness.
- Search, filter and browse of the catalogue with pagination and availability indication.
- Member management (register, edit, deactivate/reactivate).
- Borrowing (checkout) and returning books, with due dates, loan limits, copy availability, and an overdue list.

## 2. Actors

| Persona | Goal |
|---|---|
| Librarian | Sole operator of the system. Maintains books and members, issues and returns books, monitors overdue loans. Has full access; no login. |
| Member (patron) | A library user who borrows books. Not a system user in this version: members do not log in or use the UI themselves. Appears as a data record and, in US-2, as someone the librarian searches on behalf of. |

Note: the brief lists "Member/Librarian" as the persona of US-2. Because there is no authentication and members have no accounts, search is used through the same single UI by whoever operates it. **[CONFIRMED Q8]** there is no separate public/read-only member portal.

## 3. Domain terms

- **Book**: a catalogue title identified by ISBN, with a total number of physical copies.
- **Copy availability**: `available copies = total copies - number of active loans of that book`.
- **Member**: a registered library user, either `ACTIVE` or `INACTIVE`.
- **Loan**: a record that a member borrowed one copy of a book: checkout date, due date, return date (empty while active).
- **Active loan**: a loan without a return date.
- **Overdue loan**: an active loan whose due date is earlier than today's date (a loan due today is not overdue).

## 4. User stories

### US-1 Manage the book catalogue
As a librarian, I want to add, view, edit and delete books so that the catalogue reflects the library's holdings.

Priority: Must. Fields: title, author, ISBN, total copies. Linked FRs: FR-1 to FR-6.

**Backend acceptance criteria**
- [ ] AC-1.BE.1 Given valid title, author, ISBN and total copies, when a book is created, then it is stored, returned with a generated identifier and `available copies = total copies`.
- [ ] AC-1.BE.2 Given a title, author or ISBN that is missing or blank, or total copies that is not a whole number in 1..999, or a title or author longer than 255 characters **[CONFIRMED Q1]**, when a book is created or updated, then the request is rejected with a validation error naming each offending field and nothing is stored.
- [ ] AC-1.BE.3 Given an ISBN that is not a valid ISBN-10 or ISBN-13 (including check digit; hyphens and spaces ignored; stored normalised) **[CONFIRMED Q1]**, when a book is created or updated, then the request is rejected with a validation error on the `isbn` field.
- [ ] AC-1.BE.4 Given a book with ISBN X already exists, when another book with the same ISBN (after normalisation) is created, or an existing different book is updated to X, then the request is rejected with a conflict error and nothing changes. Updating a book while keeping its own ISBN succeeds.
- [ ] AC-1.BE.5 Given an existing book, when it is requested by identifier, then its details and current available copies are returned; given an unknown identifier, then a not-found error is returned (applies to get, update and delete).
- [ ] AC-1.BE.6 Given an existing book with N active loans, when its total copies is updated to a value below N, then the update is rejected with a validation error; to a value >= N it succeeds and available copies is recalculated.
- [ ] AC-1.BE.7 Given a book with at least one active loan, when it is deleted, then the request is rejected with a conflict error and the book remains.
- [ ] AC-1.BE.8 Given a book with no active loans, when it is deleted, then it is removed and subsequent requests for it return not-found. **[CONFIRMED Q6]** returned-loan history of a deleted book need not remain visible.

**Frontend acceptance criteria**
- [ ] AC-1.FE.1 Given the catalogue contains books, when the book list is opened, then each row shows title, author, ISBN, total copies and available copies, with add, edit and delete actions.
- [ ] AC-1.FE.2 Given the add or edit form, when a field is empty or invalid, then an inline error message is shown next to that field (not only a generic banner) and the form is not submitted to the backend where client-side checks detect the problem.
- [ ] AC-1.FE.3 Given the backend rejects a submission (validation or duplicate ISBN), when the response is received, then the corresponding message is shown inline on the affected field (or as a form-level message if not field-specific) and the entered values are preserved.
- [ ] AC-1.FE.4 Given the user clicks delete on a book, when the confirmation dialog appears, then the book is deleted only after the user confirms; cancelling leaves it untouched.
- [ ] AC-1.FE.5 Given the backend refuses deletion because of active loans, when the response is received, then a clear message explains the reason and the book stays in the list.
- [ ] AC-1.FE.6 Given a successful create, edit or delete, when the operation completes, then the list reflects the change and a success confirmation is shown.
- [ ] AC-1.FE.7 Given the catalogue is empty, when the list is opened, then an empty-state message with an "add book" action is shown. Loading and error states are shown while data is fetched or when fetching fails.

### US-2 Search and browse the catalogue
As a librarian (or a member being helped), I want to search and filter the catalogue so that I can quickly find a book and see whether a copy is available.

Priority: Must. Linked FRs: FR-7 to FR-9.

**Backend acceptance criteria**
- [ ] AC-2.BE.1 Given books exist, when the catalogue is requested with a title, author and/or ISBN filter, then only books whose corresponding field contains the text (case-insensitive) are returned; multiple filters combine with AND; an ISBN filter matches regardless of hyphens/spaces **[CONFIRMED Q1]**.
- [ ] AC-2.BE.2 Given an availability filter of `available`, when requested, then only books with available copies >= 1 are returned; `unavailable` returns only books with 0 available copies; absent filter returns all.
- [ ] AC-2.BE.3 Given more results than one page, when the catalogue is requested with page and size parameters, then only that page is returned together with total result count, total pages and the current page; the page index is zero-based, default page size is 20 and maximum 100; a size above the maximum is rejected with a validation error **[CONFIRMED Q4]**.
- [ ] AC-2.BE.4 Given no filters, when the catalogue is requested, then all books are returned page by page in a stable, deterministic order (title ascending, then ISBN) **[CONFIRMED Q4]**.
- [ ] AC-2.BE.5 Given filters that match nothing, when requested, then an empty page (not an error) with total count 0 is returned.
- [ ] AC-2.BE.6 Each returned book includes total copies and available copies so the frontend can derive the availability badge.

**Frontend acceptance criteria**
- [ ] AC-2.FE.1 Given the search page, when it is opened, then it shows filter inputs for title, author, ISBN and availability (all / available / all copies out) and the first page of results.
- [ ] AC-2.FE.2 Given the user enters or changes filters and submits (or after a short typing pause), when results load, then the list is updated and the matching count is shown.
- [ ] AC-2.FE.3 Given a result, when it is displayed, then it shows an "Available" badge (with the number of free copies) if at least one copy is free, or an "All copies out" badge if none; the badge is conveyed by text, not by colour alone.
- [ ] AC-2.FE.4 Given more than one page of results, when the user uses pagination controls, then the requested page is shown and the current filters are kept.
- [ ] AC-2.FE.5 Given no matches, when results load, then a "no books found" message is displayed with a way to clear filters. Loading and error states are shown.
- [ ] AC-2.FE.6 Given filters are applied, when the user clears them, then the unfiltered first page is shown.

### US-3 Manage members
As a librarian, I want to register, edit and deactivate members so that only valid library members can borrow books.

Priority: Must. Fields: name, email, status. **[CONFIRMED Q2]** no other member fields (address, phone) in this version. Linked FRs: FR-10 to FR-14.

**Backend acceptance criteria**
- [ ] AC-3.BE.1 Given a valid name and email, when a member is registered, then it is stored with a generated identifier and status `ACTIVE`.
- [ ] AC-3.BE.2 Given a missing/blank name or email, a name longer than 100 characters, or an email that is not in a valid format (single `@`, non-empty local part, domain with at least one dot, no spaces, max 254 characters) **[CONFIRMED Q2]**, when a member is registered or updated, then the request is rejected with a validation error naming each offending field.
- [ ] AC-3.BE.3 Given a member with email E exists, when another member is registered with E (compared case-insensitively), or an existing different member is updated to E, then the request is rejected with a conflict error. Updating a member while keeping their own email succeeds.
- [ ] AC-3.BE.4 Given an existing member, when name or email is edited with valid values, then the change is stored; unknown identifier yields a not-found error (applies to get, update, deactivate, reactivate).
- [ ] AC-3.BE.5 Given an active member with no active loans, when deactivated, then status becomes `INACTIVE`.
- [ ] AC-3.BE.6 Given a member with at least one active loan, when deactivation is requested, then it is rejected with a conflict error and status stays `ACTIVE`.
- [ ] AC-3.BE.7 Given an `INACTIVE` member, when reactivated, then status becomes `ACTIVE` **[CONFIRMED Q2]**.
- [ ] AC-3.BE.8 Given members exist, when the member list is requested, then members are returned paginated (same page/size rules as AC-2.BE.3) in a deterministic order (name ascending, then identifier), optionally filtered by status **[CONFIRMED Q2, Q4]**.

**Frontend acceptance criteria**
- [ ] AC-3.FE.1 Given members exist, when the members list is opened, then each row shows name, email and an Active / Inactive status label (text, not colour alone), with edit and activate/deactivate actions, and pagination.
- [ ] AC-3.FE.2 Given the register or edit form, when a field is empty or invalid, then an inline error is shown next to the field; backend validation and duplicate-email errors are shown inline on the affected field with entered values preserved.
- [ ] AC-3.FE.3 Given the user chooses to deactivate a member, when the confirmation appears and is accepted, then the member is deactivated and shown as Inactive; cancelling changes nothing.
- [ ] AC-3.FE.4 Given the backend refuses deactivation because of active loans, when the response is received, then a clear message explains the reason and the member remains Active.
- [ ] AC-3.FE.5 Given an inactive member, when viewing the list, then a reactivate action is offered **[CONFIRMED Q2]**.
- [ ] AC-3.FE.6 Empty-state, loading and error states are shown for the members list.

### US-4 Borrow and return books
As a librarian, I want to check books out to members, record returns and see overdue loans so that I always know who holds which book and what is late.

Priority: Must. Linked FRs: FR-15 to FR-21.

**Backend acceptance criteria**
- [ ] AC-4.BE.1 Given an active member with fewer than 3 active loans and a book with at least one available copy, when a checkout is requested, then a loan is created with checkout date = today, due date = checkout date + 14 days, no return date, and the book's available copies decreases by 1.
- [ ] AC-4.BE.2 Given a member who already has 3 active loans, when a checkout is requested, then it is rejected with a business-rule error (conflict) stating the loan limit, and nothing changes.
- [ ] AC-4.BE.3 Given a book with 0 available copies, when a checkout is requested, then it is rejected with a business-rule error stating no copy is available, and nothing changes.
- [ ] AC-4.BE.4 Given an `INACTIVE` member, when a checkout is requested, then it is rejected with a business-rule error **[CONFIRMED Q3]**.
- [ ] AC-4.BE.5 Given a member who already has an active loan of the same book, when a checkout of that book is requested for that member, then it is rejected with a business-rule error **[CONFIRMED Q3]**.
- [ ] AC-4.BE.6 Given an unknown member or book identifier, when a checkout is requested, then a not-found error is returned.
- [ ] AC-4.BE.7 Given an active loan, when it is returned, then the return date is set to today, the loan is no longer active, and the book's available copies increases by 1. Returning is allowed regardless of whether the loan is overdue and no fine is calculated **[CONFIRMED Q3]**.
- [ ] AC-4.BE.8 Given a loan that is already returned or does not exist, when a return is requested, then a conflict error (already returned) or not-found error respectively is returned and no counts change.
- [ ] AC-4.BE.9 Given loans exist, when overdue loans are requested, then all active loans with due date earlier than today are returned, each with loan identifier, member (id, name), book (id, title, ISBN), checkout date, due date and number of days overdue, ordered by due date ascending (most overdue first). Loans due today or later and returned loans are excluded.
- [ ] AC-4.BE.10 Given loans exist, when active loans are requested (optionally filtered by member or book), then the matching active loans are returned paginated, so the UI can select a loan to return **[CONFIRMED Q4]**.
- [ ] AC-4.BE.11 Given two checkout requests race for the last available copy of a book (or a member's last allowed loan), when both are processed, then exactly one succeeds and the other is rejected; available copies never drops below 0 and a member never exceeds 3 active loans.

**Frontend acceptance criteria**
- [ ] AC-4.FE.1 Given the issue/return screen, when it is opened, then the librarian can choose a member and a book (searchable selection) and submit a checkout; only active members are offered for selection.
- [ ] AC-4.FE.2 Given a successful checkout, when the response is received, then a confirmation shows the member, book and due date, and the selection form is reset.
- [ ] AC-4.FE.3 Given the backend rejects a checkout (loan limit reached, no copy available, inactive member, duplicate loan), when the response is received, then the specific reason is displayed in plain language and the entered selection is preserved.
- [ ] AC-4.FE.4 Given a member is selected, when their active loans are shown, then each shows book, checkout date, due date and a "Return" action; after confirming a return the loan disappears from the active list and a success message is shown. Overdue items in this list are visually flagged.
- [ ] AC-4.FE.5 Given the overdue list screen, when it is opened, then it shows member, book, due date and days overdue for each overdue loan, sorted most overdue first, with the late status conveyed by text and styling (not colour alone).
- [ ] AC-4.FE.6 Given there are no overdue loans, when the overdue list is opened, then an empty-state message ("no overdue loans") is shown. Loading and error states are shown on all screens of this story.

## 5. Functional requirements

Each FR is atomic and traceable to a story; full Given/When/Then criteria are in section 4 (referenced by AC ID).

| ID | Story | Requirement | Priority | Acceptance criteria |
|---|---|---|---|---|
| FR-1 | US-1 | The system shall allow creating a book with title, author, ISBN and total copies. | M | AC-1.BE.1, AC-1.BE.2, AC-1.FE.2, AC-1.FE.6 |
| FR-2 | US-1 | The system shall list books and show a single book's details including available copies. | M | AC-1.BE.5, AC-1.FE.1, AC-1.FE.7 |
| FR-3 | US-1 | The system shall allow editing a book; total copies shall not be reducible below the number of active loans. | M | AC-1.BE.2, AC-1.BE.6, AC-1.FE.3 |
| FR-4 | US-1 | The system shall allow deleting a book only when it has no active loans, after user confirmation in the UI. | M | AC-1.BE.7, AC-1.BE.8, AC-1.FE.4, AC-1.FE.5 |
| FR-5 | US-1 | The system shall only accept valid ISBNs (ISBN-10 or ISBN-13 with correct check digit). | M | AC-1.BE.3, AC-1.FE.2 |
| FR-6 | US-1 | The system shall enforce ISBN uniqueness across books. | M | AC-1.BE.4, AC-1.FE.3 |
| FR-7 | US-2 | The system shall search/filter books by title, author, ISBN (case-insensitive contains) and availability, combining filters with AND. | M | AC-2.BE.1, AC-2.BE.2, AC-2.BE.5, AC-2.FE.1, AC-2.FE.2, AC-2.FE.5, AC-2.FE.6 |
| FR-8 | US-2 | The system shall paginate catalogue results with a deterministic order and report totals. | M | AC-2.BE.3, AC-2.BE.4, AC-2.FE.4 |
| FR-9 | US-2 | The system shall display each book's availability as "Available" (with free-copy count) or "All copies out". | M | AC-2.BE.6, AC-2.FE.3 |
| FR-10 | US-3 | The system shall allow registering a member with name and email; new members are Active. | M | AC-3.BE.1, AC-3.BE.2, AC-3.FE.2 |
| FR-11 | US-3 | The system shall enforce email format and case-insensitive uniqueness across members. | M | AC-3.BE.2, AC-3.BE.3, AC-3.FE.2 |
| FR-12 | US-3 | The system shall allow editing a member's name and email. | M | AC-3.BE.4, AC-3.FE.2 |
| FR-13 | US-3 | The system shall allow deactivating a member only when they have no active loans. | M | AC-3.BE.5, AC-3.BE.6, AC-3.FE.3, AC-3.FE.4 |
| FR-14 | US-3 | The system shall list members (paginated, optional status filter) with their active/inactive status and allow reactivation. | M (list) / S (reactivate, status filter) | AC-3.BE.7, AC-3.BE.8, AC-3.FE.1, AC-3.FE.5, AC-3.FE.6 |
| FR-15 | US-4 | The system shall check out one copy of a book to an active member, with due date = checkout date + 14 days. | M | AC-4.BE.1, AC-4.BE.6, AC-4.FE.1, AC-4.FE.2 |
| FR-16 | US-4 | The system shall refuse checkout when the member already has 3 active loans. | M | AC-4.BE.2, AC-4.FE.3 |
| FR-17 | US-4 | The system shall refuse checkout when the book has no available copy. | M | AC-4.BE.3, AC-4.FE.3 |
| FR-18 | US-4 | The system shall refuse checkout to inactive members and a second concurrent loan of the same book to the same member. | S | AC-4.BE.4, AC-4.BE.5, AC-4.FE.1, AC-4.FE.3 |
| FR-19 | US-4 | The system shall record the return of an active loan, restoring the copy to availability. | M | AC-4.BE.7, AC-4.BE.8, AC-4.FE.4 |
| FR-20 | US-4 | The system shall list overdue loans (active, due date before today) with member, book, due date and days overdue. | M | AC-4.BE.9, AC-4.FE.5, AC-4.FE.6 |
| FR-21 | US-4 | The system shall list active loans (filterable by member) to support selecting a loan to return, and shall keep copy and loan-limit counts consistent under concurrent requests. | M | AC-4.BE.10, AC-4.BE.11, AC-4.FE.4 |
| FR-22 | All | The system shall return errors in one consistent, documented JSON format that distinguishes validation (with per-field detail), not-found, conflict/business-rule, and unexpected errors **[CONFIRMED Q5]**. | M | AC-X.1 |
| FR-23 | All | The frontend shall provide navigation between Books/Search, Members, Issue & Return and Overdue screens. | M | AC-X.2 |
| FR-24 | All | The system shall load a small demo data set (about 10 books, 5 members, and a couple of loans including at least one overdue loan) every time the backend starts **[CONFIRMED Q8]**. | S | AC-X.4 |

Cross-cutting acceptance criteria (apply to all stories, copy into the relevant sub-tasks):
- [ ] AC-X.1 [BE] Given any failing request, when the error response is produced, then it is JSON containing at least: HTTP status code (400 validation, 404 not found, 409 conflict/business rule, 500 unexpected), a stable machine-readable error code, a human-readable message, a timestamp, and for validation errors a list of `{field, message}` entries; no stack traces or internal details are exposed. The error body uses the field names `status`, `code`, `message`, `timestamp` and `fieldErrors`. **[CONFIRMED Q5]**
- [ ] AC-X.2 [FE] Given the running application, when the librarian opens it, then a persistent navigation lets them reach Books/Search, Members, Issue & Return and Overdue screens without typing URLs.
- [ ] AC-X.3 [FE] Given the backend is unreachable or returns an unexpected error, when any screen loads or submits, then a friendly error message with a retry option is shown rather than a blank screen or raw error.
- [ ] AC-X.4 [BE] Given the backend has just started, when the catalogue, member list, active loans and overdue list are requested, then they contain a demo data set of about 10 valid books, 5 valid members (active) and a couple of active loans, at least one of which is overdue (due date before today), all consistent with the business rules (valid ISBNs, copy counts matching active loans, no member above 3 active loans); after a restart the same demo data set is loaded again and earlier changes are gone. **[CONFIRMED Q8]**

## 6. Non-functional requirements

All values were proposed as defaults and are confirmed by the user **[CONFIRMED Q7]**.

| ID | Category | Requirement | Measure |
|---|---|---|---|
| NFR-1 | Performance (API) | List, search and single-record API calls respond quickly under single-user load with up to 10,000 books, 1,000 members and 10,000 loans in memory. | 95th percentile <= 500 ms; checkout/return <= 500 ms, on a typical developer laptop. |
| NFR-2 | Performance (UI) | The frontend feels responsive. | Initial page load <= 3 s on a local network; user actions show feedback (loading indicator or result) within 1 s. |
| NFR-3 | Accessibility | The UI conforms to WCAG 2.1 Level AA for the implemented screens. | All forms operable by keyboard only; every input has a programmatically associated label; inline errors are associated with their fields and announced; status (availability, active/inactive, overdue) is not conveyed by colour alone; text contrast >= 4.5:1; automated accessibility check reports no critical violations. |
| NFR-4 | Browser support | The UI works on current evergreen desktop browsers. | Latest 2 major versions of Chrome, Edge, Firefox and Safari; layout usable from 768 px width up (desktop and tablet); phone layouts not required. |
| NFR-5 | Data integrity / concurrency | Business rules hold under concurrent requests. | Copy availability never negative; member never exceeds 3 active loans; verified by an automated concurrency test (AC-4.BE.11). |
| NFR-6 | Validation / security | All input is validated on the server regardless of client checks; output rendered in the UI is escaped to prevent script injection; no sensitive personal data beyond name and email is stored. | Server rejects invalid payloads (400) in automated tests; a stored `<script>` title/name renders as text. Authentication is intentionally absent (accepted decision), so the system is intended for local/trusted use only. |
| NFR-7 | Reliability / persistence | Data is held in memory and resets on restart (accepted). | Documented in README; application starts with the demo data set (FR-24, AC-X.4) on every start. |
| NFR-8 | Usability | Destructive or state-changing actions (delete book, deactivate member, return book) require confirmation; messages use plain language. | Verified by FE acceptance criteria AC-1.FE.4, AC-3.FE.3, AC-4.FE.4. |
| NFR-9 | Observability | The backend logs each request outcome and every business-rule rejection and unexpected error at an appropriate level; a health check endpoint is available. | Errors visible in logs without stack traces being returned to clients. |
| NFR-10 | Portability / run | The system builds and runs on Windows, Linux and macOS with the stated toolchain; backend and frontend start independently with documented commands. | README documents prerequisites, build, run and test commands; fresh-clone build succeeds. |
| NFR-11 | Maintainability / testability | Every business rule has automated tests at the backend level; key frontend flows have automated component or UI tests. | Each AC-n.BE.m is covered by at least one automated test; tests run with one command per project. |
| NFR-12 | Internationalisation | English-only UI; dates and times in API are ISO-8601, shown in the browser's locale. Due date and "today" use the server's local date. | No translation framework required; date handling consistent between API and UI. |
| NFR-13 | API documentation | The API contract (endpoints, parameters, error format) is documented. | Machine-readable or markdown API description kept in sync with implementation. |

## 7. Constraints

Mandated by the user (see `workflow/brief.md`):
- C-1 Backend: Java 21, Spring Boot 3, Maven, source in `backend/`.
- C-2 Frontend: React with TypeScript, built with Vite, source in `frontend/`.
- C-3 Database: in-memory H2; data resets on restart.
- C-4 No authentication or authorisation; single librarian-style user.
- C-5 Delivery: work on branch `feature`, PR to `main` after explicit authorisation; never merged automatically. PR contains app, stage docs in `.claude/docs/`, README and CHANGELOG only.
- C-6 Local toolchain available: Java 21, Maven, Node 24 / npm 11, `gh`.
- C-7 Jira publication to project AG after Stage 1 approval: 1 Epic + 4 Stories (US-1..US-4), each with Frontend and Backend sub-tasks. Hence exactly four stories, each with separable FE and BE criteria.
- C-8 Frontend and backend run as two separately started local processes.

## 8. Assumptions

All assumptions A-1..A-8 were proposed as defaults and have been confirmed by the user (see section 9). A-9 is implicit in the brief.

| ID | Assumption (confirmed) | Question |
|---|---|---|
| A-1 | ISBN accepted as ISBN-10 or ISBN-13 with valid check digit; hyphens/spaces stripped and stored normalised; title and author 1..255 characters; total copies whole number 1..999. | Q1 |
| A-2 | Member has only name (1..100 chars) and email (pragmatic format check, max 254, case-insensitive unique). Inactive members may be reactivated. Members are never deleted. Member list supports status filter. | Q2 |
| A-3 | No renewals, no fines, no reservations, no loan-period configuration. A member may not hold two active loans of the same book. Inactive members cannot borrow. Overdue means due date strictly before today (a loan due today is not overdue). Returns allowed anytime. | Q3 |
| A-4 | Pagination is page-number based, zero-based page index, default size 20, max 100; responses include total elements and total pages; catalogue ordered by title then ISBN; members by name then id. | Q4 |
| A-5 | Error responses are JSON with HTTP status, error code, message, timestamp and a per-field error list for validation failures. | Q5 |
| A-6 | Deleting a book with no active loans is allowed; its returned-loan history need not remain visible. Loan history is otherwise retained (returned loans are kept, not deleted) while the application runs. | Q6 |
| A-7 | NFR targets in section 6 (performance, WCAG 2.1 AA, browser matrix, 768 px minimum width, English only). | Q7 |
| A-8 | The application loads a small demo data set at every start so the UI is not empty after each restart; no separate member-facing portal. Today's date is the server's local date. | Q8 |
| A-9 | Single librarian operates one instance; no multi-library or multi-branch concept; one loan = one copy of one book. | (implicit; covered by brief, no question) |

## 9. Open questions and answers

All questions Q1-Q8 are resolved: the user accepted the recommended default for each. No questions remain open.

| ID | Question | Recommended default | Answer | Source |
|---|---|---|---|---|
| Q1 | Book field validation: what ISBN format and field limits? | ISBN-10 or ISBN-13 with check digit, hyphens/spaces ignored and stored normalised; title and author 1..255 chars; total copies 1..999. Alternatives: ISBN-13 only; any non-empty string up to 20 chars. | Confirmed: recommended default. ISBN-10 or ISBN-13 with check digit, hyphens/spaces ignored and stored normalised; title and author 1..255 chars; total copies 1..999. | user |
| Q2 | Member data rules: email format, member fields, reactivation? | Name 1..100 and email (pragmatic format, max 254, case-insensitive unique) only; reactivation allowed; no member deletion; status filter on list. Alternatives: strict RFC email validation; add phone/address; no reactivation. | Confirmed: recommended default. Member = name (1..100) + email only; pragmatic email check (max 254), case-insensitive unique; reactivation allowed; no member deletion; status filter on the list. | user |
| Q3 | Loan rules beyond the brief: renewals, fines, same-book duplicates, inactive borrowers? | No renewals, no fines; one active loan per member per book; inactive members cannot borrow; overdue = due date before today. Alternatives: allow one renewal (+14 days); a per-day fine amount shown on overdue list; allow multiple copies of same book per member. | Confirmed: recommended default. No renewals, no fines; one active loan per member per book; inactive members cannot borrow; overdue = due date strictly before today. | user |
| Q4 | Pagination defaults and ordering? | Zero-based page index, size 20, max 100; catalogue ordered by title then ISBN; members by name; apply to books, members and active loans. Alternatives: size 10 or 25; configurable sort field via API parameter; clamp instead of reject oversize requests. | Confirmed: recommended default. Zero-based page, default size 20, max 100, oversize rejected; books ordered by title then ISBN, members by name then id. | user |
| Q5 | Error response format? | Consistent JSON: status, error code, message, timestamp, `fieldErrors[{field,message}]`; statuses 400/404/409/500. Alternative: RFC 7807 "problem details" shape (equivalent content, standard field names). | Confirmed: recommended default. Custom consistent JSON error: status, code, message, timestamp, fieldErrors[{field,message}]; HTTP 400/404/409/500. | user |
| Q6 | Deleting a book that has only returned (historical) loans: allowed, and what happens to its history? | Allowed; history need not remain visible. Alternatives: block deletion if any loan history exists (offer "archive" instead); keep history via title/ISBN snapshot on loan records. | Confirmed: recommended default. Book deletion allowed when it has no active loans; returned-loan history of a deleted book need not remain visible. | user |
| Q7 | NFR targets: performance, accessibility, browser support? | p95 <= 500 ms API at 10k books; WCAG 2.1 AA; latest 2 versions of Chrome/Edge/Firefox/Safari; >= 768 px width; English only. Alternatives: relaxed (no formal accessibility target, Chrome/Edge only); stricter (mobile-responsive from 360 px). | Confirmed: recommended default. p95 <= 500 ms API at 10k books; WCAG 2.1 AA; latest 2 versions of Chrome/Edge/Firefox/Safari; >= 768 px width; English only. | user |
| Q8 | Seed data on startup (since H2 resets) and member-facing access? | Load a small demo dataset (about 10 books, 5 members, a couple of loans including one overdue) at start-up to ease demo/testing; no separate member portal. Alternatives: start empty; seed only under a "demo" profile. | Confirmed: recommended default. Load a small demo dataset (~10 books, 5 members, a couple of loans including one overdue) at every start; no separate member portal. | user |

## 10. Out of scope

- Authentication, authorisation, roles, user accounts, member self-service portal.
- Persistent storage or data migration (data resets on restart); backup/restore.
- Fines, payments, reservations/holds, renewals, notifications (email/SMS), barcode scanning.
- Multiple library branches, multiple copies tracked individually by barcode/copy ID (only a count per book).
- Importing/exporting data, reports beyond the overdue list, audit trail, loan history views beyond active and overdue lists.
- Member deletion; book categories/genres/cover images; multiple authors per book.
- Internationalisation beyond English; mobile phone layouts; offline use.
- Deployment/hosting, CI/CD pipelines, containerisation (not requested).

## 11. Traceability summary

| Story | Jira story | FRs | BE criteria | FE criteria | Related NFRs |
|---|---|---|---|---|---|
| US-1 Manage the book catalogue | US-1 (+ FE, BE sub-tasks) | FR-1..FR-6 | AC-1.BE.1..8 | AC-1.FE.1..7 | NFR-3, 6, 8 |
| US-2 Search and browse | US-2 (+ FE, BE sub-tasks) | FR-7..FR-9 | AC-2.BE.1..6 | AC-2.FE.1..6 | NFR-1, 2, 3, 4 |
| US-3 Manage members | US-3 (+ FE, BE sub-tasks) | FR-10..FR-14 | AC-3.BE.1..8 | AC-3.FE.1..6 | NFR-3, 6, 8 |
| US-4 Borrow and return | US-4 (+ FE, BE sub-tasks) | FR-15..FR-21 | AC-4.BE.1..11 | AC-4.FE.1..6 | NFR-1, 3, 5, 8 |
| Cross-cutting | placed in relevant sub-tasks | FR-22, FR-23, FR-24 | AC-X.1, AC-X.4 | AC-X.2, AC-X.3 | NFR-6, 7, 9, 13 |

## 12. Jira traceability

Published to Jira site https://mypersonaljirademo.atlassian.net, project AG. Epic: [AG-23](https://mypersonaljirademo.atlassian.net/browse/AG-23) "Library Management System".

| Story | Jira Story | Frontend sub-task [FE] | Backend sub-task [BE] |
|---|---|---|---|
| US-1 Manage the book catalogue | [AG-24](https://mypersonaljirademo.atlassian.net/browse/AG-24) | [AG-28](https://mypersonaljirademo.atlassian.net/browse/AG-28) | [AG-29](https://mypersonaljirademo.atlassian.net/browse/AG-29) |
| US-2 Search and browse the catalogue | [AG-25](https://mypersonaljirademo.atlassian.net/browse/AG-25) | [AG-30](https://mypersonaljirademo.atlassian.net/browse/AG-30) | [AG-31](https://mypersonaljirademo.atlassian.net/browse/AG-31) |
| US-3 Manage members | [AG-26](https://mypersonaljirademo.atlassian.net/browse/AG-26) | [AG-32](https://mypersonaljirademo.atlassian.net/browse/AG-32) | [AG-33](https://mypersonaljirademo.atlassian.net/browse/AG-33) |
| US-4 Borrow and return books | [AG-27](https://mypersonaljirademo.atlassian.net/browse/AG-27) | [AG-34](https://mypersonaljirademo.atlassian.net/browse/AG-34) | [AG-35](https://mypersonaljirademo.atlassian.net/browse/AG-35) |

Notes:
- The Jira sub-task descriptions contain the story's FE/BE acceptance criteria copied verbatim from this document as of 2026-11-25. If the Jira text and this document diverge, this document is the source of truth.
- The cross-cutting criteria (AC-X.1 to AC-X.4) are placed in the relevant sub-tasks, as noted in section 11.
- The Jira project used is AG. Constraint C-7 in section 7 was corrected from CC to AG on 2026-11-25 after the user selected project AG (project CC does not exist on the Jira site).
