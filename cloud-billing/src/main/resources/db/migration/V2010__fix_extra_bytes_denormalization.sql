-- =============================================================
-- V2010__fix_extra_bytes_denormalization.sql  (D 组 billing，存量脏数据修复)
-- 背景：旧代码把「GB 整数」直接当作字节存进 users.extra_bytes（未乘 1024^3），
--       已审批通过、仍在生效期内的增量额度（status='active' 记录）的值是脏的，
--       前端 quota 对老账号返回的 extraBytes 错误；B 组上传配额判定也吃不进增量。
-- 新代码只对「以后审批」写对，存量不会自动变对——本迁移按权威表
--       billing_records 重算并回写冗余列：
--         extra_bytes      = SUM(active.gb_count) × 1024^3      （字节）
--         extra_expire_at  = MIN(active.expire_at)，无 active 置 NULL
-- 幂等：按 active 重算，可重复执行，结果一致。
-- 注意：本迁移必须在 V2009（extra_deducted 幂等列 + 存量 expired 置 1）之后执行。
-- =============================================================

-- 仅更新有增量额度消费痕迹的用户（非零、有 expire、或存在 active 记录），避免触碰其余行
UPDATE users u
SET extra_bytes =
        COALESCE((
            SELECT SUM(br.gb_count) * 1073741824
            FROM billing_records br
            WHERE br.user_id = u.id AND br.status = 'active'
        ), 0),
    extra_expire_at = (
        SELECT MIN(br.expire_at)
        FROM billing_records br
        WHERE br.user_id = u.id AND br.status = 'active'
    )
WHERE u.extra_bytes <> 0
   OR u.extra_expire_at IS NOT NULL
   OR EXISTS (SELECT 1 FROM billing_records br
              WHERE br.user_id = u.id AND br.status = 'active');