package com.cloudstorage.module.user.repository;

import com.cloudstorage.module.user.entity.User;
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
     */
    @Query("""
            SELECT u FROM User u WHERE
            (:kw IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :kw, '%')))
            AND (:st IS NULL OR u.status = :st)
            """)
    Page<User> search(@Param("kw") String keyword,
                      @Param("st") String status,
                      Pageable pageable);
}