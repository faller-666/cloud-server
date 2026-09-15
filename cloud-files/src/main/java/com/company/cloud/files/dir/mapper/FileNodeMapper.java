package com.company.cloud.files.dir.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.cloud.files.dir.dto.BreadcrumbItem;
import com.company.cloud.files.dir.entity.FileNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * files 表数据访问。树形操作用 PostgreSQL 递归 CTE（见 FileNodeMapper.xml），
 * 禁止代码内递归查库（单目录 5000 文件 P95 <500ms 的验收全靠它）。
 */
@Mapper
public interface FileNodeMapper extends BaseMapper<FileNode> {

    /**
     * 面包屑：自当前节点向上递归到根，按从根到当前排序。
     */
    List<BreadcrumbItem> selectBreadcrumb(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 级联软删：自身 + 全部子孙打 deleted_at 标记，返回影响行数。
     */
    int softDeleteCascade(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * targetId 是否在 id 的子树中（含 id 自身）。移动校验用：
     * 返回值 > 0 表示目标是自身或子孙，必须拒绝（R-C03）。
     */
    int countInSubtree(@Param("id") Long id, @Param("targetId") Long targetId);

    /**
     * 同级下与 base 同名或形如 "base (n)" 的名字集合（重名自动改名用）。
     */
    @Select("""
            SELECT name FROM files
            WHERE owner_id = #{userId}
              AND parent_id = #{parentId}
              AND deleted_at IS NULL
              AND (name = #{base} OR name LIKE #{base} || ' (%' ESCAPE '\')
            """)
    List<String> selectSiblingNames(@Param("userId") Long userId,
                                    @Param("parentId") Long parentId,
                                    @Param("base") String base);
}
