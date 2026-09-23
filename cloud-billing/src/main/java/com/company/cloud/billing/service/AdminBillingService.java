package com.company.cloud.billing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.cloud.auth.security.CurrentUser;
import com.company.cloud.billing.dto.RecordView;
import com.company.cloud.billing.dto.RequestView;
import com.company.cloud.billing.dto.UpdateConfigRequest;
import com.company.cloud.billing.entity.BillingConfig;
import com.company.cloud.billing.entity.BillingRecord;
import com.company.cloud.billing.entity.IncreaseRequest;
import com.company.cloud.billing.mapper.BillingConfigMapper;
import com.company.cloud.billing.mapper.BillingRecordMapper;
import com.company.cloud.billing.mapper.IncreaseRequestMapper;
import com.company.cloud.billing.mapper.UserQuotaMapper;
import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 管理端计费服务（§4.2）：配置查看/修改、增额审批（通过/驳回）、缴费台账。
 * 审批/驳回为条件更新（R5），到期收回逻辑见 {@link ExpireTask}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBillingService {

    private final BillingConfigMapper configMapper;
    private final IncreaseRequestMapper requestMapper;
    private final BillingRecordMapper recordMapper;
    private final UserQuotaMapper userQuotaMapper;
    private final AuditService auditService;

    /** 增量周期：滚动 30 天（R1 定案） */
    private static final int PERIOD_DAYS = 30;

    /**
     * 管理端读取计费配置。
     */
    public com.company.cloud.billing.dto.ConfigView getConfig() {
        BillingConfig cfg = configMapper.selectById(1);
        if (cfg == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计费配置缺失");
        }
        return new com.company.cloud.billing.dto.ConfigView(
                cfg.getFreeBytes() == null ? 0L : cfg.getFreeBytes(),
                cfg.getPricePerGbMonthCents() == null ? 0 : cfg.getPricePerGbMonthCents(),
                "freeBytes 仅影响此后新注册用户（R3 快照制）");
    }

    /**
     * 修改计费配置（§4.2 PATCH /admin/billing/config）。
     * 至少一项，值负由 @Min 校验（→40000）。写审计 billing_config。
     */
    @Transactional
    public com.company.cloud.billing.dto.ConfigView updateConfig(CurrentUser admin, UpdateConfigRequest req) {
        if (req.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "至少需要提供一个待修改字段");
        }
        BillingConfig cfg = configMapper.selectById(1);
        if (cfg == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计费配置缺失");
        }
        Long oldFree = cfg.getFreeBytes();
        Integer oldPrice = cfg.getPricePerGbMonthCents();
        String oldJson = "{freeBytes:" + oldFree + ",priceCents:" + oldPrice + "}";

        if (req.freeBytes() != null) {
            cfg.setFreeBytes(req.freeBytes());
        }
        if (req.pricePerGbMonthCents() != null) {
            cfg.setPricePerGbMonthCents(req.pricePerGbMonthCents());
        }
        cfg.setUpdatedBy(admin.getId());
        cfg.setUpdatedAt(OffsetDateTime.now());
        configMapper.updateById(cfg);

        String newJson = "{freeBytes:" + cfg.getFreeBytes() + ",priceCents:" + cfg.getPricePerGbMonthCents() + "}";
        auditService.record(new AuditEvent(
                admin.getId(), AuditActions.BILLING_CONFIG, "1", null,
                Map.of("before", oldJson, "after", newJson)));

        return getConfig();
    }

    /**
     * 管理端增额申请列表（§4.2）：status 缺省 pending，支持 all/approved/rejected。
     * 带 username。
     */
    public Page<RequestView> listRequests(String status, int page, int size) {
        String st = (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
                ? null : status.trim();
        Page<IncreaseRequest> p = new Page<>(Math.max(1, page), size);
        Page<IncreaseRequest> result;
        if (st == null) {
            result = requestMapper.selectPage(p,
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IncreaseRequest>()
                            .orderByDesc("created_at"));
        } else {
            result = requestMapper.selectPage(p,
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IncreaseRequest>()
                            .eq("status", st)
                            .orderByDesc("created_at"));
        }
        Page<RequestView> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(result.getRecords().stream()
                .map(r -> new RequestView(
                        r.getId(), r.getUserId(), userQuotaMapper.selectUsername(r.getUserId()),
                        r.getGbCount(), r.getAmountCents(), r.getStatus(), r.getRemark(),
                        r.getCreatedAt(), r.getHandledAt()))
                .toList());
        return out;
    }

    /**
     * 审批通过（§4.2 POST /admin/billing/increase-requests/{id}/approve，单事务）：
     * <ol>
     *   <li>条件更新申请 pending→approved（R5，影响行数=0 → 40402）；</li>
     *   <li>插入 billing_records：applied_at=now()、expire_at=now()+30天、status=active、request_id（uk 兜底防重 → 冲突亦 40402）；</li>
     *   <li>冗余同步 users.extra_bytes += gb_count、extra_expire_at = min(existing, expire_at)；</li>
     *   <li>审计 billing_approve。</li>
     * </ol>
     */
    @Transactional
    public ApproveResult approve(Long requestId, CurrentUser admin) {
        OffsetDateTime now = OffsetDateTime.now();
        IncreaseRequest ir = requestMapper.selectById(requestId);
        if (ir == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "申请不存在");
        }
        // 1. 条件更新（R5 防并发）
        int affected = requestMapper.approveIfPending(requestId, admin.getId(), now);
        if (affected == 0) {
            throw new BizException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
        // 2. 插入记录（uk 兜底防重）
        OffsetDateTime expireAt = now.plusDays(PERIOD_DAYS);
        BillingRecord rec = new BillingRecord();
        rec.setUserId(ir.getUserId());
        rec.setRequestId(ir.getId());
        rec.setGbCount(ir.getGbCount());
        rec.setAmountCents(ir.getAmountCents());
        rec.setAppliedAt(now);
        rec.setExpireAt(expireAt);
        rec.setStatus("active");
        rec.setApprovedBy(admin.getId());
        rec.setCreatedAt(now);
        try {
            recordMapper.insert(rec);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.REQUEST_ALREADY_HANDLED, "该申请已生成计费记录");
        }
        // 3. 冗余同步
        userQuotaMapper.addExtra(ir.getUserId(), ir.getGbCount().longValue(), expireAt);
        // 4. 审计
        auditService.record(new AuditEvent(
                admin.getId(), AuditActions.BILLING_APPROVE, String.valueOf(requestId), null,
                Map.of("requestId", requestId, "gbCount", ir.getGbCount(),
                        "expireAt", expireAt.toString(), "userId", ir.getUserId())));
        log.info("[billing] 审批通过 requestId={} userId={} gb={} expire={}",
                requestId, ir.getUserId(), ir.getGbCount(), expireAt);
        return new ApproveResult(rec.getId(), expireAt);
    }

    /**
     * 审批驳回（§4.2 POST /admin/billing/increase-requests/{id}/reject）。
     */
    @Transactional
    public void reject(Long requestId, CurrentUser admin, String reason) {
        int affected = requestMapper.rejectIfPending(
                requestId, admin.getId(), OffsetDateTime.now(), reason == null ? "" : reason.trim());
        if (affected == 0) {
            throw new BizException(ErrorCode.REQUEST_ALREADY_HANDLED);
        }
        auditService.record(new AuditEvent(
                admin.getId(), AuditActions.BILLING_REJECT, String.valueOf(requestId), null,
                Map.of("requestId", requestId, "reason", reason)));
    }

    /**
     * 缴费台账分页（§4.2 GET /admin/billing/records）。
     */
    public Page<RecordView> listRecords(Long userId, String status, int page, int size) {
        return recordMapper.selectRecordPage(
                new Page<>(Math.max(1, page), size), userId,
                (status == null || status.isBlank()) ? null : status.trim());
    }

    /** 审批通过结果（§4.2）：{ recordId, expireAt } */
    public record ApproveResult(Long recordId, OffsetDateTime expireAt) {
    }
}