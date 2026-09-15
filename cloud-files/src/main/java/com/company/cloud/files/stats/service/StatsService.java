package com.company.cloud.files.stats.service;

import com.company.cloud.files.stats.dto.StatsOverview;

/**
 * 统计大盘（任务书 04：R-C10）。
 */
public interface StatsService {

    /** 个人存储概览：文件/目录/字节/今日新增/回收站/扩展名分布 TOP10 */
    StatsOverview overview(Long userId);
}
