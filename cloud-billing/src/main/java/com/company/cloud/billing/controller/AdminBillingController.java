package com.company.cloud.billing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.cloud.auth.security.CurrentUser;
import com.company.cloud.auth.security.RequireRole;
import com.company.cloud.billing.dto.ConfigView;
import com.company.cloud.billing.dto.RecordView;
import com.company.cloud.billing.dto.RequestView;
import com.company.cloud.billing.dto.UpdateConfigRequest;
import com.company.cloud.billing.service.AdminBillingService;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端计费接口（§4.2，admin 权限）：
 *  - GET  /admin/billing/config                   查看计费配置
 *  - PATCH /admin/billing/config                  修改计费配置
 *  - GET  /admin/billing/increase-requests        增额申请列表
 *  - POST /admin/billing/increase-requests/{id}/approve  审批通过
 *  - POST /admin/billing/increase-requests/{id}/reject   审批驳回
 *  - GET  /admin/billing/records                  缴费台账
 */
@RestController
@RequestMapping("/admin/billing")
@RequireRole("admin")
@RequiredArgsConstructor
public class AdminBillingController {

    private final AdminBillingService adminBillingService;

    @GetMapping("/config")
    public Result<ConfigView> config() {
        return Result.ok(adminBillingService.getConfig());
    }

    @PatchMapping("/config")
    public Result<ConfigView> updateConfig(
            Authentication authentication,
            @Valid @RequestBody UpdateConfigRequest req) {
        return Result.ok(adminBillingService.updateConfig(currentUser(authentication), req));
    }

    @GetMapping("/increase-requests")
    public Result<Page<RequestView>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminBillingService.listRequests(status, page, size));
    }

    @PostMapping("/increase-requests/{id}/approve")
    public Result<AdminBillingService.ApproveResult> approve(
            @PathVariable Long id, Authentication authentication) {
        return Result.ok(adminBillingService.approve(id, currentUser(authentication)));
    }

    @PostMapping("/increase-requests/{id}/reject")
    public Result<Void> reject(
            @PathVariable Long id, Authentication authentication,
            @RequestBody(required = false) Map<String, String> body) {
        adminBillingService.reject(id, currentUser(authentication),
                body == null ? null : body.get("reason"));
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<Page<RecordView>> records(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminBillingService.listRecords(userId, status, page, size));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        throw new BizException(ErrorCode.TOKEN_INVALID);
    }
}