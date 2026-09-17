package com.company.cloud.files.recycle.ref;

import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.transfer.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地引用计数实现（R-C07 默认方案）：直接在本库 files 表上递减 ref_count。
 *
 * <p>引用归零（该 sha256 已无任何存活记录）时同 JVM 直调 B 组 {@link MinioStorageService}
 * 物理删除 MinIO 对象，闭环“彻底删除连磁盘数据一起清”。删除失败只告警不阻断：
 * 库记录已删，残留对象由后续一次性孤儿清理兜底。
 *
 * <p><b>演进说明：</b>单体部署同 JVM 直调即可；若未来拆微服务，改为 HTTP 调 B 组
 * decrementRef 接口（新增 HttpRefCountClient 并标记 @Primary 替换本实现）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalRefCountClient implements RefCountClient {

    private final FileNodeMapper fileNodeMapper;
    private final MinioStorageService minioStorageService;

    @Override
    public void decrement(String sha256, long fileSize) {
        if (sha256 == null || sha256.isBlank()) {
            return;
        }
        fileNodeMapper.decrementRefCount(sha256);
        Integer minRef = fileNodeMapper.selectMinAliveRefCount(sha256);
        // 调用方（forceDelete）已先级联物理删行：minRef == null 表示该 sha256 无任何存活引用 → 物理清理对象；
        // minRef != null 表示仍有其他存活文件引用同一内容（如秒传共享），对象必须保留。
        if (minRef != null) {
            return;
        }
        try {
            minioStorageService.removeObject(MinioStorageService.objectKeyOf(sha256));
            log.info("[ref] sha256={} 引用已归零，MinIO 对象已物理删除", sha256);
        } catch (Exception e) {
            // 告警不阻断：库记录已删，残留对象由后续孤儿清理兜底
            log.warn("[ref] sha256={} 引用归零但 MinIO 对象删除失败（残留由孤儿清理兜底）: {}", sha256, e.getMessage());
        }
    }
}
