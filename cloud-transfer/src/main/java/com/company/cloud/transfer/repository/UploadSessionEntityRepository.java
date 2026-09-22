package com.company.cloud.transfer.repository;

import com.company.cloud.transfer.entity.UploadSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadSessionEntityRepository extends JpaRepository<UploadSessionEntity, Long> {

    /** 24h 未完成会话清理：查过期且仍在 uploading 的会话。 */
    List<UploadSessionEntity> findByStatusAndExpiresAtBefore(String status, OffsetDateTime now);

    /** 幂等 init：查同一用户、同一 sha256、未过期且仍在 uploading 的会话（丢 sessionId 后找回续传）。 */
    Optional<UploadSessionEntity> findFirstByUserIdAndSha256AndStatusAndExpiresAtAfter(
            Long userId, String sha256, String status, OffsetDateTime now);

    /** 传输任务列表（分页）：查当前用户全部会话。 */
    Page<UploadSessionEntity> findByUserId(Long userId, Pageable pageable);

    /** 传输任务列表（分页，按状态筛选）。 */
    Page<UploadSessionEntity> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    /** 清空已完成：删除当前用户指定状态的会话记录，返回删除条数。 */
    long deleteByUserIdAndStatus(Long userId, String status);
}