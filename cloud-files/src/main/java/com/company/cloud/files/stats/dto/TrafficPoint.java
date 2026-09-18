package com.company.cloud.files.stats.dto;

import java.time.LocalDate;

/**
 * 单日流量点（R-C09，契约 GET /api/admin/stats/traffic?days=7）。
 *
 * @param date          日期（数据库时区按日聚合）
 * @param uploadBytes   当日上传字节总量
 * @param downloadBytes 当日下载字节总量
 */
public record TrafficPoint(
        LocalDate date,
        long uploadBytes,
        long downloadBytes
) {
}
