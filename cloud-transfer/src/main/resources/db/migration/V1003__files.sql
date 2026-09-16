-- ============================================================
-- 后端 B 组：files 表（表 Owner 为 B 组）
-- 对齐 docs/files-table-spec.md 组间约定结构，与 C 组 V2003 代建结构一致
-- 全部 IF NOT EXISTS：与已应用过 V2003 的本地库共存（Flyway ignore-migration-patterns 已配兜底）
-- ============================================================
CREATE TABLE IF NOT EXISTS files (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT      NOT NULL,                 -- 所有者用户 ID
    parent_id   BIGINT      NOT NULL DEFAULT 0,       -- 父目录，0 = 根目录
    name        TEXT        NOT NULL,                 -- 同级唯一（见 uk_files_sibling_name）
    is_dir      BOOLEAN     NOT NULL,
    size        BIGINT      NOT NULL DEFAULT 0,       -- 字节数，目录为 0
    sha256      TEXT,                                 -- 秒传哈希（B 组字段），目录为 null
    ref_count   INT         NOT NULL DEFAULT 0,       -- 引用计数（B 组字段），目录为 0
    deleted_at  TIMESTAMPTZ,                          -- 软删标记（回收站），null = 正常
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_files_owner_parent ON files (owner_id, parent_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_files_sibling_name ON files (owner_id, parent_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_files_owner_deleted ON files (owner_id, deleted_at)
    WHERE deleted_at IS NOT NULL;