package com.company.cloud.auth.repository;

import com.company.cloud.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * 统计仍处于 active 状态的管理员数量。
     * 用于「至少保留一个 admin」自我保护（对标 A3）：禁/降级最后一个可用管理员时应被拒绝。
     */
    long countByRoleAndStatus(String role, String status);

    /**
     * 管理端用户列表：按用户名模糊搜索 + 按状态筛选 + 分页
     *
     * <p>修复（C组审出）：Hibernate 6 对 null 的 String 参数按 bytea 绑定，
     * kw/status 不传时 lower(bytea) / text = bytea 直接 SQLGrammarException（50000）。
     * 显式 CAST(:x AS string) 强制按字符串绑定。
     */
    @Query("""
            SELECT u FROM User u WHERE
            (:kw IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:kw AS string), '%')))
            AND (:st IS NULL OR u.status = CAST(:st AS string))
            """)
    Page<User> search(@Param("kw") String keyword,
                      @Param("st") String status,
                      Pageable pageable);
}