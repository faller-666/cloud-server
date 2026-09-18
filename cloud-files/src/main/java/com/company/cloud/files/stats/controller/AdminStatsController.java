package com.company.cloud.files.stats.controller;

import com.company.cloud.auth.security.RequireRole;
import com.company.cloud.common.result.Result;
import com.company.cloud.files.stats.dto.AdminStatsOverview;
import com.company.cloud.files.stats.dto.TopUser;
import com.company.cloud.files.stats.dto.TrafficPoint;
import com.company.cloud.files.stats.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端大盘统计（R-C09，契约前缀 /api/admin/stats，context-path 已配 /api）。
 *
 * <pre>
 *  - GET /api/admin/stats/overview          全平台总览（缓存 1 分钟）
 *  - GET /api/admin/stats/top-users         用量 Top10 用户
 *  - GET /api/admin/stats/traffic?days=7    近 N 日上传/下载流量（1~90，默认 7，缺流量日补零）
 * </pre>
 *
 * <p>流量数据源为 audit_logs 的 upload / download 记录（detail.size），
 * 待 B 组 UploadService 补审计埋点后自动出数。
 */
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@RequireRole("admin")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    public Result<AdminStatsOverview> overview() {
        return Result.ok(adminStatsService.overview());
    }

    @GetMapping("/top-users")
    public Result<List<TopUser>> topUsers() {
        return Result.ok(adminStatsService.topUsers());
    }

    @GetMapping("/traffic")
    public Result<List<TrafficPoint>> traffic(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(adminStatsService.traffic(days));
    }
}
