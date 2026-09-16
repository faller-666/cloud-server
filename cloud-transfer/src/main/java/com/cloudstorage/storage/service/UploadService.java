package com.cloudstorage.storage.service;

import com.cloudstorage.storage.entity.FileEntity;
import com.cloudstorage.storage.entity.FileHashEntity;
import com.cloudstorage.storage.entity.UploadSessionEntity;
import com.cloudstorage.storage.exception.BizException;
import com.cloudstorage.storage.repository.FileEntityRepository;
import com.cloudstorage.storage.repository.FileHashEntityRepository;
import com.cloudstorage.storage.repository.UploadSessionEntityRepository;
import com.cloudstorage.storage.repository.UserQuotaRepository;
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
    private final FileHashEntityRepository hashRepo;
    private final UploadSessionEntityRepository sessionRepo;
    private final UserQuotaRepository quotaRepo;

    private final long maxFileSize;               // 默认 10GB
    private final Set<String> allowedExtensions;  // 扩展名白名单
    private final int presignExpirySeconds;       // 默认 300 秒
    private final int sessionTtlHours;            // 默认 24 小时

    public UploadService(MinioStorageService minio,
                         FileEntityRepository fileRepo,
                         FileHashEntityRepository hashRepo,
                         UploadSessionEntityRepository sessionRepo,
                         UserQuotaRepository quotaRepo,
                         @Value("${upload.max-file-size:10737418240}") long maxFileSize,
                         @Value("${upload.allowed-extensions:}") String allowedExt,
                         @Value("${download.presign-expiry-seconds:300}") int presignExpirySeconds,
                         @Value("${upload.session-ttl-hours:24}") int sessionTtlHours) {
        this.minio = minio;
        this.fileRepo = fileRepo;
        this.hashRepo = hashRepo;
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

        // 秒传分支：sha256 命中 file_hashes → 零字节传输，直接完成
        if (hasText(sha256)) {
            Optional<FileHashEntity> hit = hashRepo.findBySha256(sha256);
            if (hit.isPresent()) {
                FileHashEntity h = hit.get();
                // 预扣配额（条件 UPDATE 防超卖）
                if (!quotaRepo.tryReserve(userId, size)) {
                    throw new BizException(40901, "空间不足，请清理后重试");
                }
                hashRepo.incrementRefCount(sha256);
                FileEntity f = fileRepo.save(FileEntity.builder()
                        .ownerId(userId).parentId(parentId).name(name)
                        .isDir(false).sizeBytes(size)
                        .sha256(sha256).storageKey(h.getStorageKey())
                        .build());
                return InitResult.done(f.getId());
            }
        }

        // 未命中：预检配额（只读快速拒绝）
        if (!quotaRepo.checkAvailable(userId, size)) {
            throw new BizException(40901, "空间不足，请清理后重试");
        }

        // 先落会话（拿 id 定对象 key），再建 MinIO multipart，最后回填 uploadId
        UploadSessionEntity s = sessionRepo.save(UploadSessionEntity.builder()
                .userId(userId).uploadId("PENDING").targetParent(parentId)
                .name(name).sha256(sha256).sizeBytes(size)
                .chunkSize(minio.partSize()).status(STATUS_UPLOADING)
                .expiresAt(OffsetDateTime.now().plusHours(sessionTtlHours))
                .build());

        String uploadId;
        try {
            uploadId = minio.initMultipart(objectKeyOf(s));
        } catch (Exception e) {
            throw new BizException(50001, "初始化上传失败");
        }
        s.setUploadId(uploadId);
        sessionRepo.save(s);

        return InitResult.uploading(s.getId(), uploadId, minio.partSize());
    }

    // ============ R-B03：分片上传（流式，幂等覆盖） ============
    public void part(Long userId, Long sessionId, int partNo, InputStream stream, long size) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        if (!STATUS_UPLOADING.equals(s.getStatus())) {
            throw new BizException(40410, "上传会话不存在或已过期");
        }
        long expectedChunks = chunkCount(s.getSizeBytes(), s.getChunkSize());
        if (partNo < 1 || partNo > expectedChunks) {
            throw new BizException(40001, "分片序号越界");
        }
        if (size > s.getChunkSize()) {
            throw new BizException(40002, "分片大小超过上限");
        }
        try {
            // 同一 partNo 重复上传幂等覆盖（MinIO 原生行为）
            minio.uploadPart(objectKeyOf(s), s.getUploadId(), partNo, stream, size);
        } catch (Exception e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new BizException(50002, "分片上传失败: " + root.getMessage());
        }
    }

    // ============ R-B04：断点续传——查询会话与已传分片 ============
    public SessionStatus status(Long userId, Long sessionId) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        List<Integer> uploaded = new ArrayList<>();
        List<PartInfo> detail = new ArrayList<>();
        try {
            for (Part p : minio.listParts(objectKeyOf(s), s.getUploadId())) {
                uploaded.add(p.partNumber());
                detail.add(new PartInfo(p.partNumber(), p.partSize()));
            }
        } catch (Exception e) {
            throw new BizException(50003, "查询上传会话失败");
        }
        return new SessionStatus(s.getId(), s.getStatus(), s.getUploadId(), s.getChunkSize(), uploaded, detail);
    }

    // ============ R-B05/R-B06：complete（可重试 + 秒传去重 + 实扣配额） ============
    @Transactional
    public CompleteResult complete(Long userId, Long sessionId) {
        UploadSessionEntity s = ownedSession(userId, sessionId);
        if (STATUS_DONE.equals(s.getStatus())) {
            return new CompleteResult(s.getFileId(), true); // 幂等重试
        }
        if (!STATUS_UPLOADING.equals(s.getStatus())) {
            throw new BizException(40410, "上传会话不存在或已过期");
        }

        String objectKey = objectKeyOf(s);
        // 校验分片完整并合并
        String partsDebug = "";
        try {
            List<Part> parts = minio.listParts(objectKey, s.getUploadId());
            long expected = chunkCount(s.getSizeBytes(), s.getChunkSize());
            if (parts.size() != expected) {
                throw new BizException(40902, "分片未传完整，请继续上传");
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
            throw new BizException(50004, "合并分片失败: " + e.getClass().getSimpleName() + " - " + e.getMessage() + " | MinIO分片=" + partsDebug);
        }

        // 实扣配额（条件 UPDATE 原子防超卖）
        if (!quotaRepo.tryReserve(userId, s.getSizeBytes())) {
            try { minio.removeObject(objectKey); } catch (Exception ignored) { }
            throw new BizException(40901, "空间不足，请清理后重试");
        }

        // 写元数据 + 引用计数（秒传去重）
        String storageKey = objectKey;
        if (hasText(s.getSha256())) {
            FileHashEntity hash = hashRepo.findBySha256(s.getSha256()).orElse(null);
            if (hash != null) {
                // 并发下同内容已存在：复用已有对象，引用计数 +1
                storageKey = hash.getStorageKey();
                if (!storageKey.equals(objectKey)) {
                    try { minio.removeObject(objectKey); } catch (Exception ignored) { }
                }
                hashRepo.incrementRefCount(s.getSha256());
            } else {
                hashRepo.save(FileHashEntity.builder()
                        .sha256(s.getSha256()).storageKey(objectKey)
                        .sizeBytes(s.getSizeBytes()).refCount(1).build());
            }
        }

        FileEntity saved = fileRepo.save(FileEntity.builder()
                .ownerId(userId).parentId(s.getTargetParent()).name(s.getName())
                .isDir(false).sizeBytes(s.getSizeBytes())
                .sha256(s.getSha256()).storageKey(storageKey)
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
            try { minio.abortMultipart(objectKeyOf(s), s.getUploadId()); } catch (Exception ignored) { }
            s.setStatus(STATUS_ABORTED);
            sessionRepo.save(s);
        }
    }

    // ============ R-B07：下载签发 ============
    public String presignDownload(Long userId, Long fileId) {
        FileEntity f = fileRepo.findById(fileId)
                .orElseThrow(() -> new BizException(40400, "文件不存在"));
        if (!f.getOwnerId().equals(userId) || f.getDeletedAt() != null) {
            throw new BizException(40300, "无权访问");
        }
        try {
            // 3 分钟/5 分钟预签名 URL；字节流走 Nginx → MinIO，不过应用进程
            return minio.presignGet(f.getStorageKey(), presignExpirySeconds);
        } catch (Exception e) {
            throw new BizException(50005, "生成下载地址失败");
        }
    }

    // ============ R-B08：24h 未完成会话清理 ============
    @Transactional
    public int cleanupExpiredSessions() {
        List<UploadSessionEntity> expired = sessionRepo
                .findByStatusAndExpiresAtBefore(STATUS_UPLOADING, OffsetDateTime.now());
        int cleaned = 0;
        for (UploadSessionEntity s : expired) {
            try { minio.abortMultipart(objectKeyOf(s), s.getUploadId()); } catch (Exception ignored) { }
            s.setStatus(STATUS_ABORTED);
            sessionRepo.save(s);
            cleaned++;
        }
        return cleaned;
    }

    // ============ R-B10：引用计数维护（供 C 组彻底删除调用） ============
    @Transactional
    public void decrementRef(String sha256) {
        if (!hasText(sha256)) return;
        hashRepo.decrementRefCount(sha256);
        FileHashEntity h = hashRepo.findById(sha256).orElse(null);
        if (h != null && h.getRefCount() <= 0) {
            try { minio.removeObject(h.getStorageKey()); } catch (Exception ignored) { }
            hashRepo.deleteById(sha256);
        }
    }

    // ============ 私有工具 ============
    private UploadSessionEntity ownedSession(Long userId, Long sessionId) {
        UploadSessionEntity s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BizException(40410, "上传会话不存在或已过期"));
        if (!s.getUserId().equals(userId)) {
            throw new BizException(40300, "无权访问");
        }
        return s;
    }

    /** 对象 key：有哈希按内容寻址（同内容同 key，天然去重），否则按会话 id。 */
    private String objectKeyOf(UploadSessionEntity s) {
        return hasText(s.getSha256()) ? "objects/" + s.getSha256() : "uploads/session-" + s.getId();
    }

    private long chunkCount(long size, int chunkSize) {
        return size == 0 ? 0 : (size + chunkSize - 1) / chunkSize;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private void validateSize(long size) {
        if (size <= 0) throw new BizException(40003, "文件大小非法");
        if (size > maxFileSize) throw new BizException(41301, "文件过大，单次最大支持 10GB");
    }

    private void validateExtension(String name) {
        if (allowedExtensions.isEmpty()) return;
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw new BizException(41501, "该类型文件不允许上传");
        String ext = name.substring(dot + 1).toLowerCase();
        if (!allowedExtensions.contains(ext)) throw new BizException(41501, "该类型文件不允许上传");
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

    public record SessionStatus(Long sessionId, String status, String uploadId, int chunkSize, List<Integer> uploadedParts, List<PartInfo> partsDetail) { }

    public record PartInfo(int partNumber, long size) { }

    public record CompleteResult(Long fileId, boolean alreadyDone) { }
}