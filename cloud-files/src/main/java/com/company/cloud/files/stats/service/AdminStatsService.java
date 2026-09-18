package com.company.cloud.files.stats.service;

import com.company.cloud.files.stats.dto.AdminStatsOverview;
import com.company.cloud.files.stats.dto.TopUser;
import com.company.cloud.files.stats.dto.TrafficPoint;

import java.util.List;

/**
 * 管理端大盘统计（R-C09）：全平台总览、用量 Top10、近 N 日流量曲线。
 */
public interface AdminStatsService {

    /** 全平台总览（服务端缓存 1 分钟，见实现类） */
    AdminStatsOverview overview();

    /** 用量 Top10 用户 */
    List<TopUser> topUsers();

    /** 近 days 日上传/下载流量（days 收敛到 1~90，无流量的日期补零点，便于前端画曲线） */
    List<TrafficPoint> traffic(int days);
}
