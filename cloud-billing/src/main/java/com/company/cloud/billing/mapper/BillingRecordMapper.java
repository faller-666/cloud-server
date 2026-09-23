package com.company.cloud.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.cloud.billing.dto.RecordView;
import com.company.cloud.billing.entity.BillingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * billing_records 数据访问。
 * 到期收回（R6）与冗余同步均使用条件更新，保证幂等可重入。
 */
@Mapper
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {

    /**
     * 到期收回核心（R6 定案）：条件更新 active -> expired，返回受影响行数。
     * WHERE status='active' AND expire_at<=now() 天然幂等，重复执行无副作用。
     */
    @Update("""
            UPDATE billing_records
            SET status = 'expired'
            WHERE status = 'active' AND expire_at <= #{now}
            """)
    int expireDue(@Param("now") OffsetDateTime now);

    /**
     * 到期收回后，对指定用户在留下的 active 记录中重算 extra_expire_at：
     * 取 MIN(expire_at)；无 active 记录置 null（R6 第③步）。
     */
    @Update("""
            UPDATE users
            SET extra_expire_at = (
                SELECT MIN(expire_at) FROM billing_records
                WHERE user_id = #{userId} AND status = 'active'
            )
            WHERE id = #{userId}
            """)
    int recalcUserExpireAt(@Param("userId") Long userId);

    /**
     * 到期收回后同步扣减 extra_bytes（R6 第②步）。
     * 扣减量为本用户本次到期的 gb_count 之和；GREATEST 兜底防负。
     */
    @Update("""
            UPDATE users
            SET extra_bytes = GREATEST(extra_bytes - (
                SELECT COALESCE(SUM(b2.gb_count), 0) FROM billing_records b2
                WHERE b2.user_id = #{userId} AND b2.status = 'expired'
                  AND b2.expire_at <= #{now}
            ), 0)
            WHERE id = #{userId}
            """)
    int deductExpiredExtra(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 到期收回：从本次到期的记录中收集去重后的 user_id 列表（供逐用户重算/扣减/审计）。
     * 返回最近一批（避免一次全表，按 id 升序 LIMIT）。
     */
    @Select("""
            SELECT DISTINCT user_id FROM billing_records
            WHERE status = 'active' AND expire_at <= #{now}
            ORDER BY user_id
            LIMIT #{limit}
            """)
    List<Long> selectExpiredUserIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * 指定用户在本批次到期（<=now）但尚未扣减的记录（供审计 detail 用）。
     */
    @Select("""
            SELECT * FROM billing_records
            WHERE user_id = #{userId} AND status = 'active' AND expire_at <= #{now}
            """)
    List<BillingRecord> selectDueRecordsOfUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 管理端台账分页（§4.2 records）：billing_records LEFT JOIN users 取 username。
     * 支持 userId / status 过滤；按 id 倒序。
     */
    @Select("""
            <script>
            SELECT br.id, br.user_id, u.username, br.gb_count, br.amount_cents,
                   br.applied_at, br.expire_at, br.status, br.request_id,
                   br.approved_by, au.username AS approved_by_name
            FROM billing_records br
            LEFT JOIN users u ON u.id = br.user_id
            LEFT JOIN users au ON au.id = br.approved_by
            WHERE 1 = 1
            <if test="userId != null"> AND br.user_id = #{userId} </if>
            <if test="status != null and status != ''"> AND br.status = #{status} </if>
            ORDER BY br.id DESC
            </script>
            """)
    Page<RecordView> selectRecordPage(Page<RecordView> page,
                                      @Param("userId") Long userId,
                                      @Param("status") String status);

    /**
     * 单用户有效增量之和（active 记录 gb_count 合计），用于初始化查询聚合校验。
     */
    @Select("SELECT COALESCE(SUM(gb_count), 0) FROM billing_records WHERE user_id = #{userId} AND status = 'active'")
    long sumActiveGbByUser(@Param("userId") Long userId);

    /**
     * 用户所有 active 记录中最新的到期时刻（可能为 null）。
     */
    @Select("SELECT MAX(expire_at) FROM billing_records WHERE user_id = #{userId} AND status = 'active'")
    OffsetDateTime maxActiveExpireAt(@Param("userId") Long userId);
}