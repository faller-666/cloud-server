-- 云存储平台 · 后端C组：存量库兜底迁移
-- 背景：A 组 V1002（users 加 nickname/email/last_login_at）交付时存量库已到 v2005，
--       V1002 < v2005 会被 Flyway 标记 ignored 跳过，存量库永远建不上这三列，
--       而 User 实体已带新字段，ddl-auto=validate 启动校验将直接失败。
-- 本迁移与 V1002 完全同构、全部幂等（IF NOT EXISTS）：
--   新库：V1002 先执行建列，本迁移空转；
--   存量库：V1002 被跳过，由本迁移补齐。

ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email    TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- 邮箱唯一（仅对非空值生效，兼容历史 NULL 行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email ON users (email) WHERE email IS NOT NULL;
