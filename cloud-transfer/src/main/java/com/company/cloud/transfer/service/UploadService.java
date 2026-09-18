package com.company.cloud.transfer.service;

import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.transfer.entity.FileEntity;
import com.company.cloud.transfer.entity.UploadSessionEntity;
import com.company.cloud.transfer.repository.FileEntityRepository;
import com.company.cloud.transfer.repository.UploadSessionEntityRepository;
import com.company.cloud.transfer.repository.UserQuotaRepository;
import io.minio.messages.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 文件传输核心 Service——后端 B 组主干。
 *
 * 覆盖任务书 CS-DOC-03 全部 P0 需求：
 *  R-B01 init 三步协议首步 / R-B02 秒传 / R-B03+R-B04 分片上传与断点续传 /
 *  R-B05 complete / R-B06 配额防超卖 / R-B07 下载签发 / R-B08 24h 会话清理 /
 *  R-B09 大小与扩展名限制 / R-B10 引用计数维护（供 C 组）。
 *
 * 内容寻址：对象 key = objects/<sha256>，同内容全站同 key 天然去重；
 * 引用计数归 files 表 ref_count（对齐 spec），彻底删除时按「是否仍有存活文件记录」判断是否物理删对象。
 *
 * 说明：complete 的「MinIO 合并」与「DB 落库」跨系统无法用 DB 事务原子化，
 * 靠「幂等重试 + 脏对象对账清理」兜底，与任务书验收「重启可续、无脏数据」一致。
 */
@Service
public class UploadService {

    public static final String STATUS_UPLOADING = "uploading";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_ABORTED = "aborted";

    private final MinioStorageService minio;
    private final FileEntityRepository fileRepo;
    private final UploadSessionEntityRepository sessionRepo;
    private final UserQuotaRepository quotaRepo;

    private final long maxFileSize;               // 默认 10GB
    private final Set<String> allowedExtensions;  // 扩展名白名单
    private final int presignExpirySeconds;       // 默认 300 秒
    private final int sessionTtlHours;            // 默认 24 小时

    public UploadService(MinioStorageService minio,
                         FileEntityRepository fileRepo,
                         UploadSessionEntityRepository sessionRepo,
                         UserQuotaRepository quotaRepo,
                         @Value("${upload.max-file-size:10737418240}") long maxFileSize,
                         @Value("${upload.allowed-extensions:}") String allowedExt,
                         @Value("${download.presign-expiry-seconds:300}") int presignExpirySeconds,
                         @Value("${upload.session-ttl-hours:24}") int sessionTtlHours) {
        this.minio = minio;
        this.fileRepo = fileRepo;
        this.sessionRepo = sessionRepo;
        this.quotaRepo = quotaRepo;
        this.maxFileSize = maxFileSize;
        this.allowedExtensions = parseExtensions(allowedExt);
        this.presignExpirySeconds = presignExpirySeconds;
        this.sessionTtlHours = sessionTtlHours;
    }

    // ============ R-B01/R-B02/R-B06：init（含秒传分支） ============
    @Transactional
    public InitResult init(Long userId, String name, long size, Long parentId, String sha256) {
        validateSize(size);
        validateExtension(name);
        validateSha256(sha256);

        long targetParent = parentId == null ? 0L : parentId;

        // 秒传分支：sha256 命中存活 files 记录 → 零字节传输，直接复用对象
        Optional<FileEntity> hit = fileRepo.findFirstBySha256AndDeletedAtIsNull(sha256);
        if (hit.isPresent()) {
            // 预扣配额（条件 UPDATE 防超卖）
            if (!quotaRepo.tryReserve(userId, size)) {
                throw new BizException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
            }
            FileEntity f = fileRepo.save(FileEntity.builder()
                    .ownerId(userId).parentId(targetParent)
                    .name(resolveUniqueName(userId, targetParent, name))
                    .isDir(false).size(size).sha256(sha256).refCount(1)
                    .build());
            return InitResult.done(f.getId());
        }

        // 未命中：预检配额（只读快速拒绝）
        if (!quotaRepo.checkAvailable(userId, size)) {
            throw new BizException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }

        // 先落会话（拿 id），再建 MinIO multipart，最后回填 uploadId
        UploadSessionEntity s = sessionRepo.save(UploadSessionEntity.builder()
                .userId(userId).uploadId("PENDING").targetParent(targetParent)
                .name(name).sha256(sha256).sizeBytes(size)
                .chunkSize(minio.partSize()).status(STATUS_UPLOADING)
                .expiresAt(OffsetDateTime.now().plusHours(sessionTtlHours))
                .build());

        String uploadId;
        try {
            uploadId = minio.initMultipart(objectKeyOf(sha256));
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "初始化上传失败");
        }
        s.setUploadId(uploadId);
        sessionRepo.save(s);

