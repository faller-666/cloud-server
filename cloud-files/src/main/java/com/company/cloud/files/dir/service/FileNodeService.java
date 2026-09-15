package com.company.cloud.files.dir.service;

import com.company.cloud.files.dir.dto.DirListResponse;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.dir.dto.MkdirRequest;
import com.company.cloud.files.dir.dto.UpdateNodeRequest;

/**
 * 目录树与文件管理（任务书 04：R-C01 ~ R-C04）。
 */
public interface FileNodeService {

    /** R-C01：列目录（分页 + 排序 + 面包屑） */
    DirListResponse list(Long userId, long parentId, int page, int size, String sort);

    /** R-C02：新建文件夹（同级重名自动追加 (2)(3) 后缀） */
    FileNodeVO mkdir(Long userId, MkdirRequest request);

    /** R-C03：重命名 / 移动（禁止移入自身子目录；目标重名自动改名） */
    FileNodeVO update(Long userId, Long id, UpdateNodeRequest request);

    /** R-C04：删除入回收站（级联软删全部子孙；幂等） */
    void delete(Long userId, Long id);
}
