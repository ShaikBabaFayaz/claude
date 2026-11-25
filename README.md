# claude

## Library Management System

A small web application for a library: manage the **book catalogue**, **search** it, manage **members**, and **check books out and back in** (loan period 14 days, at most 3 active loans per member, overdue list).

- `backend/` - Java 21, Spring Boot 3.5, H2 in-memory database, REST API under `/api`.
- `frontend/` - React + TypeScript (Vite) single-page app.
- `shared-test-vectors/` - ISBN and email examples used by both test suites.

**Local use only. There is no authentication or authorisation.** Do not expose it to a network.

### Prerequisites

Java 21, Maven 3.9 or newer, Node 24 with npm 11.

### Run

Run each command from the repository root (no `cd` needed; works in bash and PowerShell). Start the backend in one terminal and the frontend in a second one:

```
mvn -f backend/pom.xml spring-boot:run      # http://127.0.0.1:8080
```

```
npm --prefix frontend install               # first time only
npm --prefix frontend run dev               # http://localhost:5173, proxies /api to the backend
```

Open http://localhost:5173. Health check: http://127.0.0.1:8080/actuator/health.

Data lives in memory: **everything resets when the backend restarts.** On every start a demo data set is loaded (10 books, 5 members, 3 active loans, one of them overdue and one due today; dates are relative to today). Set `library.demo-data.enabled=false` to start empty.

### Tests

Run each command from the repository root (works in bash and PowerShell):

```
mvn -B -f backend/pom.xml test
```

```
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
```

### Security settings

- Every request except GET, HEAD and OPTIONS (in practice POST, PUT, DELETE) must carry the header `X-Requested-With: library-ui`; otherwise the API answers 403 `MISSING_REQUIRED_HEADER`. The frontend adds it automatically. In Swagger UI it is a required parameter on each write operation with the default value `library-ui`, which "Try it out" sends.
- The backend only accepts requests whose `Host` (and, for writes, `Origin`) is on an allowlist, default `localhost`/`127.0.0.1` on ports 8080 and 5173. If you change a port, change `library.security.allowed-hosts` and `library.security.allowed-origins` in `backend/src/main/resources/application.yml` too; otherwise you get 403 `FORBIDDEN_ORIGIN`.

### API

Interactive docs: http://127.0.0.1:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

| Endpoint | Purpose |
|---|---|
| `GET/POST /api/books`, `GET/PUT/DELETE /api/books/{id}` | catalogue; `GET` filters: `title`, `author`, `isbn`, `availability=available\|unavailable`, `page`, `size` |
| `GET/POST /api/members`, `GET/PUT /api/members/{id}` | members; `GET` filters: `status`, `search`, `page`, `size` |
| `POST /api/members/{id}/deactivate`, `.../reactivate` | member status |
| `POST /api/loans` `{memberId, bookId}` | check out (201 + `Location`) |
| `POST /api/loans/{id}/return` | return a book |
| `GET /api/loans/{id}` | one loan |
| `GET /api/loans/active?memberId=&bookId=&page=&size=` | active loans (paged) |
| `GET /api/loans/overdue` | overdue loans, most overdue first |

Lists use `{content, page, size, totalElements, totalPages}`; `size` is 1..100 (default 20). Every error has the same JSON shape:

```json
{ "status": 409, "code": "NO_COPY_AVAILABLE", "message": "No copy of this book is available.",
  "timestamp": "2026-11-25T10:15:30.123+01:00", "fieldErrors": [] }
```

`fieldErrors` is a list of `{field, message}`. It is filled for validation errors (400 `VALIDATION_FAILED`) and for three other errors: 409 `ISBN_ALREADY_EXISTS` (field `isbn`), 409 `EMAIL_ALREADY_EXISTS` (field `email`) and 400 `TOTAL_COPIES_BELOW_ACTIVE_LOANS` (field `totalCopies`). It is empty for all other errors.

### Known limits

- Tomcat itself answers a few malformed request lines (for example invalid percent-encoding in the URL) with its own HTML 400 page, not the JSON error shape.
- H2's `lower()` depends on the JVM default locale, so case-insensitive search can misbehave when the JVM runs with a Turkish default locale.
- Do not enable DEBUG logging for `org.springframework.web`: it logs query strings, which can contain search terms and emails.