        return InitResult.uploading(s.getId(), uploadId, minio.partSize());
    }

    // ============ R-B03：分片上传（流式，幂等覆盖） ============
    public void part(Long userId, Long sessionId, int partNo, InputStream stream, long size) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        if (!STATUS_UPLOADING.equals(s.getStatus())) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        long expectedChunks = chunkCount(s.getSizeBytes(), s.getChunkSize());
        if (partNo < 1 || partNo > expectedChunks) {
            throw new BizException(ErrorCode.PART_INDEX_OUT_OF_RANGE);
        }
        if (size > s.getChunkSize()) {
            throw new BizException(ErrorCode.PART_SIZE_EXCEEDED);
        }
        try {
            // 同一 partNo 重复上传幂等覆盖（MinIO 原生行为）
            minio.uploadPart(objectKeyOf(s.getSha256()), s.getUploadId(), partNo, stream, size);
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new BizException(ErrorCode.SYSTEM_ERROR, "分片上传失败: " + root.getMessage());
        }
    }

    // ============ R-B04：断点续传——查询会话与已传分片 ============
    public SessionStatus status(Long userId, Long sessionId) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        List<Integer> uploaded = new ArrayList<>();
        List<PartInfo> detail = new ArrayList<>();
        try {
            for (Part p : minio.listParts(objectKeyOf(s.getSha256()), s.getUploadId())) {
                uploaded.add(p.partNumber());
                detail.add(new PartInfo(p.partNumber(), p.partSize()));
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "查询上传会话失败");
        }
        return new SessionStatus(s.getId(), s.getStatus(), s.getUploadId(), s.getChunkSize(), uploaded, detail);
    }

    // ============ R-B05/R-B06：complete（可重试 + 实扣配额） ============
    @Transactional
    public CompleteResult complete(Long userId, Long sessionId) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        if (STATUS_DONE.equals(s.getStatus())) {
            return new CompleteResult(s.getFileId(), true); // 幂等重试
        }
        if (!STATUS_UPLOADING.equals(s.getStatus())) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }

        String objectKey = objectKeyOf(s.getSha256());
        // 校验分片完整并合并
        String partsDebug = "";
        try {
            List<Part> parts = minio.listParts(objectKey, s.getUploadId());
            long expected = chunkCount(s.getSizeBytes(), s.getChunkSize());
            if (parts.size() != expected) {
                throw new BizException(ErrorCode.PARTS_INCOMPLETE);
            }
            StringBuilder sb = new StringBuilder();
            List<Part> simple = new ArrayList<>();
            for (Part p : parts) {
                sb.append("[#").append(p.partNumber()).append(" size=").append(p.partSize()).append("]");
                simple.add(new Part(p.partNumber(), "\"" + p.etag() + "\""));
            }
            partsDebug = sb.toString();
            minio.completeMultipart(objectKey, s.getUploadId(), simple.toArray(new Part[0]));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "合并分片失败: " + e.getClass().getSimpleName() + " - " + e.getMessage() + " | MinIO分片=" + partsDebug);
        }

        // 实扣配额（条件 UPDATE 原子防超卖）
        if (!quotaRepo.tryReserve(userId, s.getSizeBytes())) {
            try { minio.removeObject(objectKey); } catch (Exception ignored) { }
            throw new BizException(ErrorCode.STORAGE_QUOTA_EXCEEDED);
        }

        // 写元数据（对象为内容寻址 objects/<sha256>，引用计数归 files 表 ref_count）
        // 同级重名自动改名，避免撞 uk_files_sibling_name 唯一索引（修复上传同名文件 500 系统繁忙）
        long targetParent = s.getTargetParent() == null ? 0L : s.getTargetParent();
        FileEntity saved = fileRepo.save(FileEntity.builder()
                .ownerId(userId)
                .parentId(targetParent)
                .name(resolveUniqueName(userId, targetParent, s.getName()))
                .isDir(false).size(s.getSizeBytes())
                .sha256(s.getSha256()).refCount(1)
                .build());

        s.setFileId(saved.getId());
        s.setStatus(STATUS_DONE);
        sessionRepo.save(s);
        return new CompleteResult(saved.getId(), false);
    }

    // ============ abort ============
    @Transactional
    public void abort(Long userId, Long sessionId) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        if (STATUS_UPLOADING.equals(s.getStatus())) {
            try { minio.abortMultipart(objectKeyOf(s.getSha256()), s.getUploadId()); } catch (Exception ignored) { }
            s.setStatus(STATUS_ABORTED);
            sessionRepo.save(s);
        }
    }

    // ============ R-B07：下载签发 ============
    public String presignDownload(Long userId, Long fileId, boolean inline) {
        FileEntity f = fileRepo.findById(fileId)
                .orElseThrow(() -> new BizException(ErrorCode.FILE_NOT_FOUND));
        if (!f.getOwnerId().equals(userId) || f.getDeletedAt() != null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        try {
            // 5 分钟预签名 URL；字节流走 Nginx → MinIO，不过应用进程
            // inline=true 供预览（不强制 attachment），否则供下载（带文件名 attachment）
            return minio.presignGet(objectKeyOf(f.getSha256()), f.getName(), presignExpirySeconds, inline);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "生成下载地址失败");
        }
    }

    // ============ R-B08：24h 未完成会话清理 ============
    @Transactional
    public int cleanupExpiredSessions() {
        List<UploadSessionEntity> expired = sessionRepo
                .findByStatusAndExpiresAtBefore(STATUS_UPLOADING, OffsetDateTime.now());
        int cleaned = 0;
        for (UploadSessionEntity s : expired) {
            try { minio.abortMultipart(objectKeyOf(s.getSha256()), s.getUploadId()); } catch (Exception ignored) { }
            s.setStatus(STATUS_ABORTED);
            sessionRepo.save(s);
            cleaned++;
        }
        return cleaned;
    }

    // ============ R-B10：引用计数维护（供 C 组彻底删除调用） ============
    @Transactional
    public void decrementRef(String sha256, long fileSize) {
        if (!hasText(sha256)) {
            return;
        }
        // 仍存在存活文件记录 → 内容对象仍有引用，不删；否则物理删除 MinIO 对象
        long alive = fileRepo.countBySha256AndDeletedAtIsNull(sha256);
        if (alive <= 0) {
            try { minio.removeObject(objectKeyOf(sha256)); } catch (Exception ignored) { }
        }
    }

    // ============ 私有工具 ============
    /** 同级重名自动改名：base 被占用则追加 (2)(3)... 后缀（对齐 C 组 resolveUniqueName + uk_files_sibling_name）。 */
    private String resolveUniqueName(Long userId, long parentId, String base) {
        if (!fileRepo.existsByOwnerIdAndParentIdAndNameAndDeletedAtIsNull(userId, parentId, base)) {
            return base;
        }
        int i = 2;
        while (fileRepo.existsByOwnerIdAndParentIdAndNameAndDeletedAtIsNull(userId, parentId, base + " (" + i + ")")) {
            i++;
        }
        return base + " (" + i + ")";
    }

    private UploadSessionEntity ownedSession(Long userId, Long sessionId) {
        UploadSessionEntity s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));
        if (!s.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return s;
    }

    /** 对象 key：内容寻址 objects/<sha256>（同内容同 key，天然去重）。 */
    private String objectKeyOf(String sha256) {
        return "objects/" + sha256;
    }

    private long chunkCount(long size, int chunkSize) {
        return size == 0 ? 0 : (size + chunkSize - 1) / chunkSize;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private void validateSize(long size) {
        if (size <= 0) throw new BizException(ErrorCode.FILE_SIZE_INVALID);
        if (size > maxFileSize) throw new BizException(ErrorCode.FILE_TOO_LARGE);
    }

    private void validateExtension(String name) {
        if (allowedExtensions.isEmpty()) return;
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw new BizException(ErrorCode.EXTENSION_NOT_ALLOWED);
        String ext = name.substring(dot + 1).toLowerCase();
        if (!allowedExtensions.contains(ext)) throw new BizException(ErrorCode.EXTENSION_NOT_ALLOWED);
    }

    private void validateSha256(String sha256) {
        if (!hasText(sha256)) {
            throw new BizException(ErrorCode.SHA256_REQUIRED);
        }
    }

    private Set<String> parseExtensions(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> set = new HashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    // ============ 结果 DTO（内联，简化） ============
    public record InitResult(boolean done, Long fileId, Long sessionId, String uploadId, int chunkSize) {
        public static InitResult done(Long fileId) {
            return new InitResult(true, fileId, null, null, 0);
        }
        public static InitResult uploading(Long sessionId, String uploadId, int chunkSize) {
            return new InitResult(false, null, sessionId, uploadId, chunkSize);
        }
    }

    public record SessionStatus(Long sessionId, String status, String uploadId, int chunkSize,
                                List<Integer> uploadedParts, List<PartInfo> partsDetail) { }

    public record PartInfo(int partNumber, long size) { }

    public record CompleteResult(Long fileId, boolean alreadyDone) { }
}