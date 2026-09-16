-- complete 成功后回填 file_id，供幂等重试返回最终文件 id
ALTER TABLE upload_sessions ADD COLUMN file_id BIGINT;