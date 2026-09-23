package com.company.cloud.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.cloud.billing.entity.IncreaseRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * increase_requests 数据访问。
 * 审批/驳回使用条件更新（R5 定案）：WHERE id=? AND status='pending'，影响行数=0 即已被处理。
 */
@Mapper
public interface IncreaseRequestMapper extends BaseMapper<IncreaseRequest> {

    /**
     * 条件更新审批通过（R5：防并发双点）：
     * 仅当记录仍为 pending 时置为 approved，返回影响行数。
     */
    @Update("""
            UPDATE increase_requests
            SET status = 'approved',
                handled_by = #{handledBy},
                handled_at = #{handledAt}
            WHERE id = #{id} AND status = 'pending'
            """)
    int approveIfPending(@Param("id") Long id,
                         @Param("handledBy") Long handledBy,
                         @Param("handledAt") OffsetDateTime handledAt);

    /**
     * 条件更新驳回（R5）：remark 为驳回理由（追加到原 remark）。
     */
    @Update("""
            UPDATE increase_requests
            SET status = 'rejected',
                handled_by = #{handledBy},
                handled_at = #{handledAt},
                remark = CASE WHEN #{reason} IS NULL OR #{reason} = ''
                              THEN remark
                              ELSE COALESCE(remark, '') || ' | 驳回原因: ' || #{reason}
                         END
            WHERE id = #{id} AND status = 'pending'
            """)
    int rejectIfPending(@Param("id") Long id,
                        @Param("handledBy") Long handledBy,
                        @Param("handledAt") OffsetDateTime handledAt,
                        @Param("reason") String reason);
}