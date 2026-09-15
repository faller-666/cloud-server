package com.company.cloud.files.stats.service.impl;

import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.files.stats.dto.StatsOverview;
import com.company.cloud.files.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final FileNodeMapper mapper;

    @Override
    public StatsOverview overview(Long userId) {
        return new StatsOverview(
                mapper.countActiveFiles(userId),
                mapper.countActiveDirs(userId),
                mapper.sumActiveBytes(userId),
                mapper.countTodayNew(userId),
                mapper.countRecycle(userId),
                mapper.sumRecycleBytes(userId),
                mapper.selectTypeBreakdown(userId)
        );
    }
}
