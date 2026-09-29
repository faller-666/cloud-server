package com.company.cloud.auth.controller;

import com.company.cloud.auth.dto.AppUpdateVO;
import com.company.cloud.auth.service.AppReleaseService;
import com.company.cloud.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * App 检查更新接口（公开，无需登录，见 SecurityConfig permitAll）。
 *
 * <p>GET /api/app/check-update?platform=&amp;versionCode=&amp;userId=
 */
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppCheckController {

    private final AppReleaseService appReleaseService;

    @GetMapping("/check-update")
    public Result<AppUpdateVO> check(
            @RequestParam String platform,
            @RequestParam Integer versionCode,
            @RequestParam(required = false) Long userId) {
        return Result.ok(appReleaseService.checkUpdate(platform, versionCode, userId));
    }
}