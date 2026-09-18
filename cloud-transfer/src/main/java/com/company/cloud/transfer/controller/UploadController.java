package com.company.cloud.transfer.controller;

import com.company.cloud.auth.security.CurrentUser;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.common.result.Result;
import com.company.cloud.transfer.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件传输核心接口（后端 B 组）。
 *
 * 统一返回：{ code, message, data } 由 Result 承载，异常由 cloud-app 的 @RestControllerAdvice 转错误码。
 * context-path 已统一 /api，此处路径不再重复写 /api。
 *
 * 鉴权：与 C 组保持一致，走 A 组 JWT（Authorization: Bearer &lt;token&gt;）。
 *       JwtAuthFilter 已把 CurrentUser 注入 SecurityContext，此处直接从 Authentication 取当前用户 id。
 */
@RestController
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** R-B01/R-B02：初始化上传（含秒传分支）；返回 done 或 uploading + uploadId + chunkSize。 */
    @PostMapping("/uploads/init")
    public Result<Map<String, Object>> init(@RequestBody InitRequest req, Authentication authentication) {
        UploadService.InitResult r = uploadService.init(
                currentUserId(authentication), req.name(), req.size(), req.parentId(), req.sha256());
        if (r.done()) {
            return Result.ok(Map.of("status", "done", "fileId", r.fileId()));
        }
        return Result.ok(Map.of("status", "uploading", "sessionId", r.sessionId(),
                "uploadId", r.uploadId(), "chunkSize", r.chunkSize()));
    }

    /** R-B03：上传单个分片（流式转发，不落盘）；同片重复上传幂等覆盖。 */
    @PutMapping(value = "/uploads/{id}/parts/{no}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Result<Void> part(@PathVariable("id") Long id,
                             @PathVariable("no") int no,
                             Authentication authentication,
                             HttpServletRequest http) throws Exception {
        long size = http.getContentLengthLong();
        uploadService.part(currentUserId(authentication), id, no, http.getInputStream(), size);
        return Result.ok();
    }

    /** R-B04：查询会话与已传分片（断点续传）。 */
    @GetMapping("/uploads/{id}")
    public Result<UploadService.SessionStatus> status(@PathVariable("id") Long id,
                                                      Authentication authentication) {
        return Result.ok(uploadService.status(currentUserId(authentication), id));
    }

    /** R-B05：合并分片并落元数据、实扣配额（可重试）。 */
    @PostMapping("/uploads/{id}/complete")
    public Result<UploadService.CompleteResult> complete(@PathVariable("id") Long id,
                                                         Authentication authentication) {
        return Result.ok(uploadService.complete(currentUserId(authentication), id));
    }

    /** 取消上传（幂等）。 */
    @PostMapping("/uploads/{id}/abort")
    public Result<Void> abort(@PathVariable("id") Long id, Authentication authentication) {
        uploadService.abort(currentUserId(authentication), id);
        return Result.ok();
    }

    /** R-B07：换取 5 分钟预签名下载地址（owner 校验）。 */
    @GetMapping("/files/{id}/download")
    public Result<Map<String, String>> download(@PathVariable("id") Long id,
                                                @RequestParam(required = false) Boolean inline,
                                                Authentication authentication) {
        String url = uploadService.presignDownload(currentUserId(authentication), id, Boolean.TRUE.equals(inline));
        return Result.ok(Map.of("url", url));
    }

    // ============ 当前用户 id：从 SecurityContext 取（A 组 JwtAuthFilter 注入 CurrentUser） ============
    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser cu) {
            return cu.getId();
        }
        // 未认证（正常会被 Security 入口 40103 拦截，此处兜底）
        throw new BizException(ErrorCode.TOKEN_INVALID);
    }

    public record InitRequest(String name, long size, Long parentId, String sha256) { }
}