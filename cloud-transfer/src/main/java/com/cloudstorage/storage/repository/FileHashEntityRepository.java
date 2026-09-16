package com.cloudstorage.storage.repository;

import com.cloudstorage.storage.entity.FileHashEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FileHashEntityRepository extends JpaRepository<FileHashEntity, String> {

    Optional<FileHashEntity> findBySha256(String sha256);

    /** 引用计数 +1（秒传命中 / 复用已有对象时）。 */
    @Modifying
    @Query("UPDATE FileHashEntity h SET h.refCount = h.refCount + 1 WHERE h.sha256 = :sha256")
    int incrementRefCount(@Param("sha256") String sha256);

    /** 引用计数 -1（彻底删除时，C 组调用）；归 0 由调用方物理删除 MinIO 对象。 */
    @Modifying
    @Query("UPDATE FileHashEntity h SET h.refCount = h.refCount - 1 WHERE h.sha256 = :sha256 AND h.refCount > 0")
    int decrementRefCount(@Param("sha256") String sha256);
}