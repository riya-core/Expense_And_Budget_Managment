# Expense & Budget Management System: Shared Team Contract

Everything here must be **identical** across Person 1, 2 and 3. Commit it as `docs/TEAM_CONTRACT.md`. If something needs to change, announce it to the team first, then update this file in the same pull request.

Owners: **P1** = Backend core, **P2** = Budgets/Alerts/Analytics/Reports, **P3** = DB/Frontend/DevOps.

---

## 0. Decisions to lock in week 1

| # | Decision | Recommended | Status |
|---|---|---|---|
| 1 | Auth mechanism | **JWT** (stateless). Page routes are public shells, JS calls APIs with the token | ☐ |
| 2 | Principal identity | Principal name = user's **email** (`CurrentUser` relies on this) | ☐ |
| 3 | Who owns `CurrentUser` helper | P2's `util/CurrentUser.java` (P1 may replace it, same method names) | ☐ |
| 4 | Error JSON format | Section 7 below | ☐ |
| 5 | Pagination format | Spring `Page` JSON (Section 6.3) | ☐ |
| 6 | Currency | Single currency, `DECIMAL(12,2)` | ☐ |
| 7 | Base package | `com.expensetracker` | ☐ |

---

## 1. Global conventions

| Item | Rule |
|---|---|
| Java / Boot | Java 17, Spring Boot 3.x, Maven, Lombok |
| Base package | `com.expensetracker` |
| Sub-packages | `config, controller, service, repository, entity, dto, exception, util` |
| JSON field names | **camelCase** |
| Dates | ISO `yyyy-MM-dd` (`2026-10-13`) |
| Date-times | ISO `yyyy-MM-dd'T'HH:mm:ss` |
| Money | Java `BigDecimal`, DB `DECIMAL(12,2)`, JSON number, never `double` in entities or DTOs |
| Month | integer `1-12`. Year: 4-digit integer |
| Month label (charts) | `"2026-10"` (`YearMonth.toString()`) |
| DTOs | Java `record`s named `XxxRequest`, `XxxResponse`, `XxxDto` |
| Entities | Never returned from controllers. Always map to DTOs |
| Ownership scoping | **Every** query is filtered by the logged-in user's id. Never trust a `userId` from the request |
| IDs | `Long`, auto-increment |
| Time zone | Server default; keep DB and app in the same zone |

---

## 2. Database schema (exact table and column names)

P3 owns the Flyway scripts. Everyone codes against these names.

```sql
CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,             -- BCrypt hash
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    type     VARCHAR(10)  NOT NULL,                -- INCOME | EXPENSE
    user_id  BIGINT NULL,                          -- NULL = default category
    CONSTRAINT fk_cat_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    category_id  BIGINT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    type         VARCHAR(10) NOT NULL,             -- INCOME | EXPENSE
    description  VARCHAR(255),
    date         DATE NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE INDEX idx_tx_user_date     ON transactions (user_id, date);
CREATE INDEX idx_tx_user_cat_date ON transactions (user_id, category_id, date);

CREATE TABLE budgets (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    category_id  BIGINT NOT NULL,
    month        INT NOT NULL,
    year         INT NOT NULL,
    limit_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_budget_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT uk_budget_user_cat_month_year UNIQUE (user_id, category_id, month, year)
);

CREATE TABLE alerts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    budget_id  BIGINT NOT NULL,
    type       VARCHAR(20) NOT NULL,               -- WARNING | EXCEEDED
    message    VARCHAR(255) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_alert_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE
);
```

Rules:
- Enums are stored as **strings** (`@Enumerated(EnumType.STRING)`), never ordinals.
- `ddl-auto` must be `validate` (or `none`). **Flyway is the only thing that changes the schema.** Nobody uses `update` or `create`.
- Never edit an applied migration. Add a new `V3__...sql`.
- Column names above are final. Changing one means updating the entities of whoever uses it.

