package com.company.cloud.files.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.cloud.files.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * audit_logs 表数据访问（Owner：C 组）。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 写入一条审计日志。detail 为 JSON 文本，CAST 成 jsonb 落库——
     * MP 默认 insert 处理不了 jsonb cast，必须自定义 SQL。
     */
    @Insert("""
            INSERT INTO audit_logs(user_id, action, target, ip, detail)
            VALUES (#{userId}, #{action}, #{target}, #{ip}, CAST(#{detail} AS jsonb))
            """)
    int insertEvent(@Param("userId") Long userId,
                    @Param("action") String action,
                    @Param("target") String target,
                    @Param("ip") String ip,
                    @Param("detail") String detail);
}
