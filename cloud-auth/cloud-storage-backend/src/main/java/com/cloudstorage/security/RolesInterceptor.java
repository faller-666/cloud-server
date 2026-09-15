package com.cloudstorage.security;

import com.cloudstorage.common.api.ResultCodes;
import com.cloudstorage.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * 角色拦截器（对标任务书 RolesGuard）
 * 校验 @RequireRole("admin") 标注操作：当前用户角色不足 → 40301
 */
@Component
public class RolesInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        Method method = hm.getMethod();
        RequireRole require = method.getAnnotation(RequireRole.class);
        if (require == null) {
            require = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require == null) {
            return true; // 未标注角色限制
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser cu)
                || !require.value().equals(cu.getRole())) {
            throw ApiException.of(ResultCodes.FORBIDDEN);
        }
        return true;
    }
}