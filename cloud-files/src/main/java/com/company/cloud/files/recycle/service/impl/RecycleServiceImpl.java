package com.company.cloud.files.recycle.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.common.result.PageResult;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.dir.entity.FileNode;
import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.files.recycle.dto.SubtreeFile;
import com.company.cloud.files.recycle.ref.RefCountClient;
import com.company.cloud.files.recycle.service.RecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecycleServiceImpl implements RecycleService {

    private final FileNodeMapper mapper;
    private final RefCountClient refCountClient;
    private final AuditService auditService;

    // ---------- R-C05 回收站列表 ----------

    @Override
    public PageResult<FileNodeVO> list(Long userId, int page, int size) {
        Page<FileNode> result = mapper.selectRecycleTopPage(Page.of(page, size), userId);
        List<FileNodeVO> list = result.getRecords().stream().map(FileNodeVO::from).toList();
        return PageResult.of(result.getTotal(), list);
    }

    // ---------- R-C06 还原 ----------

    @Override
    @Transactional
    public FileNodeVO restore(Long userId, Long id) {
        FileNode node = requireRecycleNode(userId, id);

        // 还原落点：原父目录仍存在、未删、属于本人且是目录 → 回原位；否则落根目录
        long targetParent = resolveRestoreParent(userId, node.getParentId());
        // 目标位置重名自动改名（与 FileNodeServiceImpl 同款私有实现）
        String targetName = resolveUniqueName(userId, targetParent, node.getName());

        // 两步处理：先级联清整棵子树的 deleted_at（不碰 parent_id/name），
        // 再单独改顶层节点的 parent_id / name
        mapper.restoreCascade(userId, id);
        node.setParentId(targetParent);
        node.setName(targetName);
        node.setDeletedAt(null);
        mapper.updateById(node);

        auditService.record(new AuditEvent(
                userId, AuditActions.RESTORE, String.valueOf(id), null,
                Map.of("name", targetName, "size", node.getSize() == null ? 0L : node.getSize())));
        return FileNodeVO.from(node);
    }

    // ---------- R-C06 彻底删除 ----------

    @Override
    @Transactional
    public void forceDelete(Long userId, Long id) {
        FileNode node = requireRecycleNode(userId, id);

        // 先收集子树文件清单（配额 + 引用计数用），再级联物理删除
        List<SubtreeFile> files = mapper.selectSubtreeFiles(userId, id);
        long bytes = files.stream().mapToLong(f -> f.getSize() == null ? 0L : f.getSize()).sum();

        mapper.deleteCascadePhysical(userId, id);
        if (bytes > 0) {
            mapper.releaseQuota(userId, bytes);
        }
        // 每个内容哈希递减引用计数（R-C07；计数归 0 的物理清理由 B 组负责）
        files.stream()
                .filter(f -> f.getSha256() != null && !f.getSha256().isBlank())
                .forEach(f -> refCountClient.decrement(f.getSha256(), f.getSize() == null ? 0L : f.getSize()));

        auditService.record(new AuditEvent(
                userId, AuditActions.DELETE_FORCE, String.valueOf(id), null,
                Map.of("name", node.getName(), "size", bytes)));
    }

    // ---------- 内部工具 ----------

    /** 取本人回收站中的节点（deleted_at 非空），否则 40314 */
    private FileNode requireRecycleNode(Long userId, Long id) {
        FileNode node = mapper.selectById(id);
        if (node == null || !userId.equals(node.getOwnerId()) || node.getDeletedAt() == null) {
            throw new BizException(ErrorCode.RECYCLE_NOT_FOUND);
        }
        return node;
    }

    /** 原父目录可回（存在、未删、本人、是目录）则回原位，否则回根目录 0 */
    private long resolveRestoreParent(Long userId, Long parentId) {
        if (parentId == null || parentId == 0) {
            return 0;
        }
        FileNode parent = mapper.selectById(parentId);
        boolean usable = parent != null
                && userId.equals(parent.getOwnerId())
                && parent.getDeletedAt() == null
                && Boolean.TRUE.equals(parent.getIsDir());
        return usable ? parentId : 0;
    }

    /**
     * 同级重名自动改名：base 已被占用则追加 (2)(3)... 后缀。
     * （与 FileNodeServiceImpl.resolveUniqueName 同款逻辑，其为 private 故在此复制一份）
     */
    private String resolveUniqueName(Long userId, long parentId, String base) {
        List<String> siblings = mapper.selectSiblingNames(userId, parentId, base);
        if (!siblings.contains(base)) {
            return base;
        }
        int i = 2;
        while (siblings.contains(base + " (" + i + ")")) {
            i++;
        }
        return base + " (" + i + ")";
    }
}
