# AG-23 Add Library Management System (Spring Boot + React)

**Status:** ready for review

Branch `feature` -> `main`. Epic: [AG-23](https://mypersonaljirademo.atlassian.net/browse/AG-23).

## Summary

Adds a small library management system for a single librarian: a Java 21 / Spring Boot 3.5 REST backend with an in-memory H2 database and a React 19 / TypeScript / Vite single-page frontend. The librarian can manage the book catalogue, search it, manage members, and check books out and back in, with a list of overdue loans. The scope follows `.claude/docs/requirements.md`, which is split into four stories:

| Story | Jira | Scope |
|---|---|---|
| US-1 Manage the book catalogue | [AG-24](https://mypersonaljirademo.atlassian.net/browse/AG-24) (sub-tasks [AG-28](https://mypersonaljirademo.atlassian.net/browse/AG-28) FE, [AG-29](https://mypersonaljirademo.atlassian.net/browse/AG-29) BE) | Create, view, edit and delete books; ISBN-10/13 validation with check digit; ISBN uniqueness; total copies cannot drop below active loans; no delete while loaned |
| US-2 Search and browse the catalogue | [AG-25](https://mypersonaljirademo.atlassian.net/browse/AG-25) ([AG-30](https://mypersonaljirademo.atlassian.net/browse/AG-30) FE, [AG-31](https://mypersonaljirademo.atlassian.net/browse/AG-31) BE) | Case-insensitive title/author/ISBN filters, availability filter, pagination, "Available" / "All copies out" badges |
| US-3 Manage members | [AG-26](https://mypersonaljirademo.atlassian.net/browse/AG-26) ([AG-32](https://mypersonaljirademo.atlassian.net/browse/AG-32) FE, [AG-33](https://mypersonaljirademo.atlassian.net/browse/AG-33) BE) | Register and edit members, case-insensitive unique email, deactivate (blocked while loans are active) and reactivate, status filter and search |
| US-4 Borrow and return books | [AG-27](https://mypersonaljirademo.atlassian.net/browse/AG-27) ([AG-34](https://mypersonaljirademo.atlassian.net/browse/AG-34) FE, [AG-35](https://mypersonaljirademo.atlassian.net/browse/AG-35) BE) | Checkout (14-day loan, max 3 active loans per member, no duplicate active loan of a book, inactive members refused), return, active-loan list, overdue list (most overdue first) |

Nothing in this PR is merged automatically; merging is a separate decision.

## Changes Made

**Backend (`backend/`)**
- Spring Boot 3.5 / Java 21 / Maven module `com.library:library-backend`, feature packages `book`, `member`, `loan`, plus `common`, `seed`, `config`.
- REST API under `/api` for books, members and loans (endpoint table in the root `README.md`); OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`; health check at `/actuator/health`.
- Schema in an explicit `schema.sql` (named CHECK and UNIQUE constraints), Hibernate `ddl-auto=validate`.
- One error model for every failure: `{status, code, message, timestamp, fieldErrors[{field, message}]}`.
- Demo data (10 books, 5 members, 3 active loans including one overdue and one due today, dates relative to today) loaded on every start; disable with `library.demo-data.enabled=false`.

**Frontend (`frontend/`)**
- React 19, TypeScript (strict), Vite 7, React Router, TanStack Query, React Hook Form, CSS Modules, native `<dialog>` for confirmations.
- Screens: Books/Search, Members, Issue & Return (with active loans and return), Overdue. Inline field errors mapped from the backend `fieldErrors`; loading, empty and error states; status shown as text, not colour alone.
- Vite dev server on `localhost:5173` proxies `/api` to the backend, so no CORS is configured.

**Shared test data (`shared-test-vectors/`)**
- ISBN and email examples used by both the backend and frontend test suites, so both validators agree.

**Docs and changelog**
- Root `README.md`: prerequisites, run and test commands, security settings, API endpoint table, error format.
- `.claude/docs/*.md`: stage documents (requirements, architecture, design review, implementation plan, code review, verification report).
- `CHANGELOG.md`: first entry (Keep a Changelog style). The file is tracked and in the diff although `.gitignore` now lists it.
- `.gitignore`: ignores `workflow/`, `.vscode/`, `docs/` (the unrelated codemie analytics folder, not part of this diff), `CHANGELOG.md`, `target/`, `node_modules/`, `dist/` and `*.log`; the `.claude/` folder is tracked.

**Agent framework (`.claude/`, `CLAUDE.md`, `.mcp.json`)**
- The diff also contains the agentic SDLC framework that produced this work: an orchestrator plus 8 stage agents (`.claude/agents/`), stage skills (`.claude/skills/`), slash commands (`.claude/commands/`), guard hooks (`.claude/hooks/`), the shared settings (`.claude/settings.json`, `.claude/settings.local.json`), the project rules in `CLAUDE.md`, and the GitHub MCP server config `.mcp.json` (the token is read from an environment variable, no literal credential).

**Design highlights** (details in `.claude/docs/architecture.md`)
- **Concurrency**: checkout and return take pessimistic write row locks (JPA `PESSIMISTIC_WRITE`) in the fixed order loan -> member -> book, and the update, delete and deactivate/reactivate paths of `BookService` and `MemberService` take their row lock before reading state. The active-loan counters on `book` and `member` change only through the `LoanService` use cases and the demo-data seeder.
- **Database backstops**: CHECK constraints on the counters and a UNIQUE key against a duplicate active loan; a JPA `@Version` column on `book` and `member` guards against lost updates.
- **Request filter**: a single filter enforces a `Host` allowlist on every request, an `Origin` allowlist on writes, and the header `X-Requested-With: library-ui` on every method except GET, HEAD and OPTIONS. Failures return 403 `FORBIDDEN_ORIGIN` or `MISSING_REQUIRED_HEADER`.
- **Logging**: the request log line records method, path, query parameter names, status, duration and a request id, but not request bodies or query values. The Hibernate constraint-violation logger (`org.hibernate.engine.jdbc.spi.SqlExceptionHelper`) is configured off in `application.yml`.
- **Time**: all date logic uses an injected `Clock`; "today" is the server's local date.
- **Data and access**: in-memory H2, everything resets on restart. There is no authentication or authorisation; the application is for local use only and must not be exposed to a network.

## Test Evidence

Stage 7 verification verdict: **PASS for the checks that were run** (full backend and frontend suites, lint, build, live smoke run, README run commands, restart/reset behaviour, OpenAPI document). Full detail, including per-command output, is in [`.claude/docs/verification-report.md`](.claude/docs/verification-report.md). Code review is in [`.claude/docs/code-review.md`](.claude/docs/code-review.md).

| Area | Command | Result |
|---|---|---|
| Backend tests | `mvn -B -f backend/pom.xml test` | 551 tests, 0 failures, exit 0 |
| Backend repeat run | `mvn -B -q -f backend/pom.xml test -Dtest=RequestLoggingFilterTest`, run 10 times | 10 of 10 runs exit 0, 0 failures |
| Frontend tests | `npm --prefix frontend test` | 431 tests in 31 files passed, exit 0 |
| Frontend lint | `npm --prefix frontend run lint` | clean, exit 0 |
| Frontend build | `npm --prefix frontend run build` | succeeded, exit 0 |
| Live smoke run | packaged backend started with demo data, about 55 `curl` calls (run once) | results matched expectations for books, members and loans CRUD, loan rules and error codes, the header and Host checks, and the overdue list |
| README run commands, run literally from the repo root | `mvn -f backend/pom.xml spring-boot:run`, `npm --prefix frontend run dev`, then `curl` on `/actuator/health`, `http://localhost:5173/` and `http://localhost:5173/api/books?size=2` | backend started with demo data (10 books, 5 members, 3 active loans), health `{"status":"UP"}`, Vite page 200, proxied `/api/books` returned backend JSON |
| Restart / reset check | created a book with `POST /api/books`, stopped the backend, started it again with the README command | the created book was gone (404) and the demo data was back: 10 books, 5 members, 3 active loans |
| OpenAPI document | `curl http://127.0.0.1:8080/v3/api-docs` | 200, OpenAPI 3.1.0; the `X-Requested-With` header parameter is listed on all 9 write operations |

Versions in use: Spring Boot 3.5.16, Java 21, React 19, Vite 7.

## Reviewer Checklist

- [ ] Scope of the diff: the final `.gitignore` ignores `workflow/`, `.vscode/`, `docs/`, `CHANGELOG.md`, `target/`, `node_modules/`, `dist/` and `*.log`. Confirm the diff contains `backend/`, `frontend/`, `shared-test-vectors/`, root `README.md` and the updated `.gitignore`, plus the `.claude/` folder (agents, commands, hooks, skills, settings and the stage documents in `.claude/docs/*.md`), `CLAUDE.md` and `.mcp.json`, which are tracked. `CHANGELOG.md` is also tracked and in the diff even though it is listed in `.gitignore` (it was added before it was ignored). The diff must not contain `workflow/`, build output (`target/`, `dist/`, `node_modules/`), `.codemie/` or the unrelated `docs/` folder (codemie analytics, ignored).
- [ ] Architecture conformance: feature packages and layering match `.claude/docs/architecture.md` (sections 4 and 7).
- [ ] Concurrency: every write path to `book` and `member` takes its row lock before reading state, in the order loan -> member -> book; counters change only through `LoanService` and the seeder.
- [ ] Schema: `backend/src/main/resources/schema.sql` constraints (CHECK counters, UNIQUE active loan, unique ISBN and email) match the entities.
- [ ] Security-sensitive files: the Host/Origin/`X-Requested-With` filter and its allowlists in `application.yml`; no CORS configuration; no `dangerouslySetInnerHTML` in the frontend.
- [ ] Error model: all failures handled by the application use the common JSON shape and carry no stack traces or internal details.
- [ ] Logging: the request log (`RequestLoggingFilter`) records method, path, parameter names, status and duration, but not request bodies or query values; the Hibernate constraint-violation logger is configured off in `application.yml`.
- [ ] Configuration: ports 8080 (backend) and 5173 (frontend, `strictPort`) are consistent with the allowlists; demo data flag `library.demo-data.enabled`.
- [ ] Frontend: forms have associated labels and inline errors; status is conveyed by text; destructive actions (delete book, deactivate member, return book) ask for confirmation.
- [ ] Docs: the `README.md` Run, Tests, Security settings and API sections match the code; `CHANGELOG.md` entry is accurate.
- [ ] Agent framework files: `.mcp.json` holds only the `${GITHUB_PAT}` environment-variable reference and no literal token; the guard hooks in `.claude/hooks/` match the rules in `CLAUDE.md`.
- [ ] Jira keys in this description (AG-23..AG-35) match the published tickets.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
