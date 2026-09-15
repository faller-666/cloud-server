# API 契约（冻结版 v1.0）

> 本文件为后端三组与前端两组的唯一契约依据（总纲 §5）。
> **任何改动须三组会签并同步前端，PR 由 CODEOWNERS 强制评审。**

## 1. 统一返回格式

所有接口返回：

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code = 0`：成功
- `code = 4xxxx`：业务错误（段位：400xx 认证权限 / 401xx 用户配额 / 402xx 传输 / 403xx 文件管理）
- `code = 5xxxx`：系统错误

代码实现：`cloud-common` 模块 `Result<T>` + `ErrorCode` 枚举，新增错误码先登记枚举段位。

## 2. 鉴权

- 除 **登录、刷新令牌** 外，全部接口经过 JWT 鉴权（A 组 Security Filter）
- 请求头：`Authorization: Bearer <token>`
- 角色：普通用户 / admin；管理端接口要求 admin（`@PreAuthorize("hasRole('ADMIN')")`）
- token 无效返回 `code = 40001`，无权限返回 `code = 40003`

## 3. 接口前缀与端口

- 全部接口前缀 `/api`（已由 `server.servlet.context-path` 统一处理，Controller 内不要再写 /api）
- 应用实例 3001 / 3002 不对外；Nginx :8080 为内网入口

## 4. 数据口径

- 时间：ISO 8601 字符串（如 `2026-09-15T10:30:00+08:00`）
- 大小：字节数（number）
- 分页参数：`page`（从 1 开始）、`size`（默认 20，上限 100）
- 排序参数：`sort=field,asc|desc`，字段必须在各接口白名单内，禁止直接透传

## 5. 审计动作字典（C 组维护）

| action | 说明 | 调用方 |
|---|---|---|
| login / logout | 登录 / 登出 | A 组 |
| upload / download | 上传 / 下载 | B 组 |
| delete / delete_force / restore | 入回收站 / 彻底删除 / 恢复 | C 组 |
| quota_change | 配额变更 | A 组 |
| user_manage | 用户管理（细分动作放 detail） | A 组 |

代码实现：`cloud-files` 模块 `AuditActions` 常量类；新增动作先改本表并通知 A/B 组。

## 6. 幂等与并发约定

- `DELETE /files/:id`、`POST /files/:id/restore` 幂等：重复请求返回成功，不报错
- 同级重名：服务端自动追加 `(2)`、`(3)` 后缀，不向前端报冲突

---

*本契约冻结后，前端两组可基于本文档 + Swagger 并行开发（前期可用 Mock）。*
