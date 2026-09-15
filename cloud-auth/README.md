# 云存储平台 · 后端 A 组：认证与用户管理

基于 Spring Boot 3 + Java 17 的认证与用户管理后端工程，完整落地任务书 02 全部需求。

## 技术栈
- Spring Boot 3.3 / Java 17 / Maven
- Spring Security + JWT（jjwt 0.12）
- Spring Data JPA（Hibernate，表结构由 Flyway 管理）
- PostgreSQL + Flyway
- Redis（吊销列表 + 登录限流锁定）
- SpringDoc OpenAPI（Swagger）

## 目录结构
```
src/main/java/com/cloudstorage/
├── CloudStorageApplication.java     启动类
├── common/    通用：统一返回、错误码、全局异常、配置
├── security/  安全：JwtAuthFilter(AuthGuard)、RolesInterceptor(RolesGuard)
│              JwtService、RevocationService、LoginThrottle、@RequireRole
├── module/
│   ├── auth/      认证：登录/刷新/登出/改密/profile
│   ├── user/      用户：实体、仓库、管理端 CRUD
│   └── audit/     审计（对接 C 组）
└── resources/db/migration/  Flyway 迁移脚本
```

## 环境准备
1. PostgreSQL：创建库与用户
   ```sql
   CREATE DATABASE cloud_storage;
   CREATE USER cloud WITH PASSWORD 'cloud';
   GRANT ALL ON DATABASE cloud_storage TO cloud;
   ```
   应用启动时 Flyway 自动执行 `V1__create_users.sql` 建表。
2. Redis：`redis-server`（默认 localhost:6379）
3. 配置：冒烟/本机可先改 `application.yml` 中 `app.jwt.secret`；生产务必用环境变量注入。

## 启动
```bash
# 方式一：IDEA 直接运行 CloudStorageApplication
# 方式二：命令行
mvn spring-boot:run
# 或打包
mvn clean package && java -jar target/cloud-storage-backend-1.0.0.jar
```
服务启动后 Swagger：http://localhost:8080/swagger-ui.html

## 认证使用说明（交付给 B/C 组的 Guard 用法）

调用任意业务接口需在请求头携带 `Authorization: Bearer <accessToken>`。

- **AuthGuard 等价（JwtAuthFilter）**：已内置在安全链中，自动完成
  - 校验 JWT 签名/过期/签发者
  - 校验 Redis 吊销列表（登出、改密、禁用后即时失效）
  - 通过后注入当前用户，业务代码用 `Authentication#getPrincipal()` 获取 `CurrentUser`
- **RolesGuard 等价（@RequireRole + RolesInterceptor）**：控制器/方法上加注解
  ```java
  @RequireRole("admin")   // 仅 admin 可访问，否则返回 40301
  ```
- **当前用户获取**：
  ```java
  public ApiResponse<?> foo(Authentication auth) {
      CurrentUser cu = (CurrentUser) auth.getPrincipal(); // {id, username, role}
  }
  ```

## API 一览
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录，返回双 token |
| POST | /api/auth/refresh | 刷新 access（轮换） |
| POST | /api/auth/logout | 登出，吊销 token |
| GET | /api/auth/profile | 当前用户资料、配额、用量 |
| POST | /api/auth/change-password | 修改密码 |
| GET | /api/admin/users | 用户列表（分页/搜索/状态筛选）|
| POST | /api/admin/users | 创建用户 |
| PATCH | /api/admin/users/:id | 禁用/启用、配额调整、角色变更 |
| POST | /api/admin/users/:id/reset-password | 重置密码 |

## 错误码（4xxxx）
40101 用户名或密码错误 · 40102 账号已禁用 · 40103 token 无效
42301 账号锁定中 · 40301 无权限 · 40302 未修改初始密码 · 42101 配额过低

## 说明
- 表结构变更一律新建 `db/migration/VN__xxx.sql`，禁止手改数据库。
- `used_bytes` 由 B 组维护，本组只读。
- 审计事件经 `AuditService` 输出，接入 C 组真实实现时替换内部逻辑即可。