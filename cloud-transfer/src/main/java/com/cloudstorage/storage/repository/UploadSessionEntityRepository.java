package com.cloudstorage.storage.repository;

import com.cloudstorage.storage.entity.UploadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface UploadSessionEntityRepository extends JpaRepository<UploadSessionEntity, Long> {

    /** 24h 未完成会话清理：查过期且仍在 uploading 的会话。 */
    List<UploadSessionEntity> findByStatusAndExpiresAtBefore(String status, OffsetDateTime now);
}