# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

GidroGo backend v2 — a multi-farm drinking-water delivery marketplace + CRM. Java 21, Spring Boot 3.3.4, PostgreSQL 16 (Flyway), Redis 7, JWT, Lombok, springdoc. Code comments, error messages and docs are written in **Uzbek** — keep that convention.

## Commands

```bash
./mvnw clean spring-boot:run                 # run locally (needs Postgres gidrogo_db on :5432 and Redis on :6379)
./mvnw clean package -DskipTests             # build target/gidrogo-backend-2.0.0.jar (what CI does)
./mvnw test                                  # all tests
./mvnw test -Dtest='*IT'                     # integration tests only
./mvnw test -Dtest=BossActivationIT          # single test class
./mvnw test -Dtest=BossActivationIT#methodName
./mvnw test -Dtest=OpenApiExportIT           # regenerate docs/openapi.json (do this after any API contract change)
```

Integration tests (`src/test/java/uz/gidrogo/it`, profile `it`) hit a **real** PostgreSQL database `gidrogo_it` (create with `psql -U postgres -c "CREATE DATABASE gidrogo_it"`; override via `IT_DB_URL/IT_DB_USERNAME/IT_DB_PASSWORD`) plus local Redis. They extend `ItBase`, call the API over HTTP via `TestRestTemplate`, and seed data with raw SQL using unique phones/names — the DB is never cleaned (ledgers/audit log are immutable). `GidroGoCoreLogicTest.testOrderStatusEnum` is a known pre-existing failure (expects 11 statuses, enum has 12).

Swagger: `http://localhost:8080/swagger-ui/index.html` (disabled under `SPRING_PROFILES_ACTIVE=prod`).

## Architecture

Package-by-feature under `uz.gidrogo`:
- `common/` — cross-cutting: `ApiResponse` envelope, `ApiException` (+ `GlobalExceptionHandler`), `PageResponse`, `TashkentTime`, `SecurityUtils`, `TenantContext`, `FileUrls`, `PasswordPolicy`.
- `security/` — stateless JWT (`JwtAuthenticationFilter`, `JwtTokenProvider`) and the RBAC URL matrix in `SecurityConfig`.
- `modules/<feature>/` — each has entity, repository, service, controller, and a `*Dtos` class holding nested request/response records. Controllers are grouped by **role panel**, not by entity: `SuperAdminController`/`SuperAdminCrmController` (`/api/superadmin/**`), `BossController` (`/api/boss/**`), `ManagerController` in `product/` (`/api/manager/**`, also allowed for BOSS), `CourierController` (`/api/courier/**`), `ClientController` (`/api/client/**`), `AuthController` (`/api/auth/**`, single `POST /api/auth/login` for all roles).
- `websocket/` — two channels: STOMP at `/ws/client` (`WebSocketConfig`, `/topic` + `/queue`, events sent via `WebSocketEventPublisher`) for client/manager/boss dashboards, and a raw WebSocket at `/ws/courier` (`CourierWebSocketHandler`) for pushing offers to couriers.

Key cross-cutting rules:
- **Multi-tenancy**: every non-superadmin user belongs to a farm. The JWT carries `farmId`; `JwtAuthenticationFilter` puts it in `TenantContext`, and services scope queries with `SecurityUtils.getCurrentFarmId()`. Always filter farm-owned data by this id.
- **Responses**: wrap results in `ApiResponse.ok(...)`. Errors go through `ApiException(status, CODE, message)` (static helpers for 404/409/403/400/422) so the client gets `{success:false, code, message, fieldErrors, timestamp}` with a machine-readable `code` (e.g. `FARM_BLOCKED`). Lists use `PageResponse` with `page,size,sort`.
- **Time**: day/week/month boundaries are computed in `Asia/Tashkent` via `TashkentTime` (`resolveRange` normalizes `period/date/startDate/endDate/from/to` params); API timestamps are UTC `Instant`.
- **Revenue**: single source of truth is `finance/RevenueService` — only COMPLETED orders count; `revenue` = `products_sum`, `taraRevenue` = `tara_sum` (bottle deposit, excluded from profit). SuperAdmin, Boss, and farm analytics must agree, and tests assert this.
- **Audit**: SuperAdmin mutations must call `AuditService.record(...)`, which is `Propagation.MANDATORY` — it must run inside the action's transaction, so the action fails if auditing fails.
- **Immutable tables**: DB triggers (V12) forbid UPDATE/DELETE on `superadmin_audit_log`, `cash_ledger`, `bottle_ledger`, `order_status_history` — corrections are new reverse entries.
- **Order dispatch** (`courier/CourierDispatchService`): orders go SEARCHING → offers sent to nearest couriers (ETA by distance × road factor, vehicle stock checked/reserved) with a 30s TTL + 5s grace. `@Scheduled` jobs expire offers every 3s and re-dispatch PREPARING orders every 60s. Courier live location lives in Redis. Blocked/inactive farms receive no dispatch and reject checkout (409).
- Boss accounts have an `activationStatus` (PENDING/APPROVED/REJECTED); login returns `403 ACTIVATION_PENDING/ACTIVATION_REJECTED` until approved by SuperAdmin.
- Refresh tokens are persisted (`RefreshTokenService`) and revoked on logout. OTP/registration is via a Telegram bot (`TelegramBotService`, webhook at `/api/telegram/**`; `telegram.bot.mock-mode: true` logs instead of sending).
- Uploaded files are stored under `app.upload.dir`; URLs saved in the DB are relative (`/api/files/view/...`, built with `FileUrls`).

## Database migrations

Hibernate runs with `ddl-auto: validate`, so every entity change needs a new Flyway script `src/main/resources/db/migration/V<N>__description.sql` (next number after the highest existing one). Never edit an applied migration. V10 intentionally aborts if boss↔farm links are inconsistent (fix data with `docs/sql/boss_farm_fix_template.sql`).

## Docs & deploy

- `docs/openapi.json` is the contract the frontend generates types from — regenerate it with `OpenApiExportIT` when endpoints/DTOs change.
- `docs/BE_HISOBOT.md` tracks backend task status (BE-01…BE-25), open owner decisions, and frontend contract changes.
- Pushing to `main`/`master` triggers `.github/workflows/deploy.yml`: builds with tests skipped, copies the jar via SCP, and restarts `gidrogo.service` on the production server. Treat a push to `main` as a production deploy.
- `test_figma_flow.ps1` is an old manual smoke script and uses endpoints that no longer exist (e.g. `/api/auth/superadmin/login`).
