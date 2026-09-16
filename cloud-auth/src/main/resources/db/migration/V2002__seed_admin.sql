-- C组补：种子管理员账号（本地联调/演示用）
-- A组建表时未提供初始账号，空库无法登录；按组规走 Flyway 迁移（C组 V2xxx 号段）
-- 账号: admin / Admin@123（bcrypt(12)，与 SecurityConfig 的 BCryptPasswordEncoder(12) 一致）
-- must_change_password 置 false：避免联调时业务接口被 40302 拦截；如需演示首登强制改密，改回 true 即可
INSERT INTO users (username, password_hash, role, quota_bytes, used_bytes, status, must_change_password)
VALUES ('admin', '$2b$12$yFMJt11oJC9ZCD720rm4LeoRsYTHMCmA8FNgohiajCfiZdBMgcrF2',
        'admin', 21474836480, 0, 'active', false);