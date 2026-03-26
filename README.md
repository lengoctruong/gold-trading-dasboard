# Gold Trading Dashboard Backend

Spring Boot backend for Trading Bot Management Platform.

## Stack
- Java 21, Spring Boot 3
- Spring Web/Security/JPA/Validation
- JWT access + refresh token (hashed)
- PostgreSQL + Flyway
- OpenAPI Swagger (`/swagger-ui/index.html`)

## Database Configuration

The dashboard backend uses PostgreSQL with the `dashboard` schema and a dedicated `dashboard_app` user.

### Environment Variables

Configure via environment variables (see `.env.example`):

```bash
# Database Configuration (Local Development)
# For Docker runtime, use postgres:5432 instead of localhost
DB_URL=jdbc:postgresql://localhost:5432/gold_trading_bot?currentSchema=dashboard
DB_USERNAME=dashboard_app
DB_PASSWORD=dashboard_pass_change_me

# Application Port
APP_PORT=8088

# JWT Configuration (CHANGE FOR PRODUCTION!)
APP_JWT_SECRET_BASE64=__SET_BASE64_64BYTE_MINIMUM__
APP_JWT_ACCESS_TOKEN_MINUTES=15
APP_JWT_REFRESH_TOKEN_DAYS=30

# MT5 Encryption Key (16 characters minimum)
APP_MT5_ENCRYPTION_KEY=__SET_16CHAR_MIN__

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

### Local Development Setup

1. Copy `.env.example` to `.env`:
   ```powershell
   copy .env.example .env
   ```

2. Update `.env` with your configuration:
   - Generate JWT secret: `openssl rand -base64 64`
   - Set a secure MT5 encryption key (16+ characters)
   - Update database credentials if needed

3. Start PostgreSQL (if using Docker for DB only):
   ```powershell
   cd ..\gold-trading-stack
   docker-compose up -d postgres
   ```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

### Docker Deployment

When running in Docker via `gold-trading-stack/docker-compose.yml`, the database configuration is automatically injected and uses:
- Service name: `postgres` (not `localhost`)
- User: `dashboard_app` (not `postgres` superuser)
- Schema: `dashboard`

**DO NOT** use the standalone `docker-compose.yml` in this directory for production - it's legacy/dev-only. Use the main stack compose instead.

## Run locally
1. Ensure Java 21 is installed.
2. Follow the "Local Development Setup" above to configure database and environment.
3. `mvn spring-boot:run`

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
