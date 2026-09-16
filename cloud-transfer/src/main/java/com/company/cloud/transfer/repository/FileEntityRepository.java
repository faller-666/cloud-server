package com.company.cloud.transfer.repository;

import com.company.cloud.transfer.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {

    /** 秒传命中：同 sha256 的存活文件记录（内容寻址去重）。 */
    Optional<FileEntity> findFirstBySha256AndDeletedAtIsNull(String sha256);

    /** 指定 sha256 的存活文件数（彻底删除时判断是否还有引用，归 0 才物理删 MinIO 对象）。 */
    long countBySha256AndDeletedAtIsNull(String sha256);
}