-- C组临时代建：files 表（表 Owner 本为 B 组，因 B 组尚未交付正式 migration，
-- 为不阻塞本地联调与 C 组验收，按 docs/files-table-spec.md 组间约定先行代建）
-- 全部使用 IF NOT EXISTS：B 组正式 migration（V1xxx）交付后可共存，无需删除本文件——
--   · 新环境（含张贵林 Docker）：B 组 V1xxx 先建表，本文件自动跳过，不报错
--   · 已应用过本文件的本地库：B 组 V1xxx 版本号低于 2003，Flyway 默认忽略，不报错
--   · 若 B 组结构与 spec 有出入：以 B 组为准，C 组实体类跟随调整（files-table-spec.md 约定）
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