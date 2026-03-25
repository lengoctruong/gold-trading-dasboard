# Gold Trading Backend

Spring Boot backend for Trading Bot Management Platform.

## Stack
- Java 21, Spring Boot 3
- Spring Web/Security/JPA/Validation
- JWT access + refresh token (hashed)
- PostgreSQL + Flyway
- OpenAPI Swagger (`/swagger-ui/index.html`)

## Run locally
1. Ensure Java 21 is installed.
2. `docker compose up -d postgres`
3. Copy `.env.example` to `.env` and adjust values for local:
   - `APP_PORT=8088`
   - `CORS_ALLOWED_ORIGINS=http://localhost:5173`
   - set `APP_JWT_SECRET_BASE64` and `APP_MT5_ENCRYPTION_KEY`
4. `mvn spring-boot:run`

## Build
- `mvn clean package`

## Logging
- Runtime logs are written to `logs/gold-trading-dashboard-backend.log`.
- Daily rollover is enabled; previous days are archived as `logs/gold-trading-dashboard-backend.YYYY-MM-DD.log`.

## Key endpoints
- Auth: `/api/v1/auth/*`
- User: `/api/v1/users/me`, `/api/v1/mt5-accounts/*`, `/api/v1/notifications/*`, `/api/v1/trades/my`, `/api/v1/reports/my/*`
- Admin: `/api/v1/admin/*`
- Health: `/health`, `/ready`

## Notes
- Runtime is abstracted via `BotRuntimeAdapter`, with simulated MVP adapter.
- MT5 password encrypted at rest and never returned by API.
- Lifecycle updates are transactional in `Mt5AccountService`.



## Security configuration (JWT)
- Never commit `.env` or any real secrets.
- Provide JWT config only via environment variables:
  - `APP_JWT_SECRET_BASE64` (required in non-local, base64-encoded key, minimum 32 bytes)
  - `APP_JWT_ACCESS_TOKEN_MINUTES` (optional, default `15`)
  - `APP_JWT_REFRESH_TOKEN_DAYS` (optional, default `30`)
- Provide `APP_MT5_ENCRYPTION_KEY` via environment variable.
- In non-local environments, application startup fails if `APP_JWT_SECRET_BASE64` is missing/invalid/weak.
