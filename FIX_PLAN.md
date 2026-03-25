# FIX_PLAN

## Completed in this pass
- Replaced map-based admin dashboard summary contract with explicit DTO:
  - `AdminDashboardSummaryResponse`
  - `DashboardService.summary()` now returns typed contract directly.
- Completed admin MT5 account list server-table behavior:
  - added `AdminMt5AccountsQueryRequest`
  - implemented pageable + sortable + filterable + searchable query logic
  - filters now support:
    - `status`, `verificationStatus`, `strategyId`, `strategyCode`
    - `timeframe`, `riskRuleId`, `riskRuleCode`, `broker`, `portStatus`, `userId`
  - search supports:
    - account number
    - broker
    - owner full name/email (resolved to user ids)
- Added repository support required by filters:
  - mt5 specifications
  - strategy/risk-rule code lookup
  - port status lookup
- Added integration contract tests in `AdminContractsIT`:
  - dashboard typed/stable response fields
  - admin mt5 list paging/filter/sort/search expectations.

## Important environment note
- Current environment cannot compile Java 21 project (`release version 21 not supported`), so Maven test run cannot complete here.
- Test classes are structured for CI/dev Java 21 + Docker-enabled environments.

## UI integration blockers removed
- Admin dashboard now has a stable typed response for direct frontend mapping.
- Admin MT5 table endpoint now supports practical server-driven behavior for pagination, sorting, filtering, and search.
- Frontend mapping docs now reflect concrete query params and response contracts.

## Remaining optional improvements
- Add DB indexes for heavily queried MT5 filters/search columns for large datasets.
- Add explicit OpenAPI parameter docs/examples for admin MT5 list filters.

## Added in current pass (admin module phases)
- Step 1 (targeted bugfix pass):
  - Fixed admin user status persistence:
    - `POST /api/v1/admin/users/{id}/suspend` now saves status to DB
    - `POST /api/v1/admin/users/{id}/activate` now saves status to DB
  - Confirmed admin user response contract includes `preferredLanguage`.
  - Updated FE account detail logs mapping (in FE repo) to use structured params:
    - logs: `entityType=MT5_ACCOUNT`, `entityId={id}`
    - process logs: `mt5AccountId={id}`

- Phase 1:
  - Added structured optional filters to:
    - `GET /api/v1/admin/logs`: `entityType`, `entityId`, `actorType`, `result`
    - `GET /api/v1/admin/process-logs`: `mt5AccountId`, `portId`, `actionType`, `result`
  - Preserved backward compatibility for `page`, `pageSize`, `search`.

- Phase 2:
  - Enriched `Mt5AccountResponse` with optional admin display fields:
    - `userFullName`, `userEmail`, `strategyCode`, `riskRuleCode`, `assignedPortCode`
  - Implemented batched enrichment in `Mt5AccountService` to avoid N+1 patterns.

- Phase 3:
  - `GET /api/v1/admin/strategies` supports server-side:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `active`

- Phase 4:
  - `GET /api/v1/admin/risk-rules` supports server-side:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `active`

- Phase 5:
  - `GET /api/v1/admin/plans` supports server-side:
    - `page`, `pageSize`, `sortBy`, `sortOrder`, `search`, `status`, `type`, `billingCycle`

- Phase 6 backend polish:
  - Admin user response now includes `preferredLanguage`.
  - Docs updated with new admin contracts.

## Known scope boundary
- FE codebase is separate (`gold-trading-bot-ui`), so FE page/hook wiring is not changed in this repository.
- `/admin/settings` intentionally left as placeholder (out of scope).

## Step 4 polish (completed in FE repo)
- `/admin`:
  - dashboard cards now display full typed summary usage including `disabledPorts` and `recentAlertsCount`.
  - widget-level loading/error messaging added for pending list and activity logs.
- `/admin/users` and `/admin/users/:id`:
  - export remains placeholder (explicit notice), no fake backend added.
  - user detail keeps save/suspend/activate wired; related accounts panel now shows loading/error states.
- `/admin/accounts` and `/admin/accounts/:id`:
  - prior contract fixes retained; added clearer loading/error states for account detail logs/process logs sections.
- `/admin/ports`:
  - create/edit remains wired; added basic FE validation for required fields and valid port range.
  - documented KPI limitation (status cards computed from current page items).
- `/admin/logs`:
  - current list flow unchanged; no unsafe domain expansion.
- `/admin/process-logs`:
  - wired `View` action to local detail dialog using existing row fields.
  - added result filter on query.
- `/admin/settings`:
  - kept placeholder intentionally; controls disabled to avoid implying persistence.
