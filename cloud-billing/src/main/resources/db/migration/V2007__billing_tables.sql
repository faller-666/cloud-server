-- =============================================================
-- V2007__billing_tables.sql  (D 组 billing，§3.4 迁移规划)
-- 建表：billing_config / increase_requests / billing_records + §3.3 索引 + 种子
-- 金额全部以「分」存储（R4 定案）；增量周期 = 滚动 30 天（R1 定案）
-- =============================================================

-- 单行配置表（id 恒为 1）
CREATE TABLE billing_config (
    id                          INTEGER PRIMARY KEY CHECK (id = 1),
    free_bytes                  BIGINT NOT NULL DEFAULT 21474836480,          -- 新用户默认免费额度 20GB（R3 快照制）
    price_per_gb_month_cents    INTEGER NOT NULL DEFAULT 100,                 -- 单价：分/GB/月（默认 1 元）
    updated_by                  BIGINT,                                        -- 最后修改人 users.id
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE billing_config IS '计费全局配置（单行，id=1）';
COMMENT ON COLUMN billing_config.free_bytes IS '新注册用户的默认免费额度（快照制：仅影响此后新用户）';
COMMENT ON COLUMN billing_config.price_per_gb_month_cents IS '增额单价（分/GB/月），金额以分存储（R4）';

-- 初始化一行：20GB / 100 分
INSERT INTO billing_config (id, free_bytes, price_per_gb_month_cents, updated_by, updated_at)
VALUES (1, 21474836480, 100, NULL, now());

-- 增额申请
CREATE TABLE increase_requests (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,                                            -- FK -> users.id
    gb_count        INTEGER NOT NULL CHECK (gb_count >= 1 AND gb_count <= 1000),-- 整数 GB 1~1000
    amount_cents    BIGINT NOT NULL,                                            -- 应付金额（分，后端重算）
    status          VARCHAR(16) NOT NULL DEFAULT 'pending'                      -- pending/approved/rejected
                    CHECK (status IN ('pending','approved','rejected')),
    remark          VARCHAR(500),
    handled_by      BIGINT,                                                     -- 审批人 users.id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    handled_at      TIMESTAMPTZ
);
COMMENT ON TABLE increase_requests IS '用户增额申请';

-- 计费记录（审批通过生成，status: active/expired）
CREATE TABLE billing_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,                                            -- FK -> users.id
    request_id      BIGINT NOT NULL,                                            -- FK -> increase_requests.id，唯一（一申请一记录）
    gb_count        INTEGER NOT NULL,
    amount_cents    BIGINT NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL,                                       -- 生效时刻
    expire_at       TIMESTAMPTZ NOT NULL,                                       -- = applied_at + 30 天（R1 滚动周期）
    status          VARCHAR(16) NOT NULL DEFAULT 'active' CHECK (status IN ('active','expired')),
    approved_by     BIGINT,                                                     -- 审批人 users.id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE billing_records IS '计费记录（增量额度台账），status active/expired';

-- §3.3 索引
CREATE INDEX idx_ir_user ON increase_requests(user_id, created_at DESC);       -- 我的申请列表
CREATE INDEX idx_ir_status ON increase_requests(status, created_at) WHERE status='pending'; -- 审批待办（部分索引）
CREATE UNIQUE INDEX uk_br_request ON billing_records(request_id);             -- 一申请一记录（DB 级防重，§3.2）
CREATE INDEX idx_br_user_active ON billing_records(user_id) WHERE status='active'; -- 额度聚合
CREATE INDEX idx_br_expire ON billing_records(expire_at) WHERE status='active';    -- 每日到期扫描（部分索引）