package com.company.cloud.auth.controller;

import com.company.cloud.auth.dto.PatchReleaseRequest;
import com.company.cloud.auth.entity.AppRelease;
import com.company.cloud.auth.security.RequireRole;
import com.company.cloud.auth.service.AppReleaseService;
import com.company.cloud.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端 App/EXE 版本发布管理接口（admin 权限，前缀 /api/admin/app/releases）。
 * <ul>
 *   <li>POST   上传并创建发布（自动版本号/SHA-256/下载链接）</li>
 *   <li>GET    发布列表（platform 必填，status 可选，versionCode 倒序分页）</li>
 *   <li>PATCH  /{id} 改灰度/强制/说明</li>
 *   <li>POST   /{id}/publish  发布（draft/disabled→published）</li>
 *   <li>POST   /{id}/disable  停用（published→disabled，紧急止血）</li>
 *   <li>DELETE /{id} 删除草稿（仅 draft）</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/app/releases")
@RequireRole("admin")
@RequiredArgsConstructor
public class AdminAppReleaseController {

    private final AppReleaseService appReleaseService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AppRelease> create(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "android") String platform,
            @RequestParam String versionName,
            @RequestParam(required = false) String updateNotes,
            @RequestParam(required = false) Boolean forceUpdate,
            @RequestParam(required = false) Integer rolloutPercent,
            @RequestParam(required = false) Boolean publishNow) {
        return Result.ok(appReleaseService.createRelease(
                file, platform, versionName, updateNotes, forceUpdate, rolloutPercent, publishNow));
    }

    @GetMapping
    public Result<Page<AppRelease>> list(
            @RequestParam String platform,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size,
                Sort.by(Sort.Direction.DESC, "versionCode"));
        return Result.ok(appReleaseService.list(platform, status, pageable));
    }

    @PatchMapping("/{id}")
    public Result<AppRelease> patch(@PathVariable Long id,
                                    @RequestBody(required = false) PatchReleaseRequest req) {
        return Result.ok(appReleaseService.patch(id,
                req == null ? new PatchReleaseRequest(null, null, null) : req));
    }

    @PostMapping("/{id}/publish")
    public Result<AppRelease> publish(@PathVariable Long id) {
        return Result.ok(appReleaseService.publish(id));
    }

    @PostMapping("/{id}/disable")
    public Result<AppRelease> disable(@PathVariable Long id) {
        return Result.ok(appReleaseService.disable(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appReleaseService.deleteDraft(id);
        return Result.ok();
    }
}