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
 * 到期收回（R6）与冗余同步均使用条件更新 + 幂等标记（extra_deducted），保证可重入、不重复扣减。
 */
@Mapper
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {

    /**
     * 到期收回核心（R6 定案）：条件更新 active -> expired，返回受影响行数。
     * WHERE status='active' AND expire_at<=now() 天然幂等，重复执行无副作用。
     * 触发后记录 status 变为 expired，且 extra_deducted=0（等待后续扣减）。
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
     * 到期收回：计算指定用户"本次到期且尚未扣减"的增量额度（字节）。
     * 仅统计 status='expired' AND extra_deducted=0 的记录（即刚被 expireDue 标记、
     * 尚未扣减的本次到期记录；存量已扣记录 extra_deducted=1 被排除，保证存量不动）。
     * 单位与发放侧保持一致：gb_count × 1024³（字节）。
     */
    @Select("""
            SELECT COALESCE(SUM(b.gb_count) * 1073741824, 0)
            FROM billing_records b
            WHERE b.user_id = #{userId}
              AND b.status = 'expired'
              AND b.expire_at <= #{now}
              AND b.extra_deducted = 0
            """)
    long sumDueExtraBytes(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 到期收回：扣减指定用户冗余 extra_bytes（字节，GREATEST 防负）。
     * 需在 sumDueExtraBytes 之后再调用本方法，且与 markDueDeducted 在同一事务内。
     */
    @Update("""
            UPDATE users
            SET extra_bytes = GREATEST(extra_bytes - #{bytes}, 0)
            WHERE id = #{userId}
            """)
    int reduceExtraBytes(@Param("userId") Long userId, @Param("bytes") long bytes);

    /**
     * 到期收回：把指定用户已扣减的到期记录标记为 extra_deducted=1。
     * 幂等标记：任务重跑、并发时不会对同一批记录重复扣减。
     */
    @Update("""
            UPDATE billing_records
            SET extra_deducted = 1
            WHERE user_id = #{userId}
              AND status = 'expired'
              AND expire_at <= #{now}
              AND extra_deducted = 0
            """)
    int markDueDeducted(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * 到期收回：收集本次到期（expireDue 已标记为 expired）且尚未扣减的用户去重列表。
     * 状态用 'expired' 且 extra_deducted=0 精确命中"本次待扣"用户，幂等；分批（LIMIT）防全表。
     */
    @Select("""
            SELECT DISTINCT user_id FROM billing_records
            WHERE status = 'expired' AND expire_at <= #{now} AND extra_deducted = 0
            ORDER BY user_id
            LIMIT #{limit}
            """)
    List<Long> selectExpiredUserIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * 指定用户本次到期、尚未扣减的记录（供审计 detail 用）。
     */
    @Select("""
            SELECT * FROM billing_records
            WHERE user_id = #{userId} AND status = 'expired'
              AND expire_at <= #{now} AND extra_deducted = 0
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