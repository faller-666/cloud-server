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

    boolean existsByUsername(String username);

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