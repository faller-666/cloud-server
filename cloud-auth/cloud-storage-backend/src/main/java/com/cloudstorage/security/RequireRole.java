package com.cloudstorage.security;

import java.lang.annotation.*;

/**
 * 角色控制注解（对标任务书 RolesGuard、@RequireRole("admin")）
 * 标注在 Controller 或方法上，由 RolesInterceptor 校验当前用户角色。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    String value();
}