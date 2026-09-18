package com.company.cloud.files.recycle.scheduler;

import com.company.cloud.files.dir.entity.FileNode;
import com.company.cloud.files.dir.mapper.FileNodeMapper;
import com.company.cloud.files.recycle.service.RecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * R-C06：回收站超期自动物理清理（任务书 04 §3，P0）。
 *
 * <p>每天凌晨触发（默认 3 点，cron 可配），扫描 deleted_at 超过保留期（默认 30 天）
 * 的回收站<b>顶层节点</b>，逐个复用 {@link RecycleService#forceDelete} 链路：
 * 级联物理删行 → 释放配额 → 引用计数递减 → 归零物理清理 MinIO 对象，全程闭环。
 * 每条清理自带 delete_force 审计（用户 id 为节点 owner，detail 无 source 标记，
 * 与手动彻底删除一致；系统批处理特征可从时间（凌晨）与无 IP 区分）。
 *
 * <p><b>顶层节点语义</b>：与回收站列表一致——父目录在回收站中的节点不单独出现，
 * 随其顶层祖先整棵过期。级联软删同事务打标，deleted_at 相同，整树同时到期，无部分过期问题。
 *
 * <p><b>dry-run 模式（默认开启）</b>：只打印待清理清单不执行，供验收用例
 * "30 天清理任务 dry-run 清单准确、不误删 30 天内的文件"核对。核对通过后
 * 将 recycle.purge.dry-run 改为 false 即进入生产模式。
 *
 * <p>配置（application.yml）：
 * <pre>
 * recycle:
 *   retention-days: 30        # 保留期（天）
 *   purge:
 *     cron: "0 0 3 * * ?"     # 每天凌晨 3 点
 *     dry-run: true           # true=只打清单不删
 *     batch-size: 200          # 单轮最大处理条数（防长事务）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecyclePurgeScheduler {

    private final FileNodeMapper mapper;
    private final RecycleService recycleService;

    /** 回收站保留期（天）。注意：RecycleItemVO 展示的 expireAt 口径须与此值同步。 */
    @Value("${recycle.retention-days:30}")
    private int retentionDays;

    /** true=只打印待清理清单，不执行删除（验收用）。 */
    @Value("${recycle.purge.dry-run:true}")
    private boolean dryRun;

    /** 单轮最大处理条数，控制单批事务规模。 */
    @Value("${recycle.purge.batch-size:200}")
    private int batchSize;

    /**
     * 每天凌晨 3 点（默认）触发：清理超过保留期的回收站内容。
     *
     * <p>循环分批处理直至无候选；单条失败不影响同批其他节点，但本轮不再拉取新批次
     * （防失败节点被反复查出导致死循环），失败节点留待次日任务重试。
     */
    @Scheduled(cron = "${recycle.purge.cron:0 0 3 * * ?}")
    public void purgeExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusDays(retentionDays);
        log.info("[purge] 开始 retentionDays={} dryRun={} cutoff={}", retentionDays, dryRun, cutoff);

        int purged = 0;
        int failed = 0;
        while (true) {
            List<FileNode> batch = mapper.selectPurgeCandidates(cutoff, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (FileNode node : batch) {
                if (dryRun) {
                    long overdueDays = node.getDeletedAt() == null ? 0
                            : Duration.between(node.getDeletedAt(), now).toDays() - retentionDays;
                    log.info("[purge][dry-run] 待清理 id={} owner={} name={} isDir={} size={} deletedAt={} 超期{}天",
                            node.getId(), node.getOwnerId(), node.getName(),
                            node.getIsDir(), node.getSize(), node.getDeletedAt(), Math.max(overdueDays, 0));
                    continue;
                }
                try {
                    recycleService.forceDelete(node.getOwnerId(), node.getId());
                    purged++;
                } catch (Exception e) {
                    failed++;
                    // 单条失败不断批：库与 MinIO 状态由 forceDelete 事务保证一致，
                    // 残留节点下轮不再重复拉取（本轮结束即 break），次日重试。
                    log.warn("[purge] 清理失败 id={} owner={}: {}",
                            node.getId(), node.getOwnerId(), e.getMessage());
                }
            }
            if (dryRun) {
                log.info("[purge][dry-run] 清单已列出（首批 {} 条，未执行删除）。核对无误后将 recycle.purge.dry-run 改为 false。",
                        batch.size());
                if (batch.size() >= batchSize) {
                    log.info("[purge][dry-run] 返回条数已达单批上限 {}，超期节点可能不止这些；"
                            + "如需完整清单可临时调大 recycle.purge.batch-size 后再跑一轮。", batchSize);
                }
                break;
            }
            if (failed > 0) {
                // 本批有失败：不再拉取下一批，避免失败节点被重复查出造成循环
                break;
            }
        }
        if (!dryRun) {
            log.info("[purge] 完成：本次清理 {} 个顶层节点，失败 {} 个（失败项次日重试）", purged, failed);
        }
    }
}
