# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Library Management System (Jira epic AG-23): Spring Boot 3.5 / Java 21 backend (`backend/`) with an in-memory H2 database and a React 19 / TypeScript / Vite frontend (`frontend/`).
- Book catalogue (AG-24): create, view, edit and delete books; ISBN-10 and ISBN-13 validation with check digit, stored normalised; unique ISBN; total copies limited to 1..999 and cannot be set below the number of active loans; deletion refused while a book is on loan.
- Search and browse (AG-25): case-insensitive title, author and ISBN filters (ISBN matches regardless of hyphens and spaces), availability filter, zero-based pagination (default size 20, maximum 100) in a stable order, and "Available" / "All copies out" badges.
- Members (AG-26): register and edit members (name and email, case-insensitive unique email), deactivate (refused while the member has active loans) and reactivate, status filter and name/email search.
- Borrowing and returning (AG-27): checkout with a 14-day due date, a limit of 3 active loans per member, one active loan per member and book, and no checkout for inactive members or when no copy is free; return at any time; active-loan list and overdue list (most overdue first).
- Concurrency control: pessimistic row locks in the order loan, member, book, counter columns with database CHECK and UNIQUE constraints, and a `@Version` column on books and members as a lost-update backstop.
- Demo data (10 books, 5 members, 3 active loans including one overdue and one due today) loaded on every start; disable with `library.demo-data.enabled=false`.
- Consistent JSON error model (`status`, `code`, `message`, `timestamp`, `fieldErrors`) for all API errors.
- API documentation: OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, and a health check at `/actuator/health`.
- Root `README.md` with prerequisites, run and test commands, security settings, API endpoint table and error format.
- Shared ISBN and email test vectors (`shared-test-vectors/`) used by both test suites.

### Security

- Request filter with a `Host` allowlist on all requests, an `Origin` allowlist on write requests, and a required `X-Requested-With: library-ui` header on every method except GET, HEAD and OPTIONS (403 `FORBIDDEN_ORIGIN` / `MISSING_REQUIRED_HEADER`); no CORS configuration.
- The application has no authentication or authorisation and is intended for local use only.
- The request log line records method, path, query parameter names, status and duration, but not request bodies or query values. The Hibernate constraint-violation logger is configured off in `application.yml`.
