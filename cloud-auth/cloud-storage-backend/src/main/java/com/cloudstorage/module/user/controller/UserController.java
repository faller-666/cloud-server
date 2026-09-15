package com.cloudstorage.module.user.controller;

import com.cloudstorage.common.api.ApiResponse;
import com.cloudstorage.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 普通用户接口（当前用户）
 * 保留当前登录用户维度的入口扩展位；资料查询已在 AuthController /profile 提供。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<Page<?>> me(org.springframework.security.core.Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        // 骨架占位：返回当前用户基础信息集合
        return ApiResponse.ok(new PageImpl<>(List.of(cu), PageRequest.of(0, 1), 1));
    }
}