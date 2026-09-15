package com.company.cloud.files.stats.controller;

import com.company.cloud.common.result.Result;
import com.company.cloud.files.stats.dto.StatsOverview;
import com.company.cloud.files.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计大盘接口（R-C10）。
 *
 * <p><b>鉴权说明（TODO）：</b>X-User-Id 请求头为开发期 Mock；
 * A 组 Security Filter 交付后改从 SecurityContext 取当前用户，签名不变。
 */
@Tag(name = "统计大盘", description = "个人存储统计概览（C 组）")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "存储概览（R-C10）：文件/目录/字节/今日新增/回收站/类型分布")
    @GetMapping("/overview")
    public Result<StatsOverview> overview(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId) {
        return Result.ok(statsService.overview(userId));
    }
}