### Flyway files
| File | Content | Owner |
|---|---|---|
| `V1__create_tables.sql` | All tables above | P3 |
| `V2__seed_categories.sql` | Default categories (below) | P3 |
| `V3__demo_data.sql` | Optional demo user and transactions | P3 |

### Default categories (`user_id = NULL`)
- **INCOME:** Salary, Freelance, Gift, Other Income
- **EXPENSE:** Food, Rent, Transport, Utilities, Entertainment, Health, Shopping, Education, Other

---

## 3. Shared enums

```java
package com.expensetracker.entity;
public enum TransactionType { INCOME, EXPENSE }      // owner: P1; used by everyone
public enum AlertType       { WARNING, EXCEEDED }    // owner: P2
```

Budget progress status (string in `BudgetResponse.status`, calculated by the backend; **P3 must not recompute it**):

| status | percentUsed | Progress bar colour |
|---|---|---|
| `OK` | below 80 | green |
| `WARNING` | 80 up to (not including) 100 | yellow |
| `EXCEEDED` | 100 or more | red |

---

## 4. Entity field names (Java)

Everyone uses exactly these getters/setters.

| Entity | Owner | Fields |
|---|---|---|
| `User` | P1 | `Long id`, `String name`, `String email`, `String password`, `LocalDateTime createdAt` |
| `Category` | P1 | `Long id`, `String name`, `TransactionType type`, `User user` (null = default) |
| `Transaction` | P1 | `Long id`, `User user`, `Category category`, `BigDecimal amount`, `TransactionType type`, `String description`, `LocalDate date`, `LocalDateTime createdAt` |
| `Budget` | P2 | `Long id`, `User user`, `Category category`, `int month`, `int year`, `BigDecimal limitAmount` |
| `Alert` | P2 | `Long id`, `User user`, `Budget budget`, `AlertType type`, `String message`, `boolean readFlag`, `LocalDateTime createdAt` |

All entities use Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, and relationships are `@ManyToOne(fetch = LAZY)`.

---

## 5. Shared Java classes and functions between people

### 5.1 Hand-off interfaces (the critical ones)

| Provider | Class / method | Consumer | Purpose |
|---|---|---|---|
| **P2** | `BudgetAlertService.check(Transaction tx)` | P1 | Call in `TransactionService.create()` and `update()` after saving. Never in delete |
| **P1** | `UserRepository.findByEmail(String email) : Optional<User>` | P2 (`CurrentUser`) | Resolve the logged-in user |
| **P1** | `CategoryRepository.findById(Long)` | P2 (`BudgetService`) | Validate budget category |
| **P1** | `Transaction`, `Category`, `User` entities (Section 4) | P2 | Analytics JPQL depends on the field names |
| **P2** | `CurrentUser.get()` / `CurrentUser.id()` | P1, P2 | Get the logged-in user. P1 may use it in their services |
| **P2** | `TransactionAnalyticsRepository` | P2 only | Aggregate queries, so P1's repository is never edited |
| **P2** | `AnalyticsService.monthlyFor(Long uid, YearMonth ym)` | P2 | Used by the reports |

### 5.2 Query methods P1's `TransactionRepository` should expose (needed by P1's own filtering)

```java
Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);
Optional<Transaction> findByIdAndUserId(Long id, Long userId);
```

P2 does **not** need anything else from it because it has its own analytics repository.

### 5.3 Naming for service and repository methods (so code looks consistent)

