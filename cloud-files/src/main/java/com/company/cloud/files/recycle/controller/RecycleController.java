package com.company.cloud.files.recycle.controller;

import com.company.cloud.common.result.PageResult;
import com.company.cloud.common.result.Result;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.recycle.dto.RecycleItemVO;
import com.company.cloud.files.recycle.service.RecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站接口（任务书 04 §5：R-C05 / R-C06）。
 *
 * <p><b>鉴权说明（TODO）：</b>X-User-Id 请求头为开发期 Mock；
 * A 组 Security Filter 交付后改从 SecurityContext 取当前用户，签名不变。
 */
@Tag(name = "回收站", description = "回收站列表、还原、彻底删除（C 组）")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    @Operation(summary = "回收站列表（R-C05）：仅顶层被删节点，按删除时间倒序；含 deletedAt/expireAt")
    @GetMapping("/trash")
    public Result<PageResult<RecycleItemVO>> list(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(recycleService.list(userId, page, size));
    }

    @Operation(summary = "还原（R-C06）：原父目录可用则回原位，否则回根目录；重名自动改名")
    @PostMapping("/{id}/restore")
    public Result<FileNodeVO> restore(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        return Result.ok(recycleService.restore(userId, id));
    }
}
