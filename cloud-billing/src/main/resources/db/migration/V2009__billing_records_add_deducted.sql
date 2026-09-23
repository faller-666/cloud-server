-- =============================================================
-- V2009__billing_records_add_deducted.sql  (D 组 billing，到期收回幂等修复)
-- 到期收回（R6）保证幂等：标记 billing_records 是否已完成 extra_bytes 扣减。
--   extra_deducted = 1 表示该记录的到期增量额度已从 users.extra_bytes 扣减，
--   任务重跑、多实例并发时均不会重复扣减。
--  存量已过期记录（status='expired'）统一置 1，视为已扣（存量数据不动，
--  避免首次运行误改历史余额）。
-- =============================================================

ALTER TABLE billing_records
    ADD COLUMN extra_deducted SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN billing_records.extra_deducted IS
    '到期后冗余 extra_bytes 是否已扣减（幂等标记：1=已扣，0=未扣）';

-- 存量已过期记录视为已扣，跳过（存量数据不动）
UPDATE billing_records SET extra_deducted = 1 WHERE status = 'expired';