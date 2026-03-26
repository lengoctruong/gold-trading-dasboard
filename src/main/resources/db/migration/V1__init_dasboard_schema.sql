CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Schema will be created/renamed by V2__rename_dasboard_to_dashboard.sql
-- For backward compatibility with existing installations

CREATE TABLE IF NOT EXISTS dashboard.users (
  id UUID PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(50) NOT NULL,
  address VARCHAR(500),
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  preferred_language VARCHAR(10),
  email_verified_at TIMESTAMPTZ,
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS dashboard.refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES dashboard.users(id),
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.password_reset_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES dashboard.users(id),
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.plans (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  type VARCHAR(30) NOT NULL,
  name VARCHAR(255) NOT NULL,
  billing_cycle VARCHAR(30) NOT NULL,
  price NUMERIC(12,2) NOT NULL,
  profit_share_percent INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.user_plan_history (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES dashboard.users(id),
  plan_id UUID NOT NULL REFERENCES dashboard.plans(id),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  is_current BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.strategies (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name_vi VARCHAR(255) NOT NULL,
  name_en VARCHAR(255) NOT NULL,
  description TEXT,
  monthly_price NUMERIC(12,2) NOT NULL,
  risk_level VARCHAR(50) NOT NULL,
  supported_timeframes TEXT NOT NULL,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.risk_rules (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  params_json TEXT NOT NULL,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.port_master (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  ip_address VARCHAR(100) NOT NULL,
  port_number INT NOT NULL,
  environment VARCHAR(50) NOT NULL,
  broker_binding VARCHAR(100),
  status VARCHAR(30) NOT NULL,
  current_mt5_account_id UUID,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.mt5_accounts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES dashboard.users(id),
  account_number VARCHAR(50) NOT NULL,
  encrypted_password TEXT NOT NULL,
  broker VARCHAR(100) NOT NULL,
  server VARCHAR(200) NOT NULL,
  account_type VARCHAR(30) NOT NULL,
  verification_status VARCHAR(30) NOT NULL,
  verification_message TEXT,
  strategy_id UUID REFERENCES dashboard.strategies(id),
  timeframe VARCHAR(20),
  risk_rule_id UUID REFERENCES dashboard.risk_rules(id),
  status VARCHAR(30) NOT NULL,
  admin_action VARCHAR(40) NOT NULL,
  assigned_port_id UUID REFERENCES dashboard.port_master(id),
  submitted_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  stopped_at TIMESTAMPTZ,
  last_config_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT uq_mt5_accounts_account_number UNIQUE (account_number)
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_port_current_account'
      AND connamespace = 'dashboard'::regnamespace
  ) THEN
    ALTER TABLE dashboard.port_master
      ADD CONSTRAINT fk_port_current_account
      FOREIGN KEY (current_mt5_account_id) REFERENCES dashboard.mt5_accounts(id);
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS dashboard.notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES dashboard.users(id),
  type VARCHAR(30) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.audit_logs (
  id UUID PRIMARY KEY,
  actor_type VARCHAR(50) NOT NULL,
  actor_id VARCHAR(100),
  actor_name VARCHAR(255),
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(100),
  result VARCHAR(30) NOT NULL,
  message TEXT NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.process_logs (
  id UUID PRIMARY KEY,
  mt5_account_id UUID NOT NULL REFERENCES dashboard.mt5_accounts(id),
  port_id UUID REFERENCES dashboard.port_master(id),
  action_type VARCHAR(50) NOT NULL,
  result VARCHAR(30) NOT NULL,
  exit_code INT,
  message TEXT NOT NULL,
  config_snapshot_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.bot_operations (
  id UUID PRIMARY KEY,
  mt5_account_id UUID NOT NULL REFERENCES dashboard.mt5_accounts(id),
  type VARCHAR(50) NOT NULL,
  status VARCHAR(30) NOT NULL,
  requested_by_type VARCHAR(50) NOT NULL,
  requested_by_id VARCHAR(100) NOT NULL,
  port_id UUID REFERENCES dashboard.port_master(id),
  payload_json TEXT,
  result_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS dashboard.brokers (
  id UUID PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dashboard.broker_servers (
  id UUID PRIMARY KEY,
  broker_id UUID NOT NULL REFERENCES dashboard.brokers(id),
  code VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_broker_servers_broker_code UNIQUE (broker_id, code)
);

CREATE INDEX IF NOT EXISTS idx_mt5_user ON dashboard.mt5_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_mt5_status ON dashboard.mt5_accounts(status);
CREATE INDEX IF NOT EXISTS idx_mt5_verification ON dashboard.mt5_accounts(verification_status);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON dashboard.notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reset_token_user ON dashboard.password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_reset_token_expires ON dashboard.password_reset_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_broker_servers_broker_id ON dashboard.broker_servers(broker_id);
CREATE INDEX IF NOT EXISTS idx_broker_servers_active ON dashboard.broker_servers(active);
