-- 任务书 04 §4：audit_logs（Owner：C 组）
-- Flyway 版本号约定：B 组 V1xxx，C 组 V2xxx，禁止手工改库。

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,                          -- 操作者（系统任务可为空）
    action      TEXT NOT NULL,                   -- login/upload/download/delete/restore/quota_change/...
    target      TEXT,                            -- 操作对象（文件ID、用户名等）
    ip          TEXT,
    detail      JSONB,                           -- 扩展信息（文件名、大小、前后值）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 按人 + 时间段筛选（审计查询主路径）
CREATE INDEX idx_audit_logs_user_time   ON audit_logs (user_id, created_at DESC);
-- 按动作 + 时间段筛选
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, created_at DESC);
