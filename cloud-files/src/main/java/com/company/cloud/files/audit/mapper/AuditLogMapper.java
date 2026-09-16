package com.company.cloud.files.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.cloud.files.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 按 user_id 批量取用户名（查询补 VO 的 username 用）。
     * 返回 List of Map：{id=Long, username=String}；users 表 Owner 为 A 组，只读。
     */
    @Select("""
            <script>
            SELECT id, username FROM users
            WHERE id IN
            <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectUsernames(@Param("userIds") List<Long> userIds);
}
