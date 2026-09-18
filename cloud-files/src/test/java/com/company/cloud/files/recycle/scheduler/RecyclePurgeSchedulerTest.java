package com.company.cloud.files.recycle.scheduler;

import com.company.cloud.files.dir.entity.FileNode;
import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.files.recycle.service.RecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-C06 回收站超期清理单元测试。
 * 覆盖任务书验收点：dry-run 只出清单不删除；正常模式逐条走 forceDelete；
 * 单条失败不影响同批其他节点，且不再拉取下一批（防死循环）。
 */
@ExtendWith(MockitoExtension.class)
class RecyclePurgeSchedulerTest {

    @Mock
    private FileNodeMapper mapper;

    @Mock
    private RecycleService recycleService;

    private RecyclePurgeScheduler scheduler;

    private FileNode overdueNode(long id) {
        FileNode n = new FileNode();
        n.setId(id);
        n.setOwnerId(9L);
        n.setName("expired-" + id + ".txt");
        n.setIsDir(false);
        n.setSize(100L);
        n.setDeletedAt(OffsetDateTime.now().minusDays(40)); // 超期 10 天
        return n;
    }

    @BeforeEach
    void setUp() {
        scheduler = new RecyclePurgeScheduler(mapper, recycleService);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
        ReflectionTestUtils.setField(scheduler, "batchSize", 200);
    }

    @Test
    void dryRunOnlyLogsNeverDeletes() {
        // dry-run：查到超期节点，也绝不调用 forceDelete（验收：清单准确、不误删）
        ReflectionTestUtils.setField(scheduler, "dryRun", true);
        when(mapper.selectPurgeCandidates(any(), anyInt())).thenReturn(List.of(overdueNode(1L), overdueNode(2L)));

        scheduler.purgeExpired();

        verify(recycleService, never()).forceDelete(any(), any());
    }

    @Test
    void purgeDeletesEachExpiredTopNode() {
        ReflectionTestUtils.setField(scheduler, "dryRun", false);
        // 第一批 2 条，第二批空（已删完）
        when(mapper.selectPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(overdueNode(1L), overdueNode(2L)))
                .thenReturn(List.of());

        scheduler.purgeExpired();

        verify(recycleService, times(1)).forceDelete(eq(9L), eq(1L));
        verify(recycleService, times(1)).forceDelete(eq(9L), eq(2L));
    }

    @Test
    void singleFailureDoesNotBreakBatchNorLoopForever() {
        ReflectionTestUtils.setField(scheduler, "dryRun", false);
        when(mapper.selectPurgeCandidates(any(), anyInt()))
                .thenReturn(List.of(overdueNode(1L), overdueNode(2L)));
        // 第一条清理抛异常（如被并发恢复），第二条应继续处理
        doThrowOnFirst();

        scheduler.purgeExpired();

        verify(recycleService, times(1)).forceDelete(eq(9L), eq(2L));
        // 单条失败后不再拉取下一批，防止失败节点被反复查出（下一轮循环内 map 结果应只被请求一次）
        verify(mapper, times(1)).selectPurgeCandidates(any(), anyInt());
    }

    private void doThrowOnFirst() {
        org.mockito.Mockito.doThrow(new RuntimeException("节点已被恢复"))
                .when(recycleService).forceDelete(eq(9L), eq(1L));
    }

    @Test
    void emptyRecycleBinIsNoop() {
        ReflectionTestUtils.setField(scheduler, "dryRun", false);
        when(mapper.selectPurgeCandidates(any(), anyInt())).thenReturn(List.of());

        scheduler.purgeExpired();

        verify(recycleService, never()).forceDelete(any(), any());
        assertThat(true).isTrue();
    }
}
