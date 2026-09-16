package com.company.cloud.files.dir.service;

import com.company.cloud.files.dir.dto.BatchMoveRequest;
import com.company.cloud.files.dir.dto.DirListResponse;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.dir.dto.MkdirRequest;
import com.company.cloud.files.dir.dto.UpdateNodeRequest;

import java.util.List;

/**
 * 目录树与文件管理（任务书 04：R-C01 ~ R-C04）。
 */
public interface FileNodeService {

    /** R-C01：列目录（分页 + 排序 + 面包屑）；keyword 非空时切换为全局搜索（忽略 parent） */
    DirListResponse list(Long userId, long parentId, int page, int size, String sort, String keyword);

    /** 目录树（前端树状导航/拖拽用）：一次性返回当前用户全部目录，扁平 id+parentId 列表 */
    List<FileNodeVO> tree(Long userId);

    /** R-C02：新建文件夹（同级重名自动追加 (2)(3) 后缀） */
    FileNodeVO mkdir(Long userId, MkdirRequest request);

    /** R-C03：重命名 / 移动（禁止移入自身子目录；目标重名自动改名） */
    FileNodeVO update(Long userId, Long id, UpdateNodeRequest request);

    /** 批量移动（前端多选拖拽）：同一目标目录，任一失败整体回滚 */
    List<FileNodeVO> batchMove(Long userId, BatchMoveRequest request);

    /** R-C04：删除入回收站（级联软删全部子孙；幂等） */
    void delete(Long userId, Long id);
}
