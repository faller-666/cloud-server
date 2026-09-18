# cloud-files — 文件管理与审计模块（C 组）

私有云存储平台文件域后端：目录树管理（R-C01~C04）、回收站与 30 天自动清理（R-C05~C06）、配额口径（R-C07）、审计查询（R-C08）、管理端大盘统计（R-C09~C10）。

## 包结构

```
com.company.cloud.files
├── dir/        目录树：列表/搜索/树/新建/重命名/移动/删除（Controller、Service、Mapper）
├── recycle/    回收站：列表/恢复/彻底删除 + scheduler(R-C06 定时清理)
├── audit/      审计查询（写入侧在 cloud-common，本包只负责查询与 VO）
├── stats/      统计：个人 overview(R-C10) + 管理端大盘(R-C09)
└── (ref/)      引用计数客户端（秒传共享计数，物理清理走 B 组对象接口）
```

## AuditService 使用指南（面向 B 组 / 后续开发者）

审计写入统一走 `cloud-common` 的 `AuditService`，注入后一行调用：

```java
private final AuditService auditService;

auditService.record(new AuditEvent(
        userId,                 // 操作者
        AuditActions.UPLOAD,    // 动作（见下方字典）
        String.valueOf(fileId), // 目标对象 ID（字符串）
        clientIp,               // 可为 null（A 组 Security 上下文交付后补齐）
        Map.of("name", filename, "size", bytes)  // detail：扩展信息
));
```

- **异步写入**：`@Async` 专用线程池（`auditExecutor`，核心 2 / 最大 8 / 队列 1000），**失败仅记日志、不影响主流程**，不要用审计结果做业务判断。
- **B 组埋点契约**（当前 UploadService / DownloadService 尚未接入，traffic 统计依赖此项）：
  - `upload`：detail 必含 `{"size": <字节数>, "name": <文件名>}`
  - `download`：detail 必含 `{"size": <字节数>, "name": <文件名>}`
  - `size` 缺失的记录在大盘 traffic 聚合中按 0 计入。

## 审计动作字典（权威定义：cloud-common `AuditActions`）

| 常量 | 值 | 记录方 | detail 约定 |
|---|---|---|---|
| LOGIN / LOGOUT | login / logout | A 组 | - |
| UPLOAD / DOWNLOAD | upload / download | B 组（待接入） | size, name |
| MKDIR | mkdir | C 组 | name, id, parentId |
| MOVE | move（含拖拽/批量移动） | C 组 | name, id, fromParentId, toParentId |
| DELETE | delete（入回收站） | C 组 | - |
| DELETE_FORCE | delete_force | C 组 | name, size |
| RESTORE | restore | C 组 | name, size |
| QUOTA_CHANGE | quota_change | A 组 | 前后值 |
| USER_MANAGE | user_manage | A 组 | 操作摘要 |

> 清理提示：`cloud-files/audit/AuditActions.java` 为早期草稿，已被 cloud-common 版取代且全仓零引用，可删除。

## 统计接口字段文档（面向前端 B 组）

**个人视角** `GET /api/stats/overview`（R-C10）

| 字段 | 含义 |
|---|---|
| totalFiles / totalDirs / totalBytes | 存活文件数 / 目录数 / 字节 |
| todayNew | 今日新增（文件+目录） |
| recycleCount / recycleBytes | 回收站条目数 / 字节 |
| typeBreakdown | 扩展名 TOP10（ext, count, bytes） |

**管理端大盘**（R-C09，需 admin 角色）

| 接口 | 字段 |
|---|---|
| `GET /api/admin/stats/overview` | totalQuotaBytes / usedBytes / remainingBytes / userCount |
| `GET /api/admin/stats/top-users` | Top10：userId, username, usedBytes, quotaBytes |
| `GET /api/admin/stats/traffic?days=7` | 按日：date, uploadBytes, downloadBytes（无流量日补零，days 1~90） |

**口径说明（重要）**：配额 `used_bytes` **含回收站占用**（软删不释放，物理删除才释放）。因此 `overview.usedBytes` 与个人 `totalBytes + recycleBytes` 之和一致；前端进度条请用配额口径，不要用 totalBytes。

**刷新频率建议**：admin overview 服务端已缓存 1 分钟，前端轮询 ≥1 分钟即可；traffic 曲线建议 5 分钟；top-users 实时查询，按需刷新。

## R-C06 回收站自动清理配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `recycle.retention-days` | 30 | 保留天数（列表 expireAt 同口径） |
| `recycle.purge.cron` | `0 0 3 * * ?` | 清理触发时刻 |
| `recycle.purge.dry-run` | **true** | 只打清单不删除；上线核对清单无误后改 false |
| `recycle.purge.batch-size` | 200 | 每轮单批上限 |

清理复用 `forceDelete` 全链路（级联物理删 → 释放配额 → 引用计数递减 → 归零删对象），单条失败不断批、当轮不再拉新批（防死循环），次日自动重试。

## 已知偏差与待办

1. 审计查询实际路径为 `GET /api/audit-logs`（契约 §5 写的是 `/api/admin/logs`），前端已按现状对接；是否改契约需三组会签。
2. traffic 接口依赖 B 组 upload / download 埋点，埋点补齐前数据为 0（接口已就绪，无需改动）。
3. `FileNodeServiceImpl.resolveUniqueName` 的并发兜底已由部分唯一索引 `uk_files_sibling_name`（V2003，`WHERE deleted_at IS NULL`）闭环。
