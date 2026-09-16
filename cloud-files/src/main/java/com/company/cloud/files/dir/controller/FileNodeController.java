package com.company.cloud.files.dir.controller;

import com.company.cloud.common.result.Result;
import com.company.cloud.files.dir.dto.BatchMoveRequest;
import com.company.cloud.files.dir.dto.DirListResponse;
import com.company.cloud.files.dir.dto.FileNodeVO;
import com.company.cloud.files.dir.dto.MkdirRequest;
import com.company.cloud.files.dir.dto.UpdateNodeRequest;
import com.company.cloud.files.dir.service.FileNodeService;
import com.company.cloud.files.recycle.service.RecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件管理接口（任务书 04 §5）。
 *
 * <p><b>鉴权说明（TODO）：</b>当前从 X-User-Id 请求头取用户 ID 仅为开发期 Mock；
 * A 组 Security Filter 交付后，改为从 SecurityContext 获取当前用户，
 * 本 Controller 签名不变，业务零改动。
 */
@Tag(name = "文件管理", description = "目录树、重命名/移动、回收站入口（C 组）")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileNodeController {

    private final FileNodeService fileNodeService;
    private final RecycleService recycleService;

    @Operation(summary = "列目录（R-C01）：分页 + 排序 + 面包屑；keyword 非空时全局搜索")
    @GetMapping
    public Result<DirListResponse> list(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @RequestParam(defaultValue = "0") long parent,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword) {
        return Result.ok(fileNodeService.list(userId, parent, page, size, sort, keyword));
    }

    @Operation(summary = "目录树：一次性返回当前用户全部目录（扁平 id+parentId 列表，前端自行建树）")
    @GetMapping("/tree")
    public Result<java.util.List<FileNodeVO>> tree(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId) {
        return Result.ok(fileNodeService.tree(userId));
    }

    @Operation(summary = "新建文件夹（R-C02）：同级重名自动改名")
    @PostMapping("/mkdir")
    public Result<FileNodeVO> mkdir(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @Valid @RequestBody MkdirRequest request) {
        return Result.ok(fileNodeService.mkdir(userId, request));
    }

    @Operation(summary = "重命名 / 移动（R-C03）：禁止移入自身子目录")
    @PatchMapping("/{id}")
    public Result<FileNodeVO> update(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateNodeRequest request) {
        return Result.ok(fileNodeService.update(userId, id, request));
    }

    @Operation(summary = "批量移动：多选文件/目录一次移入同一目标目录，任一失败整体回滚")
    @PatchMapping("/batch-move")
    public Result<java.util.List<FileNodeVO>> batchMove(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @Valid @RequestBody BatchMoveRequest request) {
        return Result.ok(fileNodeService.batchMove(userId, request));
    }

    @Operation(summary = "删除（R-C04/R-C06）：默认入回收站；?force=1 彻底删除（级联物理删+释放配额）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int force) {
        if (force == 1) {
            recycleService.forceDelete(userId, id);
        } else {
            fileNodeService.delete(userId, id);
        }
        return Result.ok();
    }
}
