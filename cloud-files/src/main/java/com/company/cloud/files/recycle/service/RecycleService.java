package com.company.cloud.files.recycle.service;

import com.company.cloud.common.result.PageResult;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.recycle.dto.RecycleItemVO;

/**
 * 回收站（任务书 04：R-C05 列表 / R-C06 还原与彻底删除）。
 */
public interface RecycleService {

    /**
     * R-C05：回收站列表。parent 空/0 返回回收站顶层节点（父不在回收站中），parent&gt;0 返回 parent_id 子项；
     * size<=0 时返回全量（不分页），否则按删除时间倒序分页。
     */
    PageResult<RecycleItemVO> list(Long userId, int page, int size, Long parent);

    /**
     * R-C06：还原。targetParentId 为 null 时回原位置（父目录不可用则落根目录）；
     * 传 0 或指定目录时恢复到该目标目录；目标位置重名自动追加 (2)(3) 后缀；子树级联还原。
     */
    FileNodeVO restore(Long userId, Long id, Long targetParentId);

    /**
     * R-C06：彻底删除。子树级联物理删除，释放配额，
     * 并对每个文件 sha256 递减引用计数（R-C07）。
     */
    void forceDelete(Long userId, Long id);
}
