package com.company.cloud.auth.controller;

import com.company.cloud.common.result.Result;
import com.company.cloud.auth.dto.*;
import com.company.cloud.auth.service.AuthService;
import com.company.cloud.auth.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口（对标任务书 §6 API 契约）
 *  - POST /api/auth/login
 *  - POST /api/auth/refresh
 *  - POST /api/auth/logout
 *  - GET  /api/auth/profile
 *  - POST /api/auth/change-password
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest http) {
        return Result.ok(authService.login(req, http));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication, HttpServletRequest http) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser cu) {
            authService.logout(cu, http);
        }
        return Result.ok();
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        return Result.ok(authService.profile(cu));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        authService.changePassword(cu, req);
        return Result.ok();
    }
}