package com.company.cloud.auth.service;

import com.company.cloud.auth.dto.AppUpdateInfo;
import com.company.cloud.auth.dto.AppUpdateVO;
import com.company.cloud.auth.dto.PatchReleaseRequest;
import com.company.cloud.auth.entity.AppRelease;
import com.company.cloud.auth.repository.AppReleaseRepository;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;

/**
 * App(Android/iOS)/EXE(Windows) 推送更新服务（依据《云盘 App 推送更新方案（定稿）》§5）。
 * <ul>
 *   <li>checkUpdate：App 检查更新，实现版本比较、灰度命中(userId%100)、强制判断</li>
 *   <li>createRelease：管理端上传落盘 + 流式 SHA-256 + versionCode 自动 +1 + 发布</li>
 *   <li>list / patch / publish / disable / deleteDraft：管理端发布管理</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppReleaseService {

    private final AppReleaseRepository appReleaseRepository;

    @Value("${app.update.download-dir}")
    private String downloadDir;

    @Value("${app.update.base-url}")
    private String baseUrl;

    private static final Set<String> PLATFORMS = Set.of("android", "ios", "windows");

    /**
     * App 检查更新（公开接口）。
     * 灰度名单固定：userId % 100 &lt; rollout_percent 才命中，同一用户结果稳定。
     */
    public AppUpdateVO checkUpdate(String platform, Integer versionCode, Long userId) {
        if (platform == null || !PLATFORMS.contains(platform)) {
            throw new BizException(ErrorCode.APP_PLATFORM_INVALID);
        }
        if (versionCode == null || versionCode < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "versionCode 非法");
        }
        AppRelease latest = appReleaseRepository
                .findFirstByPlatformAndStatusAndVersionCodeGreaterThanOrderByVersionCodeDesc(
                        platform, "published", versionCode)
                .orElse(null);
        if (latest == null) {
            return new AppUpdateVO(false, null);
        }
        int pct = latest.getRolloutPercent() == null ? 100 : latest.getRolloutPercent();
        boolean hit = userId == null ? pct >= 100 : (userId % 100) < pct;
        if (!hit) {
            return new AppUpdateVO(false, null);
        }
        boolean force = Boolean.TRUE.equals(latest.getForceUpdate())
                || (latest.getMinForceCode() != null && latest.getMinForceCode() > 0
                    && versionCode < latest.getMinForceCode());
        AppUpdateInfo info = new AppUpdateInfo(
                latest.getVersionCode(),
                latest.getVersionName(),
                latest.getApkUrl(),
                latest.getFileSize() == null ? 0L : latest.getFileSize(),
                latest.getFileHash(),
                force,
                latest.getUpdateNotes());
        return new AppUpdateVO(true, info);
    }

    /**
     * 管理端上传并创建发布：落盘 downloads/ + 流式 SHA-256 + versionCode 自动 +1。
     * publishNow=true → published 并写 published_at，否则存 draft。
     * DB 保存失败（如并发撞 versionCode）时回滚并清理已落盘文件，转友好错误。
     */
    @Transactional
    public AppRelease createRelease(MultipartFile file, String platform, String versionName,
                                    String updateNotes, Boolean forceUpdate, Integer rolloutPercent,
                                    Boolean publishNow, Integer versionCode) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.APP_UPLOAD_REQUIRED);
        }
        if (platform == null || !PLATFORMS.contains(platform)) {
            throw new BizException(ErrorCode.APP_PLATFORM_INVALID);
        }
        if (versionName == null || versionName.isBlank()) {
            throw new BizException(ErrorCode.APP_VERSION_NAME_REQUIRED);
        }
        int percent = rolloutPercent == null ? 5 : rolloutPercent;
        if (percent < 0 || percent > 100) {
            throw new BizException(ErrorCode.APP_ROLLOUT_INVALID);
        }
        if (versionCode != null && versionCode < 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "versionCode 需为正整数或留空由后端自动分配");
        }

        String ext = extensionOf(platform, file.getOriginalFilename());
        String fileName = platform + "-v" + sanitize(versionName) + "-"
                + System.currentTimeMillis() + "." + ext;

        long size;
        String hash;
        try {
            Path dir = Paths.get(downloadDir);
            Files.createDirectories(dir);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            size = 0L;
            try (InputStream in = file.getInputStream();
                 OutputStream os = Files.newOutputStream(dir.resolve(fileName));
                 DigestOutputStream dos = new DigestOutputStream(os, md)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    dos.write(buf, 0, n);
                    size += n;
                }
            }
            hash = HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            log.error("[app-release] 安装包落盘/哈希失败 platform={} name={}", platform, fileName, e);
            throw new BizException(ErrorCode.APP_STORAGE_FAILED);
        }

        boolean publish = Boolean.TRUE.equals(publishNow);
        String downloadUrl = baseUrl + "/downloads/" + fileName;
        AppRelease saved = null;
        // 版本号分配：前端传了 versionCode 则用指定值（撞号则直接报错，绝不自动改号）；
        // 没传则自动 max+1，并发撞号（唯一约束）时取下一号自动重试，最多 5 次，普通传包不感知冲突
        boolean explicitCode = versionCode != null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            int nextCode = explicitCode ? versionCode
                    : appReleaseRepository.maxVersionCodeByPlatform(platform) + 1;
            AppRelease rel = AppRelease.builder()
                    .platform(platform)
                    .versionCode(nextCode)
                    .versionName(versionName)
                    .apkUrl(downloadUrl)
                    .fileSize(size)
                    .fileHash(hash)
                    .updateNotes(updateNotes)
                    .forceUpdate(Boolean.TRUE.equals(forceUpdate))
                    .minForceCode(0)
                    .rolloutPercent(percent)
                    .status(publish ? "published" : "draft")
                    .publishedAt(publish ? OffsetDateTime.now() : null)
                    .build();
            try {
                saved = appReleaseRepository.save(rel);
                break;
            } catch (DataIntegrityViolationException e) {
                // 只有「版本号唯一约束」冲突才是撞号，才可取下一号重试；
                // 其它约束冲突（如字段为 null / 非法值）直接暴露真实异常，绝不误判成撞号
                if (!isPlatformVersionConflict(e)) {
                    try {
                        Files.deleteIfExists(Paths.get(downloadDir).resolve(fileName));
                    } catch (Exception ex) {
                        log.warn("[app-release] 清理冲突落盘文件失败 name={}", fileName, ex);
                    }
                    log.error("[app-release] 保存发布记录约束冲突（非版本撞号），清理文件 name={}", fileName, e);
                    throw e;
                }
                if (explicitCode) {
                    // 前端明确指定的版本号已被占用：不自动改号，清理落盘文件后报清晰错误
                    try {
                        Files.deleteIfExists(Paths.get(downloadDir).resolve(fileName));
                    } catch (Exception ex) {
                        log.warn("[app-release] 清理冲突落盘文件失败 name={}", fileName, ex);
                    }
                    log.warn("[app-release] 指定版本号已存在 platform={} versionCode={}，清理文件 name={}",
                            platform, versionCode, fileName);
                    throw new BizException(ErrorCode.APP_VERSION_ALREADY_EXISTS);
                }
                if (attempt >= 5) {
                    // 持续撞号：清理刚落盘的文件，转友好错误
                    try {
                        Files.deleteIfExists(Paths.get(downloadDir).resolve(fileName));
                    } catch (Exception ex) {
                        log.warn("[app-release] 清理冲突落盘文件失败 name={}", fileName, ex);
                    }
                    log.warn("[app-release] 版本号持续撞号（已自动重试5次仍失败），清理文件 name={}", fileName);
                    throw new BizException(ErrorCode.APP_VERSION_CONFLICT);
                }
                log.warn("[app-release] versionCode={} 撞号，取下一号重试 attempt={}",
                        rel.getVersionCode(), attempt);
            }
        }
        log.info("[app-release] 创建发布 id={} platform={} versionCode={} status={} size={}",
                saved.getId(), platform, saved.getVersionCode(), saved.getStatus(), size);
        return saved;
    }

    /** 管理端发布列表：platform 必填，status 可选，versionCode 倒序 */
    public Page<AppRelease> list(String platform, String status, Pageable pageable) {
        boolean hasPlatform = platform != null && !platform.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasPlatform && hasStatus) {
            return appReleaseRepository.findByPlatformAndStatusOrderByVersionCodeDesc(platform.trim(), status.trim(), pageable);
        }
        if (hasPlatform) {
            return appReleaseRepository.findByPlatformOrderByVersionCodeDesc(platform.trim(), pageable);
        }
        if (hasStatus) {
            return appReleaseRepository.findByStatusOrderByVersionCodeDesc(status.trim(), pageable);
        }
        return appReleaseRepository.findAll(pageable);
    }

    /** 修改发布：放量 / 改强制 / 改说明 / 改全局最低强制版本号（不允许改 versionCode 与替换文件） */
    @Transactional
    public AppRelease patch(Long id, PatchReleaseRequest req) {
        AppRelease rel = appReleaseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.APP_RELEASE_NOT_FOUND));
        if (req.rolloutPercent() != null) {
            if (req.rolloutPercent() < 0 || req.rolloutPercent() > 100) {
                throw new BizException(ErrorCode.APP_ROLLOUT_INVALID);
            }
            rel.setRolloutPercent(req.rolloutPercent());
        }
        if (req.forceUpdate() != null) {
            rel.setForceUpdate(req.forceUpdate());
        }
        if (req.updateNotes() != null) {
            rel.setUpdateNotes(req.updateNotes());
        }
        if (req.minForceCode() != null) {
            if (req.minForceCode() < 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "minForceCode 需 >= 0");
            }
            rel.setMinForceCode(req.minForceCode());
        }
        return appReleaseRepository.save(rel);
    }

    /** 发布（draft/disabled → published），写 published_at */
    @Transactional
    public AppRelease publish(Long id) {
        AppRelease rel = appReleaseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.APP_RELEASE_NOT_FOUND));
        if ("published".equals(rel.getStatus())) {
            throw new BizException(ErrorCode.APP_RELEASE_STATE_INVALID, "该版本已发布");
        }
        rel.setStatus("published");
        rel.setPublishedAt(OffsetDateTime.now());
        return appReleaseRepository.save(rel);
    }

    /** 停用（published → disabled，紧急止血）；检查更新接口立即不再下发 */
    @Transactional
    public AppRelease disable(Long id) {
        AppRelease rel = appReleaseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.APP_RELEASE_NOT_FOUND));
        if (!"published".equals(rel.getStatus())) {
            throw new BizException(ErrorCode.APP_RELEASE_STATE_INVALID, "仅已发布版本可停用");
        }
        rel.setStatus("disabled");
        return appReleaseRepository.save(rel);
    }

    /** 删除草稿：仅 draft；删除磁盘文件 */
    @Transactional
    public void deleteDraft(Long id) {
        AppRelease rel = appReleaseRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.APP_RELEASE_NOT_FOUND));
        if (!"draft".equals(rel.getStatus())) {
            throw new BizException(ErrorCode.APP_RELEASE_STATE_INVALID, "仅草稿可删除（已发布/已停用请用停用保留审计）");
        }
        try {
            if (rel.getApkUrl() != null && !rel.getApkUrl().isBlank()) {
                String name = rel.getApkUrl().substring(rel.getApkUrl().lastIndexOf('/') + 1);
                Files.deleteIfExists(Paths.get(downloadDir).resolve(name));
            }
        } catch (Exception e) {
            log.warn("[app-release] 删除草稿磁盘文件失败 id={}", id, e);
        }
        appReleaseRepository.delete(rel);
    }

    private String extensionOf(String platform, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if ("windows".equals(platform)) {
            if (lower.endsWith(".exe")) return "exe";
            throw new BizException(ErrorCode.APP_FILE_TYPE_INVALID, "Windows 安装包需为 .exe");
        }
        if (lower.endsWith(".apk")) return "apk";
        if (lower.endsWith(".ipa")) return "ipa";
        throw new BizException(ErrorCode.APP_FILE_TYPE_INVALID);
    }

    private String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    /** 判断约束冲突是否由「版本号唯一约束 uk_app_release_platform_version」引起，即真正的撞号 */
    private boolean isPlatformVersionConflict(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("uk_app_release_platform_version")) {
            return true;
        }
        // 某些驱动把约束名放在 cause 链的 message 里，兜底查一层
        Throwable t = e.getCause();
        int depth = 0;
        while (t != null && depth < 3) {
            String m = t.getMessage();
            if (m != null && m.contains("uk_app_release_platform_version")) {
                return true;
            }
            t = t.getCause();
            depth++;
        }
        return false;
    }
}