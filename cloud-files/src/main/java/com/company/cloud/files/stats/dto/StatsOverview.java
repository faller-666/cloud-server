package com.company.cloud.files.stats.dto;

import java.util.List;

/**
 * 统计大盘视图（R-C10）：按 owner 聚合的个人存储概览。
 *
 * @param totalFiles    存活文件总数
 * @param totalDirs     存活目录总数
 * @param totalBytes    存活文件总字节
 * @param todayNew      今日新增（文件 + 目录）
 * @param recycleCount  回收站条数
 * @param recycleBytes  回收站占用字节
 * @param typeBreakdown 扩展名分布 TOP10（按文件数降序）
 */
public record StatsOverview(
        long totalFiles,
        long totalDirs,
        long totalBytes,
        long todayNew,
        long recycleCount,
        long recycleBytes,
        List<TypeCount> typeBreakdown
) {
    /**
     * 扩展名分布项。
     *
     * @param ext   扩展名（小写，不含点）
     * @param count 文件数
     * @param bytes 总字节
     */
    public record TypeCount(String ext, long count, long bytes) {
    }
}
