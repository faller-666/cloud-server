# storage —— 后端 B 组（文件传输核心）最小可运行版

> 用途：在 A 组 common / auth 交付之前，让 B 组能本地独立拉起服务、自测上传/下载全链路。
> 本目录内的 `StorageApplication`、`application.yml`、`db/migration`、`docker/docker-compose.dev.yml`
> 均为「最小可运行版」专用；正式联调接 A 组后，启动类与本地 migration 会被 common 底座取代。

## 一、环境准备（一次性）

1. **Docker + Docker Compose**（用来起 PostgreSQL / MinIO）
2. **JDK 21**
3. **VSCode** + 插件：`Extension Pack for Java`、`Spring Boot Extension Pack`

## 二、启动步骤

### 1. 起基础设施（PG + MinIO）

```bash
cd cloud-storage
docker compose -f docker/docker-compose.dev.yml up -d
```

- PG：`localhost:5432`（库 `cloudstorage` / 用户 `cloud` / 密码 `cloud123`）
- MinIO API：`localhost:9000`，控制台 `localhost:9001`（账号密码都是 `minioadmin`）

### 2. 起应用

VSCode 打开 `storage/` 目录（等 Maven 首次拉依赖），运行 `StorageApplication` 的 `main`；
或命令行：

```bash
cd storage
mvn spring-boot:run
```

看到以下日志即启动成功：

```text
Flyway 迁移完成（users + files + file_hashes + upload_sessions）
[storage] MinIO bucket 已就绪
```

## 三、验证接口（curl）

默认端口 8080，所有请求带 `X-User-Id: 1`（本地 seed 的 dev 用户，配额 20GB）。

> 说明：最小可运行版返回的是「裸 data 对象」（无 `{code,message,data}` 统一包装），
> 接 A 组 common 后才会套统一返回壳。

### 1. 造一个 10MB 测试文件 + 算 sha256

```bash
cd /tmp
dd if=/dev/urandom of=test.bin bs=1M count=10
sha256sum test.bin     # 记下这串 hash，下面用 <SHA> 代替
```

### 2. 完整分片上传链路（10MB = 8MB + 2MB，2 个分片）

```bash
# (1) init：开启上传会话
curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"name":"test.bin","size":10485760,"sha256":"<SHA>"}'
# 返回 {"status":"uploading","sessionId":1,"uploadId":"...","chunkSize":8388608}

# (2) 切两片
dd if=test.bin of=part1 bs=1M count=8
dd if=test.bin of=part2 bs=1M skip=8

# (3) 传分片 1、2（SESSION 换成上面的 sessionId）
curl -X PUT http://localhost:8080/api/uploads/<SESSION>/parts/1 \
  -H "X-User-Id: 1" --data-binary @part1
curl -X PUT http://localhost:8080/api/uploads/<SESSION>/parts/2 \
  -H "X-User-Id: 1" --data-binary @part2

# (4) 断点续传：查已传分片
curl http://localhost:8080/api/uploads/<SESSION> -H "X-User-Id: 1"
# 返回 {"status":"uploading","uploadId":"...","uploadedParts":[1,2],...}

# (5) complete：合并分片、落库、实扣配额
curl -X POST http://localhost:8080/api/uploads/<SESSION>/complete -H "X-User-Id: 1"
# 返回 {"fileId":1,"alreadyDone":false}   ← 上传完成，档案已生成（fileId 即 files 表主键）

# (6) 下载签发（FILE 是 complete 后 files 表的 id，可在数据库查；首条通常是 1）
curl http://localhost:8080/api/files/1/download -H "X-User-Id: 1"
# 返回 {"url":"http://localhost:9000/cloud-files/objects/<SHA>?...预签名参数..."}
# 浏览器打开这个 url 即可下载
```

### 3. 秒传验证（第二次上传同内容，零字节）

```bash
# 再 init 一次同一个文件（sha256 相同）
curl -X POST http://localhost:8080/api/uploads/init \
  -H "Content-Type: application/json" -H "X-User-Id: 1" \
  -d '{"name":"test2.bin","size":10485760,"sha256":"<SHA>"}'
# 返回 {"status":"done","fileId":2}   ← 命中秒传，直接完成，不再传字节
```

## 四、接口速查

| 方法 | 路径 | 说明 | 关键头 / body |
|---|---|---|---|
| POST | `/api/uploads/init` | 初始化（含秒传分支） | `X-User-Id` + JSON `{name,size,parentId?,sha256?}` |
| PUT | `/api/uploads/{id}/parts/{no}` | 传单个分片 | `X-User-Id` + 二进制 body |
| GET | `/api/uploads/{id}` | 断点续传查询 | `X-User-Id` |
| POST | `/api/uploads/{id}/complete` | 合并落库 | `X-User-Id` |
| POST | `/api/uploads/{id}/abort` | 取消 | `X-User-Id` |
| GET | `/api/files/{id}/download` | 预签名下载 | `X-User-Id` |

## 五、注意事项

- **验证顺序**：先 `docker compose up` 起 PG/MinIO，再起应用；否则应用起不来（Flyway 会连库失败）。
- **本地 migration 与启动类是临时物**：接 A 组 common 后删除 `StorageApplication`、`db/migration`，
  数据源/MinIO 配置切到 common 的 application.yml，鉴权切 `SecurityUtil.currentUserId()`。
- **分片大小 8MB（`upload.part-size`）**：单片超 8MB 会报 40002，序号 `≤ ceil(size/8MB)`。
- **配额**：dev 用户 20GB，测试大文件不要真传超配额；`release` 逻辑在彻底删除时触发（C 组接口）。