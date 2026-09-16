-- ============================================================
-- 后端 B 组：文件传输核心
-- Owner: files / file_hashes / upload_sessions
-- 对应任务书 CS-DOC-03 §5
-- ============================================================

-- 文件/目录元数据（目录树由 C 组做业务编排，表结构归 B 组）
CREATE TABLE files (
    id          BIGSERIAL   PRIMARY KEY,
    owner_id    BIGINT      NOT NULL REFERENCES users(id),
    parent_id   BIGINT,                              -- 目录树，NULL 为根
    name        TEXT        NOT NULL,
    is_dir      BOOLEAN     NOT NULL DEFAULT false,
    size_bytes  BIGINT      NOT NULL DEFAULT 0,
    mime        TEXT,
    sha256      CHAR(64),                            -- 文件级哈希，关联 file_hashes
    storage_key TEXT,                                -- MinIO 对象 key
    deleted_at  TIMESTAMPTZ,                         -- 回收站软删标记（C 组维护）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN files.parent_id   IS '父目录 id，NULL 为根目录';
COMMENT ON COLUMN files.deleted_at  IS '回收站软删标记，非空即已删除（C 组维护）';

CREATE INDEX idx_files_dir   ON files (owner_id, parent_id, deleted_at);
CREATE INDEX idx_files_trash ON files (owner_id, deleted_at);

-- 秒传去重表：同内容文件全站只存一份，引用计数管理生命周期
CREATE TABLE file_hashes (
    sha256      CHAR(64)    PRIMARY KEY,
    storage_key TEXT        NOT NULL,                -- 实际存储的唯一对象 key
    size_bytes  BIGINT      NOT NULL,
    ref_count   INT         NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN file_hashes.ref_count IS '引用计数，彻底删除时 -1，归 0 物理删除 MinIO 对象';

-- 分片上传会话（断点续传状态持久化，勿放内存）
CREATE TABLE upload_sessions (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    upload_id     TEXT        NOT NULL,              -- MinIO multipart uploadId
    target_parent BIGINT,                            -- 目标目录
    name          TEXT        NOT NULL,
    sha256        CHAR(64),                          -- 文件级哈希（init 存，complete 写 file_hashes 用）
    size_bytes    BIGINT      NOT NULL,
    chunk_size    INT         NOT NULL DEFAULT 8388608,
    status        TEXT        NOT NULL DEFAULT 'uploading',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sessions_user   ON upload_sessions (user_id);
CREATE INDEX idx_sessions_expire ON upload_sessions (expires_at, status);