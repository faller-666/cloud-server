package com.cloudstorage.storage.repository;

import com.cloudstorage.storage.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEntityRepository extends JpaRepository<FileEntity, Long> {
}