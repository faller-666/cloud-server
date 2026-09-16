package com.company.cloud.files.recycle.service;

import com.company.cloud.common.result.PageResult;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.recycle.dto.RecycleItemVO;

/**
 * 回收站（任务书 04：R-C05 列表 / R-C06 还原与彻底删除）。
 */
public interface RecycleService {

    /** R-C05：回收站列表（仅顶层被删节点，按删除时间倒序分页；含 deletedAt/expireAt） */
    PageResult<RecycleItemVO> list(Long userId, int page, int size);

    /**
     * R-C06：还原。原父目录仍可用则回原位，否则落到根目录；
     * 目标位置重名自动追加 (2)(3) 后缀；子树级联还原。
     */
    FileNodeVO restore(Long userId, Long id);

    /**
     * R-C06：彻底删除。子树级联物理删除，释放配额，
     * 并对每个文件 sha256 递减引用计数（R-C07）。
     */
    void forceDelete(Long userId, Long id);
}
