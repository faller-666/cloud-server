# 私有云存储平台 — App 推送更新模块 接口文档

> 项目：cloud-server（Spring Boot 3.3.4 多模块，聚合应用 cloud-app）
> 模块：cloud-auth · AppReleaseService
> 版本：v1.0
> 更新日期：2026-09-30

---

## 0. 文档阅读指引

接口分两类：

| 类型 | 前缀 | 鉴权 | 说明 |
|------|------|------|------|
| **公开接口** | `/app/**` | 免登录（SecurityConfig permitAll） | App 客户端检查更新用 |
| **管理接口** | `/admin/app/releases/**` | 需 admin 角色（JWT） | 管理端发布/管理版本用 |

- **统一前缀**：所有接口经过 `context-path=/api`，即完整路径以 `/api` 开头。
- **统一返回**：`Result<T>` 包装，见下文 §1。
- **时间口径**：ISO 8601，时区 `Asia/Shanghai`。

---

## 1. 统一返回结构 Result<T>

所有接口返回：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | `0` 成功；非 0 为业务错误码 |
| `message` | String | 提示信息 |
| `data` | T | 业务数据；无数据时为 `null` |

---

## 第一部分：公开接口（App 客户端）

---

## 2. 检查更新

**检查指定平台、指定当前版本是否可升级。**

```
GET /api/app/check-update
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `platform` | String | 是 | 平台，仅 `android` / `ios` / `windows` |
| `versionCode` | Integer | 是 | 客户端当前构建版本号（≥ 0） |
| `userId` | Long | 否 | 用户 ID；用于灰度放量判定，不传则全量规则 |

**示例**
```
GET /api/app/check-update?platform=windows&versionCode=10001&userId=3
```

### 响应 data：AppUpdateVO

| 字段 | 类型 | 说明 |
|------|------|------|
| `hasUpdate` | Boolean | 是否有可用更新 |
| `update` | AppUpdateInfo | 有更新时的详情；无更新为 `null`（不输出） |

### 响应 data.update：AppUpdateInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `versionCode` | Integer | 最新版本号 |
| `versionName` | String | 版本名，如 `1.0.3` |
| `apkUrl` | String | 安装包下载地址 |
| `fileSize` | Long | 包体字节数 |
| `fileHash` | String | 安装包 SHA-256（小写 hex） |
| `forceUpdate` | Boolean | 是否强制更新 |
| `updateNotes` | String | 更新说明 |

### 响应示例

**有更新：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "hasUpdate": true,
    "update": {
      "versionCode": 10003,
      "versionName": "1.0.3",
      "apkUrl": "http://192.168.9.121:3001/api/downloads/windows-v1.0.3-1790735631262.exe",
      "fileSize": 102218558,
      "fileHash": "332b36c5a61cec6fe766e200864d1f8b12ac1887d8073533b52ec613769f61d5",
      "forceUpdate": false,
      "updateNotes": "更换图标"
    }
  }
}
```

**无更新：**

```json
{
  "code": 0,
  "message": "ok",
  "data": { "hasUpdate": false }
}
```

### 判定逻辑（服务端）

1. `platform` 非法 → 业务错误。
2. `versionCode < 0` → 业务错误（`BAD_REQUEST`）。
3. 取该平台 `status = published` 且 `versionCode > 客户端当前值` 的最新一条；无更高版本 → `hasUpdate=false`。
4. 灰度命中判定：
   - `userId == null`：仅当 `rolloutPercent >= 100` 命中；
   - `userId != null`：`userId % 100 < rolloutPercent` 命中（同一用户判定稳定）。
5. 未命中灰度 → `hasUpdate=false`。
6. `forceUpdate` 计算：
   - 记录的 `forceUpdate == true`；或
   - `minForceCode > 0` 且 `客户端 versionCode < minForceCode`。

---

## 3. 安装包下载（静态资源）

```
GET /api/downloads/<文件名>
```

- 后端将 `download-dir` 目录静态映射到 `/downloads/**`（context-path=/api 时对外为 `/api/downloads/**`）。
- 无需鉴权。
- 返回安装包二进制流（Content-Type 按扩展名，如 `application/x-msdownload`；缺省静默响应 `Content-Length`）。

