-- app_release.created_at / published_at：由无时区 timestamp 改为带时区 timestamptz
-- 目的：根治「无时区列 + now() 受会话时区影响 + 序列化固定 +08」导致的时区错位（错 8 小时）。
--       timestamptz 存绝对时刻，不随会话/JVM/序列化时区偏移，OffsetDateTime 天然匹配。
-- 存量数据处理：把现有值按东八区墙钟解释为绝对时刻，展示口径保持不变，不额外位移。
ALTER TABLE app_release
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Shanghai',
    ALTER COLUMN published_at TYPE TIMESTAMPTZ USING published_at AT TIME ZONE 'Asia/Shanghai';