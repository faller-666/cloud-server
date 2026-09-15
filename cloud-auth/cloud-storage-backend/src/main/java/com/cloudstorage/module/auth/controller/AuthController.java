package com.cloudstorage.module.auth.controller;

import com.cloudstorage.common.api.ApiResponse;
import com.cloudstorage.module.auth.dto.*;
import com.cloudstorage.module.auth.service.AuthService;
import com.cloudstorage.security.CurrentUser;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, http));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication, HttpServletRequest http) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser cu) {
            authService.logout(cu, http);
        }
        return ApiResponse.ok();
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        return ApiResponse.ok(authService.profile(cu));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        authService.changePassword(cu, req);
        return ApiResponse.ok();
    }
}