**示例**
```
GET /api/downloads/windows-v1.0.3-1790735631262.exe   → HTTP 200, 102218558 字节
GET /api/downloads/不存在.exe                          → HTTP 404
```

---

## 第二部分：管理端接口（需 admin 鉴权）

> 管理接口统一前缀 `/api/admin/app/releases`。
> **鉴权**：请求头携带 JWT `Authorization: Bearer <token>`，且用户须为 `admin` 角色，否则返回 401/权限错误。

---

## 4. 上传并创建发布

上传安装包并创建一条版本发布记录（自动算版本号、SHA-256、下载链接）。

```
POST /api/admin/app/releases
```

**请求格式**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 安装包。windows→`.exe`，android→`.apk`，ios→`.ipa` |
| `platform` | String | 否 | 默认 `android`；合法 `android`/`ios`/`windows` |
| `versionName` | String | 是 | 展示版本名，如 `1.0.3` |
| `updateNotes` | String | 否 | 更新说明（多行） |
| `forceUpdate` | Boolean | 否 | 是否强制，默认 `false` |
| `rolloutPercent` | Integer | 否 | 灰度比例 0~100，默认 **5** |
| `publishNow` | Boolean | 否 | 是否立即发布（true→`published`，false/缺省→`draft`） |
| `versionCode` | Integer | 否 | 指定版本号；缺省自动 `max+1`（见下方撞号逻辑） |

### 服务端行为

- **文件落盘**：写入 `download-dir`，文件名为 `<平台>-v<版本名>-<时间戳>.<扩展名>`。
- **下载链接**：`base-url + "/downloads/" + 文件名`。
- **SHA-256**：上传时实时计算，写库。
- **版本号分配（撞号逻辑）**：
  - 显式传 `versionCode`：固用该值，撞号（唯一约束冲突）直接报错 43109；
  - 不传：自动 `maxVersionCodeByPlatform + 1`，撞号则重试自动 +1，最多 5 次。
- 上传/哈希失败 → `APP_STORAGE_FAILED`；文件类型不符 → `APP_FILE_TYPE_INVALID`。

### 响应 data：AppRelease（完整字段见 §9）

---

## 5. 发布列表（分页）

