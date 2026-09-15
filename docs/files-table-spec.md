# files 表结构约定（待 B 组确认并出正式 migration）

> Owner：B 组（任务书 03）。本文档是 C 组开发所依据的组间约定，
> B 组正式 migration 交付后如有出入，以 B 组为准、C 组实体类跟随调整。

## 字段约定

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | |
| owner_id | BIGINT NOT NULL | 所有者用户 ID |
| parent_id | BIGINT NOT NULL DEFAULT 0 | 父目录，0 = 根目录 |
| name | TEXT NOT NULL | 同级唯一（见下方索引） |
| is_dir | BOOLEAN NOT NULL | |
| size | BIGINT NOT NULL DEFAULT 0 | 字节数，目录为 0 |
| sha256 | TEXT | 秒传哈希（B 组字段），目录为 null |
| ref_count | INT NOT NULL DEFAULT 0 | 引用计数（B 组字段），目录为 0 |
| deleted_at | TIMESTAMPTZ | 软删标记（回收站），null = 正常 |
| created_at / updated_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

## 索引需求（C 组验收依赖）

```sql
-- 列目录主路径（R-C01，单目录 5000 文件 P95 <500ms）
CREATE INDEX idx_files_owner_parent ON files (owner_id, parent_id)
    WHERE deleted_at IS NULL;

-- 同级重名唯一兜底（防并发 mkdir/移动重名；先查后插无法防并发）
CREATE UNIQUE INDEX uk_files_sibling_name ON files (owner_id, parent_id, name)
    WHERE deleted_at IS NULL;

-- 回收站列表
CREATE INDEX idx_files_owner_deleted ON files (owner_id, deleted_at)
    WHERE deleted_at IS NOT NULL;
```

## C 组对 B 组的接口依赖（任务书 04 §6）

- 彻底删除时调用 B 组 **ref_count -1** 接口（归 0 由 B 组删 MinIO 对象），C 组禁止直连 MinIO
- 配额口径：未物理删除即占用（回收站内容仍占配额，R-C07）
