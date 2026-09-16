-- ============================================================
-- C 组：audit_logs 增加 status 列（前端清单 P2：审计执行结果展示）
-- 现有审计只记录成功事件（失败抛异常不落库），故默认值 success 语义正确；
-- insertEvent 不带该列，靠 DEFAULT 兜底，未来要记失败时再扩展写入路径。
-- ============================================================
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'success';
