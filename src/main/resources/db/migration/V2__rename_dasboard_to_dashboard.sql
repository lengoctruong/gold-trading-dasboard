-- ============================================
-- Migration: Rename schema from 'dasboard' to 'dashboard'
-- ============================================
-- This migration fixes the typo in the schema name.
-- It safely handles both cases:
-- 1. New installations (schema doesn't exist yet)
-- 2. Existing installations (schema needs to be renamed)
-- ============================================

DO $$
BEGIN
    -- Check if 'dasboard' schema exists (typo version)
    IF EXISTS (
        SELECT 1
        FROM information_schema.schemata
        WHERE schema_name = 'dasboard'
    ) THEN
        -- Rename existing schema
        ALTER SCHEMA dasboard RENAME TO dashboard;
        RAISE NOTICE 'Renamed schema from dasboard to dashboard';
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.schemata
        WHERE schema_name = 'dashboard'
    ) THEN
        -- Fresh install: create dashboard schema
        CREATE SCHEMA dashboard;
        RAISE NOTICE 'Created dashboard schema (fresh install)';
    ELSE
        -- Dashboard schema already exists (migration already run)
        RAISE NOTICE 'Dashboard schema already exists, skipping';
    END IF;
END $$;

-- Ensure pgcrypto extension exists
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
