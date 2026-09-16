-- ============================================================
-- 后端 B 组：upload_sessions 表（分片上传会话，断点续传状态持久化）
-- 去 users 外键（users 表 Owner 为 A 组，避免迁移顺序依赖）；file_id 合并进建表
-- ============================================================
CREATE TABLE IF NOT EXISTS upload_sessions (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    upload_id     TEXT        NOT NULL,               -- MinIO multipart uploadId
    target_parent BIGINT,                             -- 目标目录（0 = 根目录）
    name          TEXT        NOT NULL,
    sha256        TEXT,                               -- 文件级哈希（init 存，complete 写 files 表用）
    size_bytes    BIGINT      NOT NULL,
    chunk_size    INT         NOT NULL DEFAULT 8388608,
    status        TEXT        NOT NULL DEFAULT 'uploading',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL,
    file_id       BIGINT                              -- complete 成功后回填，尊幂等重试
);

CREATE INDEX IF NOT EXISTS idx_sessions_user   ON upload_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expire ON upload_sessions (expires_at, status);