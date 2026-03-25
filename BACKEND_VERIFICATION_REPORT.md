# BACKEND VERIFICATION REPORT

## Scope
Verification and targeted completion for admin-module FE-BE integration blockers in this pass:
- Phase 1: account detail logs/process-logs structured filter mapping
- Phase 2: enriched admin MT5 account DTO for FE direct display
- Phase 3-5: server-side table contracts for strategies/risk-rules/plans
- Phase 6: admin contract polish/docs alignment (backend-side)

## Verified and present (already in code)
- Auth route hardening in `SecurityConfig`:
  - public only: register/login/refresh/forgot-password/reset-password
  - private: `GET /auth/me`, `POST /auth/logout`, `POST /auth/change-password`
  - `/api/v1/admin/**` restricted to ADMIN.
- Password reset flow:
  - `PasswordResetToken` entity + Flyway migration/table
  - hashed token storage, expiry validation, single-use semantics
  - reset password updates hash and invalidates token.
- Notification ownership safety:
  - mark-read uses owner-scoped lookup (`findByIdAndUserId`), preventing ID guessing.
- MT5 lifecycle/port lifecycle policies:
  - `Mt5AccountLifecyclePolicy` and `PortLifecyclePolicy` are present and enforced.
- AES-GCM MT5 password encryption:
  - random IV per encryption in crypto service
  - encrypted MT5 password stored at rest
  - raw MT5 password not exposed in API response DTOs.

## Implemented in this pass
- Step 1.A (`/admin/users/:id` bugfix):
  - Verified admin user response includes `preferredLanguage`.
  - Fixed `POST /api/v1/admin/users/{id}/suspend` and `POST /api/v1/admin/users/{id}/activate` to persist status changes via repository save.
  - Added integration coverage to verify:
    - user detail returns `preferredLanguage`
    - suspend/activate status changes are persisted in DB.

- Phase 1 (`/admin/accounts/:id` logs mapping support):
  - `GET /api/v1/admin/logs` extended with optional filters:
    - `entityType`, `entityId`, `actorType`, `result`
  - `GET /api/v1/admin/process-logs` extended with optional filters:
    - `mt5AccountId`, `portId`, `actionType`, `result`
  - Backward compatibility preserved for `page`, `pageSize`, `search`.

- Phase 2 (admin MT5 response enrichment):
  - `Mt5AccountResponse` extended with optional fields:
    - `userFullName`, `userEmail`, `strategyCode`, `riskRuleCode`, `assignedPortCode`
  - Enrichment implemented with batched lookups in `Mt5AccountService` (user/strategy/riskRule/port maps), avoiding N+1 loops.
  - Existing fields/contracts kept unchanged.

- Phase 3-5 (server-side table contract for admin catalog pages):
  - `GET /api/v1/admin/strategies` now supports:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `active`
  - `GET /api/v1/admin/risk-rules` now supports:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `active`
  - `GET /api/v1/admin/plans` now supports:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `status`, `type`, `billingCycle`
  - Invalid enum filter inputs for plans return empty paged result (non-breaking, validation-safe behavior).

- Phase 6 polish (backend-side):
  - Admin user response now includes `preferredLanguage` (domain field already present), avoiding FE form mismatch on admin user detail edit.
  - API mapping doc updated for all new admin query params and enriched fields.

- Repository/query support updates:
  - `AuditLogRepository` and `ProcessLogRepository` now support specifications (`JpaSpecificationExecutor`) for structured filters.
  - `StrategyRepository`, `RiskRuleRepository`, `PlanRepository` now support specification-driven filtering where needed.

- Integration test updates:
  - `AdminContractsIT` extended with:
    - structured filter tests for `/admin/logs` + `/admin/process-logs`
    - enriched admin MT5 response field presence checks
    - server-side filter behavior checks for strategies/risk-rules/plans

## Still incomplete
- Frontend wiring changes are **not in this repository** (current repo is backend-only). FE implementation must be applied in `gold-trading-bot-ui`:
  - `/admin/accounts/:id` should call structured log filters instead of `search=id`
  - strategies/risk-rules/plans pages should switch fully to server-side query usage and wire add/edit dialogs against existing endpoints.
- `/admin/settings` remains out-of-scope placeholder by request.
- FE placeholder/admin-polish tasks beyond Step 1 are not part of this scope.

## Step 4 FE polish status (separate FE repo)
- Admin dashboard:
  - confirmed KPI mapping to typed summary fields; FE now surfaces `disabledPorts` and `recentAlertsCount`.
  - added loading/error messaging for pending accounts and activity logs widgets.
- Admin users:
  - export button kept as placeholder with explicit toast (no export domain/API in current scope).
  - user detail keeps `preferredLanguage` contract and now has explicit loading/error note for related MT5 accounts panel.
- Admin accounts:
  - list/detail remain wired to real actions and structured account log filters from prior steps.
  - added explicit loading/error notes for account-detail logs/process-logs blocks.
- Admin ports:
  - create/edit remains wired; added light FE validation (required fields + port range).
  - documented limitation: KPI status counters are computed from current page items.
- Admin process logs:
  - kept existing table; wired `View` action to detail dialog using existing returned fields.
  - added server-side `result` filter usage on FE.
- Admin settings:
  - intentionally kept placeholder; controls disabled to avoid fake persistence behavior.

## Environment/Test note
- `mvn -Dtest=AdminContractsIT test` executes successfully but integration tests are skipped in this environment due to Testcontainers Docker detection issue.
- Compile/build pass and test class compilation pass locally.
