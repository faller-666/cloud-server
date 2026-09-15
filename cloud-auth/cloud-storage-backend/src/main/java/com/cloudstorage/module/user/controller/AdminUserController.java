package com.cloudstorage.module.user.controller;

import com.cloudstorage.common.api.ApiResponse;
import com.cloudstorage.module.user.dto.CreateUserRequest;
import com.cloudstorage.module.user.dto.UpdateUserRequest;
import com.cloudstorage.module.user.entity.User;
import com.cloudstorage.module.user.service.AdminUserService;
import com.cloudstorage.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端用户管理接口（对标任务书 §6 API 契约，admin 权限）
 *  - GET    /api/admin/users         用户列表（分页/搜索/状态筛选）
 *  - POST   /api/admin/users         创建用户
 *  - PATCH  /api/admin/users/:id     禁用/启用、配额调整、角色变更
 *  - POST   /api/admin/users/:id/reset-password  重置密码
 */
@RestController
@RequestMapping("/api/admin/users")
@RequireRole("admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<Page<User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id"));
        return ApiResponse.ok(adminUserService.list(keyword, status, pageable));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest req) {
        String initialPassword = adminUserService.create(req);
        return ApiResponse.ok(Map.of("created", true, "initialPassword", initialPassword));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateUserRequest req) {
        adminUserService.update(id, req);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@PathVariable Long id,
                                                          @RequestBody(required = false) Map<String, String> body) {
        String newPwd = adminUserService.resetPassword(id, body == null ? null : body.get("newPassword"));
        return ApiResponse.ok(Map.of("newPassword", newPwd));
    }
}