-- ============================================================
-- 本地最小可运行版 —— users 表（B 组自测依赖的最小子集）
-- 说明：正式环境 users 表由后端 A 组（common V1__auth_users.sql）管理，
--       这里仅复刻 B 组配额 SQL 需要的字段 + 外键依赖，并 seed 一个 dev 用户。
--       联调接 A 组后，本文件随 storage 启动类一并移除。
-- ============================================================

CREATE TABLE users (
    id           BIGSERIAL   PRIMARY KEY,
    username     TEXT        NOT NULL UNIQUE,
    password_hash TEXT       NOT NULL,
    role         TEXT        NOT NULL DEFAULT 'user',
    quota_bytes  BIGINT      NOT NULL DEFAULT 21474836480, -- 20GB
    used_bytes   BIGINT      NOT NULL DEFAULT 0,
    status       TEXT        NOT NULL DEFAULT 'active',    -- active / disabled
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 自测账号：所有接口走 X-User-Id: 1 即代表该用户
INSERT INTO users (username, password_hash, role) VALUES
    ('dev', '$2a$12$local-dev-placeholder-hash', 'user');