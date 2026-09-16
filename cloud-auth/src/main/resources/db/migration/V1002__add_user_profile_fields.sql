-- 云存储平台 · 后端A组：认证与用户管理
-- V1002 用户资料字段补充（P2，前端 UI 已就绪）
--   nickname      显示昵称（可选）
--   email         邮箱（可选，唯一）
--   last_login_at 最近登录时间（登录成功时回填）
-- users 表为 A 组 Owner；已存在行允许为空，seed admin 无需回填。
-- email 唯一索引用表达式索引，避免多行 NULL 冲突（PostgreSQL 中 NULL != NULL）。

ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email    TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- 邮箱唯一（仅对非空值生效，兼容历史 NULL 行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users (email) WHERE email IS NOT NULL;