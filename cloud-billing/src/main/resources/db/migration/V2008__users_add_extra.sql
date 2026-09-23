-- =============================================================
-- V2008__users_add_extra.sql  (D 组 billing，§3.4 迁移规划)
-- users 表加两列（冗余，仅 D 组 billing 维护，§3.2/§3.4 定案）
--   extra_bytes      有效增量额度之和（BIGINT NOT NULL DEFAULT 0）
--   extra_expire_at  最近一笔增量到期时刻（可空，展示冗余）
-- =============================================================

ALTER TABLE users
    ADD COLUMN extra_bytes BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN users.extra_bytes IS '有效增量额度之和（冗余，仅 D 组 billing 在审批通过/到期收回两个写点维护）';

ALTER TABLE users
    ADD COLUMN extra_expire_at TIMESTAMPTZ;
COMMENT ON COLUMN users.extra_expire_at IS '最近一笔增量到期时刻（冗余展示，可空）';