```
GET /api/admin/app/releases
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `platform` | String | 否 | 平台过滤（留空查全部） |
| `status` | String | 否 | 状态过滤：`draft` / `published` / `disabled` |
| `page` | Integer | 否 | 页码，从 1 起，默认 1 |
| `size` | Integer | 否 | 每页条数，默认 20 |

排序：`versionCode` 倒序。

### 响应 data：分页结果（Spring Data `Page<AppRelease>`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | AppRelease[] | 本页记录 |
| `pageable` | Object | 分页信息 |
| `totalElements` | Long | 总记录数 |
| `totalPages` | Integer | 总页数 |
| `number` | Integer | 当前页码（从 0 起） |
| `size` | Integer | 每页条数 |
| `first` / `last` | Boolean | 是否首页/末页 |
| `empty` | Boolean | 是否为空 |

---

## 6. 修改发布（PATCH）

```
PATCH /api/admin/app/releases/{id}
```

**请求体**（JSON，部分可空，只传要改的字段）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `rolloutPercent` | Integer | 灰度比例 0~100 |
| `forceUpdate` | Boolean | 是否强制 |
| `updateNotes` | String | 更新说明 |
| `minForceCode` | Integer | 全局最低强制版本号（≥0，0=不使用） |

> **注意**：不允许改 `versionCode` 或替换安装包，换包需重新发布新版本。

校验：`rolloutPercent` 超范围 → `APP_ROLLOUT_INVALID`；`minForceCode < 0` → `BAD_REQUEST`。

**示例**
```json
{
  "rolloutPercent": 50,
  "forceUpdate": true,
  "updateNotes": "修复崩溃，强制更新"
}
```

### 响应 data：AppRelease

---

## 7. 发布 / 停用

### 7.1 发布（draft / disabled → published）

```
POST /api/admin/app/releases/{id}/publish
```

- 非 `draft`/`disabled`（即已 published）→ `APP_RELEASE_STATE_INVALID`（"该版本已发布"）。
- 成功后将 `status=published`、写入 `publishedAt`。

### 7.2 停用（published → disabled，紧急止血）

```
POST /api/admin/app/releases/{id}/disable
```

- 仅 `published` 可停用，否则 `APP_RELEASE_STATE_INVALID`。
- 停用后 `check-update` 立即不再下发该版本。

### 响应 data：AppRelease

---

## 8. 删除草稿

```
DELETE /api/admin/app/releases/{id}
```

- **仅 `draft` 可删除**；已发布/已停用 → `APP_RELEASE_STATE_INVALID`（提示用停用保留审计）。
- 删除时会同时删除磁盘上的安装包文件（如存在），再删除数据库记录。

### 响应
```json
{ "code": 0, "message": "ok", "data": null }
```

---

## 第三部分：通用定义

## 9. AppRelease 实体（发布记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键 |
| `platform` | String | 平台 `android` / `ios` / `windows` |
| `versionCode` | Integer | 构建版本号（同平台唯一递增） |
| `versionName` | String | 展示版本名 |
| `apkUrl` | String | 下载地址 |
| `fileSize` | Long | 包体字节数 |
| `fileHash` | String | SHA-256（小写 hex） |
| `updateNotes` | String | 更新说明 |
| `forceUpdate` | Boolean | 是否强制更新（默认 false） |
| `minForceCode` | Integer | 全局最低强制版本号（默认 0） |
| `rolloutPercent` | Integer | 灰度比例 0~100（默认 100） |
| `status` | String | `draft` / `published` / `disabled` |
| `createdAt` | OffsetDateTime | 创建时间 |
| `publishedAt` | OffsetDateTime | 发布时间（草稿为 null） |

**状态机：**
```
draft ──publish──▶ published ──disable──▶ disabled
                ▲                 │
                └───── publish ───┘
```

---

## 10. 常见错误码

| code | 含义 |
|------|------|
| `0` | 成功 |
| `40000` | 请求参数错误 |
| `401xx` | 认证/授权失败（未登录、token 失效、非 admin） |
| `43107` | 安装包缺失（未上传 file） |
| `43108` | 平台参数无效 |
| `43109` | 版本号已存在（撞号），换版本号或传新包 |
| `43110` | 版本名校验失败（versionName 必填） |
| `43112` | 灰度比例非法（0~100 之外） |
| `43113` | 发布记录不存在（id 无对应） |
| `43114` | 状态非法（如已发布/非草稿等） |
| `43115` | 文件类型不支持 |
| `43116` | 安装包存储/哈希失败 |

> 具体 code 数字以 service 中 `ErrorCode` 枚举实际定义为准，此处为对齐字段归纳。

---

## 11. 当前环境联调信息

- 接口根地址：`http://192.168.9.121:3001/api`
- 登录获取 admin token：`POST /api/auth/login` 体 `{"username":"admin","password":"Admin@123"}`，返回 `data.token` 后带 `Authorization: Bearer <token>` 访问管理接口。
- 当前生效版本：**windows 1.0.3**（published）
- 安装包落盘目录（**与项目同级**）：`C:\Users\HS\Coze\Drive\编程专家\downloads`

---

## 12. 典型调用流程

**管理端发版：**
1. `POST /api/admin/app/releases`（multipart）上传包 + `publishNow=true`（或创建后调 publish）
2. `GET /api/admin/app/releases?platform=windows` 查看列表
3. 需要时 `PATCH /{id}` 调灰度/强制/说明，`POST /{id}/disable` 紧急下线

**客户端升级：**
1. 启动时 `GET /api/app/check-update?platform=windows&versionCode=<当前版本>`
2. `hasUpdate=true` → 读取 `update.apkUrl` 下载 → 校验 `fileHash`（SHA-256）→ 按 `forceUpdate` 决定是否强制升级