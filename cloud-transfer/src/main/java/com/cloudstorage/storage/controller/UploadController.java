package com.cloudstorage.storage.controller;

import com.cloudstorage.storage.exception.BizException;
import com.cloudstorage.storage.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件传输核心接口（后端 B 组）。
 *
 * 统一返回：此处直接返回 data 对象，{ code, message, data } 包装由 common 的
 * ResultAdvice（ResponseBodyAdvice）统一完成，异常由 @RestControllerAdvice 转错误码。
 *
 * 鉴权：当前用户 id 依赖 A 组 AuthGuard 从 JWT 注入；联调前先用 X-User-Id 头占位，
 *       接入 A 组后改为从 SecurityContext 取（见 currentUserId 注释）。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** R-B01/R-B02：初始化上传（含秒传分支）；返回 done 或 uploading + uploadId + chunkSize。 */
    @PostMapping("/uploads/init")
    public Map<String, Object> init(@RequestBody InitRequest req, HttpServletRequest http) {
        UploadService.InitResult r = uploadService.init(
                currentUserId(http), req.name(), req.size(), req.parentId(), req.sha256());
        if (r.done()) {
            return Map.of("status", "done", "fileId", r.fileId());
        }
        return Map.of("status", "uploading", "sessionId", r.sessionId(),
                "uploadId", r.uploadId(), "chunkSize", r.chunkSize());
    }

    /** R-B03：上传单个分片（流式转发，不落盘）；同片重复上传幂等覆盖。 */
    @PutMapping(value = "/uploads/{id}/parts/{no}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void part(@PathVariable("id") Long id,
                     @PathVariable("no") int no,
                     HttpServletRequest http) throws Exception {
        long size = http.getContentLengthLong();
        uploadService.part(currentUserId(http), id, no, http.getInputStream(), size);
    }

    /** R-B04：查询会话与已传分片（断点续传）。 */
    @GetMapping("/uploads/{id}")
    public UploadService.SessionStatus status(@PathVariable("id") Long id,
                                              HttpServletRequest http) {
        return uploadService.status(currentUserId(http), id);
    }

    /** R-B05：合并分片并落元数据、实扣配额（可重试）。 */
    @PostMapping("/uploads/{id}/complete")
    public UploadService.CompleteResult complete(@PathVariable("id") Long id,
                                                 HttpServletRequest http) {
        return uploadService.complete(currentUserId(http), id);
    }

    /** 取消上传（幂等）。 */
    @PostMapping("/uploads/{id}/abort")
    public void abort(@PathVariable("id") Long id, HttpServletRequest http) {
        uploadService.abort(currentUserId(http), id);
    }

    /** R-B07：换取 5 分钟预签名下载地址（owner 校验）。 */
    @GetMapping("/files/{id}/download")
    public Map<String, String> download(@PathVariable("id") Long id,
                                        HttpServletRequest http) {
        String url = uploadService.presignDownload(currentUserId(http), id);
        return Map.of("url", url);
    }

    // ============ 占位：当前用户 id ============
    private Long currentUserId(HttpServletRequest req) {
        // TODO 接入 A 组鉴权后替换为 common 的 SecurityUtil.currentUserId()（从 SecurityContext 取）
        String uid = req.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) {
            throw new BizException(40103, "登录已过期，请重新登录");
        }
        return Long.parseLong(uid);
    }

    public record InitRequest(String name, long size, Long parentId, String sha256) { }
}