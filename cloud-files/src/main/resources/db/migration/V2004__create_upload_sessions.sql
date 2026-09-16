-- ============================================================
-- C 组代建：upload_sessions 表（Owner 为 B 组，结构与 B 组 V1004 完全一致）
--
-- 背景：B 组 V1004 交付晚于 C 组 V2003，已应用过 V2003 的存量库中
-- V1004 会被 Flyway ignore-migration-patterns 跳过（ignored），
-- upload_sessions 永远不会创建，上传链路直接不可用。
-- 本迁移兜底：全部 IF NOT EXISTS ——
--   存量库：V1004 被跳过，本迁移建表；
--   全新库：V1004 先建表，本迁移空转。
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
