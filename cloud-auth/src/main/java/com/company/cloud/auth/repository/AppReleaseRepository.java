package com.company.cloud.auth.repository;

import com.company.cloud.auth.entity.AppRelease;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 应用发布（App/EXE 更新）数据访问层。
 */
public interface AppReleaseRepository extends JpaRepository<AppRelease, Long> {

    /** 检查更新：取某平台 published 中，versionCode 高于入参的最高一条（仅该条对灰度/全量判断有意义） */
    Optional<AppRelease> findFirstByPlatformAndStatusAndVersionCodeGreaterThanOrderByVersionCodeDesc(
            String platform, String status, int versionCode);

    /** 管理端列表：按平台 + 状态筛选，versionCode 倒序 */
    Page<AppRelease> findByPlatformAndStatusOrderByVersionCodeDesc(
            String platform, String status, Pageable pageable);

    /** 管理端列表：仅按平台筛选，versionCode 倒序 */
    Page<AppRelease> findByPlatformOrderByVersionCodeDesc(String platform, Pageable pageable);

    /** 管理端列表：仅按状态筛选，versionCode 倒序（platform 不填时用） */
    Page<AppRelease> findByStatusOrderByVersionCodeDesc(String status, Pageable pageable);

    /** 某平台当前最大 versionCode（自动 +1 用，无记录返回 0） */
    @Query("SELECT COALESCE(MAX(r.versionCode), 0) FROM AppRelease r WHERE r.platform = :p")
    int maxVersionCodeByPlatform(@Param("p") String platform);

    boolean existsByIdAndStatus(Long id, String status);
}