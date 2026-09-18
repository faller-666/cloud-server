package com.company.cloud.files.dir.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import com.company.cloud.common.result.BizException;
import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.files.dir.dto.BatchMoveRequest;
import com.company.cloud.files.dir.dto.DirListResponse;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.dir.dto.MkdirRequest;
import com.company.cloud.files.dir.dto.UpdateNodeRequest;
import com.company.cloud.files.dir.entity.FileNode;
import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.files.dir.service.FileNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileNodeServiceImpl implements FileNodeService {

    /** 排序字段白名单（契约：禁止前端字段直接透传 SQL） */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "name",
            "size", "size",
            "createdAt", "created_at",
            "updatedAt", "updated_at"
    );

    private final FileNodeMapper mapper;
    private final AuditService auditService;

    // ---------- R-C01 列目录（+ keyword 全局搜索） ----------

    @Override
    public DirListResponse list(Long userId, long parentId, int page, int size, String sort, String keyword) {
        boolean searching = keyword != null && !keyword.isBlank();
        if (!searching && parentId > 0) {
            requireOwnedDir(userId, parentId);
        }

        QueryWrapper<FileNode> qw = new QueryWrapper<>();
        qw.eq("owner_id", userId)
          .isNull("deleted_at");
        if (searching) {
            // 全局搜索：忽略 parent，全空间按名称模糊匹配；%/_ 转义防通配符注入
            qw.like("name", escapeLike(keyword.trim()));
        } else {
            qw.eq("parent_id", parentId);
        }
        applySort(qw, sort);

        Page<FileNode> result = mapper.selectPage(Page.of(page, size), qw);
        List<FileNodeVO> list = result.getRecords().stream().map(FileNodeVO::from).toList();

        return new DirListResponse(
                result.getTotal(),
                list,
                (!searching && parentId > 0) ? mapper.selectBreadcrumb(userId, parentId) : List.of()
        );
    }

    /** PostgreSQL LIKE 默认以反斜杠为转义符，逐字符转义即可 */
    private static String escapeLike(String kw) {
        return kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------- 目录树（前端树状导航 / 拖拽移动） ----------

    @Override
    public List<FileNodeVO> tree(Long userId) {
        QueryWrapper<FileNode> qw = new QueryWrapper<>();
        qw.eq("owner_id", userId)
          .eq("is_dir", true)
          .isNull("deleted_at")
          .orderByAsc("name");
        return mapper.selectList(qw).stream().map(FileNodeVO::from).toList();
    }

    private void applySort(QueryWrapper<FileNode> qw, String sort) {
        if (sort == null || sort.isBlank()) {
            // 默认：目录在前，名称升序
            qw.orderByDesc("is_dir").orderByAsc("name");
            return;
        }
        String[] parts = sort.split(",");
        String column = SORT_COLUMNS.get(parts[0].trim());
        if (column == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的排序字段: " + parts[0]);
        }
        boolean asc = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
        qw.orderBy(true, asc, column);
    }

    // ---------- R-C02 新建文件夹 ----------

    @Override
    public FileNodeVO mkdir(Long userId, MkdirRequest request) {
        long parentId = request.parentIdOrRoot();
        if (parentId > 0) {
            requireOwnedDir(userId, parentId);
        }

        FileNode node = new FileNode();
        node.setOwnerId(userId);
        node.setParentId(parentId);
        node.setName(resolveUniqueName(userId, parentId, request.name().trim()));
        node.setIsDir(true);
        node.setSize(0L);
        node.setRefCount(0);
        mapper.insert(node);
        // 审计：ip 暂传 null（TODO：A 组 Security 交付后从请求上下文补齐）
        auditService.record(new AuditEvent(
                userId, AuditActions.MKDIR, String.valueOf(node.getId()), null,
                Map.of("name", node.getName(), "id", node.getId(), "parentId", parentId)));
        return FileNodeVO.from(node);
    }

    // ---------- R-C03 重命名 / 移动 ----------

    @Override
    @Transactional
    public FileNodeVO update(Long userId, Long id, UpdateNodeRequest request) {
        if (request.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "name 与 parentId 至少传一个");
        }
        FileNode node = requireOwnedNode(userId, id);

        String newName = (request.name() == null || request.name().isBlank())
                ? node.getName()
                : request.name().trim();
        Long targetParent = request.parentId();

        boolean isMove = targetParent != null && !targetParent.equals(node.getParentId());
        long fromParentId = node.getParentId() == null ? 0L : node.getParentId();
        if (isMove) {
            if (targetParent > 0) {
                requireOwnedDir(userId, targetParent);
                // R-C03 红线：禁止移入自身或其子目录（递归 CTE 一条 SQL 判定）
                if (mapper.countInSubtree(id, targetParent) > 0) {
                    throw new BizException(ErrorCode.MOVE_INTO_SUBDIR);
                }
            }
            node.setParentId(targetParent);
            node.setName(resolveUniqueName(userId, targetParent, newName));
        } else if (!newName.equals(node.getName())) {
            node.setName(resolveUniqueName(userId, node.getParentId(), newName));
        }

        mapper.updateById(node);
        if (isMove) {
            // 审计：移动（含前端拖拽），记录源/目标目录
            auditService.record(new AuditEvent(
                    userId, AuditActions.MOVE, String.valueOf(id), null,
                    Map.of("name", node.getName(), "id", id,
                            "fromParentId", fromParentId,
                            "toParentId", targetParent)));
        }
        return FileNodeVO.from(node);
    }

    // ---------- 批量移动（前端多选拖拽） ----------

    @Override
    @Transactional
    public List<FileNodeVO> batchMove(Long userId, BatchMoveRequest request) {
        long targetParent = request.targetParentId();
        if (targetParent > 0) {
            requireOwnedDir(userId, targetParent);
        }

        List<FileNodeVO> moved = new ArrayList<>();
        for (Long id : request.ids()) {
            FileNode node = requireOwnedNode(userId, id);
            long currentParent = node.getParentId() == null ? 0L : node.getParentId();
            if (currentParent == targetParent) {
                // 已在目标目录，跳过不记审计
                moved.add(FileNodeVO.from(node));
                continue;
            }
            if (targetParent > 0 && mapper.countInSubtree(id, targetParent) > 0) {
                throw new BizException(ErrorCode.MOVE_INTO_SUBDIR);
            }
            node.setParentId(targetParent);
            node.setName(resolveUniqueName(userId, targetParent, node.getName()));
            mapper.updateById(node);

            auditService.record(new AuditEvent(
                    userId, AuditActions.MOVE, String.valueOf(id), null,
                    Map.of("name", node.getName(), "id", id,
                            "fromParentId", currentParent,
                            "toParentId", targetParent)));
            moved.add(FileNodeVO.from(node));
        }
        return moved;
    }

    // ---------- R-C04 删除入回收站（级联软删） ----------

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        // 递归 CTE 一次 UPDATE 标记自身 + 全部子孙；
        // 幂等：不存在或已删除时影响行数为 0，按契约直接视为成功，不报错
        mapper.softDeleteCascade(userId, id);
    }

    // ---------- 内部工具 ----------

    /** 取本人名下未删除节点，否则 404 */
    private FileNode requireOwnedNode(Long userId, Long id) {
        FileNode node = mapper.selectById(id);
        if (node == null || !userId.equals(node.getOwnerId()) || node.getDeletedAt() != null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return node;
    }

    /** 取本人名下未删除目录，否则 404 */
    private FileNode requireOwnedDir(Long userId, Long id) {
        FileNode node = requireOwnedNode(userId, id);
        if (!Boolean.TRUE.equals(node.getIsDir())) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND, "目标不是目录");
        }
        return node;
    }

    /**
     * 同级重名自动改名：base 已被占用则在扩展名前插入 (2)(3)... 序号
     * （如 test.docx → test (2).docx；无扩展名/目录则尾部追加）。
     *
     * <p>TODO（并发兜底）：需 B 组在 files 表加部分唯一索引，防并发重名——
     * {@code CREATE UNIQUE INDEX ... ON files(owner_id, parent_id, name) WHERE deleted_at IS NULL}
     * 已登记在 docs/files-table-spec.md，待 B 组 migration 落实。
     */
    private String resolveUniqueName(Long userId, long parentId, String base) {
        List<String> siblings = mapper.selectSiblingNames(userId, parentId, base);
        if (!siblings.contains(base)) {
            return base;
        }
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        int i = 2;
        while (siblings.contains(stem + " (" + i + ")" + ext)) {
            i++;
        }
        return stem + " (" + i + ")" + ext;
    }
}
