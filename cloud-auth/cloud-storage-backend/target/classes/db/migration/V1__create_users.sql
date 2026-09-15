-- 云存储平台 · 后端A组：认证与用户管理
-- V1 初始建表：users（本组 Owner）
CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    username             TEXT UNIQUE NOT NULL,
    password_hash        TEXT NOT NULL,               -- bcrypt(12)
    role                 TEXT NOT NULL DEFAULT 'user', -- admin / user
    quota_bytes          BIGINT NOT NULL DEFAULT 21474836480, -- 20GB
    used_bytes           BIGINT NOT NULL DEFAULT 0,
    status               TEXT NOT NULL DEFAULT 'active', -- active / disabled
    must_change_password BOOLEAN NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- used_bytes 的增减由后端 B 组在传输完成/删除时更新，本组只读使用该字段
-- 字段说明：
--   role:                  admin / user（RBAC 两角色）
--   status:                active / disabled
--   must_change_password:  首登强制改密标记，true 时禁止访问业务接口
--   quota_bytes:           用户配额（默认 20GB = 21474836480）
--   used_bytes:            已用容量，由 B 组维护