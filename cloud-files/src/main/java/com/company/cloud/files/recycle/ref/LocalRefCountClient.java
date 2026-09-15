package com.company.cloud.files.recycle.ref;

import com.company.cloud.files.dir.mapper.FileNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地引用计数实现（R-C07 默认方案）：直接在本库 files 表上递减 ref_count。
 *
 * <p><b>降级方案说明：</b>B 组 HTTP 引用计数接口交付后，新增 HttpRefCountClient
 * 并标记 {@code @Primary} 替换本实现；本类保留为降级方案（B 组服务不可用时兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalRefCountClient implements RefCountClient {

    private final FileNodeMapper fileNodeMapper;

    @Override
    public void decrement(String sha256, long fileSize) {
        if (sha256 == null || sha256.isBlank()) {
            return;
        }
        fileNodeMapper.decrementRefCount(sha256);
        Integer minRef = fileNodeMapper.selectMinAliveRefCount(sha256);
        if (minRef != null && minRef == 0) {
            log.info("[ref] sha256={} 引用已归零，等待 B 组物理清理", sha256);
        }
    }
}