| Pattern | Example |
|---|---|
| Ownership lookup | `findByIdAndUserId(id, userId)` |
| List for user | `findByUserId...` |
| Services return DTOs, not entities | `BudgetResponse create(BudgetRequest)` |
| Not found | `ResponseStatusException(HttpStatus.NOT_FOUND, "...")` (or P1's `ResourceNotFoundException`, if the `@RestControllerAdvice` maps both to the same JSON) |
| Duplicate | `409 CONFLICT` |
| Validation failure | `400 BAD_REQUEST` |

---

## 6. REST API contract

All endpoints are under `/api`, JSON in and out, and need `Authorization: Bearer <token>` except register and login.

### 6.1 Auth (P1)

| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `{"name","email","password"}` | `201` `{"id","name","email"}` |
| POST | `/api/auth/login` | `{"email","password"}` | `200` `{"token","tokenType":"Bearer","expiresIn":3600,"user":{"id","name","email"}}` |

Public (no auth): `/api/auth/**`, `/login`, `/register`, static assets (`/css/**`, `/js/**`), Swagger (`/swagger-ui/**`, `/v3/api-docs/**`).

### 6.2 Categories (P1)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/categories?type=EXPENSE` | Returns the user's categories plus defaults |
| POST / PUT / DELETE | `/api/categories[/{id}]` | Defaults (`userId = null`) can't be edited or deleted |

`CategoryRequest`: `{"name": "Food", "type": "EXPENSE"}`
`CategoryResponse`: `{"id": 1, "name": "Food", "type": "EXPENSE", "isDefault": true}`

### 6.3 Transactions (P1)

| Method | Path |
|---|---|
| GET | `/api/transactions?page=0&size=10&sort=date,desc&from=2026-10-01&to=2026-10-31&categoryId=1&type=EXPENSE` |
| GET / POST / PUT / DELETE | `/api/transactions[/{id}]` |

`TransactionRequest`:
```json
{ "categoryId": 1, "type": "EXPENSE", "amount": 250.00, "description": "Lunch", "date": "2026-10-13" }
```
`TransactionResponse`:
```json
{ "id": 7, "categoryId": 1, "categoryName": "Food", "type": "EXPENSE",
  "amount": 250.00, "description": "Lunch", "date": "2026-10-13", "createdAt": "2026-10-13T12:30:00" }
```
List response is Spring's page shape, which P3's table and pagination UI depend on:
```json
{ "content": [ ... ], "number": 0, "size": 10, "totalElements": 42, "totalPages": 5, "first": true, "last": false }
```

### 6.4 Budgets (P2)

| Method | Path |
|---|---|
| GET | `/api/budgets?month=10&year=2026` (omit both for all budgets) |
| GET / POST / PUT / DELETE | `/api/budgets[/{id}]` |

`BudgetRequest`: `{"categoryId": 1, "month": 10, "year": 2026, "limitAmount": 5000.00}`
`BudgetResponse`:
```json
{ "id": 3, "categoryId": 1, "categoryName": "Food", "month": 10, "year": 2026,
  "limitAmount": 5000.00, "spent": 4200.00, "percentUsed": 84.0, "status": "WARNING" }
```
Errors: `409` if a budget already exists for that user, category, month and year. `400` if the category isn't an EXPENSE category.

### 6.5 Summary / analytics (P2)

| Method | Path | Response |
|---|---|---|
| GET | `/api/summary/monthly?month=10&year=2026` | `{"month":10,"year":2026,"totalIncome":..,"totalExpense":..,"balance":..}` |
| GET | `/api/summary/by-category?month=10&year=2026&type=EXPENSE` | `[{"category":"Food","total":4200.00}, ...]` → pie chart |
| GET | `/api/summary/trend?months=6` | `[{"label":"2026-05","income":..,"expense":..}, ...]` → bar/line charts (oldest first, gaps filled with 0) |

Month and year default to the current month. `months` defaults to 6 (max 24).

### 6.6 Alerts (P2)

| Method | Path | Response |
|---|---|---|
| GET | `/api/alerts` | `[{"id","budgetId","type":"WARNING","message","read":false,"createdAt"}]` newest first |
| GET | `/api/alerts/unread-count` | `{"count": 3}` → navbar bell badge |
| PUT | `/api/alerts/{id}/read` | updated alert |
| PUT | `/api/alerts/read-all` | `204` |

### 6.7 Reports (P2)

`GET /api/reports/monthly?month=10&year=2026&format=pdf|xlsx|csv` returns the file bytes.
- `Content-Disposition: attachment; filename="report-2026-10.pdf"`
- Content types: `application/pdf`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `text/csv; charset=UTF-8`.

> **P3 note:** because auth is a Bearer header, a plain `<a href>` download link won't work. Use `fetch()` with the header, convert the response to a blob, and trigger the download from JS (create an object URL and click a temporary `<a download>`).

---

## 7. Standard error JSON (P1 builds it in `@RestControllerAdvice`)

```json
{
  "timestamp": "2026-10-13T12:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/budgets",
  "fieldErrors": [ { "field": "limitAmount", "message": "must be greater than or equal to 0.01" } ]
}
```
`fieldErrors` is only present for validation errors. The advice must also handle `ResponseStatusException` (used by P2) and produce this same shape. P3 shows `message` in error toasts.

| Status | Meaning |
|---|---|
| 400 | Validation or bad input |
| 401 | Missing or invalid token (frontend redirects to `/login`) |
| 404 | Not found, **or belongs to another user** (don't leak existence) |
| 409 | Duplicate (budget, email) |
| 500 | Unexpected |

---

## 8. Configuration keys and environment variables

| Key / env var | Used by | Notes |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | all | In `application.yml`: `${DB_URL:jdbc:mysql://localhost:3306/expense_db}` |
| `spring.jpa.hibernate.ddl-auto` | all | `validate` |
| `spring.flyway.enabled` | P3 | `true` |
| `jwt.secret`, `jwt.expiration-ms` | P1 | secret from env `JWT_SECRET`, never committed |
| `MAIL_USER`, `MAIL_PASS` | P2 | Mailtrap or Gmail App Password |
| `app.mail.enabled`, `app.mail.from` | P2 | `false` disables emails |
| `app.alerts.cron` | P2 | default `0 0 8 * * *` |
| Profiles | P3 | `dev` (local MySQL, mail off) and `prod` (Docker) |
| DB name | all | `expense_db` |

**Secrets (passwords, JWT secret, mail credentials) never go into Git.** Commit a `.env.example` only.

---

## 9. Frontend ↔ backend rules (P3 and everyone)

- Token is stored once, under a single `localStorage` key: **`et_token`** (user info under `et_user`).
- All API calls go through one shared JS helper (`/js/api.js`) that adds `Authorization: Bearer <token>`, parses the error JSON, and redirects to `/login` on 401.
- Chart data comes only from `/api/summary/*`. The frontend may add up and format numbers it already received (for example the total of several budgets, or a percentage shown as a label), but must never recompute backend-owned values (budget `status`, `percentUsed`).
- Progress-bar colour comes from `BudgetResponse.status`.
- Page routes (Thymeleaf): `/login`, `/register`, `/dashboard`, `/transactions`, `/budgets`, `/reports`. They render shells and load data by JS.

---

## 10. Git, ownership and process

**Branches:** `main` (stable), `develop` (integration), `feature/<person>-<task>`. Pull requests go into `develop` with one reviewer, and `develop` merges into `main` only after the weekly demo.

**Commit messages:** `feat(budget): add duplicate check`, `fix(auth): ...`, `test(alert): ...`

**File ownership (edit only your own; ask the owner for anything else):**

| Area | Owner |
|---|---|
| `pom.xml` (base) | P1 (others send dependency lines in a PR) |
| `application.yml`, Flyway scripts, Docker | P3 |
| `entity/User, Category, Transaction`, auth, security config, exception advice | P1 |
| `entity/Budget, Alert`, `service/Budget*, Alert*, Analytics*, Report*, Notification*`, matching controllers | P2 |
| `templates/**`, `static/**`, Swagger config, Postman, README | P3 |

**Week-1 hand-offs:**
1. P3 → P1, P2: ER diagram, `V1` and `V2` scripts
2. P1 → P2: `Transaction`, `Category`, `User` entities and repositories (even as stubs)
3. P2 → P1: `BudgetAlertService.check(Transaction)` signature
4. P1, P2 → P3: DTO JSON shapes (this document) and Swagger annotations
