package com.company.cloud.transfer.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * 配额操作——users.used_bytes 由 B 组增减维护（任务书 CS-DOC-03 §7）。
 *
 * 用原生 SQL 直接操作 users 表（表 Owner 为 A 组，B 组只读写 used_bytes/quota_bytes），
 * 不引入完整 UserEntity，解耦 A 组表结构。
 * 核心：条件 UPDATE 在单条 SQL 内原子防超卖（PG 自动加行锁），
 *      比「SELECT … FOR UPDATE 再判断」更简洁，且同样满足任务书「PG 事务 + 行锁」要求。
 */
@Repository
public class UserQuotaRepository {

    @PersistenceContext
    private EntityManager em;

    /** 预检：只读判断会不会超配额（供 init 快速拒绝，不加锁）。 */
    public boolean checkAvailable(long userId, long delta) {
        try {
            Long remaining = (Long) em.createNativeQuery(
                            "SELECT quota_bytes - used_bytes FROM users WHERE id = :id")
                    .setParameter("id", userId)
                    .getSingleResult();
            return remaining != null && remaining >= delta;
        } catch (NoResultException e) {
            return false;
        }
    }

    /**
     * 实扣配额（complete 时）——条件 UPDATE 原子防超卖。
     * @return true 扣减成功；false 表示配额不足（已被并发占满），调用方应拒绝并回滚。
     */
    public boolean tryReserve(long userId, long delta) {
        int updated = em.createNativeQuery(
                        "UPDATE users SET used_bytes = used_bytes + :delta, updated_at = now() " +
                        "WHERE id = :id AND status = 'active' AND used_bytes + :delta <= quota_bytes")
                .setParameter("delta", delta)
                .setParameter("id", userId)
                .executeUpdate();
        return updated == 1;
    }

    /** 释放配额（彻底删除 / abort 回滚时）。 */
    public void release(long userId, long delta) {
        em.createNativeQuery(
                        "UPDATE users SET used_bytes = GREATEST(0, used_bytes - :delta), updated_at = now() " +
                        "WHERE id = :id")
                .setParameter("delta", delta)
                .setParameter("id", userId)
                .executeUpdate();
    }
}