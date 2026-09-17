# cloud-server · 私有云存储平台后端

[![CI](https://github.com/faller-666/cloud-server/actions/workflows/ci.yml/badge.svg)](https://github.com/faller-666/cloud-server/actions/workflows/ci.yml)
![JDK](https://img.shields.io/badge/JDK-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)
![MinIO](https://img.shields.io/badge/MinIO-S3%20Compatible-orange)

企业内网私有云存储平台后端服务：**数据不出域**，部署于自有服务器（Dell R630），为前端提供认证、文件传输、文件管理与审计全套 REST API。

## ✨ 功能特性

- **JWT 认证**：access / refresh 双 token，登录限流、账号锁定、token 吊销（Redis）
- **分片上传**：大文件分片并发上传、断点续传（init 上报 sha256 支持校验）
- **秒传**：内容寻址存储（对象 key = sha256），相同文件服务端去重
- **下载**：MinIO 预签名 URL 直连下载，支持过期时间控制
- **文件管理**：目录树、重命名、批量移动（batch-move）
- **回收站**：软删除 + 恢复
- **操作审计**：全量操作留痕，管理员可按用户/类型/时间查询
- **存储统计**：用量概览
- **OpenAPI 文档**：内置 Swagger UI，在线查看与调试全部接口

## 🏗 架构

```
                    ┌────────────────────┐
                    │      前端 (SPA)     │
                    └─────────┬──────────┘
                              │ Bearer JWT
                              ▼
┌─────────────────────────────────────────────────────┐
│                   cloud-app (3001)                    │
│   Spring Boot 启动入口 · 统一异常 · context-path /api  │
├──────────┬──────────────┬───────────────┬───────────┤
│ cloud-   │  cloud-      │  cloud-       │  cloud-   │
│ auth     │  transfer    │  files        │  common   │
│ 认证域    │  传输域       │  文件域        │  公共模块  │
│ 登录/JWT  │  分片上传/下载 │  目录/回收站   │  (三组共有) │
│ 限流/吊销 │  秒传/预签名   │  审计/统计    │           │
└────┬─────┴──────┬───────┴──────┬───────┴───────────┘
     ▼            ▼              ▼
┌─────────┐  ┌─────────┐   ┌───────────┐
│ Redis 7 │  │ MinIO   │   │ PostgreSQL │
│ 限流/吊销 │  │ 对象存储  │   │ 16 (Flyway)│
└─────────┘  └─────────┘   └───────────┘
```

| 模块 | 职责 |
| --- | --- |
| `cloud-app` | 启动模块（`CloudApplication`），聚合全部依赖，唯一可运行入口 |
| `cloud-auth` | 认证域：JWT 签发/刷新/校验、登录限流、账号锁定、token 吊销 |
| `cloud-transfer` | 传输域：分片上传、断点续传、秒传、预签名下载 |
| `cloud-files` | 文件域：目录树、批量移动、回收站、审计查询、存储统计 |
| `cloud-common` | 公共模块：通用响应体、异常、工具类（**三组共有，改动须会签**） |

## 🛠 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.3.4（Maven 多模块单仓） |
| 数据库 | PostgreSQL 16 + Flyway（schema 版本管理） |
| ORM | MyBatis-Plus |
| 缓存 | Redis 7（StringRedisTemplate：限流、token 吊销） |
| 对象存储 | MinIO（S3 兼容，分片 + 预签名 URL） |
| 安全 | Spring Security + JWT（自研 cloud-auth） |
| 文档 | springdoc-openapi（Swagger UI） |
| CI | GitHub Actions（`mvn verify`，PR 必须通过） |

## 🚀 快速开始

### 1. 环境要求

- JDK 17
- Maven 3.9+
- PostgreSQL 16（库名默认 `cloud`）
- Redis 7
- MinIO（任意 S3 兼容部署均可）

### 2. 构建验证

```bash
mvn clean verify
```

### 3. 启动依赖服务

**启动顺序很重要**：Redis → MinIO → 应用。应用启动时会执行 `ensureBucket()`，若 MinIO 未就绪桶不会创建，上传接口将报 50000。

```bash
# Redis
redis-server.exe

# MinIO（Windows 示例，数据目录自定）
minio.exe server D:\minio\data
```

### 4. 启动应用

```bash
mvn -pl cloud-app spring-boot:run
```

启动成功日志：`[transfer] MinIO bucket 已就绪`

> 注意：若应用启动时 Redis/MinIO 未就绪，连接不会自动恢复（Lettuce 行为），需重启应用。

### 5. 默认账号

`admin / Admin@123`（Flyway V2002 初始化，仅限内网开发环境，首次登录后请修改）

### 6. 接口文档

```
http://<服务器IP>:3001/api/swagger-ui/index.html
```

`/auth/login` 等白名单接口可直接调试；其余接口先调 login 拿 token，在 Swagger 右上角 **Authorize** 填入。

## ⚙️ 配置（环境变量）

所有配置支持环境变量覆盖（详见 `cloud-app/src/main/resources/application.yml`）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `cloud` | PostgreSQL 连接 |
| `DB_USER` / `DB_PASSWORD` | `cloud` / `cloud` | 数据库凭证 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接 |
| `JWT_SECRET` | dev-only | **生产必须替换**（≥32 字节） |
| `JWT_ACCESS_TTL_MINUTES` / `JWT_REFRESH_TTL_DAYS` | `120` / `14` | token 有效期 |
| `SECURITY_MAX_LOGIN_FAILURES` / `SECURITY_LOCK_MINUTES` | `5` / `15` | 账号锁定策略 |
| `SECURITY_RATE_LIMIT_PER_MINUTE` | `5` | 单 IP 登录限流 |
| `MINIO_ENDPOINT` | 见 yml | MinIO 地址（**按部署环境显式配置**） |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `minioadmin` | MinIO 凭证 |
| `MINIO_BUCKET` | `cloud-files` | 存储桶名 |
| `DEFAULT_QUOTA_BYTES` | `21474836480`（20GB） | 新用户默认配额 |

> 部署提示：不要依赖默认值里的 localhost / 具体 IP，容器与跨机部署一律显式注入环境变量。

## 📖 开发规范

- **分支策略**：禁止直推 `main`。`feature/<域>-<主题>` 分支 → PR → CI 全绿 → 合并
- **CI 门禁**：GitHub Actions `ci.yml`（`mvn verify`），PR 必须 Checks 通过才可合并
- **代码评审**：CODEOWNERS 强制评审；契约改动须三组会签
- **数据库变更**：一律走 Flyway migration，禁止手工改库；版本号 = main 上最大版本号 + 1（不按分组号段）
- **公共模块**：`cloud-common` 为三组共有，任何改动须全员会签，禁止单组私改

## 📁 目录结构

```
cloud-server
├── cloud-app/        # 启动模块（CloudApplication）
├── cloud-auth/       # 认证域：登录、JWT、限流、吊销
├── cloud-transfer/   # 传输域：分片上传、秒传、预签名下载
├── cloud-files/      # 文件域：目录、回收站、审计、统计
├── cloud-common/     # 公共模块（三组共有）
└── .github/workflows/ci.yml   # CI：mvn verify
```

## 👥 团队分工

| 分组 | GitHub | 职责域 |
| --- | --- | --- |
| A 组 | tang-qin1026 | 认证（cloud-auth） |
| B 组 | www-yl | 传输（cloud-transfer） |
| C 组 | faller-666 | 文件管理与审计（cloud-files） |
